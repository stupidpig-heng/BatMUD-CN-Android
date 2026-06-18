package com.batmudcn.translate

import java.util.LinkedHashMap

/**
 * Simple LRU (Least Recently Used) cache.
 * Mirrors Python translator.py LRUCache class.
 */
class LruCache<K, V>(private val maxSize: Int = 2000) {
    private val map = object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V) {
        map[key] = value
    }

    val size: Int
        @Synchronized
        get() = map.size

    @Synchronized
    fun clear() = map.clear()
}
