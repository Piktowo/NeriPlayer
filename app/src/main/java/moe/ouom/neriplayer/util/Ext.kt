package moe.ouom.neriplayer.util

import java.io.ByteArrayOutputStream
import java.io.InputStream

fun InputStream.readBytesCompat(bufferSize: Int = 8 * 1024): ByteArray {
    ByteArrayOutputStream().use { out ->
        val buf = ByteArray(bufferSize)
        while (true) {
            val n = this.read(buf)
            if (n == -1) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
