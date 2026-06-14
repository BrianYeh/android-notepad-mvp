# Agent B Google Payment Implementation Plan - 2026-06-12

## Scope

Brian asked for a planning-only response to Agent A's Google payment assessment. This document does not implement code, commit, push, or run a release. It translates Agent A's recommendations into a concrete implementation plan for the existing Just Notes Android app at `/mnt/d/AndroidStudioProjects`.

## Current App Findings

- The app already uses Play Billing, not Google Pay, through `app/src/main/java/com/example/notepad/billing/PremiumBilling.kt`.
- `app/build.gradle.kts` currently depends on `com.android.billingclient:billing-ktx:7.1.1`.
- `PremiumPlan` currently has two subscription product IDs:
  - `just_notes_premium_monthly`
  - `just_notes_premium_annual`
- `PremiumBillingState` is currently a small client-side state object: `isPremium`, `debugPremiumOverride`, `billingAvailable`, `loading`, `monthlyPrice`, `annualPrice`, and `lastError`.
- Entitlement is cached only as `premium_entitled: Boolean` in the `billing_entitlement` SharedPreferences file.
- `PremiumBilling.updateEntitlement()` grants local premium when any matching `PURCHASED` subscription is seen, before durable acknowledgement success and without backend verification.
- `PremiumBilling.acknowledgePurchase()` only attempts acknowledgement once and does not persist retry work.
- `launchPurchase()` chooses `subscriptionOfferDetails?.firstOrNull()`, which is risky once Play Console has more than one base plan or offer.
- `NotepadViewModel` owns `PremiumBilling`, starts it in `init`, exposes `refreshPremiumEntitlement()`, and combines real billing state with `DebugPremiumAccess`.
- `PremiumScreen` in `NotepadApp.kt` already shows plan rows only when real localized prices are available, has a subscribe button, and has a restore button.
- Premium UI strings in `UiText.kt` still describe a setup-pending preview rather than a live auto-renewing subscription.
- The app currently has no backend/API module for entitlement verification. Existing network dependencies are for Google Drive sync and Google sign-in.
- The app's `applicationId` is still `com.example.notepad`; Brian should decide whether this is the final Play package name before Play Console setup.
- Existing automated coverage includes `PremiumBillingStateTest`, release tests for the debug premium stub, and several instrumentation tests around premium feature gates and the Premium screen fallback.

## Official Constraints Checked

- Google Pay API is not the right product: Google says Android apps selling digital goods or services must use Google Play In-app Billing, while Google Pay in apps is for physical products and services.
- Google Play Billing Library 9.0.0 is available as of 2026-05-19.
- Google's Billing Library deprecation table says Billing Library 7 can be used for new apps or updates only until 2026-08-31, with extension request available until 2026-11-01.
- Google recommends a secure backend for purchase verification, entitlements, subscription lifecycle management, and RTDN handling.
- Google says new subscription purchases must be acknowledged within three days or they are refunded and revoked.

References:

- Google Pay FAQ: https://developers.google.com/pay/api/android/support/faq
- Play Billing overview: https://developer.android.com/google/play/billing
- Play Billing release notes: https://developer.android.com/google/play/billing/release-notes
- Play Billing deprecation FAQ: https://developer.android.com/google/play/billing/deprecation-faq
- Purchase lifecycle and RTDNs: https://developer.android.com/google/play/billing/lifecycle
- Subscription lifecycle: https://developer.android.com/google/play/billing/lifecycle/subscriptions
- Billing testing: https://developer.android.com/google/play/billing/test
- License testing: https://support.google.com/googleplay/android-developer/answer/6062777
- Subscription product setup: https://support.google.com/googleplay/android-developer/answer/140504

## Recommended Product Model

Preferred production model, if Brian has not already activated Play Console products:

- One subscription product:
  - Product ID: `just_notes_premium`
  - User-facing name: `Just Notes Premium`
- Two auto-renewing base plans under that product:
  - Base plan ID: `monthly`
  - Base plan ID: `annual`
- Optional offers should be deferred until the base subscription flow is proven:
  - No free trial or intro offer for the first test pass.
  - Add offers only after the app selects offers by `basePlanId` and `offerId`, never by `firstOrNull()`.

Why this model:

- The app sells one benefit bundle with different billing periods. That maps better to one subscription product with multiple base plans than to two unrelated subscription products.
- It keeps upgrade/downgrade, renewal, cancellation, and reporting cleaner.
- It forces the client to select the intended base plan explicitly, which removes the current `firstOrNull()` offer-token risk.

