package com.mishiranu.dashchan.chan.fourchan

internal class ThreadsWithTailCache private constructor() {
    private val cache =
        object : LinkedHashMap<String, Any>(MAXIMUM_CAPACITY, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Any>): Boolean = size > MAXIMUM_CAPACITY
        }

    @Synchronized
    fun contains(threadNumber: String?): Boolean = if (threadNumber != null) cache[threadNumber] != null else false

    @Synchronized
    fun add(threadNumber: String?) {
        if (threadNumber != null) {
            cache[threadNumber] = VALUE
        }
    }

    companion object {
        private const val MAXIMUM_CAPACITY = 20
        private const val LOAD_FACTOR = 1f
        private val VALUE = Any()

        @JvmField
        val INSTANCE = ThreadsWithTailCache()
    }
}
