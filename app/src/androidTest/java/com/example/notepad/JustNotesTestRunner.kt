package com.example.notepad

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import com.example.notepad.debug.DebugPremiumAccess

class JustNotesTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle) {
        DebugPremiumAccess.suppressBillingConnectionForTests(true)
        super.onCreate(arguments)
    }

    override fun finish(resultCode: Int, results: Bundle?) {
        DebugPremiumAccess.suppressBillingConnectionForTests(false)
        super.finish(resultCode, results)
    }
}