Fallback model, if Play Console already has active products matching current code:

- Keep:
  - `just_notes_premium_monthly`
  - `just_notes_premium_annual`
- Create one auto-renewing base plan per product.
- Still update the client to select by explicit product ID plus base plan/offer criteria, and treat this as a compatibility path rather than the ideal long-term catalog.

Decision point for Brian:

- If no subscription products have been activated yet, use `just_notes_premium` with `monthly` and `annual` base plans.
- If the two existing product IDs are already active in Play Console, avoid deleting or renaming them without checking Play Console restrictions and reporting needs.

## Phased Work Breakdown

### Phase 0 - Product and Release Identity Decisions

Goal: avoid irreversible Play Console mistakes.

Tasks:

- Confirm final Play package name before creating products or uploading test releases:
  - Current app ID is `com.example.notepad`.
  - Decide whether production should remain that ID or use a final branded package ID.
- Choose product catalog model:
  - Preferred: `just_notes_premium` with `monthly` and `annual` base plans.
  - Fallback: current two-product model.
- Choose prices, launch countries, renewal type, grace period/account hold defaults, pause/resubscribe settings, and whether trials are in scope.
- Confirm the exact Premium benefit bundle:
  - Current app gates folders, text formatting, reminders/repeat reminders/calendar filters.
  - Decide whether OCR, checklist, sync, import/export, or future writing assistant are free or paid before writing store/subscription copy.
- Decide production security bar:
  - Internal testing can use client-only entitlement with warnings.
  - Production should wait for backend verification and RTDN.

Files likely touched later:

- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml` if package/permissions/links change.
- Play Console only; no code required for some decisions.

Client-only now:

- None required beyond documenting decisions.

Wait for backend/Play Console:

- Product IDs, base plan IDs, prices, and tester setup must exist before live purchase testing.

### Phase 1 - Billing Library 9 Upgrade and API Safety

Goal: remove the Billing Library 7 deadline risk before deeper billing work.

Tasks:

- Update `app/build.gradle.kts` from Billing Library `7.1.1` to `9.0.0` or the latest supported PBL 9 patch available at implementation time.
- Review PBL 9 migration notes and compile errors.
- Confirm the merged release manifest includes:
  - `com.android.vending.BILLING`
  - `com.google.android.play.billingclient.version`
- Centralize billing constants in a small catalog model instead of scattering product IDs in `PremiumPlan`.
- Make product/base-plan selection explicit:
  - For the preferred model, query `just_notes_premium` once and select `SubscriptionOfferDetails` by `basePlanId == "monthly"` or `"annual"`.
  - If using current two-product model, still select the expected base plan/offer token explicitly.
- Treat missing products as a setup/configuration state, not just generic billing unavailable.
- Handle BillingResult categories deliberately:
  - `OK`
  - `USER_CANCELED`
  - `ITEM_ALREADY_OWNED`
  - `SERVICE_DISCONNECTED`
  - `SERVICE_UNAVAILABLE`
  - `NETWORK_ERROR`
  - `BILLING_UNAVAILABLE`
  - `DEVELOPER_ERROR`

Files likely touched:

- `app/build.gradle.kts`
- `app/src/main/java/com/example/notepad/billing/PremiumBilling.kt`
- New billing files if the single class is split:
  - `billing/PremiumCatalog.kt`
  - `billing/PlayBillingGateway.kt`
  - `billing/BillingResultMapper.kt`

Tests:

- Expand `app/src/test/java/com/example/notepad/billing/PremiumBillingStateTest.kt`.
- Add unit tests for catalog selection and offer-token selection using fake product models where possible.
- Run debug unit tests, release unit tests, and existing connected tests before merging.

Client-only now:

- Yes. This phase can be implemented before backend and before Play Console products, though real price/purchase testing waits for Play Console.

Wait for backend/Play Console:

- Real product detail loading and purchase flow validation.

### Phase 2 - Local Entitlement State Machine and Cache Hardening

Goal: replace the boolean entitlement cache with a subscription-aware model.

Tasks:

- Replace `premium_entitled: Boolean` with a structured local cache:
  - `status`: `Unknown`, `Free`, `VerificationPending`, `Active`, `GracePeriod`, `OnHold`, `Expired`, `Revoked`, `PendingPurchase`, `BillingUnavailable`, `Error`
  - `productId`
  - `basePlanId`
  - `offerId`
  - `purchaseTokenHash`
  - `purchaseTime`
  - `expiryTime`
  - `lastPlayQueryAt`
  - `lastBackendVerifiedAt`
  - `lastEntitlementChangeAt`
  - `acknowledgementState`
  - `source`: `BackendVerified`, `ClientObserved`, `DebugOverride`
- Keep `hasPremiumAccess` strict:
  - True for backend-verified `Active` and `GracePeriod`.
  - True for debug override in debug builds only.
  - False for `PendingPurchase`, `OnHold`, `Expired`, `Revoked`, `Free`, and unknown/error states.
- For a client-only internal-test phase, allow `ClientObservedActive` behind a clear internal flag, but do not treat it as production-authoritative.
- Query purchases on:
  - app start
  - Premium screen restore/refresh tap
  - foreground/resume after a billing flow
  - billing service reconnection
- Do not unlock for `PENDING` purchases.
- Clear or downgrade local entitlement when Play query returns no active matching purchase, unless a fresh backend-verified offline grace cache is still valid.
- Use a short offline cache policy:
  - Production backend-verified access can survive brief offline use.
  - Proposed starting value: 72 hours after `lastBackendVerifiedAt`.
  - Client-only observed access should be test-only and shorter.

Files likely touched:

- `billing/PremiumBilling.kt` or replacement classes.
- New `billing/PremiumEntitlement.kt`.
- New `billing/PremiumEntitlementStore.kt`.
- `app/src/main/java/com/example/notepad/viewmodel/NotepadViewModel.kt`.
- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt` where `hasPremiumAccess` is consumed.

