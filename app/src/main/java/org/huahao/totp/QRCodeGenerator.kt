package org.huahao.totp

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object QRCodeGenerator {
    fun generate(entry: AuthEntry, size: Int = 512): Bitmap? {
        return try {
            val uri = buildOtpUri(entry)
            val hints = hashMapOf<EncodeHintType, Any>()
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            hints[EncodeHintType.MARGIN] = 1

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(uri, BarcodeFormat.QR_CODE, size, size, hints)

            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun buildOtpUri(entry: AuthEntry): String {
        val label = if (entry.issuer.isNotBlank() && entry.account.isNotBlank()) {
            "${entry.issuer}:${entry.account}"
        } else if (entry.issuer.isNotBlank()) {
            entry.issuer
        } else {
            entry.account
        }

        val params = mutableListOf("secret" to entry.secret)
        
        // 添加 issuer 参数，这是标准格式中重要的部分
        if (entry.issuer.isNotBlank()) {
            params.add("issuer" to entry.issuer)
        }
        
        // 只有当不是默认值时才添加这些参数
        if (entry.algorithm != "SHA1") {
            params.add("algorithm" to entry.algorithm)
        }
        if (entry.digits != 6) {
            params.add("digits" to entry.digits.toString())
        }
        if (entry.period != 30) {
            params.add("period" to entry.period.toString())
        }

        val paramString = params.joinToString("&") { (k, v) -> "$k=$v" }
        
        // 使用正确的 URL 编码方式
        val encodedLabel = android.net.Uri.encode(label)
        return "otpauth://totp/$encodedLabel?$paramString"
    }
}
