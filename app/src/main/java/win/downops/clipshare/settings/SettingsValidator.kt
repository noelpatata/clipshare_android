package win.downops.clipshare.settings

import win.downops.clipshare.util.Constants

/** Result of validating a single settings field. */
sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val message: String) : ValidationResult
}

/** Validates raw user input before it is persisted to [Prefs]. */
object SettingsValidator {

    fun validateDeviceName(value: String): ValidationResult =
        if (value.isBlank()) {
            ValidationResult.Invalid(Constants.Validation.DEVICE_NAME_BLANK)
        } else {
            ValidationResult.Valid
        }

    fun validateMaxHistoryEntries(value: Int?): ValidationResult {
        val min = Constants.History.MIN_MAX_ENTRIES
        val max = Constants.History.MAX_MAX_ENTRIES
        return if (value == null || value < min || value > max) {
            ValidationResult.Invalid(String.format(Constants.Validation.HISTORY_ENTRIES_RANGE, min, max))
        } else {
            ValidationResult.Valid
        }
    }

    fun validateMaxLogFileKb(value: Int?): ValidationResult {
        val min = Constants.Log.MIN_MAX_FILE_KB
        val max = Constants.Log.MAX_MAX_FILE_KB
        return if (value == null || value < min || value > max) {
            ValidationResult.Invalid(String.format(Constants.Validation.LOG_FILE_SIZE_RANGE, min, max))
        } else {
            ValidationResult.Valid
        }
    }

    fun validateClipboardPollMs(value: Long?): ValidationResult {
        val min = Constants.Clipboard.MIN_POLL_MS
        val max = Constants.Clipboard.MAX_POLL_MS
        return if (value == null || value < min || value > max) {
            ValidationResult.Invalid(String.format(Constants.Validation.CLIPBOARD_POLL_RANGE, min, max))
        } else {
            ValidationResult.Valid
        }
    }

    fun validateMaxImagePayloadKb(value: Int?): ValidationResult {
        val min = Constants.Image.MIN_MAX_PAYLOAD_KB
        val max = Constants.Image.MAX_MAX_PAYLOAD_KB
        return if (value == null || value < min || value > max) {
            ValidationResult.Invalid(String.format(Constants.Validation.IMAGE_PAYLOAD_RANGE, min, max))
        } else {
            ValidationResult.Valid
        }
    }

    fun validatePort(value: Int?): ValidationResult {
        val min = Constants.Network.MIN_PORT
        val max = Constants.Network.MAX_PORT
        return if (value == null || value < min || value > max) {
            ValidationResult.Invalid(String.format(Constants.Validation.PORT_RANGE, min, max))
        } else {
            ValidationResult.Valid
        }
    }
}
