# Parent Simulated Backend/RTDN Gate Report - 2026-06-13

## Verdict

LOCAL SIMULATION GATE ADDED AND PASSING.

This does not remove the production release blockers for Play Console setup, legal URLs, backend deployment, or real Google Play purchase/lifecycle validation. It does turn the backend verification, acknowledgement, RTDN re-query, duplicate RTDN, rejected-token, and replacement-token expectations into local Kotlin code and unit-test evidence.

## Implemented

- Added `PremiumBackendEntitlementMapper` and related backend/RTDN models in `app/src/main/java/com/example/notepad/billing/PremiumBackendEntitlement.kt`.
- Added `PremiumBackendEntitlementMapperTest` in `app/src/test/java/com/example/notepad/billing/PremiumBackendEntitlementMapperTest.kt`.
- The mapper is deliberately pure Kotlin and side-effect free:
  - accepts only expected package name, known Premium products, and known Premium base plans
  - maps backend-verified active/grace states to Premium access
  - keeps unacknowledged or acknowledgement-failed active purchases in `VerificationPending`
  - maps expired, revoked, on-hold, and pending states to non-entitled snapshots
  - models RTDN handling as "dedupe message ID, then re-query Google Play" instead of trusting notification type payloads

## Simulated Coverage Added

- Backend-verified acknowledged active subscription grants Premium.
- Active subscription without backend acknowledgement stays `VerificationPending`.
- Backend acknowledgement failure schedules retry and does not grant Premium.
- Wrong package, wrong product, and wrong base plan fail closed.
- RTDN duplicate message is ignored after a revoke re-query.
- RTDN notification type is not trusted; entitlement follows the re-queried Google Play state.
- Replacement purchase moves entitlement to the new purchase token hash.

## Verification

- `git diff --check`: PASS.
- Static billing guardrail scan: PASS.
  - Billing dependency remains `com.android.billingclient:billing:9.0.0`.
  - No `billing-ktx`, old SKU APIs, no-arg `enablePendingPurchases()`, risky `subscriptionOfferDetails?.firstOrNull`, or default-true client entitlement flag found in scanned source/tests.
  - The only `premium_entitled` match is the expected legacy migration key.
- Focused billing unit tests via Windows PowerShell/JBR:
  - `:app:testDebugUnitTest --tests com.example.notepad.billing.PremiumBackendEntitlementMapperTest --tests com.example.notepad.billing.PremiumBillingStateTest --no-daemon`: PASS.
- Broader unit gate via Windows PowerShell/JBR:
  - `:app:testDebugUnitTest :app:testReleaseUnitTest --no-daemon`: PASS.
- Required Codex review:
  - `codex -m gpt-5.5 -c 'model_reasoning_effort="xhigh"' review --uncommitted`: PASS.
  - Result: no discrete correctness issues found in staged, unstaged, or untracked changes reviewed.

## Still Blocked Externally

- Play Console app/catalog/base-plan/offer setup.
- License testers, internal test track, and opt-in validation.
- Real Google Play purchase, restore, cancellation, refund/revoke, pending, suspended, grace, and account-hold lifecycle validation.
- Deployed backend using Google Play Developer API.
- Deployed RTDN Pub/Sub receiver and operational monitoring.
- Real Privacy Policy and Terms URLs.

## Notes

- No commit, push, APK copy, release, or Play Console action was performed.
- Direct Linux Gradle remains blocked because WSL has no Linux `java`; Windows PowerShell/JBR was used for Gradle verification.
