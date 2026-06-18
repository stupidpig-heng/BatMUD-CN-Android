package com.batmudcn.translate

import android.util.Log
import com.batmudcn.data.AppDatabase
import com.batmudcn.data.TranslationEntity
import com.batmudcn.util.Constants

/**
 * Translation engine with two-level cache: memory LRU + Room SQLite.
 * Mirrors Python translator.py Translator class.
 */
class Translator(
    private val appId: String,
    private val secretKey: String,
    private val modelType: String = Constants.DEFAULT_MODEL_TYPE,
    private val timeout: Int = Constants.DEFAULT_TIMEOUT,
    cacheSize: Int = Constants.DEFAULT_CACHE_SIZE,
    database: AppDatabase,
) {
    companion object {
        private const val TAG = "Translator"
    }

    private val apiClient = BaiduApiClient(
        appId = appId,
        secretKey = secretKey,
        modelType = modelType,
        timeoutSec = timeout,
    )

    // Level 1: Memory LRU cache
    private val memoryCache = LruCache<String, String>(cacheSize)

    // Level 2: Room persistent cache
    private val diskCache = database.translationDao()

    // Stats
    private var apiCalls = 0
    private var memoryHits = 0
    private var diskHits = 0

    /**
     * Translate a single text with full cache lookup chain:
     * Memory → Disk → API
     */
    suspend fun translate(text: String): String {
        if (text.isBlank()) return text
        if (!text.any { it.code < 128 && it.isLetter() }) return text

        val key = makeKey(text)

        // 1. Memory cache
        memoryCache.get(key)?.let {
            memoryHits++
            return it
        }

        // 2. Disk cache
        diskCache.get(key)?.let { cached ->
            diskHits++
            memoryCache.put(key, cached) // backfill memory
            return cached
        }

        // 3. API call
        val result = apiClient.translate(text)
        apiCalls++

        // Store in both caches
        memoryCache.put(key, result)
        diskCache.put(TranslationEntity(key = key, value = result))

        return result
    }

    /**
     * Batch translate multiple texts.
     */
    suspend fun translateBatch(texts: List<String>): List<String> {
        if (texts.isEmpty()) return emptyList()

        val results = mutableMapOf<String, String>()
        val needTranslate = mutableListOf<String>()

        for (t in texts) {
            if (t.isBlank() || !t.any { it.code < 128 && it.isLetter() }) {
                results[t] = t
                continue
            }

            val key = makeKey(t)

            // Memory
            val memCached = memoryCache.get(key)
            if (memCached != null) {
                memoryHits++
                results[t] = memCached
                continue
            }

            // Disk
            val diskCached = diskCache.get(key)
            if (diskCached != null) {
                diskHits++
                memoryCache.put(key, diskCached)
                results[t] = diskCached
                continue
            }

            needTranslate.add(t)
        }

        if (needTranslate.isEmpty()) {
            return texts.map { results[it] ?: it }
        }

        // Batch API call
        try {
            val batchResult = apiClient.translateBatch(needTranslate)
            apiCalls += needTranslate.size

            for ((orig, trans) in needTranslate.zip(batchResult)) {
                val key = makeKey(orig)
                memoryCache.put(key, trans)
                diskCache.put(TranslationEntity(key = key, value = trans))
                results[orig] = trans
            }
        } catch (e: Exception) {
            Log.w(TAG, "Batch translation failed: ${e.message}")
            for (t in needTranslate) {
                results[t] = t
            }
        }

        return texts.map { results[it] ?: it }
    }

    val cacheStats: CacheStats
        get() = CacheStats(
            memorySize = memoryCache.size,
            apiCalls = apiCalls,
            memoryHits = memoryHits,
            diskHits = diskHits,
        )

    data class CacheStats(
        val memorySize: Int,
        val apiCalls: Int,
        val memoryHits: Int,
        val diskHits: Int,
    )

    /**
     * Generate a cache key from text.
     * Short text: normalized text itself.
     * Long text: truncated + hash.
     * Mirrors Python _make_key.
     */
    private fun makeKey(text: String): String {
        val normalized = text.trim()
        return if (normalized.length <= 100) {
            normalized
        } else {
            val hash = normalized.hashCode().toString(16).take(8)
            "${normalized.take(80)}#$hash"
        }
    }
}
