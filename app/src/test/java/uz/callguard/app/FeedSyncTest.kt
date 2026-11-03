package uz.callguard.app

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import uz.callguard.app.data.*
import uz.callguard.app.sync.*

class FeedSyncTest {
    private val valid = """[{"numberHash":"${"a".repeat(64)}","spamScore":45,"category":"advertising"}]"""
    private fun feed(id: Long) = SpamFeedEntity(id, "Feed $id", "https://example.org/$id", true)

    private class Response(url: URL, val status: Int = 200, val body: ByteArray = "[]".toByteArray(),
        val location: String? = null, val length: Long = body.size.toLong(), val encoding: String? = null) : HttpURLConnection(url) {
        var closed = false
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getHeaderField(name: String?) = if (name == "Location") location else null
        override fun getContentLengthLong() = length
        override fun getContentEncoding() = encoding
        override fun getInputStream() = ByteArrayInputStream(body)
    }

    @Test fun httpsDownloadReturnsUtf8AndClosesConnection() = runBlocking {
        val response = Response(URL("https://example.org/feed"), body = valid.toByteArray())
        val client = FeedHttpClient({ response }, {})
        assertEquals(valid, client.download("https://example.org/feed"))
        assertTrue(response.closed)
        assertFalse(response.instanceFollowRedirects)
    }