Tests:

- State-machine tests for every status.
- Cache load/migration tests from the old boolean key.
- Offline grace tests.
- Pending purchase does not unlock.
- Expired/revoked/on-hold states remove access.
- Debug override does not mutate real subscription state.

Client-only now:

- Yes, as internal-test hardening.

Wait for backend/Play Console:

- Real `GracePeriod`, `OnHold`, `Expired`, and `Revoked` authority should come from backend plus Developer API, not the app alone.

### Phase 3 - Acknowledgement and Restore Reliability

Goal: eliminate refund/revoke risk from missed acknowledgement and make restore deterministic.

Client-only internal testing path:

- Persist unacknowledged purchase tokens locally until acknowledged or superseded.
- Retry acknowledgement on:
  - app start
  - restore tap
  - billing reconnection
  - foreground after purchase
- Add exponential backoff metadata:
  - `nextAckAttemptAt`
  - `ackAttemptCount`
  - `lastAckError`
- Only mark acknowledgement complete after `BillingClient.acknowledgePurchase()` returns `OK` or a later query shows already acknowledged.
- Surface a non-scary UI state for "Purchase received, finishing setup" when acknowledgement or verification is still pending.

Production backend path:

- App sends purchase token to backend after purchase or restore query.
- Backend validates with Google Play Developer API and acknowledges the subscription if needed.
- App treats backend response as entitlement authority.
- Client acknowledgement can remain as a temporary internal-test fallback, but production should not rely on it.

Restore strategy:

- Rename the current "Restore purchase status" behavior conceptually to "Refresh purchase status"; Google Play subscriptions do not need a separate restore purchase transaction.
- On restore tap:
  - reconnect billing if needed
  - query active subscriptions
  - send each matching purchase token to backend
  - update local cache from backend response
  - show one of: active restored, no active subscription found, still pending, billing unavailable, verification failed
- Do not use purchase history as an entitlement source for expired subscriptions.

Files likely touched:

- `billing/PremiumBilling.kt` or new gateway/repository split.
- `billing/PremiumAckQueue.kt` for client-only testing if backend is not ready.
- `viewmodel/NotepadViewModel.kt`.
- `ui/NotepadApp.kt`.
- `ui/UiText.kt`.

Tests:

- Ack succeeds after retry.
- Ack failure does not lose retry token.
- Pending purchase does not unlock.
- Restore active purchase updates entitlement.
- Restore no purchase clears stale client-observed entitlement.
- Backend verification failure leaves user in `VerificationPending` or `Free`, depending on error type.

Client-only now:

- Yes for internal testing.

Wait for backend/Play Console:

- Production acknowledgement authority should wait for backend.
- Restore with real purchases waits for Play Console products and license/internal testers.

