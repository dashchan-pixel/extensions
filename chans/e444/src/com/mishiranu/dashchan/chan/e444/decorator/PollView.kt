package com.mishiranu.dashchan.chan.e444.decorator

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import chan.content.ChanPostDecorator
import com.mishiranu.dashchan.chan.e444.E444PostExtra
import kotlin.math.roundToInt

/**
 * A post's poll: one clickable bar per answer, filled to that answer's share of the vote.
 *
 * Created once per recycled post holder and rebound to whatever post the holder shows, so every bar
 * keeps the identity of the row it currently displays and only animates when that identity is
 * unchanged -- otherwise scrolling would animate every reused bar from a stranger's numbers.
 */
internal class PollView(
    context: Context,
) : LinearLayout(context) {
    private val bars = ArrayList<Bar>()
    private var onVote: ((Int) -> Unit)? = null

    init {
        orientation = VERTICAL
    }

    fun bind(
        extra: E444PostExtra,
        theme: ChanPostDecorator.PostTheme,
        rowKeyPrefix: String,
        selectedIndex: Int,
        onVote: (Int) -> Unit,
    ) {
        this.onVote = onVote
        val total = extra.pollTotalVotes
        while (bars.size < extra.pollAnswers.size) {
            val bar = Bar(context)
            bars.add(bar)
            addView(bar.root, barLayoutParams())
        }
        for (index in bars.indices) {
            val bar = bars[index]
            if (index >= extra.pollAnswers.size) {
                bar.root.visibility = View.GONE
                continue
            }
            bar.root.visibility = View.VISIBLE
            bar.bind(
                extra.pollAnswers[index],
                extra.pollVotes[index],
                total,
                theme,
                "$rowKeyPrefix:$index",
                index == selectedIndex,
            )
            bar.root.setOnClickListener { this.onVote?.invoke(index) }
        }
    }

    /** Stops every running fill animation, for a view whose post scrolled away. */
    fun unbind() {
        for (bar in bars) {
            bar.cancelAnimation()
        }
    }

    private fun barLayoutParams(): LayoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(BAR_GAP_DP)
        }

    private fun dp(value: Int): Int =
        TypedValue
            .applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value.toFloat(),
                resources.displayMetrics,
            ).roundToInt()

    private inner class Bar(
        context: Context,
    ) {
        val root = FrameLayout(context)
        private val fill = View(context)
        private val answerView = TextView(context)
        private val resultView = TextView(context)
        private var animator: ValueAnimator? = null
        private var rowKey: String? = null
        private var fillRatio = 0f

        init {
            root.isClickable = true
            root.isFocusable = true
            fill.pivotX = 0f
            root.addView(
                fill,
                FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START),
            )
            val content = LinearLayout(context)
            content.orientation = HORIZONTAL
            content.gravity = Gravity.CENTER_VERTICAL
            content.setPadding(dp(8), dp(6), dp(8), dp(6))
            root.addView(
                content,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            answerView.setSingleLine(true)
            answerView.ellipsize = TextUtils.TruncateAt.END
            answerView.setTextSize(TypedValue.COMPLEX_UNIT_SP, ANSWER_TEXT_SP)
            content.addView(
                answerView,
                LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) },
            )
            resultView.setSingleLine(true)
            resultView.gravity = Gravity.END
            resultView.setTextSize(TypedValue.COMPLEX_UNIT_SP, RESULT_TEXT_SP)
            content.addView(
                resultView,
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT),
            )
            // The fill is a plain view resized to the answer's share, so its width has to be
            // recomputed whenever the bar itself is measured differently.
            root.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
                if (right - left != oldRight - oldLeft) {
                    applyFillWidth(fillRatio)
                }
            }
        }

        fun bind(
            answer: String,
            votes: Int,
            total: Int,
            theme: ChanPostDecorator.PostTheme,
            rowKey: String,
            selected: Boolean,
        ) {
            answerView.text = answer
            answerView.setTextColor(theme.postTextColor)
            resultView.text = formatResult(votes, total)
            resultView.setTextColor(theme.metaTextColor)
            root.background = barBackground(theme, selected)
            fill.background =
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(CORNER_RADIUS_DP).toFloat()
                    setColor(applyAlpha(theme.accentColor, FILL_ALPHA))
                }
            val ratio = if (total > 0) votes.toFloat() / total else 0f
            val sameRow = rowKey == this.rowKey
            this.rowKey = rowKey
            if (sameRow) {
                animateFillWidth(ratio)
            } else {
                cancelAnimation()
                applyFillWidth(ratio)
            }
        }

        fun cancelAnimation() {
            animator?.cancel()
            animator = null
        }

        private fun animateFillWidth(target: Float) {
            if (target == fillRatio) {
                return
            }
            cancelAnimation()
            val from = fillRatio
            animator =
                ValueAnimator.ofFloat(from, target).apply {
                    duration = FILL_ANIMATION_MS
                    addUpdateListener { applyFillWidth(it.animatedValue as Float) }
                    start()
                }
        }

        private fun applyFillWidth(ratio: Float) {
            fillRatio = ratio
            val width = root.width
            val params = fill.layoutParams
            params.width = if (width > 0) (width * ratio).roundToInt() else 0
            fill.layoutParams = params
        }

        private fun barBackground(
            theme: ChanPostDecorator.PostTheme,
            selected: Boolean,
        ): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(CORNER_RADIUS_DP).toFloat()
                setColor(applyAlpha(theme.postTextColor, BACKGROUND_ALPHA))
                if (selected) {
                    setStroke(dp(SELECTED_STROKE_DP), theme.accentColor)
                }
            }

        private fun formatResult(
            votes: Int,
            total: Int,
        ): String {
            val percentage = if (total > 0) (votes * PERCENT / total.toFloat()).roundToInt() else 0
            return "$percentage% ($votes)"
        }
    }

    private companion object {
        const val BAR_GAP_DP = 5
        const val CORNER_RADIUS_DP = 5
        const val SELECTED_STROKE_DP = 1
        const val ANSWER_TEXT_SP = 13f
        const val RESULT_TEXT_SP = 12f
        const val FILL_ALPHA = 0.24f
        const val BACKGROUND_ALPHA = 0.08f
        const val FILL_ANIMATION_MS = 420L
        const val PERCENT = 100f

        fun applyAlpha(
            color: Int,
            alpha: Float,
        ): Int =
            Color.argb(
                (Color.alpha(color) * alpha).roundToInt(),
                Color.red(color),
                Color.green(color),
                Color.blue(color),
            )
    }
}
