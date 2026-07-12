package com.brianyeh.justnotes.backend

import com.brianyeh.justnotes.backend.config.BackendConfig
import com.brianyeh.justnotes.backend.entitlement.EntitlementRepository
import com.brianyeh.justnotes.backend.entitlement.FirestoreEntitlementRepository
import com.brianyeh.justnotes.backend.entitlement.GoogleCloudFirestoreEntitlementDocumentStore
import com.brianyeh.justnotes.backend.play.GoogleAndroidPublisherGateway
import com.brianyeh.justnotes.backend.play.GooglePlaySubscriptionAcknowledger
import com.brianyeh.justnotes.backend.play.GooglePlaySubscriptionVerifier
import com.brianyeh.justnotes.backend.play.PlaySubscriptionAcknowledger
import com.brianyeh.justnotes.backend.play.PlaySubscriptionVerifier
import com.brianyeh.justnotes.backend.rtdn.FirestoreRtdnEventRepository
import com.brianyeh.justnotes.backend.rtdn.GoogleCloudFirestoreRtdnEventDocumentStore
import com.brianyeh.justnotes.backend.rtdn.RtdnEventRepository
import com.brianyeh.justnotes.backend.reviewer.NoReviewerGrantPolicy
import com.brianyeh.justnotes.backend.reviewer.ReviewerGrantPolicy
import com.brianyeh.justnotes.backend.reviewer.SecretBackedReviewerGrantPolicy
import com.brianyeh.justnotes.backend.security.CachingSecretManagerSecretValueProvider
import com.brianyeh.justnotes.backend.security.GoogleCloudKmsGateway
import com.brianyeh.justnotes.backend.security.GoogleCloudSecretManagerGateway
import com.brianyeh.justnotes.backend.security.GoogleKmsPurchaseTokenCipher
import com.brianyeh.justnotes.backend.security.HmacSha256ObfuscatedAccountIdDeriver
import com.brianyeh.justnotes.backend.security.HmacSha256PurchaseTokenHasher
import com.brianyeh.justnotes.backend.security.ObfuscatedAccountIdDeriver
import com.brianyeh.justnotes.backend.security.PurchaseTokenCipher
import com.brianyeh.justnotes.backend.security.PurchaseTokenHasher
import com.brianyeh.justnotes.backend.security.SecretValueProvider
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.kms.v1.KeyManagementServiceClient
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient

class ProductionBackendDependencies private constructor(
    val entitlementRepository: EntitlementRepository,
    val playSubscriptionVerifier: PlaySubscriptionVerifier,
    val playSubscriptionAcknowledger: PlaySubscriptionAcknowledger,
    val purchaseTokenHasher: PurchaseTokenHasher,
    val obfuscatedAccountIdDeriver: ObfuscatedAccountIdDeriver,
    val purchaseTokenCipher: PurchaseTokenCipher,
    val emailHashSecretProvider: SecretValueProvider,
    val reviewerGrantPolicy: ReviewerGrantPolicy,
    val rtdnEventRepository: RtdnEventRepository,
    private val firestore: Firestore,
    private val secretManagerClient: SecretManagerServiceClient,
    private val kmsClient: KeyManagementServiceClient,
) : AutoCloseable {
    override fun close() {
        runCatching { firestore.close() }
        runCatching { secretManagerClient.close() }
        runCatching { kmsClient.close() }
    }

    companion object {
        fun create(config: BackendConfig): ProductionBackendDependencies {
            val errors = config.validateForProductionAdapters()
            require(errors.isEmpty()) { errors.joinToString(separator = " ") }

            val firestore = FirestoreOptions.getDefaultInstance().toBuilder()
                .setProjectId(requireNotNull(config.firestoreProjectId))
                .setDatabaseId(config.firestoreDatabaseId)
                .build()
                .service
            val secretManagerClient = SecretManagerServiceClient.create()
            val kmsClient = KeyManagementServiceClient.create()

            try {
                val secretGateway = GoogleCloudSecretManagerGateway(secretManagerClient)
                val tokenHashSecretProvider = CachingSecretManagerSecretValueProvider(
                    resourceName = requireNotNull(config.tokenHashSecretResource),
                    gateway = secretGateway,
                )
                val obfuscatedAccountSecretProvider = CachingSecretManagerSecretValueProvider(
                    resourceName = requireNotNull(config.obfuscatedAccountSecretResource),
                    gateway = secretGateway,
                )
                val emailHashSecretProvider = CachingSecretManagerSecretValueProvider(
                    resourceName = requireNotNull(config.emailHashSecretResource),
                    gateway = secretGateway,
                )
                val reviewerGrantPolicy = config.reviewerGrantSecretResource?.let { resourceName ->
                    SecretBackedReviewerGrantPolicy(
                        CachingSecretManagerSecretValueProvider(
                            resourceName = resourceName,
                            gateway = secretGateway,
                        ),
                    )
                } ?: NoReviewerGrantPolicy
                val purchaseTokenHasher = HmacSha256PurchaseTokenHasher(tokenHashSecretProvider)
                val publisherGateway = GoogleAndroidPublisherGateway(createAndroidPublisher())

                return ProductionBackendDependencies(
                    entitlementRepository = FirestoreEntitlementRepository(
                        GoogleCloudFirestoreEntitlementDocumentStore(firestore),
                    ),
                    playSubscriptionVerifier = GooglePlaySubscriptionVerifier(
                        gateway = publisherGateway,
                        purchaseTokenHasher = purchaseTokenHasher,
                    ),
                    playSubscriptionAcknowledger = GooglePlaySubscriptionAcknowledger(publisherGateway),
                    purchaseTokenHasher = purchaseTokenHasher,
                    obfuscatedAccountIdDeriver = HmacSha256ObfuscatedAccountIdDeriver(
                        obfuscatedAccountSecretProvider,
                    ),
                    purchaseTokenCipher = GoogleKmsPurchaseTokenCipher(
                        keyResourceName = requireNotNull(config.kmsTokenEncryptionKeyResource),
                        gateway = GoogleCloudKmsGateway(kmsClient),
                    ),
                    emailHashSecretProvider = emailHashSecretProvider,
                    reviewerGrantPolicy = reviewerGrantPolicy,
                    rtdnEventRepository = FirestoreRtdnEventRepository(
                        store = GoogleCloudFirestoreRtdnEventDocumentStore(firestore),
                        ttlDays = config.rtdnEventTtlDays,
                    ),
                    firestore = firestore,
                    secretManagerClient = secretManagerClient,
                    kmsClient = kmsClient,
                )
            } catch (exception: Exception) {
                runCatching { firestore.close() }
                runCatching { secretManagerClient.close() }
                runCatching { kmsClient.close() }
                throw exception
            }
        }

        private fun createAndroidPublisher(): AndroidPublisher {
            val credentials = GoogleCredentials.getApplicationDefault()
                .createScoped(AndroidPublisherScopes.ANDROIDPUBLISHER)
            return AndroidPublisher.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                HttpCredentialsAdapter(credentials),
            )
                .setApplicationName("Just Notes Entitlement API")
                .build()
        }
    }
}
