package win.downops.clipshare.ws

import android.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import win.downops.clipshare.util.Constants

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProtocolTest {

    @Test
    fun hello_serializesTypeNamePlatformVersion() {
        val json = JSONObject(Protocol.hello("Pixel", "android", "1.2.3"))

        assertEquals(Constants.Protocol.Msg.HELLO, json.getString("type"))
        val data = json.getJSONObject("data")
        assertEquals("Pixel", data.getString("name"))
        assertEquals("android", data.getString("platform"))
        assertEquals("1.2.3", data.getString("version"))
    }

    @Test
    fun clipboard_serializesTextPayload() {
        val json = JSONObject(Protocol.clipboard("hello world", "phone"))

        assertEquals(Constants.Protocol.Msg.CLIPBOARD, json.getString("type"))
        val data = json.getJSONObject("data")
        assertEquals(Constants.Protocol.Content.TEXT, data.getString("type"))
        assertEquals("hello world", data.getString("text"))
        assertEquals("phone", data.getString("from"))
        assertTrue("ts must be set", data.getLong("ts") > 0)
    }

    @Test
    fun clipboardImage_encodesBytesAsBase64() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x7f, 0x00, -1)
        val json = JSONObject(Protocol.clipboardImage(bytes, Constants.Mime.IMAGE_PNG, "phone"))

        assertEquals(Constants.Protocol.Msg.CLIPBOARD, json.getString("type"))
        val data = json.getJSONObject("data")
        assertEquals(Constants.Protocol.Content.IMAGE, data.getString("type"))
        assertEquals(Constants.Mime.IMAGE_PNG, data.getString("mime"))
        assertEquals("phone", data.getString("from"))
        assertArrayEquals(bytes, Base64.decode(data.getString("data"), Base64.DEFAULT))
    }

    @Test
    fun parseClipboard_parsesTextMessage() {
        val clip = ProtocolParser.parseClipboard(Protocol.clipboard("some text", "laptop"))!!

        assertNull(clip.image)
        assertNull(clip.mime)
        assertEquals("some text", clip.text)
        assertEquals("laptop", clip.from)
        assertFalse(clip.isEmpty)
    }

    @Test
    fun parseClipboard_roundTripsImage() {
        val bytes = ByteArray(16) { it.toByte() }
        val clip = ProtocolParser.parseClipboard(Protocol.clipboardImage(bytes, Constants.Mime.IMAGE_JPEG, "desktop"))!!

        assertNull(clip.text)
        assertEquals(Constants.Mime.IMAGE_JPEG, clip.mime)
        assertEquals("desktop", clip.from)
        assertArrayEquals(bytes, clip.image)
        assertFalse(clip.isEmpty)
    }

    @Test
    fun parseClipboard_defaultsMissingMimeToPng() {
        val json = JSONObject()
            .put("type", Constants.Protocol.Msg.CLIPBOARD)
            .put(
                "data",
                JSONObject()
                    .put("type", Constants.Protocol.Content.IMAGE)
                    .put("data", Base64.encodeToString(byteArrayOf(1, 2, 3), Base64.DEFAULT))
                    .put("from", "desktop"),
            )
            .toString()

        val clip = ProtocolParser.parseClipboard(json)!!
        assertEquals(Constants.Mime.IMAGE_PNG, clip.mime)
    }

    @Test
    fun parseClipboard_returnsNullForNonClipboardType() {
        assertNull(ProtocolParser.parseClipboard(Protocol.ping()))
        assertNull(ProtocolParser.parseClipboard("""{"type":"hello","data":{"name":"x"}}"""))
    }

    @Test
    fun parseClipboard_returnsNullForBlankText() {
        val json = JSONObject()
            .put("type", Constants.Protocol.Msg.CLIPBOARD)
            .put("data", JSONObject().put("type", Constants.Protocol.Content.TEXT).put("text", "  ").put("from", "x"))
            .toString()
        assertNull(ProtocolParser.parseClipboard(json))
    }

    @Test
    fun parseClipboard_returnsNullForBlankOrInvalidImageData() {
        val blank = JSONObject()
            .put("type", Constants.Protocol.Msg.CLIPBOARD)
            .put("data", JSONObject().put("type", Constants.Protocol.Content.IMAGE).put("data", "").put("from", "x"))
            .toString()
        assertNull(ProtocolParser.parseClipboard(blank))

        val invalid = JSONObject()
            .put("type", Constants.Protocol.Msg.CLIPBOARD)
            .put("data", JSONObject().put("type", Constants.Protocol.Content.IMAGE).put("data", "!!not-base64!!").put("from", "x"))
            .toString()
        assertNull(ProtocolParser.parseClipboard(invalid))
    }

    @Test
    fun parseClipboard_returnsNullForMalformedJson() {
        assertNull(ProtocolParser.parseClipboard("{not valid json"))
        assertNull(ProtocolParser.parseClipboard(""))
    }

    @Test
    fun parseError_parsesCodeAndMessage() {
        val json = JSONObject()
            .put("type", Constants.Protocol.Msg.ERROR)
            .put("data", JSONObject().put("code", "E_AUTH").put("msg", "bad token"))
            .toString()

        val error = ProtocolParser.parseError(json)!!
        assertEquals("E_AUTH", error.code)
        assertEquals("bad token", error.msg)
    }

    @Test
    fun parseError_returnsNullForNonErrorType() {
        assertNull(ProtocolParser.parseError(Protocol.ping()))
    }

    @Test
    fun parseType_extractsMessageType() {
        assertEquals(Constants.Protocol.Msg.HELLO, ProtocolParser.parseType(Protocol.hello("n", "p", "v")))
        assertEquals(Constants.Protocol.Msg.PING, ProtocolParser.parseType(Protocol.ping()))
        assertNull(ProtocolParser.parseType("garbage"))
    }

    @Test
    fun parseHello_extractsName() {
        assertEquals("my-device", ProtocolParser.parseHello(Protocol.hello("my-device", "android", "1.0.0")))
    }

    @Test
    fun parseHello_returnsNullForWrongTypeOrBlankName() {
        assertNull(ProtocolParser.parseHello(Protocol.ping()))
        val blank = JSONObject()
            .put("type", Constants.Protocol.Msg.HELLO)
            .put("data", JSONObject().put("name", "  ").put("platform", "android"))
            .toString()
        assertNull(ProtocolParser.parseHello(blank))
    }

    @Test
    fun parseRoutesEachMessageType() {
        assertTrue(ProtocolParser.parse(Protocol.hello("n", "p", "v")) is ProtocolMessage.Hello)
        assertTrue(ProtocolParser.parse(Protocol.clipboard("text", "x")) is ProtocolMessage.Clipboard)
        assertTrue(ProtocolParser.parse(Protocol.ping()) is ProtocolMessage.Ping)
        assertTrue(ProtocolParser.parse(Protocol.pong()) is ProtocolMessage.Pong)
        assertTrue(ProtocolParser.parse("garbage") is ProtocolMessage.Unknown)
    }

    @Test
    fun parseFallsBackToUnknownForInvalidKnownType() {
        // clipboard with blank text fails validation, so it is dropped
        val blank = JSONObject()
            .put("type", Constants.Protocol.Msg.CLIPBOARD)
            .put("data", JSONObject().put("type", Constants.Protocol.Content.TEXT).put("text", " "))
            .toString()
        assertTrue(ProtocolParser.parse(blank) is ProtocolMessage.Unknown)
    }

    @Test
    fun clipboard_isEmptyFlags() {
        assertTrue(Protocol.Clipboard(text = null, image = null, mime = null, from = "x").isEmpty)
        assertTrue(Protocol.Clipboard(text = "", image = null, mime = null, from = "x").isEmpty)
        assertTrue(Protocol.Clipboard(text = null, image = ByteArray(0), mime = null, from = "x").isEmpty)
        assertFalse(Protocol.Clipboard(text = "a", image = null, mime = null, from = "x").isEmpty)
        assertFalse(Protocol.Clipboard(text = null, image = byteArrayOf(1), mime = null, from = "x").isEmpty)
    }
}