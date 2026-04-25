package org.huahao.totp

import android.util.Base64
import java.net.URLDecoder

object GoogleMigrationParser {
    
    data class OtpEntry(
        val secret: String,
        val name: String,
        val issuer: String,
        val algorithm: String = "SHA1",
        val digits: Int = 6,
        val period: Int = 30
    )
    
    fun parse(migrationUri: String): List<OtpEntry> {
        if (!migrationUri.startsWith("otpauth-migration://")) {
            return emptyList()
        }
        
        try {
            // 提取 data 参数
            val dataParam = migrationUri.substringAfter("data=").substringBefore("&")
            // URL 解码
            val urlDecoded = URLDecoder.decode(dataParam, "UTF-8")
            // Base64 解码
            val bytes = Base64.decode(urlDecoded, Base64.DEFAULT)
            
            // 手动解析 Protobuf
            return parseProtobuf(bytes)
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }
    
    private fun parseProtobuf(bytes: ByteArray): List<OtpEntry> {
        val entries = mutableListOf<OtpEntry>()
        var offset = 0
        
        while (offset < bytes.size) {
            val tag = readVarint(bytes, offset)
            offset += tag.second
            val fieldNumber = tag.first shr 3
            val wireType = tag.first and 0x07
            
            when (wireType) {
                2 -> { // Length-delimited
                    val lengthResult = readVarint(bytes, offset)
                    val length = lengthResult.first
                    offset += lengthResult.second
                    
                    if (fieldNumber == 1) { // OtpParameters
                        val otpEntry = parseOtpParameters(bytes, offset, length)
                        if (otpEntry != null) {
                            entries.add(otpEntry)
                        }
                    }
                    offset += length
                }
                0 -> { // Varint
                    val varintResult = readVarint(bytes, offset)
                    offset += varintResult.second
                }
                else -> {
                    // 跳过未知类型
                    val varintResult = readVarint(bytes, offset)
                    offset += varintResult.second
                }
            }
        }
        
        return entries
    }
    
    private fun parseOtpParameters(bytes: ByteArray, start: Int, length: Int): OtpEntry? {
        var secret: ByteArray? = null
        var name = ""
        var issuer = ""
        var algorithm = 1 // SHA1
        var digits = 1 // 6 digits
        var type = 2 // TOTP
        
        var offset = start
        val end = start + length
        
        while (offset < end) {
            val tag = readVarint(bytes, offset)
            offset += tag.second
            val fieldNumber = tag.first shr 3
            val wireType = tag.first and 0x07
            
            when (wireType) {
                2 -> { // Length-delimited
                    val lengthResult = readVarint(bytes, offset)
                    val len = lengthResult.first
                    offset += lengthResult.second
                    
                    when (fieldNumber) {
                        1 -> secret = bytes.copyOfRange(offset, offset + len)
                        2 -> name = String(bytes, offset, len, Charsets.UTF_8)
                        3 -> issuer = String(bytes, offset, len, Charsets.UTF_8)
                    }
                    offset += len
                }
                0 -> { // Varint
                    val varintResult = readVarint(bytes, offset)
                    offset += varintResult.second
                    
                    when (fieldNumber) {
                        4 -> algorithm = varintResult.first
                        5 -> digits = varintResult.first
                        6 -> type = varintResult.first
                    }
                }
            }
        }
        
        if (secret == null) return null
        
        val algorithmStr = when (algorithm) {
            1 -> "SHA1"
            2 -> "SHA256"
            3 -> "SHA512"
            4 -> "MD5"
            else -> "SHA1"
        }
        
        val digitsInt = when (digits) {
            1 -> 6
            2 -> 8
            else -> 6
        }
        
        // 将 secret 字节数组转换为 Base32 字符串
        val secretBase32 = bytesToBase32(secret)
        
        return OtpEntry(
            secret = secretBase32,
            name = name,
            issuer = issuer,
            algorithm = algorithmStr,
            digits = digitsInt,
            period = 30
        )
    }
    
    private fun readVarint(bytes: ByteArray, offset: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var index = offset
        
        while (index < bytes.size) {
            val b = bytes[index].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            index++
            if ((b and 0x80) == 0) break
            shift += 7
        }
        
        return Pair(result, index - offset)
    }
    
    private fun bytesToBase32(bytes: ByteArray): String {
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val result = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            
            while (bitsLeft >= 5) {
                val index = (buffer shr (bitsLeft - 5)) and 0x1F
                result.append(base32Chars[index])
                bitsLeft -= 5
            }
        }
        
        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1F
            result.append(base32Chars[index])
            // 补 padding（虽然标准 Base32 需要，但 TOTP 不需要）
        }
        
        return result.toString()
    }
}