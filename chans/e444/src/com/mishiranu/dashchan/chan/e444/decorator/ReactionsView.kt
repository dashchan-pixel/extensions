package com.mishiranu.dashchan.chan.e444.decorator

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.TypedValue
import android.view.ContextThemeWrapper
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
                    cornerRadius = theme.cornerRadius
                    setColor(theme.cardBackgroundColor)
                    if (selected) {
                        setStroke(dp(density, SELECTED_STROKE_DP), theme.accentColor)
                    }
                }
        }
    }

    /**
     * The picker's grid. Cells are [PICKER_CELL_SIZE_DP] where the row has the room for them, and
     * otherwise give up their padding, down to none, to keep [PICKER_MIN_COLUMNS] of them on a row:
     * a picker that breaks after the seventh icon wastes most of a row and reads as an accident.
     *
     * The width to divide up is not something the extension can work out ahead of time -- it is the
     * dialog's, which depends on the screen, on the 16dp its background insets itself by, and on the
     * display size the user picked -- so the fit is measured here rather than encoded as dp values
     * that happen to come out right on one phone. Only the padding shrinks while it can, so the
     * icons stay the size of a menu entry's check box and the whole cell stays the tap target.
     */
    private class PickerLayout(
        context: Context,
    ) : FlowLayout(context) {
        private val density = context.resources.displayMetrics.density
        private val iconSize = dp(density, PICKER_ICON_SIZE_DP)
        private val preferredCellSize = dp(density, PICKER_CELL_SIZE_DP)
        private var appliedCellSize = 0

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            applyCellSize(cellSizeFor(widthMeasureSpec))
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }

        private fun cellSizeFor(widthMeasureSpec: Int): Int {
            val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED || available <= 0) {
                return preferredCellSize
            }
            val gaps = (PICKER_MIN_COLUMNS - 1) * horizontalSpacing
            // Floored at the icon size: a screen too narrow for eight of those is better served by
            // seven icons a row than by eight the icon cannot be told apart in.
            return ((available - gaps) / PICKER_MIN_COLUMNS).coerceIn(iconSize, preferredCellSize)
        }

        private fun applyCellSize(cellSize: Int) {
            if (cellSize == appliedCellSize) {
                return
            }
            appliedCellSize = cellSize
            val padding = (cellSize - iconSize) / 2
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                child.layoutParams.width = cellSize
                child.layoutParams.height = cellSize
                child.setPadding(padding, padding, padding, padding)
            }
        }
    }

    companion object {
        private const val CHIP_GAP_DP = 3
        private const val SELECTED_STROKE_DP = 1
        private const val COUNT_TEXT_SP = 12f

        const val ICON_SIZE_DP = 18

        /**
         * Icon size in the context menu picker. Matches the 24dp check box the client's own menu
         * entries use, so no icon is visually larger than a normal entry's control.
         */
        private const val PICKER_ICON_SIZE_DP = 24

        /**
         * How wide a picker cell would like to be: the icon plus 8dp of padding on either side,
         * which is what makes it a comfortable tap target. [PickerLayout] hands that padding back
         * when a row would otherwise not hold [PICKER_MIN_COLUMNS] icons.
         */
        private const val PICKER_CELL_SIZE_DP = PICKER_ICON_SIZE_DP + 2 * 8

        private const val PICKER_GAP_DP = 4

        /**
         * How many icons a row is expected to hold before the cells are allowed to keep their full
         * [PICKER_CELL_SIZE_DP]. Eight fits the boards' icon sets into a couple of rows on a phone.
         */
        private const val PICKER_MIN_COLUMNS = 8

        /**
         * Used when the theme has no [android.R.attr.listPreferredItemPaddingStart], which no
         * Material-derived theme is missing. Matches `dialog_padding_material`, the value every
         * dialog theme resolves that attribute to.
         */
        private const val PICKER_FALLBACK_SIDE_PADDING_DP = 24

        private const val PICKER_MAX_HEIGHT_FRACTION = 0.4f

        fun dp(
            density: Float,
            value: Int,
        ): Int = (density * value).roundToInt()

        /**
         * The gap the context menu's own rows leave between their text and the dialog's edge, so
         * that the picker can line its icons up with the entry titles above them.
         *
         * A row is `select_dialog_item`, which pads itself by
         * [android.R.attr.listPreferredItemPaddingStart], and every Material dialog theme points
         * that attribute at `dialogPreferredPadding` -- 24dp, where a list row's own value is 16dp.
         * The decorator is handed the activity's context rather than the dialog's, so the alert
         * dialog theme has to be resolved first, the way `AlertDialog.Builder` resolves it, or the
         * attribute read back is the list row's.
         */
        private fun menuEntryTextPadding(
            context: Context,
            density: Float,
        ): Int {
            val themeArray = context.obtainStyledAttributes(intArrayOf(android.R.attr.alertDialogTheme))
            val themeResId =
                try {
                    themeArray.getResourceId(0, 0)
                } finally {
                    themeArray.recycle()
                }
            val dialogContext =
                if (themeResId != 0) ContextThemeWrapper(context, themeResId) else context
            val paddingArray =
                dialogContext.obtainStyledAttributes(
                    intArrayOf(android.R.attr.listPreferredItemPaddingStart),
                )
            return try {
                paddingArray.getDimensionPixelSize(0, dp(density, PICKER_FALLBACK_SIDE_PADDING_DP))
            } finally {
                paddingArray.recycle()
            }
        }

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
            val picker = PickerLayout(context)
            picker.horizontalSpacing = dp(density, PICKER_GAP_DP)
            picker.verticalSpacing = dp(density, PICKER_GAP_DP)
            val sidePadding = menuEntryTextPadding(context, density)
            picker.setPadding(sidePadding, dp(density, 8), sidePadding, dp(density, 16))
            for (icon in icons) {
                val iconView = ImageView(context)
                // Every cell is the same fixed size and the drawable is fitted inside it, so icons
                // that differ in intrinsic size still come out uniform. The size and the padding
                // that centres the icon in it are PickerLayout's to set, once it knows the width.
                iconView.scaleType = ImageView.ScaleType.FIT_CENTER
                iconView.adjustViewBounds = false
                iconView.isClickable = true
                iconView.isFocusable = true
                iconView.setOnClickListener { onReact(icon) }
                picker.addView(iconView)
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
