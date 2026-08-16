package win.downops.clipshare.clipboard

import android.net.Uri
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClipboardDedupTest {

    @Before
    fun setUp() {
        ClipboardDedup.setUriResult(Uri.parse("content://none"), null, false)
    }

    @Test
    fun claimTreatsTextAndImageBytesUniformly() {
        val textBytes = "hello".toByteArray()
        val imageBytes = byteArrayOf(1, 2, 3)

        assertTrue(ClipboardDedup.claim(textBytes))
        // same bytes again (e.g. re-poll) are not re-claimed
        assertFalse(ClipboardDedup.claim(textBytes))
        // a different byte sequence is a new copy
        assertTrue(ClipboardDedup.claim(imageBytes))
        assertFalse(ClipboardDedup.claim(imageBytes))
    }

    @Test
    fun claimIsUnifiedAcrossTextAndImagePaths() {
        val bytes = "shared content".toByteArray()
        // whichever path claims first wins; the other path is suppressed
        assertTrue(ClipboardDedup.claim(bytes))
        assertFalse(ClipboardDedup.claim(bytes))
    }

    @Test
    fun markRemoteWrittenSuppressesLoopback() {
        val bytes = "from peer".toByteArray()
        ClipboardDedup.markRemoteWritten(bytes)

        // content the service wrote itself is never pushed back
        assertFalse(ClipboardDedup.claim(bytes))
        // unrelated content is still a new copy
        assertTrue(ClipboardDedup.claim("local copy".toByteArray()))
    }

    @Test
    fun uriCacheRemembersCompressedBytes() {
        val uri = Uri.parse("content://media/image1")
        val bytes = byteArrayOf(9, 8, 7)

        assertFalse(ClipboardDedup.isLastUri(uri))
        ClipboardDedup.setUriResult(uri, bytes, false)

        assertTrue(ClipboardDedup.isLastUri(uri))
        assertFalse(ClipboardDedup.uriFailed())
        assertArrayEquals(bytes, ClipboardDedup.cachedUriBytes())
    }

    @Test
    fun uriFailureIsRemembered() {
        val uri = Uri.parse("content://media/broken")
        ClipboardDedup.setUriResult(uri, null, true)

        assertTrue(ClipboardDedup.isLastUri(uri))
        assertTrue(ClipboardDedup.uriFailed())
        assertNull(ClipboardDedup.cachedUriBytes())
    }

    @Test
    fun emptyBytesAreStillClaimable() {
        assertTrue(ClipboardDedup.claim(ByteArray(0)))
        assertFalse(ClipboardDedup.claim(ByteArray(0)))
    }

    @Test
    fun differentTextSameClaimPathNoFalseDedup() {
        assertTrue(ClipboardDedup.claim("aaaa".toByteArray()))
        assertTrue(ClipboardDedup.claim("aaab".toByteArray()))
        assertFalse(ClipboardDedup.claim("aaab".toByteArray()))
    }
}