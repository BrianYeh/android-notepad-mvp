# Agent F Billing/Premium Review Report - 2026-06-12

## Verdict

PASS WITH BLOCKERS.

Agent G should not start the Google Billing/Premium connected/regression validation gate yet. Return to Agent E/D for the suspended-purchase acknowledgement fix below, then rerun focused static/unit verification and the required xhigh review. Non-billing exploratory smoke testing is not blocked by this report, but it should not be counted as the billing gate.

## Findings

1. P2 - Suspended purchased subscriptions are never recorded for acknowledgement retry.

   In `PremiumBilling.updateEntitlementFromPurchases()`, `purchasedPurchases` filters out `Purchase.PurchaseState.PURCHASED` purchases when `isSuspended` is true:

   - `app/src/main/java/com/example/notepad/billing/PremiumBilling.kt:205`
   - `app/src/main/java/com/example/notepad/billing/PremiumBilling.kt:212`
   - `app/src/main/java/com/example/notepad/billing/PremiumBilling.kt:246`

   That keeps suspended purchases non-entitled, which is correct, but it also means unacknowledged suspended purchased tokens never enter `pendingAcknowledgementPurchases`, are not persisted in `PremiumEntitlementStore`, and are not retried through the client-only acknowledgement path. Google still requires acknowledgement for purchased tokens; missing it can cause refund/revocation during internal billing tests. Fix by keeping suspended purchases non-entitled while still recording/retrying unacknowledged `PURCHASED` premium tokens.

## Guardrail Review

- Production entitlement is not granted from client-observed purchases: PASS. `ClientObserved` access requires the explicit build gate plus `Active` plus `Acknowledged`; generated debug/release BuildConfig values are currently `false`.
- Risky offer selection is removed: PASS. No `subscriptionOfferDetails?.firstOrNull()` path remains; selection uses product ID, base plan ID, and `offerId == null`.
- Legacy SKU APIs are absent: PASS. No `SkuDetails`, `SkuType`, or `querySku*` usage found.
- PBL pending-purchase setup uses the PBL 9 params API: PASS. No no-arg `enablePendingPurchases()` remains.
- Default-true premium flags are absent: PASS. `ALLOW_CLIENT_ONLY_BILLING_ENTITLEMENT` defaults false and release is hardcoded false.
- Acknowledgement reliability: BLOCKED by the suspended-purchase issue above.

## Verification Commands

- `git status --short` - inspected current uncommitted scope; billing/premium source files are modified and new billing/report files are untracked.
- `git diff --stat` - reviewed source diff size; tracked diff is six files, 587 insertions and 81 deletions, plus untracked billing files.
- `git diff --name-status` - confirmed tracked modified files are `app/build.gradle.kts`, manifest, `PremiumBilling.kt`, Premium UI/copy, and billing state tests.
- `git ls-files --others --exclude-standard Doc/jobs_view/google_payment app/src/main/java/com/example/notepad/billing` - confirmed untracked Google payment reports plus `PremiumCatalog.kt`, `PremiumEntitlement.kt`, and `PremiumEntitlementStore.kt`.
- `git diff --check` - passed with no whitespace errors.
- `rg -n "billing-ktx|billingclient:billing:|enablePendingPurchases\\(\\)|subscriptionOfferDetails\\?\\.firstOrNull|querySku|SkuDetails|ALLOW_CLIENT_ONLY_BILLING_ENTITLEMENT.*true|premium_entitled|isSuspended|PendingPurchasesParams|enableAutoServiceReconnection|setOfferToken" app/build.gradle.kts app/src/main app/src/test app/src/androidTest` - found expected PBL 9 dependency/API usage and the legacy migration key; found no PBL 7 dependency, no no-arg pending-purchase setup, no risky offer `firstOrNull`, no SKU APIs, and no default-true entitlement flag.
- `rg -n "com.android.vending.BILLING|com.google.android.play.billingclient.version" app/build/intermediates/merged_manifest app/build/intermediates/merged_manifests` - existing generated debug/release merged manifests contain the Billing permission and billing client version metadata.
- `rg -n "ALLOW_CLIENT_ONLY_BILLING_ENTITLEMENT" app/build/generated/source/buildConfig` - existing generated debug, androidTest debug, and release BuildConfig values are all `false`.
- `codex -m gpt-5.5 -c 'model_reasoning_effort="xhigh"' review --uncommitted` - completed and reported the P2 suspended-purchase acknowledgement issue above.

## Verification Limits

- I did not modify source code, commit, push, build an APK, copy an APK, or touch Play Console.
- I did not run Gradle unit or connected tests in this pass; Agent E reported compile/unit tests passing before this independent review, but this report only counts the static scans and xhigh review listed above.
- Connected Android validation and real billing flows still require the blocker fix, Play Console/test-track/license-tester setup, and the normal emulator/device gate.

## Remaining Blockers

- Fix suspended purchased-token acknowledgement retry before Agent G's billing validation.
- Phase 0 decisions remain open: final application ID, product model/base-plan IDs, prices, countries, offers, testers, launch track, Privacy Policy URL, and Terms URL.
- No production backend exists yet for Google Play Developer API verification, backend acknowledgement, RTDN, linked-token invalidation, or server-authoritative entitlement writes.
- Subscription replacement/upgrade/downgrade behavior remains a backend lifecycle requirement before production monetization.
