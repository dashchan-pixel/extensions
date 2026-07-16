package com.mishiranu.dashchan.chan.fourchan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import java.util.ArrayList
import java.util.Arrays

object FourchanCaptchaUtils {
	@JvmStatic
	fun findCenterOffset(image: Bitmap): Int? {
		// Find offset of central non-empty space to the center of the image
		// Array structure: [non-empty, empty, non-empty, empty, ..., non-empty]
		val ranges = ArrayList<Int>()
		var empty = false
		var rangeLength = 0
		val line = IntArray(image.height)
		for (i in 0 until image.width) {
			image.getPixels(line, 0, 1, i, 0, 1, line.size)
			var emptyCount = 0
			for (c in line) {
				if (c shr 24 == 0) {
					emptyCount++
				}
			}
			val itEmpty = emptyCount >= 10
			if (itEmpty == empty) {
				rangeLength++
			} else {
				empty = itEmpty
				ranges.add(rangeLength)
				rangeLength = 1
			}
		}
		ranges.add(rangeLength)
		if (empty) {
			ranges.add(0)
		}
		// Length should always be an odd number
		if (ranges.size % 2 != 1) {
			throw IllegalStateException()
		}
		return if (ranges.size == 1) {
			null
		} else {
			val centerIndex = ranges.size / 2
			var cx = (ranges[centerIndex - 1] + ranges[centerIndex] + ranges[centerIndex + 1]) / 2
			for (i in 0 until centerIndex - 1) {
				cx += ranges[i]
			}
			image.width / 2 - cx
		}
	}

	interface BinarySearchCallback<T : Throwable> {
		fun getIndex(images: Array<Bitmap>): Int?
	}

	@JvmStatic
	@Throws(Throwable::class)
	fun <T : Throwable> binarySearchOffset(
			image: Bitmap,
			background: Bitmap,
			maxCount: Int,
			baseOffset: Int,
			callback: BinarySearchCallback<T>
	): Int? {
		var min = 0
		var max = background.width - image.width
		val minStep = 3
		val bitmaps = arrayOfNulls<Bitmap>(maxCount)
		val canvases = arrayOfNulls<Canvas>(maxCount)
		try {
			while (max - min >= 2 * minStep) {
				val count = Math.min(maxCount, (max - min + minStep - 1) / minStep)
				for (i in 0 until count) {
					if (bitmaps[i] == null) {
						val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
						bitmaps[i] = bitmap
						canvases[i] = Canvas(bitmap)
					}
					val dx = min + (max - min) * i / (count - 1)
					canvases[i]!!.drawBitmap(background, (baseOffset - dx).toFloat(), 0f, null)
					canvases[i]!!.drawBitmap(image, baseOffset.toFloat(), 0f, null)
				}
				// TODO Display only "count" images after releasing a bug fix in ForegroundManager
				for (i in count until maxCount) {
					if (bitmaps[i] == null) {
						val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
						bitmaps[i] = bitmap
						canvases[i] = Canvas(bitmap)
					}
					bitmaps[i]!!.eraseColor(0x00000000)
				}
				@Suppress("UNCHECKED_CAST")
				val result = callback.getIndex(Arrays.copyOf(bitmaps, maxCount) as Array<Bitmap>)
				if (result == null) {
					return null
				} else if (result < 0 || result >= count) {
					break
				} else if (result == 0) {
					max = min + (max - min) / (count - 1) - 1
				} else if (result == count - 1) {
					min = min + (max - min) * (count - 2) / (count - 1) + 1
				} else {
					val newMin = min + (max - min) * (result - 1) / (count - 1) + 1
					val newMax = min + (max - min) * (result + 1) / (count - 1) - 1
					min = newMin
					max = newMax
				}
			}
			return -(min + max) / 2
		} finally {
			for (bitmap in bitmaps) {
				bitmap?.recycle()
			}
		}
	}

	// Transforms white into transparent
	private val CAPTCHA_FILTER = ColorMatrixColorFilter(floatArrayOf(
			0f, 0f, 0f, 0f, 0f,
			0f, 0f, 0f, 0f, 0f,
			0f, 0f, 0f, 0f, 0f,
			-1f, -1f, -1f, 1f, 0f
	))

	@JvmStatic
	fun create(image: Bitmap, background: Bitmap?, offset: Int): Bitmap {
		val tmp = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
		val tmpCanvas = Canvas(tmp)
		if (background != null) {
			tmpCanvas.drawBitmap(background, offset.toFloat(), 0f, null)
		}
		tmpCanvas.drawBitmap(image, 0f, 0f, null)
		val result = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
		val paint = Paint()
		paint.colorFilter = CAPTCHA_FILTER
		Canvas(result).drawBitmap(tmp, 0f, 0f, paint)
		tmp.recycle()
		return result
	}
}
