package uz.callguard.app.sync

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import uz.callguard.app.data.SpamBackupCodec

object FeedUrlPolicy {
    fun validate(value: String): URL {
        require(value.length in 1..2048)
        val uri = URI(value)
        require(uri.scheme.equals("https", ignoreCase = true)) { "HTTPS is required" }
        require(!uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawFragment == null)
        require(uri.port == -1 || uri.port in 1..65535)
        return uri.toURL()
    }

    fun isPublic(address: InetAddress): Boolean {
        val bytes = address.address
        if (bytes.size == 4) {
            val first = bytes[0].toInt() and 255
            val second = bytes[1].toInt() and 255
            if (first == 0 || first >= 224 || (first == 100 && second in 64..127) ||
                (first == 198 && second in 18..19)) return false
        }
        return !address.isAnyLocalAddress && !address.isLoopbackAddress &&
        !address.isLinkLocalAddress && !address.isSiteLocalAddress && !address.isMulticastAddress &&
        // IPv6 unique-local addresses are not covered by isSiteLocalAddress.
        !(address.address.size == 16 && (address.address[0].toInt() and 0xfe) == 0xfc)
    }

    fun checkHost(host: String) {
        require(InetAddress.getAllByName(host).all(::isPublic)) { "Public feed hosts only" }
    }
}

class FeedHttpException(val status: Int) : IOException("HTTP $status")

/** GET-only client: no credentials, caller IDs, cookies or request body are added. */
class FeedHttpClient(
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val checkHost: (String) -> Unit = FeedUrlPolicy::checkHost
) {
    suspend fun download(value: String): String = withTimeoutOrNull(45_000) {
        runInterruptible(Dispatchers.IO) { downloadBlocking(value) }
    } ?: throw SocketTimeoutException("Feed deadline exceeded")

    private fun downloadBlocking(value: String): String {
        val deadline = System.nanoTime() + 40_000_000_000L
        var url = FeedUrlPolicy.validate(value)
        repeat(6) { hop ->
            check(System.nanoTime() < deadline) { "Feed deadline exceeded" }
            checkHost(url.host)
            val connection = open(url)
            try {
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.useCaches = false
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Accept-Encoding", "identity")
                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    require(hop < 5) { "Too many redirects" }
                    val location = requireNotNull(connection.getHeaderField("Location"))
                    url = FeedUrlPolicy.validate(url.toURI().resolve(location).toString())
                } else {
                    if (status != HttpURLConnection.HTTP_OK) throw FeedHttpException(status)
                    require(connection.contentLengthLong <= SpamBackupCodec.MAX_BYTES) { "Feed too large" }
                    val encoding = connection.contentEncoding.orEmpty()
                    require(encoding.isBlank() || encoding.equals("identity", true) || encoding.equals("gzip", true))
                    val stream = connection.inputStream
                    val decoded = if (encoding.equals("gzip", true)) GZIPInputStream(stream) else stream
                    val bytes = decoded.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedException()
                            check(System.nanoTime() < deadline) { "Feed deadline exceeded" }
                            val read = input.read(buffer)
                            if (read < 0) break
                            require(output.size() + read <= SpamBackupCodec.MAX_BYTES) { "Feed too large" }
                            output.write(buffer, 0, read)
                        }
                        output.toByteArray()
                    }
                    return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                }
            } finally {
                connection.disconnect()
            }
        }
        error("Too many redirects")
    }
}