### Phase 4 - Backend Entitlement Service

Goal: make entitlements secure, auditable, and responsive to refunds/cancellations.

Minimum backend responsibilities:

- Verify every purchase token using Google Play Developer API.
- Store purchase-token lifecycle and entitlement state.
- Acknowledge new subscription purchases after validation.
- Receive RTDN events through Google Cloud Pub/Sub.
- Re-query Google Play on every RTDN before changing entitlements.
- Expose a small entitlement API to the app.
- Maintain idempotency and audit logs.

Suggested API shape:

- `POST /v1/play/subscriptions/verify`
  - Request: `packageName`, `productId`, `basePlanId`, `purchaseToken`, `appInstanceId`, optional signed-in app account ID.
  - Response: entitlement status, expiry, product/base plan, whether acknowledged, next refresh deadline.
- `GET /v1/entitlements/premium`
  - Request identity depends on Brian's account decision.
  - Response: current Premium entitlement state.
- `POST /v1/play/rtdn`
  - Pub/Sub push endpoint or pull-worker subscriber.
  - Must verify message authenticity, deduplicate by message ID, decode RTDN data, then call Google Play Developer API.

Suggested data model:

- `app_installations`
  - `app_instance_id`
  - app version, package name, platform, created/last seen
- `users` if Brian adds an app account system
  - If not, keep entitlement Play-account/install-bound for now.
- `play_purchase_tokens`
  - token hash, encrypted token if needed, package name, product ID, base plan ID, linked purchase token hash, order ID, acknowledgement state, subscription state, expiry, region, latest event time.
- `premium_entitlements`
  - user/install identity, status, source token hash, valid until, last verified at.
- `rtdn_events`
  - Pub/Sub message ID, event type, token hash, received at, processed at, result.

Identity decision:

- No app account, lower friction:
  - App generates an `appInstanceId`.
  - Purchases restore by querying Google Play on the device and verifying the returned token.
  - Cross-device access should work only when Google Play returns the subscription on that device's Play account.
  - Backend cannot reliably push account-wide entitlement without app identity.
- App account, stronger long-term:
  - Tie entitlement to a signed-in Just Notes user.
  - Use `setObfuscatedAccountId()` in BillingFlowParams with a non-PII stable hash.
  - Supports cross-device, customer support, and safer token ownership checks.
- Reusing Google Drive sign-in:
  - Possible, but decide carefully because the Google Drive account may not be the same as the Play billing account.

Security requirements:

- Never trust SharedPreferences or client-reported entitlement for production.
- Never send raw email or PII in Play Billing obfuscated IDs.
- Treat purchase tokens as sensitive.
- Store full tokens only where needed for Developer API calls; hash for lookups/logs.
- Make backend token verification idempotent.
- Reject tokens for the wrong package name or product/base plan.
- Consider Play Integrity after the basic backend works, especially if abuse appears.

Files likely touched in Android app:

- New API client package if backend is added:
  - `data/PremiumEntitlementApi.kt` or `billing/PremiumEntitlementApi.kt`
  - Network dependency decision: existing repo does not use Retrofit/OkHttp directly.
- `billing/PremiumBilling.kt` or `billing/PremiumRepository.kt`.
- `viewmodel/NotepadViewModel.kt`.

Backend files:

- Not present in this repo today. Brian needs to choose hosting/runtime before implementation.

Client-only now:

- App-side API interfaces can be drafted behind fakes, but production behavior should wait.

Wait for backend/Play Console:

- Production entitlement authority, RTDN, reliable revoke/refund/cancel handling.

### Phase 5 - Premium UI, Disclosure Copy, and Policy Readiness

Goal: turn the Premium tab from preview into a compliant subscription purchase screen.

Tasks:

- Replace preview strings in `UiText.kt`:
  - Remove "Subscription preview only" and "Billing is not connected yet" from live commerce state.
  - Show clear plan labels such as "Monthly" and "Annual".
  - Show localized price from Google Play only.
  - Show renewal/cancellation disclosure near the subscribe button.
- Add clear subscription disclosure copy:
  - Subscription auto-renews unless canceled.
  - Payment is charged to the Google Play account.
  - Manage or cancel in Google Play subscriptions.
  - Trial/intro offer terms if Brian enables offers.
  - Current period access behavior after cancellation.
- Make Privacy Policy and Terms of Service actual links.
- Add "Manage subscription" link for active users:
  - Deep link to Play subscription management for the relevant package/product.
