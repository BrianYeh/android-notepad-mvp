# Just Notes Entitlement Backend

This module contains the production adapters and active v1.0.7
backend-authoritative purchase flow for Just Notes internal testing.

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

## v1.0.7 Purchase Boundary

Both billing endpoints require a Google ID token verified against the exact
configured Web client audience and issuer allowlist. Billing responses use
`Cache-Control: no-store`.

- `GET /v1/billing/context` derives the Play obfuscated external account ID
  from the authenticated Google subject. It returns `503` if the backing secret
  is unavailable.
- `POST /v1/billing/verify` accepts at most 8 KiB of JSON and transiently passes
  the raw purchase token through this order: Play verification, authoritative
  catalog selection, obfuscated-account ownership proof, KMS encryption,
  owner-bound Firestore persistence, backend acknowledgement, and final
  entitlement reconciliation.
- `GET /v1/entitlement` remains an owner-only read and clamps expired,
  unacknowledged, invalid-catalog, or excessively stale records to
  non-Premium.

Premium is written only by the final Firestore reconciliation transaction. It
uses the newest stored Play lifecycle plus the current acknowledgement state;
the client never grants from Play state alone. Concurrent acknowledgement uses
a lease and monotonically increasing claim generation so a stale worker cannot
overwrite a newer completion.

`subscriptions/{purchaseTokenHash}` stores only backend-owned identity and
encrypted verification state:

- `purchaseTokenHash`, `hashVersion`, and `pepperVersion`;
- `ownerGoogleSub`, package/product/base-plan/offer, linked-token hash,
  authoritative status, expiry, and Play observation time;
- `tokenCiphertext`, KMS key version, encryption time, and algorithm;
- acknowledgement state, attempt count, next retry, redacted error code,
  claim generation, and lease expiry.

It must never contain a raw purchase token, Google ID token, email address,
secret payload, or exported key material.

Retryable acknowledgement failures start at 15 minutes, double per failed
attempt, and cap at 6 hours. There is no background token cache: a later Android
refresh/restore reacquires the purchase from Play and invokes
`POST /v1/billing/verify` again. That request is the retry trigger.

`POST /v1/play/rtdn` remains disabled with HTTP `501`.
Until a separately reviewed RTDN flow is deployed, refunds, renewals, and
off-device cancellations become visible only when the client performs another
verified query.

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
