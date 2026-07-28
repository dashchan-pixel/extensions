package com.mishiranu.dashchan.chan.e444.decorator

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import chan.content.ChanPostDecorator
import com.mishiranu.dashchan.chan.e444.E444Reaction
import kotlin.math.roundToInt

/**
 * A [android.widget.ScrollView] that refuses to grow past [maxHeight].
 */
private class MaxHeightScrollView(
    context: Context,
) : android.widget.ScrollView(context) {
    var maxHeight = 0

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val limited =
            if (maxHeight > 0) {
                MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
            } else {
                heightMeasureSpec
            }
        super.onMeasure(widthMeasureSpec, limited)
    }
}

/**
 * A post's reactions: one chip per reaction, showing its icon and how many times it was used.
 *
 * Icons are loaded through [ChanPostDecorator.PostContext.loadImage], which binds the request to the
 * target view, so a chip recycled onto a different reaction cannot end up showing the icon a
 * finished request was carrying. The previous build hand-rolled that: a process-wide unbounded
 * bitmap cache plus per-icon lists of `WeakReference<TextView>`, each waiter re-checked against a
 * view tag on delivery.
 */
internal class ReactionsView(
    context: Context,
) : FlowLayout(context) {
    private val chips = ArrayList<Chip>()

    init {
        horizontalSpacing = dp(CHIP_GAP_DP)
        verticalSpacing = dp(CHIP_GAP_DP)
    }

    fun bind(
        reactions: List<E444Reaction>,
        theme: ChanPostDecorator.PostTheme,
        selectedIcons: Set<String>,
        iconUri: (String) -> Uri,
        postContext: ChanPostDecorator.PostContext,
        onReact: (String) -> Unit,
    ) {
        while (chips.size < reactions.size) {
            val chip = Chip(context)
            chips.add(chip)
            addView(chip.root)
        }
        for (index in chips.indices) {
            val chip = chips[index]
            if (index >= reactions.size) {
                chip.root.visibility = View.GONE
                continue
            }
            val reaction = reactions[index]
            chip.root.visibility = View.VISIBLE
            chip.bind(reaction, theme, reaction.icon in selectedIcons)
            postContext.loadImage(iconUri(reaction.icon), chip.iconView)
            chip.root.setOnClickListener { onReact(reaction.icon) }
        }
    }

    private fun dp(value: Int): Int = dp(resources.displayMetrics.density, value)

    /**
     * One reaction chip: the icon with its count beside it.
     */
    private class Chip(
        context: Context,
    ) {
        val root = LinearLayout(context)
        val iconView = ImageView(context)
        private val countView = TextView(context)

        init {
            val density = context.resources.displayMetrics.density
            root.orientation = LinearLayout.HORIZONTAL
            root.gravity = Gravity.CENTER_VERTICAL
            root.isClickable = true
            root.isFocusable = true
            root.setPadding(
                dp(density, 6),
                dp(density, 4),
                dp(density, 6),
                dp(density, 4),
            )
            iconView.scaleType = ImageView.ScaleType.FIT_CENTER
            root.addView(iconView, dp(density, ICON_SIZE_DP), dp(density, ICON_SIZE_DP))
            countView.setTextSize(TypedValue.COMPLEX_UNIT_SP, COUNT_TEXT_SP)
            root.addView(
                countView,
                LinearLayout
                    .LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { leftMargin = dp(density, 4) },
            )
        }

        fun bind(
            reaction: E444Reaction,
            theme: ChanPostDecorator.PostTheme,
            selected: Boolean,
        ) {
            countView.text = reaction.count.toString()
            countView.setTextColor(if (selected) theme.accentColor else theme.metaTextColor)
            val density = root.resources.displayMetrics.density
            root.background =
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(density, CORNER_RADIUS_DP).toFloat()
                    setColor(theme.cardBackgroundColor)
                    if (selected) {
                        setStroke(dp(density, SELECTED_STROKE_DP), theme.accentColor)
                    }
                }
        }
    }

    companion object {
        private const val CHIP_GAP_DP = 3
        private const val CORNER_RADIUS_DP = 4
        private const val SELECTED_STROKE_DP = 1
        private const val COUNT_TEXT_SP = 12f

        const val ICON_SIZE_DP = 18

        /**
         * Icon size in the context menu picker. Matches the 24dp check box the client's own menu
         * entries use, so no icon is visually larger than a normal entry's control.
         */
        private const val PICKER_ICON_SIZE_DP = 24

        /**
         * Padding around each picker icon, which is what makes the cell a comfortable tap target
         * without letting the icon itself grow past a check box.
         */
        private const val PICKER_ICON_PADDING_DP = 8

        private const val PICKER_MAX_HEIGHT_FRACTION = 0.4f

        fun dp(
            density: Float,
            value: Int,
        ): Int = (density * value).roundToInt()

        /**
         * Builds the picker shown under the post context menu: every icon the board allows, sized to
         * be tappable. Not a [ReactionsView] because these carry no counts and no selection.
         */
        fun createPicker(
            context: Context,
            icons: List<String>,
            iconUri: (String) -> Uri,
            postContext: ChanPostDecorator.PostContext,
            onReact: (String) -> Unit,
        ): View {
            val density = context.resources.displayMetrics.density
            val picker = FlowLayout(context)
            picker.horizontalSpacing = dp(density, 4)
            picker.verticalSpacing = dp(density, 4)
            picker.setPadding(
                dp(density, 16),
                dp(density, 8),
                dp(density, 16),
                dp(density, 16),
            )
            val padding = dp(density, PICKER_ICON_PADDING_DP)
            val cellSize = dp(density, PICKER_ICON_SIZE_DP) + 2 * padding
            for (icon in icons) {
                val iconView = ImageView(context)
                // Every cell is the same fixed size and the drawable is fitted inside it, so icons
                // that differ in intrinsic size still come out uniform.
                iconView.scaleType = ImageView.ScaleType.FIT_CENTER
                iconView.adjustViewBounds = false
                iconView.setPadding(padding, padding, padding, padding)
                iconView.isClickable = true
                iconView.isFocusable = true
                iconView.setOnClickListener { onReact(icon) }
                picker.addView(iconView, cellSize, cellSize)
                postContext.loadImage(iconUri(icon), iconView)
            }
            // A board may allow far more icons than fit above the menu, so the picker scrolls and
            // never claims more than a fraction of the screen -- the entry list has to stay usable.
            val scroll = MaxHeightScrollView(context)
            scroll.maxHeight =
                (context.resources.displayMetrics.heightPixels * PICKER_MAX_HEIGHT_FRACTION)
                    .roundToInt()
            scroll.addView(
                picker,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            return scroll
        }
    }
}