- Add explicit status messages:
  - Premium active
  - Purchase pending
  - Finishing purchase setup
  - Payment issue/account hold
  - Subscription expired
  - No active subscription found
  - Billing temporarily unavailable
- Keep current feature samples, but ensure benefit copy exactly matches real gated features.
- Maintain current fallback behavior:
  - If products are unavailable, no subscribe button should be shown.
  - Free features should remain usable.

Files likely touched:

- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
- `app/src/main/java/com/example/notepad/ui/UiText.kt`
- Possibly `app/src/main/AndroidManifest.xml` for external link handling if needed.

Tests:

- Premium screen with prices shows subscribe button.
- Missing product details hides subscribe button.
- Active subscription shows active/manage state.
- Pending/hold/expired states show correct messages.
- Privacy/terms links are clickable.
- Existing `premiumFallbackHidesCommerceAndShowsAllowedBenefits` should be updated, not weakened.
- Existing premium gates should still route free users to Premium and preserve drafts.

Client-only now:

- UI state work and copy scaffolding can be done now.

Wait for backend/Play Console:

- Final price/period copy should be checked against real Play product details and Brian's legal/privacy URLs.

### Phase 6 - Play Console Setup Checklist

Goal: prepare an environment where subscriptions can actually be found and tested.

Checklist:

- Confirm final `applicationId`.
- Create or select the Play Console app for that exact package name.
- Complete merchant/monetization setup.
- Upload an internal testing release with Billing Library 9.
- Create subscription product catalog:
  - Preferred: `just_notes_premium`
  - Base plans: `monthly`, `annual`
  - Auto-renewing
  - Active
  - Countries/regions selected
  - Prices set
  - Grace/account hold settings reviewed
  - Pause/resubscribe settings reviewed
- If using fallback two-product catalog:
  - `just_notes_premium_monthly`
  - `just_notes_premium_annual`
  - Each has one active auto-renewing base plan.
- Avoid multiple offers until client offer selection is explicit and tested.
- Add license testers:
  - Brian's Gmail
  - QA Gmail accounts
  - Optional Google Group
- Create internal test track tester list.
- Send opt-in URL and verify testers accepted.
- Confirm products are published/active before testing.
- Wait for Play propagation and clear Play Store cache if product queries stay empty.
- Configure RTDN:
  - Google Cloud project
  - Pub/Sub topic/subscription
  - Play Console RTDN link
  - Backend push/pull subscriber
- Complete subscription policy/store readiness:
  - Privacy policy URL
  - Terms URL
  - Data safety form
  - App content declarations
  - Paid feature/subscription description

Client-only now:

- None beyond documenting IDs and expected state.

Wait for backend/Play Console:

- Real purchase testing and RTDN.

### Phase 7 - License Tester and Internal Testing Matrix

Goal: prove billing behavior before any production release.

Automated tests before device billing:

- Unit tests:
  - state transitions
  - old cache migration
  - offer selection
  - pending purchase
  - ack retry queue
  - backend response mapping
  - restore result mapping
  - debug override combination
- Instrumentation tests:
  - free user gates route to Premium
  - premium/debug users can use folders, formatting, reminders, and calendar filters
  - release build cannot enable debug premium
  - Premium screen fallback does not show fake prices or subscribe button
  - Premium screen state variants using fakes or injectable repository

License tester/internal track manual matrix:

- Product detail loading:
  - monthly price appears
  - annual price appears
  - wrong package/test account shows no product and a useful fallback
- New purchase:
  - monthly success
  - annual success
  - user canceled
  - payment declined
  - pending then approved
  - pending then declined
- Acknowledgement:
  - backend acknowledgement success
  - acknowledgement retry after simulated network failure
  - purchase is not refunded/revoked because of missed ack
- Restore:
  - reinstall and restore active subscription
  - clear app data and restore active subscription
  - same device different Play account
  - no active subscription found
- Lifecycle:
  - cancel subscription
  - access remains until expiry
  - grace period retains access
  - account hold removes access and shows payment issue
  - expiry removes access
  - refund/revoke removes access through RTDN
  - resubscribe creates/handles new token
- Backend/offline:
  - backend down after successful purchase
  - backend down on app start with fresh verified cache
  - backend down after cache expiry
  - RTDN duplicate message is ignored
  - RTDN out-of-order event does not corrupt latest entitlement
