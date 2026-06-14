# Agent G Rerun Connected Validation Report - 2026-06-12

## Verdict

PASS WITH LIMITS.

Current connected validation passed for the updated parent suspended-acknowledgement diff, but not as one uninterrupted 13-test Gradle invocation. The full 13-test rerun reached instrumentation, completed one passing test, marked `freeDefaultOnlyFolderUiIsHidden` failed with an empty failure body, and then aborted with `INSTRUMENTATION_ABORTED: System has crashed`. I did not count that interrupted run as a pass.

Retry validation in smaller batches and individual invocations completed all 13 requested `TextInputTest` methods successfully on `LocalNotepad_API35(AVD) - 15`.

Real Play purchase/lifecycle validation remains blocked on external Play Console, license tester, internal-track, and backend setup.

## Scope

Workspace: `D:\AndroidStudioProjects` (`/mnt/d/AndroidStudioProjects` from WSL).

I made no source-code changes, did not commit, did not push, did not build/upload a release, and did not touch Play Console. Because no source code was changed by me, I did not run a new Codex xhigh review command.

## Device

- `/mnt/d/android/Sdk/emulator/emulator.exe -list-avds`: `LocalNotepad_API35` exists.
- `/mnt/d/android/Sdk/platform-tools/adb.exe devices`: `emulator-5554 device`.
- `/mnt/d/android/Sdk/platform-tools/adb.exe shell getprop sys.boot_completed`: `1`.
- The emulator was already booted; I did not start a new emulator.

## Static / Cheap Checks

- `git diff --check`: PASS.
- Focused static billing guardrail scan: PASS.
  - Billing dependency remains `com.android.billingclient:billing:9.0.0`.
  - No `billing-ktx`, old SKU API, no-arg `enablePendingPurchases()`, risky `subscriptionOfferDetails?.firstOrNull`, or default-true `ALLOW_CLIENT_ONLY_BILLING_ENTITLEMENT` match was found in the scanned app source/test files.
  - The only `premium_entitled` match is the expected legacy migration key in `PremiumEntitlementStore.kt`.

## Connected Runs

Initial full focused rerun command:

`.\gradlew.bat -Dkotlin.compiler.execution.strategy=in-process connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<13 requested methods> --no-daemon`

Result: FAIL/INCONCLUSIVE for the single invocation.

- Started 13 tests on `LocalNotepad_API35(AVD) - 15`.
- `premiumHomeReminderButtonOpensCalendar` completed as passed.
- `freeDefaultOnlyFolderUiIsHidden` was marked failed, but the XML failure body was empty.
- Gradle reported: `Test run failed to complete. Expected 13 tests, received 1. onError: commandError=false message=INSTRUMENTATION_ABORTED: System has crashed.`
- Generated evidence at the time included `app/build/outputs/androidTest-results/connected/debug/TEST-LocalNotepad_API35(AVD) - 15-_app-.xml` and per-test logcat files under `app/build/outputs/androidTest-results/connected/debug/LocalNotepad_API35(AVD) - 15/`.

Retry evidence:

- `freeDefaultOnlyFolderUiIsHidden` individual rerun: PASS, 1/1, build successful.
- Batch 1: PASS, 4/4, build successful.
  - `premiumFallbackHidesCommerceAndShowsAllowedBenefits`
  - `textFormattingControlsRouteNonPremiumUsersToPremium`
  - `debugPremiumSwitchUnlocksTextFormattingWithoutSubscription`
  - `freeExistingFolderCanFilterAndMoveBackToDefaultOnly`
- Batch 2: PASS, 4/4, build successful.
  - `debugPremiumKeepsFolderCreationAndFolderRowVisible`
  - `reminderControlsRouteNonPremiumUsersToPremium`
  - `freeReminderClearWorksAndRepeatControlsAreHidden`
  - `drawingReminderGateSavesDraftBeforePremium`
- Final 4-test batch: NO RESULT. The host session ended immediately after `Starting 4 tests on LocalNotepad_API35(AVD) - 15`; app/test processes were still running, so I force-stopped both packages and did not count this as pass.
- `checklistReminderGateSavesDraftBeforePremium` individual rerun: PASS, 1/1, build successful.
- `freeUsersDoNotSeeCalendarViewChip` individual rerun: PASS, 1/1, build successful. This run hit a Kotlin daemon incremental-cache error during `compileDebugKotlin`, recovered with Gradle's fallback compile without the Kotlin daemon, then reached instrumentation and passed.
- `premiumHomeReminderButtonOpensCalendar` individual rerun: PASS, 1/1, build successful.
- `premiumReminderFiltersSeparateWithOverdueAndUpcomingNotes` individual rerun: PASS, 1/1, build successful.

## Requested Method Coverage

All 13 requested methods passed in completed retry invocations:

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

## Limits / Blockers

- The full 13-test invocation did not pass as a single uninterrupted Gradle run because Android reported a system crash.
- One later 4-test batch produced no usable host result and was not counted.
- Current connected validation nevertheless passed for the updated diff through completed smaller-batch and individual reruns.
- Real Google Play Billing product detail retrieval, purchase flow, pending/suspended lifecycle transitions, cancellation/restore behavior, licence tester behavior, internal-track behavior, backend verification, backend acknowledgement, RTDN, linked-token invalidation, and Play Console catalog validation remain externally blocked.
