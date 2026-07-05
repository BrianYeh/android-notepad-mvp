# Just Notes Backend Skeleton

This module is the v1.0.6-A backend entitlement skeleton. It is intentionally fail-closed:

- No real Google Play Developer API calls are made.
- No Cloud Run, Firestore, Pub/Sub, or service account resources are created.
- Runtime auth uses a fail-closed verifier until real Google ID token verification is configured.
- Test fakes live in tests only and must not be used to grant production entitlements.

Planned Firestore shape:

- `users/{googleSub}`
- `subscriptions/{purchaseTokenHash}`
- `entitlements/{googleSub}`
- `verificationEvents/{id}` / `auditLogs/{id}`

Future purchase flow must use a backend-owned encrypted purchase token strategy, token-hash ownership transactions, and a Google Play Billing obfuscated account/profile ID derived from the signed-in Google subject.
