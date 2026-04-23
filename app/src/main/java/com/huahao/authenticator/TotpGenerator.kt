package com.huahao.authenticator

import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class TotpGenerator {
    companion object {
        @Throws(NoSuchAlgorithmException::class, InvalidKeyException::class)
        fun generate(secret: String, timeStep: Long, digits: Int, algorithm: String): String {
            val key = decodeBase32(secret)
            val data = ByteArray(8)
            var value = timeStep
            for (i in 7 downTo 0) {
                data[i] = (value and 0xFF).toByte()
                value = value shr 8
            }

            // 转换算法名称为 Java 兼容格式
            val javaAlgorithm = when (algorithm.uppercase()) {
                "SHA1" -> "HmacSHA1"
                "SHA256" -> "HmacSHA256"
                "SHA512" -> "HmacSHA512"
                else -> "HmacSHA1"
            }

            val mac = Mac.getInstance(javaAlgorithm)
            mac.init(SecretKeySpec(key, javaAlgorithm))
            val hash = mac.doFinal(data)

            val offset = hash[hash.size - 1].toInt() and 0x0F
            val code = ((hash[offset].toInt() and 0x7F) shl 24) or
                    ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                    ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                    (hash[offset + 3].toInt() and 0xFF)

            val result = code % Math.pow(10.0, digits.toDouble()).toInt()
            return String.format("%0${digits}d", result)
        }

        fun getTimeStep(period: Int): Long {
            return System.currentTimeMillis() / 1000 / period
        }

        private fun decodeBase32(secret: String): ByteArray {
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
            var bits = 0
            var value = 0
            val result = mutableListOf<Byte>()

            for (c in secret.uppercase()) {
                if (c !in alphabet) continue

                value = (value shl 5) or alphabet.indexOf(c)
                bits += 5

                if (bits >= 8) {
                    result.add(((value shr (bits - 8)) and 0xFF).toByte())
                    bits -= 8
                }
            }

            // 处理剩余的 bits
            if (bits > 0) {
                result.add(((value shl (8 - bits)) and 0xFF).toByte())
            }

            return result.toByteArray()
        }
    }
}