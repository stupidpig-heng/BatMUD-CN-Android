package com.batmudcn.util

import java.security.MessageDigest

object Md5Utils {
    fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /** Generate Baidu API sign: MD5(appid + query + salt + secretKey) */
    fun baiduSign(appId: String, query: String, salt: String, secretKey: String): String {
        return md5(appId + query + salt + secretKey)
    }
}