    @Test fun rejectsUnsafeUrlsBeforeOpeningConnection() = runBlocking {
        for (url in listOf("http://example.org/feed", "file:///tmp/feed", "https://user:pass@example.org/feed", "https://example.org/feed#fragment")) {
            try { FeedHttpClient({ error("Must not connect") }, {}).download(url); fail(url) }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun refusesPrivateAndLoopbackAddresses() {
        for (bytes in listOf(byteArrayOf(127,0,0,1), byteArrayOf(10,0,0,1), byteArrayOf(169.toByte(),254.toByte(),1,1),
            byteArrayOf(192.toByte(),168.toByte(),1,1))) {
            assertFalse(FeedUrlPolicy.isPublic(InetAddress.getByAddress(bytes)))
        }
        assertFalse(FeedUrlPolicy.isPublic(InetAddress.getByAddress(ByteArray(16).apply { this[0] = 0xfd.toByte() })))
        assertTrue(FeedUrlPolicy.isPublic(InetAddress.getByAddress(byteArrayOf(8,8,8,8))))
    }

    @Test fun refusesHttpRedirectAndStillDisconnects() = runBlocking {
        val response = Response(URL("https://example.org"), 302, location = "http://example.org/insecure")
        try { FeedHttpClient({ response }, {}).download("https://example.org"); fail("downgrade") }
        catch (_: IllegalArgumentException) { }
        assertTrue(response.closed)
    }

    @Test fun checksRedirectHostsAndFollowsRelativeLocations() = runBlocking {
        val opened = mutableListOf<String>()
        val checked = mutableListOf<String>()
        val client = FeedHttpClient({ url ->
            opened += url.toString()
            if (opened.size == 1) Response(url, 302, location = "/final") else Response(url)
        }, { checked += it })
        assertEquals("[]", client.download("https://example.org/start"))
        assertEquals(listOf("https://example.org/start", "https://example.org/final"), opened)
        assertEquals(2, checked.size)
    }

    @Test fun rejectsOversizedAndMalformedUtf8Bodies() = runBlocking {
        for (response in listOf(Response(URL("https://example.org"), length = SpamBackupCodec.MAX_BYTES + 1L),
            Response(URL("https://example.org"), body = byteArrayOf(0xc3.toByte(),0x28)))) {
            try { FeedHttpClient({ response }, {}).download("https://example.org"); fail("bad body") }
            catch (_: Exception) { }
            assertTrue(response.closed)
        }
    }

    @Test fun httpErrorIsReportedWithoutReadingBody() = runBlocking {
        val response = Response(URL("https://example.org"), 503)
        try { FeedHttpClient({ response }, {}).download("https://example.org"); fail("status") }
        catch (error: FeedHttpException) { assertEquals(503, error.status) }
        assertTrue(response.closed)
    }

    @Test fun failureDoesNotPreventNextFeedAndInactiveFeedIsSkipped() = runBlocking {
        val downloaded = mutableListOf<String>()
        val committed = mutableListOf<Long>()
        val failures = mutableListOf<Long>()
        var cleaned = false
        val result = SpamFeedSynchronizer().sync(listOf(feed(1), feed(2), feed(3).copy(isActive = false)),
            download = { url -> downloaded += url; if (url.endsWith("/1")) throw SocketTimeoutException() else valid },
            commit = { feed, rows, now ->
                assertTrue(rows.all { it.isGlobal && it.phoneNumber == null && it.updatedAt == now })
                committed += feed.id; true
            }, cleanup = { cleaned = true }, onFailure = { feed, _ -> failures += feed.id }, now = { 1234 })
        assertEquals(FeedSyncSummary(1,1,0), result)
        assertEquals(listOf(2L), committed)
        assertEquals(listOf(1L), failures)
        assertEquals(2, downloaded.size)
        assertTrue(cleaned)
    }

    @Test fun invalidJsonNeverCommitsAndEditedFeedIsNotSuccessful() = runBlocking {
        var commits = 0
        val result = SpamFeedSynchronizer().sync(listOf(feed(1),feed(2)),
            download = { if (it.endsWith("/1")) "not JSON" else valid },
            commit = { _, _, _ -> commits++; false }, cleanup = {}, onFailure = { _, _ -> })
        assertEquals(1, commits)
        assertEquals(FeedSyncSummary(0,1,1), result)
    }

    @Test fun cancellationPropagatesWithoutProcessingLaterFeeds() = runBlocking {
        var downloads = 0
        try {
            SpamFeedSynchronizer().sync(listOf(feed(1),feed(2)), download = { downloads++; throw CancellationException() },
                commit = { _,_,_ -> error("No commit") }, cleanup = { error("No cleanup on cancellation") }, onFailure = { _,_ -> error("Not an ordinary failure") })
            fail("Must cancel")
        } catch (_: CancellationException) { }
        assertEquals(1, downloads)
    }

    @Test fun noActiveFeedsStillCleansExpiredCache() = runBlocking {
        var cleaned = false
        val result = SpamFeedSynchronizer().sync(emptyList(), download = { error("No download") },
            commit = { _,_,_ -> error("No commit") }, cleanup = { cleaned = true }, onFailure = { _,_ -> })
        assertEquals(FeedSyncSummary(0,0,0), result)
        assertTrue(cleaned)
    }
    @Test fun boundsGzipAfterDecompression() = runBlocking {
        val output = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(output).use { it.write(ByteArray(SpamBackupCodec.MAX_BYTES + 1)) }
        val response = Response(URL("https://example.org"), body = output.toByteArray(), encoding = "gzip")
        try { FeedHttpClient({ response }, {}).download("https://example.org"); fail("gzip expansion") }
        catch (_: IllegalArgumentException) { }
        assertTrue(response.closed)
    }

    @Test fun redirectLoopIsBounded() = runBlocking {
        var connections = 0
        try {
            FeedHttpClient({ connections++; Response(it, 302, location = "/loop") }, {}).download("https://example.org")
            fail("redirect loop")
        } catch (_: IllegalArgumentException) { }
        assertEquals(6, connections)
    }

    @Test fun feedRequiresScoreAndSupportsScoreAlias() {
        val row = GlobalSpamFeed.parse("""[{"numberHash":"${"a".repeat(64)}","score":35}]""", 1234).single()
        assertEquals(35, row.spamScore)
        try {
            GlobalSpamFeed.parse("""[{"numberHash":"${"a".repeat(64)}"}]""", 1234)
            fail("Unscored feed must not implicitly block")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun supportsPlainNumbersEnvelopeAndNormalizesPhones() {
        val entry = GlobalSpamFeed.parse("""{"numbers":[{"number":"+998 (90) 123-45-67","score":45,"category":"advertising"}]}""", 1234).single()
        assertEquals(HashUtils.sha256("+998901234567"), entry.numberHash)
        assertNull(entry.phoneNumber)
        assertEquals(45, entry.spamScore)
    }

}
