package com.mishiranu.dashchan.chan.e444.decorator

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import chan.content.ChanPostDecorator
import com.mishiranu.dashchan.chan.e444.E444MenuLink
import com.mishiranu.dashchan.chan.e444.E444MenuSection
import kotlin.math.roundToInt

/**
 * A post's menu: the board's navigation buttons, under the headings the poster grouped them into.
 *
 * The buttons are not in the comment. The board sends them as a field of the post and its own pages
 * build them in JavaScript, so a client that renders only the comment shows nothing of them at all
 * -- which is what the previous builds did, sticky navigator threads included.
 *
 * Created once per recycled post holder and rebound to whatever post the holder shows, so sections
 * and buttons are reused and the ones the current post has no use for are hidden rather than
 * dropped.
 */
internal class MenuView(
    context: Context,
) : LinearLayout(context) {
    private val sections = ArrayList<Section>()

    init {
        orientation = VERTICAL
        // Every section leads with a gap, so the menu has to end with one too: what follows it in
        // the decorated post is the reactions row, which would otherwise sit against the last row
        // of buttons. The poll gets away without one because each of its bars carries the same gap
        // below it, the last one included.
        setPadding(0, 0, 0, dp(resources.displayMetrics.density, SECTION_GAP_DP))
    }

    fun bind(
        menu: List<E444MenuSection>,
        theme: ChanPostDecorator.PostTheme,
        onNavigate: (String) -> Unit,
    ) {
        while (sections.size < menu.size) {
            val section = Section(context)
            sections.add(section)
            addView(section.root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        for (index in sections.indices) {
            val section = sections[index]
            if (index >= menu.size) {
                section.root.visibility = View.GONE
                continue
            }
            section.root.visibility = View.VISIBLE
            section.bind(menu[index], theme, onNavigate)
        }
    }

    /** One section: its heading, and the grid its buttons wrap in. */
    private class Section(
        context: Context,
    ) {
        val root = LinearLayout(context)
        private val headingView = TextView(context)
        private val grid = ButtonGrid(context)
        private val buttons = ArrayList<MenuButton>()

        init {
            val density = context.resources.displayMetrics.density
            root.orientation = LinearLayout.VERTICAL
            root.setPadding(0, dp(density, SECTION_GAP_DP), 0, 0)
            headingView.setTypeface(headingView.typeface, Typeface.BOLD)
            root.addView(
                headingView,
                LinearLayout
                    .LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { bottomMargin = dp(density, HEADING_GAP_DP) },
            )
            grid.horizontalSpacing = dp(density, BUTTON_GAP_DP)
            grid.verticalSpacing = dp(density, BUTTON_GAP_DP)
            root.addView(
                grid,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        fun bind(
            section: E444MenuSection,
            theme: ChanPostDecorator.PostTheme,
            onNavigate: (String) -> Unit,
        ) {
            headingView.text = section.name
            headingView.setTextColor(theme.postTextColor)
            headingView.setTextSize(TypedValue.COMPLEX_UNIT_PX, theme.postTextSize)
            // A section the poster left unnamed is still a section, but an empty heading would only
            // add a blank line above its buttons.
            headingView.visibility = if (section.name.isEmpty()) View.GONE else View.VISIBLE
            // Cells hold the same number of characters whatever size the user reads posts at, since
            // the labels are what they have to be wide enough for.
            grid.preferredCellWidth = (PREFERRED_CELL_CHARACTERS * theme.postTextSize).roundToInt()
            while (buttons.size < section.links.size) {
                val button = MenuButton(root.context)
                buttons.add(button)
                grid.addView(button.view)
            }
            for (index in buttons.indices) {
                val button = buttons[index]
                if (index >= section.links.size) {
                    button.view.visibility = View.GONE
                    continue
                }
                button.view.visibility = View.VISIBLE
                button.bind(section.links[index], theme, onNavigate)
            }
        }
    }

    /**
     * One button. Its drawables are built once and recoloured on bind rather than replaced: a
     * navigator post carries dozens of these, and they are rebound on every scroll past the post.
     */
    private class MenuButton(
        context: Context,
    ) {
        val view = TextView(context)
        private val fill = GradientDrawable()
        private val mask = GradientDrawable()
        private val ripple: RippleDrawable

        init {
            val density = context.resources.displayMetrics.density
            fill.shape = GradientDrawable.RECTANGLE
            mask.shape = GradientDrawable.RECTANGLE
            // Only the mask's shape is used, to clip the ripple to the rounded corners; its colour
            // is never drawn.
            mask.setColor(Color.WHITE)
            ripple = RippleDrawable(ColorStateList.valueOf(Color.TRANSPARENT), fill, mask)
            view.background = ripple
            view.isClickable = true
            view.isFocusable = true
            view.gravity = Gravity.CENTER
            // The labels are the board's, not something this end can shorten, and two lines is what
            // a cell wide enough to read holds of a thread's name before it has to give up on it.
            view.maxLines = BUTTON_MAX_LINES
            view.ellipsize = TextUtils.TruncateAt.END
            view.setPadding(
                dp(density, BUTTON_SIDE_PADDING_DP),
                0,
                dp(density, BUTTON_SIDE_PADDING_DP),
                0,
            )
            view.layoutParams =
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(density, BUTTON_MIN_HEIGHT_DP),
                )
        }

        fun bind(
            link: E444MenuLink,
            theme: ChanPostDecorator.PostTheme,
            onNavigate: (String) -> Unit,
        ) {
            view.text = link.label
            view.setTextColor(theme.accentColor)
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, theme.postTextSize)
            fill.cornerRadius = theme.cornerRadius
            mask.cornerRadius = theme.cornerRadius
            fill.setColor(applyAlpha(theme.postTextColor, BACKGROUND_ALPHA))
            // Without a ripple a tap on a flat rectangle gives no sign it landed, and the board it
            // navigates to takes a moment to answer.
            ripple.setColor(ColorStateList.valueOf(applyAlpha(theme.accentColor, RIPPLE_ALPHA)))
            // Tall enough for the labels that wrap, at whatever size the post is read at, so every
            // cell of a row is the same height instead of each one being as tall as its own text.
            val density = view.resources.displayMetrics.density
            view.layoutParams.height =
                maxOf(
                    dp(density, BUTTON_MIN_HEIGHT_DP),
                    view.lineHeight * BUTTON_MAX_LINES + 2 * dp(density, BUTTON_VERTICAL_PADDING_DP),
                )
            view.setOnClickListener { onNavigate(link.url) }
        }
    }

    /**
     * A section's buttons: equal-width cells, as many to a row as fit a whole
     * [preferredCellWidth] one, and never fewer than [MIN_COLUMNS].
     *
     * The board lays these out as a CSS grid of fixed-width cells, which leaves a ragged gap at the
     * end of every row on a screen its width does not divide. The width to divide up is the post's,
     * which depends on the screen, on the post's own padding and on the display size the user
     * picked, so it is measured here rather than assumed.
     */
    private class ButtonGrid(
        context: Context,
    ) : FlowLayout(context) {
        var preferredCellWidth = 0

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            applyCellWidth(cellWidthFor(widthMeasureSpec))
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }

        private fun cellWidthFor(widthMeasureSpec: Int): Int {
            val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED ||
                available <= 0 ||
                preferredCellWidth <= 0
            ) {
                return preferredCellWidth
            }
            val columns =
                ((available + horizontalSpacing) / (preferredCellWidth + horizontalSpacing))
                    .coerceAtLeast(MIN_COLUMNS)
            return ((available - (columns - 1) * horizontalSpacing) / columns).coerceAtLeast(1)
        }

        /**
         * Checked per child rather than against the last width applied: buttons are added to a
         * grid that has already been measured, and a grid whose width did not change would
         * otherwise leave every new one at its own text's width.
         */
        private fun applyCellWidth(width: Int) {
            if (width <= 0) {
                return
            }
            for (index in 0 until childCount) {
                val params = getChildAt(index).layoutParams
                if (params.width != width) {
                    params.width = width
                }
            }
        }
    }

    private companion object {
        const val SECTION_GAP_DP = 6
        const val HEADING_GAP_DP = 4
        const val BUTTON_GAP_DP = 4
        const val BUTTON_MIN_HEIGHT_DP = 42
        const val BUTTON_SIDE_PADDING_DP = 6
        const val BUTTON_VERTICAL_PADDING_DP = 4
        const val BUTTON_MAX_LINES = 2
        const val BACKGROUND_ALPHA = 0.1f
        const val RIPPLE_ALPHA = 0.3f

        /**
         * How wide a button would like to be, counted in comment-text sizes rather than in dp. The
         * board asks for 200 CSS pixels, and eleven of them is about the same on a phone read at the
         * default text size -- half a post, which is the two columns its own narrow layout uses.
         */
        const val PREFERRED_CELL_CHARACTERS = 11f

        /**
         * How few buttons a row may hold. The board's own narrow layout puts two of them on a row,
         * and one button a row reads as a list of links rather than as a menu.
         */
        const val MIN_COLUMNS = 2

        fun dp(
            density: Float,
            value: Int,
        ): Int = (density * value).roundToInt()

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