- Play Billing Lab:
  - country/region price display
  - repeated trial/intro eligibility if Brian enables offers
  - accelerated renewal
  - grace period
  - account hold
- Regression:
  - existing notes/editors/sync/backup flows still work when billing unavailable
  - free-core workflows never dead-end behind entitlement outages

## Release Gates

Internal testing gate:

- Billing Library 9 migration complete.
- App builds and unit tests pass.
- Existing instrumentation tests pass on local emulator or device.
- Product catalog exists in Play Console.
- License testers and internal test opt-in are configured.
- Premium screen does not show fake prices.
- Client-only entitlement is clearly labeled as internal-test-only if backend is not ready.

Closed/open testing gate:

- Backend verification is implemented or Brian explicitly accepts client-only risk for non-production testing.
- Acknowledgement retry is durable.
- Restore flow is deterministic.
- Subscription copy and links are final.
- Internal test matrix passes for monthly and annual.
- Play Billing Lab lifecycle tests pass for grace/account hold/expiry.
- Debug premium override remains unavailable in release.
- xhigh Codex review is run for implemented code changes:
  - `codex -m gpt-5.5 -c 'model_reasoning_effort="xhigh"' review ...`

Production gate:

- Backend is the source of truth for entitlements.
- RTDN is live and monitored.
- Refund/revoke/cancel/expire paths remove access correctly.
- Acknowledgement is performed reliably by backend.
- Privacy policy, terms, Data Safety, subscription disclosure, and Play Console subscription setup are complete.
- Brian has approved package name, prices, countries, products, base plans, and trial/offer choices.
- No known PBL 7 dependency remains in release artifacts.

## Key Risks

- Package-name risk: `com.example.notepad` may not be the final production package. Play Console identity should be decided before catalog and release work.
- Catalog irreversibility risk: subscription product IDs, base plan IDs, and offer IDs have constraints and cannot always be changed or reused after activation.
- PBL 7 deadline risk: current `7.1.1` cannot be used for new apps/updates after 2026-08-31 without extension.
- Client-only entitlement risk: local preferences and app code can be tampered with, and refunds/revocations cannot be handled promptly.
- Acknowledgement risk: current one-shot client acknowledgement can fail and cause automatic refunds/revocation.
- Offer-token risk: current `firstOrNull()` selection can launch the wrong base plan or offer once multiple offers exist.
- RTDN ordering/duplication risk: backend must dedupe events and re-query Google Play instead of trusting notification payloads alone.
- Account mismatch risk: Google Drive sign-in account and Play billing account may be different.
- Product discovery risk: products may not load if the app is not published to a test track, tester did not opt in, product is inactive, package name differs, or Play cache has not propagated.
- Policy/copy risk: live subscription screens need accurate renewal, cancellation, trial, privacy, and terms disclosures.

## Decision Points for Brian

1. Final package name: keep `com.example.notepad` or change before Play Console setup?
2. Product model: preferred single product `just_notes_premium` with `monthly`/`annual`, or keep two current product IDs?
3. Pricing and countries: monthly price, annual price, launch regions, taxes/local pricing strategy.
4. Offers: no trial initially, or enable a free trial/intro offer from day one?
5. Backend bar: internal client-only testing first, or backend before any purchasable build?
6. User identity: no app account, Google Drive sign-in reuse, or a dedicated Just Notes account?
7. Backend host/runtime: Cloud Run/Firebase/other, service account ownership, monitoring, logs.
8. Premium benefit set: exactly which current and future features are paid?
9. Legal links: production Privacy Policy and Terms of Service URLs.
10. Launch track: internal only first, then closed, open, and production after gates.

## Recommended Path

1. Decide package name and product model before touching Play Console.
2. Use one subscription product, `just_notes_premium`, with `monthly` and `annual` auto-renewing base plans if no products are active yet.
3. Upgrade the app to Billing Library 9 before implementing more billing behavior.
4. Refactor billing into a testable gateway/repository/state-machine structure, with explicit base-plan/offer selection and a structured entitlement cache.
5. Run internal testing client-only only long enough to prove UI, purchase flow, ack retry, and restore behavior.
6. Build backend verification, acknowledgement, and RTDN before production monetization.
7. Replace preview copy with real subscription disclosure and active Privacy/Terms/Manage Subscription links.
8. Require the full license tester/internal testing matrix, xhigh code review, and Brian's explicit product/catalog approval before any production release.
