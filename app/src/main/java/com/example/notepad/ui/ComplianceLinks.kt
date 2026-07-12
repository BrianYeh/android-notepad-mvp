package com.example.notepad.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

const val PRIVACY_POLICY_URL =
    "https://brianyeh.github.io/android-notepad-mvp/privacy-policy/"
const val TERMS_OF_SERVICE_URL =
    "https://brianyeh.github.io/android-notepad-mvp/terms-of-service/"
const val ACCOUNT_DELETION_URL =
    "https://brianyeh.github.io/android-notepad-mvp/account-deletion/"

fun openComplianceUrl(context: Context, url: String): Boolean {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) return false
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
