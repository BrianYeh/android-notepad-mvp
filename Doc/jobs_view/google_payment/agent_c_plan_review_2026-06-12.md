# Agent C Plan Review - Google Payment Work - 2026-06-12

## Verdict

Agent B's plan is directionally correct and should be used as the implementation backbone, but it needs a revision pass before Agent D starts code. The plan correctly rejects Google Pay, prefers Google Play Billing subscriptions, identifies the existing client-only entitlement risks, and sequences product decisions before Play Console work. The main gap is that several items are still too implicit for safe implementation: Billing Library 9 migration details, product-catalog commitment, backend/user-identity source of truth, entitlement-state boundaries, and subscription plan-change handling.

Recommended status: approve with required plan changes. Do not begin source implementation until the must-fix items below are folded into Agent D's instructions.

## Codebase Checks

- Current app ID and namespace are `com.example.notepad` in `app/build.gradle.kts`.
- Billing dependency is `com.android.billingclient:billing-ktx:7.1.1`.
- `PremiumBilling.kt` uses two subscription product IDs: `just_notes_premium_monthly` and `just_notes_premium_annual`.
- `PremiumBilling.kt` still uses no-arg `enablePendingPurchases()`, `queryProductDetailsAsync` with the old list-shaped callback, `subscriptionOfferDetails?.firstOrNull()`, and boolean `premium_entitled` SharedPreferences entitlement.
- `updateEntitlement()` grants local premium for any matching `PURCHASED` subscription before durable acknowledgement success or backend verification.
- Premium UI hides commerce when prices are absent, but copy is still preview/setup-pending and Privacy/Terms are only underlined text, not links.
- Existing tests cover only simple `PremiumBillingState.hasPremiumAccess`, release debug-premium stub behavior, and UI gates/fallbacks. They do not cover BillingClient behavior, offer selection, ack retry, backend mapping, RTDN, or subscription lifecycle.

## Must-Fix Plan Changes

1. Make Phase 0 a hard gate. Brian must explicitly approve final `applicationId`, product model, product/base-plan IDs, prices, countries, offer/trial policy, backend bar, user identity, Privacy URL, Terms URL, and launch track before Play Console setup or source implementation that bakes in IDs.

2. Expand the Billing Library 9 migration steps. Agent D must account for removed APIs and changed signatures, not just bump Gradle:
   - Replace no-arg `enablePendingPurchases()` with `enablePendingPurchases(PendingPurchasesParams...)`.
   - Update `queryProductDetailsAsync` handling for the PBL 8/9 result shape and unfetched-product status data.
   - Consider `enableAutoServiceReconnection()`.
   - Handle PBL 9 sub-response codes from `launchBillingFlow()`.
   - Verify the merged manifest includes `com.android.vending.BILLING` and billing client metadata.

3. Treat the product-catalog choice as irreversible until proven otherwise. The preferred one-product model (`just_notes_premium` with `monthly`/`annual` base plans) is good if nothing is active in Play Console. If the current two IDs are already active, Agent D must not rename/delete/migrate products without a separate Brian decision.

4. Tighten entitlement authority. Production must not unlock from SharedPreferences or client-observed purchases alone. `Active`, `GracePeriod`, `OnHold`, `Expired`, and `Revoked` should come from backend verification using Google Play Developer API state. The client can only observe purchases, cache short-lived backend decisions, and show setup/error states.

5. Make acknowledgement durable before any broader testing. Purchase tokens must be persisted until backend acknowledgement succeeds or a client-only internal-test retry returns `OK`/already acknowledged. A one-shot client ack is not acceptable. If verification/ack is pending, the UI should show a finishing/pending state rather than silently granting durable premium.

6. Remove all `firstOrNull()` offer selection. Select by explicit `productId` plus `basePlanId` and, when offers exist, explicit `offerId`/eligibility. If any trial or intro offer is enabled, the UI must display the selected offer's phases accurately, not just the last pricing phase.

7. Add subscription change handling to the implementation guardrails. Switching monthly/annual, resubscribe, deferred replacement, linked purchase tokens, and replacement mode defaults need explicit behavior. Backend must invalidate `linkedPurchaseToken` when Google reports a replacement.

8. Preserve free-core behavior during billing outages. Product-detail failure, billing unavailable, backend unavailable, Play cache propagation, and no active subscription must not block notes, editing, sync, backup, or existing free workflows.

## Blockers and Brian Decisions

