package com.brianyeh.justnotes.backend.account

sealed interface AccountDeletionResult {
    data object Deleted : AccountDeletionResult
    data object BlockedByNonterminalSubscription : AccountDeletionResult
    data object FailedClosed : AccountDeletionResult
}

fun interface AccountDeletionRepository {
    suspend fun deleteAccountData(googleSub: String, now: Long): AccountDeletionResult
}

object UnavailableAccountDeletionRepository : AccountDeletionRepository {
    override suspend fun deleteAccountData(googleSub: String, now: Long): AccountDeletionResult =
        AccountDeletionResult.FailedClosed
}
