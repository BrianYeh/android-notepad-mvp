# Just Notes Entitlement Backend

This module contains the v1.0.6-C production adapters and the intentionally
non-granting HTTP boundary for the future v1.0.7 purchase flow.

Implemented production adapters:

- Google ID-token verification with an exact Web client audience and issuer allowlist;
- Secret Manager-backed, versioned HMAC secrets with a short in-process cache;
- Cloud KMS symmetric purchase-token encryption/decryption;
- Firestore monotonic entitlement persistence and transactional purchase-token ownership binding;
- Android Publisher `purchases.subscriptionsv2.get` verification;
- Android Publisher subscription acknowledgement with retryable failure classification.

The adapters are wired with Application Default Credentials and the Cloud Run
runtime service account. No service-account JSON key, OAuth client secret,
Secret Manager payload, KMS key material, Google ID token, or raw purchase token
belongs in source control or deployment environment variables.

## v1.0.6-C Safety Boundary

`POST /v1/billing/verify` deliberately does not call the Play verifier,
acknowledge a purchase, persist a subscription, or grant Premium. It returns
HTTP `202`, `hasPremium=false`, `VerificationPending`, and
`POST_VERIFY_DISABLED`. The real purchase lifecycle remains deferred to the
separately reviewed v1.0.7 flow.

`POST /v1/play/rtdn` remains disabled with HTTP `501`.

## Local Verification

From the repository root:

```powershell
./gradlew.bat :backend:test --no-daemon
```

Container build:

```bash
docker build -t just-notes-entitlement-backend:test backend
```

## Cloud Run Configuration

`cloudrun/dev.env.yaml` contains only non-secret resource names and identifiers.
The Cloud Run service account must have only the resource-scoped permissions
recorded in the billing setup guide. The container validates all required
Firestore, Secret Manager, KMS, and OAuth identifiers at startup and fails
closed when they are absent or malformed.

The runtime image contains no in-memory or no-op entitlement repository. A
backend record is eligible for a Premium response only when it is explicitly
`BackendVerified`, acknowledged, grantable, unexpired, and inside the stale
window. The final container runs as an unprivileged numeric user.

Deployment is allowed only after tests and `codex xhigh/review` pass. The
reviewed deployment target is:

- project: `gen-lang-client-0599059254`
- region: `asia-east1`
- service: `just-notes-entitlement-api-dev`
- runtime identity:
  `just-notes-entitlement-api-dev@gen-lang-client-0599059254.iam.gserviceaccount.com`