- Final package identity: keeping `com.example.notepad` is a product decision, not just a code default. Changing it affects Play Console identity, Google sign-in/Drive OAuth config, signing/package constraints, test tracks, and user data migration expectations.
- Subscription catalog: one product with two base plans vs two existing products. Decide before creating or activating products.
- Monetization setup: merchant profile, app distribution countries, tax/local pricing, grace period/account hold, pause/resubscribe settings, license testers, and internal test opt-in URL.
- Offer policy: no trial initially is safest. If Brian wants trial/intro/winback from day one, plan and tests must grow substantially.
- Backend identity: install-bound `appInstanceId`, Google Drive sign-in reuse, or a dedicated Just Notes account. Google Drive account and Play billing account may differ.
- Backend host/runtime and owner: Cloud Run/Firebase/other, service account, Pub/Sub RTDN, monitoring, logs, secret storage, and support workflow are not present in this repo.
- Legal/policy assets: production Privacy Policy URL, Terms URL, subscription disclosure copy, Data Safety, and paid-feature description.

## Guardrails for Agent D

- Do not implement Google Pay, external card payments, or external payment links for these digital app features.
- Do not commit, push, upload, or release as part of implementation unless separately instructed.
- Do not weaken premium gates or the release debug-premium stub.
- Keep debug override separate from real entitlement; debug must never mutate subscription cache.
- Do not log raw purchase tokens, emails, or PII. Hash tokens for lookup/logging and use non-PII obfuscated IDs if account identity is chosen.
- Do not infer subscription expiry, grace, hold, refund, or revoke from local clock or SharedPreferences. Use backend/Developer API state.
- Handle pending and suspended purchases as non-entitled. If using PBL 9, include suspended-subscription behavior in tests and UI.
- Use an injectable billing gateway/repository so unit and instrumentation tests do not need real Play Billing.
- Keep the fallback UI: when products are not available, hide subscribe buttons and show useful non-commerce status.
- Require focused unit tests before connected tests, then license tester/internal track validation before any production gate.
- Run the mandated xhigh Codex review for actual source changes with:
  `codex -m gpt-5.5 -c 'model_reasoning_effort="xhigh"' review ...`

## Sequencing Recommendation

1. Revise Agent B's plan with the must-fix items above.
2. Get Brian's Phase 0 decisions in writing.
3. Implement PBL 9 migration and test seam first, with no live commerce copy yet.
4. Implement explicit catalog/offer selection and local state-machine migration.
5. Add durable acknowledgement and restore/refresh behavior for internal testing.
6. Build backend verification, acknowledgement, RTDN, and entitlement API before production monetization.
7. Replace UI copy/links and add manage-subscription deep links.
8. Run automated tests, internal track/license tester matrix, Play Billing Lab lifecycle checks, and xhigh review before any release decision.

## Official References Checked

- Google Pay FAQ: Google Pay is for card checkout through merchant/payment-gateway flows, not the right mechanism for in-app digital feature subscriptions. https://developers.google.com/pay/api/android/support/faq
- Play Billing Library 9 migration: PBL 9 removes deprecated APIs, changes `queryProductDetailsAsync` handling from PBL 7, removes no-arg `enablePendingPurchases()`, recommends auto service reconnection, and adds sub-response codes. https://developer.android.com/google/play/billing/migrate-gpblv9
- Play Billing release/deprecation: PBL 7 last allowed for new apps/updates on 2026-08-31 unless extension; PBL 9.0.0 is current in the checked docs. https://developer.android.com/google/play/billing/release-notes and https://developer.android.com/google/play/billing/deprecation-faq
- Purchase processing: verify purchases before granting benefits, do not grant pending purchases, and acknowledge within three days to avoid refund/revoke. https://developer.android.com/google/play/billing/integrate
- Backend/RTDN: backend should manage purchase lifecycle, entitlements, RTDN dedupe, and Developer API sync. https://developer.android.com/google/play/billing/backend and https://developer.android.com/google/play/billing/lifecycle
- Subscriptions: base plans/offers, replacement modes, linked purchase tokens, manage-subscription deep links, and restore/resubscribe behavior. https://developer.android.com/google/play/billing/subscriptions
- Testing: Play Billing Lab supports country, offer, accelerated renewal, grace period, and account hold testing for license testers. https://developer.android.com/google/play/billing/test

## Review Limits

This was a planning review only. I read Agent A and Agent B reports, inspected billing/build/UI/test code as needed, and checked current official Google billing docs. I did not modify source, run Gradle/tests, commit, push, upload to Play Console, or run release tooling. I did not invoke Codex review tooling because no implementation code was written or modified.
