package com.mishiranu.dashchan.chan.e444.decorator

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Lays children out left to right, wrapping to a new line when the next one would not fit.
 *
 * The previous build pulled in Google's Flexbox library for this. That library was one of the four
 * dependencies dropped when the extension was rewritten (521 KB down to 65 KB), and wrapping a row
 * of small chips is the only thing it was used for, so it is done here instead.
 */
internal open class FlowLayout(
    context: Context,
) : ViewGroup(context) {
    var horizontalSpacing = 0
    var verticalSpacing = 0

    override fun generateDefaultLayoutParams(): LayoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val horizontalPadding = paddingLeft + paddingRight
        val availableWidth =
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
                Int.MAX_VALUE
            } else {
                MeasureSpec.getSize(widthMeasureSpec) - horizontalPadding
            }
        var lineWidth = 0
        var lineHeight = 0
        var totalWidth = 0
        var totalHeight = 0
        forEachVisibleChild { child ->
            // Measured through getChildMeasureSpec so a child that asked for an exact size gets it.
            // Measuring everything as UNSPECIFIED instead made each child fall back to its content's
            // own size, which is why a row of fixed-size icons came out ragged.
            child.measure(
                getChildMeasureSpec(widthMeasureSpec, horizontalPadding, child.layoutParams.width),
                getChildMeasureSpec(
                    heightMeasureSpec,
                    paddingTop + paddingBottom,
                    child.layoutParams.height,
                ),
            )
            val spacing = if (lineWidth > 0) horizontalSpacing else 0
            if (lineWidth > 0 && lineWidth + spacing + child.measuredWidth > availableWidth) {
                totalWidth = maxOf(totalWidth, lineWidth)
                totalHeight += lineHeight + verticalSpacing
                lineWidth = child.measuredWidth
                lineHeight = child.measuredHeight
            } else {
                lineWidth += spacing + child.measuredWidth
                lineHeight = maxOf(lineHeight, child.measuredHeight)
            }
        }
        totalWidth = maxOf(totalWidth, lineWidth)
        totalHeight += lineHeight
        setMeasuredDimension(
            resolveSize(totalWidth + horizontalPadding, widthMeasureSpec),
            resolveSize(totalHeight + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    override fun onLayout(
        changed: Boolean,
        l: Int,
        t: Int,
        r: Int,
        b: Int,
    ) {
        val lineEnd = r - l - paddingRight
        var x = paddingLeft
        var y = paddingTop
        var lineHeight = 0
        var lineStart = true
        forEachVisibleChild { child ->
            if (!lineStart && x + horizontalSpacing + child.measuredWidth > lineEnd) {
                x = paddingLeft
                y += lineHeight + verticalSpacing
                lineHeight = 0
                lineStart = true
            }
            if (!lineStart) {
                x += horizontalSpacing
            }
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
            x += child.measuredWidth
            lineHeight = maxOf(lineHeight, child.measuredHeight)
            lineStart = false
        }
    }

    private inline fun forEachVisibleChild(action: (View) -> Unit) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != View.GONE) {
                action(child)
            }
        }
    }
}
