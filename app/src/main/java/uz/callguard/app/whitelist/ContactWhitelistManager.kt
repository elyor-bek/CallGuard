package uz.callguard.app.whitelist

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import uz.callguard.app.heuristic.NumberFormatRules

class ContactWhitelistManager(context: Context) {

    private val appContext: Context = context.applicationContext

    /**
     * Проверяет, содержится ли номер в контактах устройства.
     * Номера из контактов никогда не блокируются.
     */
    fun isContact(rawNumber: String): Boolean {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val normalizedNumber = NumberFormatRules.check(rawNumber).normalized
        if (normalizedNumber.isBlank()) return false

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(normalizedNumber)
        )

        val projection = arrayOf(ContactsContract.PhoneLookup._ID)

        return try {
            appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (_: Exception) {
            false
        }
    }
}
