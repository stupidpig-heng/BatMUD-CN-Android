package com.batmudcn.translate

import android.util.Log
import com.batmudcn.util.Constants
import com.batmudcn.util.Md5Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Baidu AI Text Translate API client.
 * Uses MD5 signature auth with QPS rate limiting.
 *
 * Mirrors Python translator.py Translator._call_api.
 */
class BaiduApiClient(
    private val appId: String,
    private val secretKey: String,
    private val modelType: String = Constants.DEFAULT_MODEL_TYPE,
    private val reference: String = Constants.MUD_REFERENCE,
    timeoutSec: Int = Constants.DEFAULT_TIMEOUT,
) {
    companion object {
        private const val TAG = "BaiduApi"
        private const val QPS_LIMIT = Constants.DEFAULT_QPS
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
        .readTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
        .writeTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
        .build()

    // QPS rate limiting
    private val mutex = Mutex()
    private var lastRequestTime: Long = 0
    private val minIntervalMs = 1000L / QPS_LIMIT

    /**
     * Translate a single query string.
     * @return Translated text, or original text on failure.
     */
    suspend fun translate(query: String): String {
        if (query.isBlank()) return query
        if (!query.any { it.code < 128 && it.isLetter() }) return query

        // QPS rate limiting
        mutex.withLock {
            val elapsed = System.currentTimeMillis() - lastRequestTime
            if (elapsed < minIntervalMs) {
                kotlinx.coroutines.delay(minIntervalMs - elapsed)
            }
            lastRequestTime = System.currentTimeMillis()
        }

        return withContext(Dispatchers.IO) {
            try {
                val salt = (10000..99999).random().toString()
                val sign = Md5Utils.baiduSign(appId, query, salt, secretKey)

                val url = buildUrl(query, salt, sign)

                Log.d(TAG, "API request: q=${query.take(60)}...")

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: throw Exception("Empty response")

                Log.d(TAG, "API response: ${body.take(200)}")

                parseResponse(body)

            } catch (e: Exception) {
                Log.w(TAG, "API call failed: ${e.message}")
                query // fallback: return original
            }
        }
    }

    /**
     * Batch translate multiple texts by joining with newlines.
     */
    suspend fun translateBatch(texts: List<String>): List<String> {
        if (texts.isEmpty()) return emptyList()
        if (texts.size == 1) return listOf(translate(texts[0]))

        val combined = texts.joinToString("\n")
        val result = translate(combined)
        val parts = result.split("\n")

        // Ensure we have the right number of parts
        return texts.mapIndexed { idx, original ->
            parts.getOrElse(idx) { original }
        }
    }

    private fun buildUrl(query: String, salt: String, sign: String): String {
        val sb = StringBuilder(Constants.BAIDU_API_URL)
        sb.append("?q=").append(java.net.URLEncoder.encode(query, "UTF-8"))
        sb.append("&from=en&to=zh")
        sb.append("&appid=").append(appId)
        sb.append("&salt=").append(salt)
        sb.append("&sign=").append(sign)
        sb.append("&model_type=").append(modelType)
        if (modelType == "llm") {
            sb.append("&reference=").append(java.net.URLEncoder.encode(reference, "UTF-8"))
        }
        return sb.toString()
    }

    private fun parseResponse(body: String): String {
        val json = JSONObject(body)

        // Check for error
        if (json.has("error_code")) {
            val code = json.optString("error_code", "unknown")
            val msg = json.optString("error_msg", "unknown")
            val friendly = errorMessage(code, msg)
            throw RuntimeException("API error [$code]: $friendly")
        }

        // Extract translation result
        val transResult = json.optJSONArray("trans_result")
        if (transResult != null && transResult.length() > 0) {
            return transResult.getJSONObject(0).optString("dst", "")
        }

        // Fallback: return empty
        return ""
    }

    private fun errorMessage(code: String, default: String): String {
        return when (code) {
            "52000" -> "成功(无结果)"
            "52001" -> "请求超时"
            "52002" -> "系统错误"
            "52003" -> "未授权 - 请检查 APPID 或是否已开通大模型翻译服务"
            "54000" -> "参数错误"
            "54001" -> "签名错误 - 请检查密钥"
            "54003" -> "访问频率受限"
            "54004" -> "账户余额不足"
            "54005" -> "长query请求频繁"
            "58000" -> "客户端IP非法"
            "58001" -> "译文语言不支持"
            "58002" -> "服务已关闭"
            "58004" -> "model_type 参数错误"
            "59002" -> "翻译指令(reference)过长"
            "59003" -> "请求文本过长"
            "59004" -> "QPS超限"
            "90107" -> "认证未通过"
            else -> default
        }
    }
}
