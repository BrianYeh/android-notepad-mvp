package com.brianyeh.justnotes.backend.rtdn

import com.google.cloud.firestore.Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleCloudFirestoreRtdnEventDocumentStore(
    private val firestore: Firestore,
) : RtdnEventDocumentStore {
    override suspend fun <T> transact(
        documentId: String,
        operation: (Map<String, Any?>?) -> RtdnEventMutation<T>,
    ): T = withContext(Dispatchers.IO) {
        val reference = firestore.collection(RTDN_EVENTS_COLLECTION).document(documentId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(reference).get()
            val mutation = operation(snapshot.data)
            mutation.fields?.let { fields -> transaction.set(reference, fields) }
            mutation.result
        }.get()
    }

    private companion object {
        const val RTDN_EVENTS_COLLECTION = "rtdnEvents"
    }
}
