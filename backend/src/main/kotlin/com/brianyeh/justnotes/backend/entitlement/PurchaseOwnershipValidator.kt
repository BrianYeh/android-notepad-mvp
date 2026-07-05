package com.brianyeh.justnotes.backend.entitlement

import com.brianyeh.justnotes.backend.play.PlayExternalAccountIdentifiers
import com.brianyeh.justnotes.backend.security.ObfuscatedAccountIdDeriver

enum class PurchaseOwnershipErrorCode {
    OWNER_MISMATCH,
    MISSING_OBFUSCATED_ACCOUNT_ID,
}

sealed class PurchaseOwnershipResult {
    data object Verified : PurchaseOwnershipResult()
    data class Failure(val code: PurchaseOwnershipErrorCode) : PurchaseOwnershipResult()
}

class PurchaseOwnershipValidator(
    private val obfuscatedAccountIdDeriver: ObfuscatedAccountIdDeriver,
) {
    fun validate(
        googleSub: String,
        externalAccountIdentifiers: PlayExternalAccountIdentifiers?,
    ): PurchaseOwnershipResult {
        val playAccountId = externalAccountIdentifiers?.obfuscatedExternalAccountId
            ?.takeIf { it.isNotBlank() }
            ?: return PurchaseOwnershipResult.Failure(PurchaseOwnershipErrorCode.MISSING_OBFUSCATED_ACCOUNT_ID)
        val expectedAccountId = obfuscatedAccountIdDeriver.derive(googleSub)
        return if (playAccountId == expectedAccountId) {
            PurchaseOwnershipResult.Verified
        } else {
            PurchaseOwnershipResult.Failure(PurchaseOwnershipErrorCode.OWNER_MISMATCH)
        }
    }
}
