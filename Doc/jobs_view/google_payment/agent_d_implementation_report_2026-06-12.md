# Agent D Google Payment Implementation Report - 2026-06-12

## Summary

Implemented safe Google Play Billing/Premium scaffolding that can be done before Brian's Phase 0 decisions and before a production backend exists. The app now targets Play Billing Library 9, uses explicit subscription product/base-plan selection, replaces the old boolean entitlement cache with a subscription snapshot model, persists acknowledgement retry metadata for internal client-only testing, and blocks production purchase launch/access until backend verification, backend acknowledgement, and RTDN are available.

No Google Pay, external card payment, commit, push, upload, or release work was performed.

## Files Changed

- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/notepad/billing/PremiumBilling.kt`
- `app/src/main/java/com/example/notepad/billing/PremiumCatalog.kt`
- `app/src/main/java/com/example/notepad/billing/PremiumEntitlement.kt`
- `app/src/main/java/com/example/notepad/billing/PremiumEntitlementStore.kt`
- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
- `app/src/main/java/com/example/notepad/ui/UiText.kt`
- `app/src/test/java/com/example/notepad/billing/PremiumBillingStateTest.kt`
- `Doc/jobs_view/google_payment/agent_d_implementation_report_2026-06-12.md`

## Implemented

- Upgraded billing dependency from `com.android.billingclient:billing-ktx:7.1.1` to `9.0.0`.
- Added explicit `com.android.vending.BILLING` manifest permission and enabled custom `BuildConfig` generation.
- Added build-time guard:
  - debug: `ALLOW_CLIENT_ONLY_BILLING_ENTITLEMENT=true`
  - release: `ALLOW_CLIENT_ONLY_BILLING_ENTITLEMENT=false`
- Added `PremiumCatalog` with preferred safe constants:
  - subscription product: `just_notes_premium`
  - base plans: `monthly`, `annual`
  - legacy product IDs are recognized as explicit fallback candidates only.
- Removed blind subscription offer selection. The app selects only matching `productId` + `basePlanId` + no-offer (`offerId == null`) base-plan entries.
- Migrated PBL integration for:
  - `enablePendingPurchases(PendingPurchasesParams...)`
  - `enableAutoServiceReconnection()`
  - PBL 9 `QueryProductDetailsResult`
  - unfetched product status reporting
  - sub-response code display from `BillingResult`
- Replaced `premium_entitled: Boolean` entitlement behavior with `PremiumSubscriptionSnapshot`, `PremiumSubscriptionStatus`, `PremiumEntitlementSource`, and `PremiumAcknowledgementStatus`.
- Kept legacy `premium_entitled` only as a migration key; a legacy true value becomes non-entitled `VerificationPending`, not trusted premium access.
- Production access is granted only for `BackendVerified` `Active` or `GracePeriod` states. No backend writer exists yet, so production purchases/access remain blocked.
- Debug/internal client-only access can unlock only after a client-observed purchase is acknowledged.
- Pending purchases never unlock premium.
- Active purchased subscriptions are preferred over pending purchases so pending plan changes do not downgrade existing access.
- Billing setup failure no longer overwrites persisted entitlement; it only marks availability/error in memory.
- Persisted raw purchase token only in the acknowledgement retry slot; entitlement metadata stores token hashes.
- Added acknowledgement retry metadata with bounded exponential backoff.
- Premium UI now shows pending/verification/backend-required states and disables purchase launch when backend-required release behavior is in effect.
- Expanded unit coverage for entitlement access rules and catalog/base-plan selection.

## Blockers

- Phase 0 decisions are still required before Play Console setup or production monetization:
  - final package/application ID
  - product model and exact product/base-plan IDs
  - prices, countries, launch track, tester setup
  - trial/intro/winback offer policy
  - backend identity model
  - backend host/runtime/owner
  - Privacy Policy URL and Terms URL
- No production backend exists for Google Play Developer API verification, backend acknowledgement, RTDN handling, linked-purchase-token invalidation, or server-authoritative entitlement writes.
- Subscription change handling is only guarded, not complete. Backend still needs to handle monthly/annual switches, replacement modes, resubscribe, deferred replacement, and linked purchase tokens.
- Privacy/Terms are still not real links because URLs are not decided.
- Real purchase testing cannot start until Play Console catalog/test track/license testers are configured.

## Verification

- `git diff --check`: passed.
- Static sweeps:
  - no PBL 7 dependency remains in `app/build.gradle.kts`
  - no no-arg `enablePendingPurchases()` remains
  - no old `SkuDetails`/`SkuType`/`querySkuDetails` usage found
  - old `premium_entitled` remains only as a migration key
  - risky `subscriptionOfferDetails?.firstOrNull()` billing selection removed
- Codex CLI review:
  - Ran `codex -m gpt-5.5 -c 'model_reasoning_effort="xhigh"' review --uncommitted`.
  - Review found two P2 issues:
    - transient billing setup failure overwrote cached entitlement
    - pending purchase could downgrade an existing purchased subscription
  - Both review findings were fixed in `PremiumBilling.kt`.

## Verification Blocked

- Gradle build/unit tests could not run in this runtime:
  - `./gradlew :app:compileDebugKotlin --no-daemon` failed because Linux `java` is not installed.
  - `powershell.exe` is not on PATH.
  - `/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe` failed with a WSL vsock error.
  - The mounted Android Studio JBR is Windows-only and cannot execute as Linux `/mnt/d/android/Android Studio/jbr/bin/java`.
- Because Gradle could not run, PBL 9 compile/API verification and unit tests must be run from Brian's normal Windows Android environment.

## Agent E Follow-Up

- Run `compileDebugKotlin`, debug unit tests, release unit tests, and relevant connected tests in the proper Windows/Android environment.
- Verify PBL 9 APIs compile, especially `PendingPurchasesParams`, `QueryProductDetailsResult`, `UnfetchedProduct`, and `BillingResult.onPurchasesUpdatedSubResponseCode`.
- Add a fake/injectable billing gateway if deeper BillingClient interaction tests are required.
- Add store/ack-retry tests once an Android test runtime or SharedPreferences fake is available.
- Implement backend verification/acknowledgement/RTDN before production billing.
- Add manage-subscription deep links and real Privacy/Terms links after Brian provides URLs/package/product decisions.
- Validate license tester/internal-track purchase flows and Play Billing Lab lifecycle states after Play Console setup.
