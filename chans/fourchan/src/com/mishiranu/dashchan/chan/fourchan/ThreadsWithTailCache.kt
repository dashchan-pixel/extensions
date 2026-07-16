package com.mishiranu.dashchan.chan.fourchan

import java.util.LinkedHashMap

internal class ThreadsWithTailCache private constructor() {
	private val MAXIMUM_CAPACITY = 20
	private val LOAD_FACTOR = 1f

	private val cache = object : LinkedHashMap<String, Any>(MAXIMUM_CAPACITY, LOAD_FACTOR, true) {
		override fun removeEldestEntry(eldest: Map.Entry<String, Any>): Boolean {
			return size > MAXIMUM_CAPACITY
		}
	}

	@Synchronized
	fun contains(threadNumber: String): Boolean {
		return cache[threadNumber] != null
	}

	@Synchronized
	fun add(threadNumber: String) {
		cache[threadNumber] = VALUE
	}

	companion object {
		@JvmField
		val INSTANCE = ThreadsWithTailCache()
		private val VALUE = Any()
	}
}
