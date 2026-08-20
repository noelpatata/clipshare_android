package win.downops.clipshare.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import win.downops.clipshare.util.Constants

class SettingsValidatorTest {

    @Test
    fun validDeviceNameIsAccepted() {
        val result = SettingsValidator.validateDeviceName("Pixel")
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun blankDeviceNameIsRejected() {
        val result = SettingsValidator.validateDeviceName("   ")
        assertTrue(result is ValidationResult.Invalid)
        assertEquals(Constants.Validation.DEVICE_NAME_BLANK, (result as ValidationResult.Invalid).message)
    }

    @Test
    fun maxHistoryEntriesWithinRangeIsValid() {
        assertTrue(SettingsValidator.validateMaxHistoryEntries(5) is ValidationResult.Valid)
        assertTrue(SettingsValidator.validateMaxHistoryEntries(1000) is ValidationResult.Valid)
        assertTrue(SettingsValidator.validateMaxHistoryEntries(50) is ValidationResult.Valid)
    }

    @Test
    fun maxHistoryEntriesOutsideRangeIsInvalid() {
        val tooLow = SettingsValidator.validateMaxHistoryEntries(4)
        assertTrue(tooLow is ValidationResult.Invalid)
        assertTrue((tooLow as ValidationResult.Invalid).message.contains(Constants.History.MIN_MAX_ENTRIES.toString()))

        val tooHigh = SettingsValidator.validateMaxHistoryEntries(1001)
        assertTrue(tooHigh is ValidationResult.Invalid)
        assertTrue((tooHigh as ValidationResult.Invalid).message.contains(Constants.History.MAX_MAX_ENTRIES.toString()))
    }

    @Test
    fun maxHistoryEntriesNullIsInvalid() {
        assertTrue(SettingsValidator.validateMaxHistoryEntries(null) is ValidationResult.Invalid)
    }

    @Test
    fun maxLogFileKbWithinRangeIsValid() {
        assertTrue(SettingsValidator.validateMaxLogFileKb(16) is ValidationResult.Valid)
        assertTrue(SettingsValidator.validateMaxLogFileKb(4096) is ValidationResult.Valid)
    }

    @Test
    fun maxLogFileKbOutsideRangeIsInvalid() {
        assertTrue(SettingsValidator.validateMaxLogFileKb(1) is ValidationResult.Invalid)
        assertTrue(SettingsValidator.validateMaxLogFileKb(10000) is ValidationResult.Invalid)
    }

    @Test
    fun clipboardPollMsWithinRangeIsValid() {
        assertTrue(SettingsValidator.validateClipboardPollMs(200L) is ValidationResult.Valid)
        assertTrue(SettingsValidator.validateClipboardPollMs(10_000L) is ValidationResult.Valid)
    }

    @Test
    fun clipboardPollMsOutsideRangeIsInvalid() {
        assertTrue(SettingsValidator.validateClipboardPollMs(100L) is ValidationResult.Invalid)
        assertTrue(SettingsValidator.validateClipboardPollMs(20_000L) is ValidationResult.Invalid)
    }

    @Test
    fun maxImagePayloadKbWithinRangeIsValid() {
        assertTrue(SettingsValidator.validateMaxImagePayloadKb(8) is ValidationResult.Valid)
        assertTrue(SettingsValidator.validateMaxImagePayloadKb(2048) is ValidationResult.Valid)
    }

    @Test
    fun maxImagePayloadKbOutsideRangeIsInvalid() {
        assertTrue(SettingsValidator.validateMaxImagePayloadKb(1) is ValidationResult.Invalid)
        assertTrue(SettingsValidator.validateMaxImagePayloadKb(5000) is ValidationResult.Invalid)
    }

    @Test
    fun portWithinRangeIsValid() {
        assertTrue(SettingsValidator.validatePort(1) is ValidationResult.Valid)
        assertTrue(SettingsValidator.validatePort(65535) is ValidationResult.Valid)
        assertTrue(SettingsValidator.validatePort(40403) is ValidationResult.Valid)
    }

    @Test
    fun portOutsideRangeIsInvalid() {
        assertTrue(SettingsValidator.validatePort(0) is ValidationResult.Invalid)
        assertTrue(SettingsValidator.validatePort(65536) is ValidationResult.Invalid)
    }

    @Test
    fun portNullIsInvalid() {
        assertTrue(SettingsValidator.validatePort(null) is ValidationResult.Invalid)
    }

    @Test
    fun errorMessagesUseConstants() {
        val message = (SettingsValidator.validateMaxHistoryEntries(1) as ValidationResult.Invalid).message
        assertFalse(message.isBlank())
        assertTrue(message.contains(Constants.History.MIN_MAX_ENTRIES.toString()))
        assertTrue(message.contains(Constants.History.MAX_MAX_ENTRIES.toString()))
    }
}
