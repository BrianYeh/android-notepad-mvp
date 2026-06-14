# Agent G Connected/Device Validation Report - 2026-06-12

## VERDICT

PASS for local connected/device validation of the current Google Billing/Premium guardrails.

This is not a production monetization pass. Real purchase, subscription lifecycle, Play Billing Lab, license tester, internal-track, backend verification, backend acknowledgement, RTDN, linked-token, and Play Console catalog validation remain blocked by missing external setup.

## Current Diff / Scope Confirmed

Tracked billing/premium files modified after Agent F:

- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/notepad/billing/PremiumBilling.kt`
- `app/src/main/java/com/example/notepad/ui/NotepadApp.kt`
- `app/src/main/java/com/example/notepad/ui/UiText.kt`
- `app/src/test/java/com/example/notepad/billing/PremiumBillingStateTest.kt`

Untracked billing files that must be included by the eventual committer:

- `app/src/main/java/com/example/notepad/billing/PremiumCatalog.kt`
- `app/src/main/java/com/example/notepad/billing/PremiumEntitlement.kt`
- `app/src/main/java/com/example/notepad/billing/PremiumEntitlementStore.kt`

Other untracked report directories were present and left untouched. Agent G made no source-code changes and did not commit, push, upload, or perform Play Console work.

## Commands Run

- `git status --short` - PASS; confirmed tracked billing/premium modifications and untracked new billing files.
- `git diff --stat` / targeted `git diff` - PASS; confirmed the billing/premium diff shape matched Agent F's report.
- `git diff --check` - PASS; no whitespace/conflict-marker issues.
- Static guardrail scan for `billing-ktx`, old SKU APIs, no-arg `enablePendingPurchases()`, risky `subscriptionOfferDetails?.firstOrNull`, default-true client entitlement, and `premium_entitled` - PASS. The only `premium_entitled` match is the legacy migration key in `PremiumEntitlementStore.kt`.
- Static acknowledgement retry scan - PASS; all unacknowledged premium purchases are collected and recorded/retried through `recordPendingAcknowledgement`, `ACK_PURCHASE_TOKENS_KEY`, and in-flight token tracking.
- Manifest/BuildConfig scan - PASS:
  - `com.android.vending.BILLING` exists in source and merged debug/release manifests.
  - `com.google.android.play.billingclient.version` exists in merged debug/release manifests.
  - generated debug, androidTest debug, and release `ALLOW_CLIENT_ONLY_BILLING_ENTITLEMENT` values are `false`.
  - billing dependency is `com.android.billingclient:billing:9.0.0`.
- `/mnt/d/android/Sdk/platform-tools/adb.exe devices` - PASS; `emulator-5554 device`.
- `/mnt/d/android/Sdk/emulator/emulator.exe -list-avds` - PASS; `LocalNotepad_API35`.
- `/mnt/d/android/Sdk/platform-tools/adb.exe shell getprop sys.boot_completed` - PASS; returned `1`.
- Windows PowerShell/JBR full `connectedDebugAndroidTest` attempt - INCONCLUSIVE; 163 tests started and 8 completed with 0 failures before the shell/Gradle session ended without a named test failure. I did not count this as a suite pass.
- Removed only generated connected-test result/report directories under `app/build`, ran `gradlew --stop`, and reran a focused connected subset.
- Focused connected command via Windows PowerShell/JBR:
  `.\gradlew.bat -Dkotlin.compiler.execution.strategy=in-process connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=... --no-daemon`
  - PASS; 13/13 tests passed on `LocalNotepad_API35(AVD) - 15`.
- Unit command via Windows PowerShell/JBR:
  `.\gradlew.bat -Dkotlin.compiler.execution.strategy=in-process testDebugUnitTest testReleaseUnitTest --no-daemon`
  - PASS; build successful.
- Debug APK install:
  `/mnt/d/android/Sdk/platform-tools/adb.exe install -r app/build/outputs/apk/debug/app-debug.apk`
  - PASS.
- App launch smoke:
  `adb shell monkey -p com.example.notepad -c android.intent.category.LAUNCHER 1`
  plus `pidof` / activity dumpsys checks
  - PASS; `com.example.notepad/.MainActivity` was displayed and focused, process `8299` alive.

## Connected / Device Results

Focused connected premium/gate regression passed on `LocalNotepad_API35`:

- `premiumFallbackHidesCommerceAndShowsAllowedBenefits`
- `textFormattingControlsRouteNonPremiumUsersToPremium`
- `debugPremiumSwitchUnlocksTextFormattingWithoutSubscription`
- `freeDefaultOnlyFolderUiIsHidden`
- `freeExistingFolderCanFilterAndMoveBackToDefaultOnly`
- `debugPremiumKeepsFolderCreationAndFolderRowVisible`
- `reminderControlsRouteNonPremiumUsersToPremium`
- `freeReminderClearWorksAndRepeatControlsAreHidden`
- `drawingReminderGateSavesDraftBeforePremium`
- `checklistReminderGateSavesDraftBeforePremium`
- `freeUsersDoNotSeeCalendarViewChip`
- `premiumHomeReminderButtonOpensCalendar`
- `premiumReminderFiltersSeparateWithOverdueAndUpcomingNotes`

Generated result evidence:

- `app/build/outputs/androidTest-results/connected/debug/TEST-LocalNotepad_API35(AVD) - 15-_app-.xml`
- `app/build/reports/androidTests/connected/debug/index.html`
- test log reported `OK (13 tests)`.

## Local Guardrails Validated

- Production and default debug builds fail closed for client-only billing entitlement.
- Premium purchase launch is blocked by default without the explicit internal debug flag.
- Premium UI fallback remains usable when Play products are unavailable.
- Free users remain gated away from premium formatting, folder, reminder, and calendar affordances where expected.
- Debug premium override still unlocks debug-only premium paths without a real subscription.
- PBL 9 compile path is validated by debug/release unit tests and connected APK packaging.
- Acknowledgement retry storage no longer collapses multiple unacknowledged premium tokens to a single token.

## Code Changes

None by Agent G.

Generated Android test outputs under `app/build` were cleared once after an interrupted full connected run caused Gradle to fail hashing the stale results directory. This did not modify source code.

Because no source code was changed by Agent G, I did not run a new `codex -m gpt-5.5 -c 'model_reasoning_effort="xhigh"' review --uncommitted` pass. Agent F already ran the required review after its source fix.

## Could Not Validate Locally

- Real Google Play Billing product detail retrieval from Play Console.
- New purchase flow, pending purchase transitions, suspended subscription transitions, purchase cancellation, restore from an actual Play account, and acknowledgement against Google Play.
- License tester behavior, internal-track opt-in behavior, package/signing/track propagation, and Play Billing Lab accelerated lifecycle states.
- Backend purchase-token verification, backend acknowledgement, RTDN handling, grace period/account hold/expired/revoked authority, and linked purchase token invalidation.
- Real Privacy Policy / Terms links and production subscription disclosure copy, because Brian has not provided final URLs/product decisions.
- Full 163-test connected suite completion; the broad run was interrupted after partial progress, so only the focused 13-test billing/premium regression is counted as passed.

## Remaining Release Blockers

- Brian must make Phase 0 decisions: final application ID, product model, exact product/base-plan IDs, prices, countries, offer/trial policy, launch track, tester list, Privacy Policy URL, and Terms URL.
- Play Console subscription catalog, internal/test track, license testers, and opt-in setup are not validated.
- Production backend for Google Play Developer API verification, acknowledgement, entitlement authority, RTDN, and lifecycle reconciliation does not exist.
- Subscription upgrade/downgrade/replacement and linked-token handling remain backend/product-lifecycle work.
- The repo still has untracked billing implementation files that must be included with any commit.

## Next Owner Recommendation

Next owner should be the Play Console/backend owner, not another local-only validation pass. Set up Phase 0 product decisions, backend verification/acknowledgement/RTDN, and internal-track/license-tester infrastructure, then rerun connected tests plus real billing purchase/lifecycle validation before any release decision.
