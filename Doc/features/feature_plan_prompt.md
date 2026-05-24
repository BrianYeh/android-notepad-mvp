# Just Notes Feature B Implementation Prompt (for Codex xhigh Full Access)

Codex xhigh Full Access, work in this repo: `/mnt/d/AndroidStudioProjects`.

Product goal: raise Just Notes from MVP behavior to paid-worthy trust and daily reliability (data safety, truthful sync behavior, privacy baseline, and dependable reminders/search workflows) before monetization polish.

Read first (in order) before editing anything:
1. `/mnt/d/AndroidStudioProjects/Doc/features/feature_B.md`
2. `/mnt/d/AndroidStudioProjects/README.md`
3. `/mnt/d/AndroidStudioProjects/GOOGLE_ACCOUNT_SYNC_SETUP.md` (if present)
4. Any touched module docs/tests relevant to the selected slice

Current workspace caution: this worktree may already be dirty with unrelated regression fixes. Protect existing changes. Do not revert, reformat, or move unrelated files. Keep edits minimal and scoped to the slice you are implementing.

Implement Feature B as pragmatic slices, prioritizing the first low-blast-radius, high-trust slices below. Execute one slice at a time, complete gates, then continue.

Slice priority and scope:
1. Slice 0 - Capability truth alignment (low risk, immediate trust gain)
   - Align user-facing wording and docs so sync/backup capabilities are truthful and non-misleading.
   - Remove/adjust claims that imply finished features when only scaffolding exists.
2. Slice 1 - Restore safety checkpoint + rollback (high value, contained blast radius)
   - Add pre-restore checkpoint and one-step rollback path for failed/undesired restore outcomes.
   - Ensure invalid/corrupt backup input cannot destroy existing local data.
3. Slice 2 - Sync reliability hardening on existing foundation (bounded hardening, not rewrite)
   - Add account-switch/sign-out safeguards for unsynced local changes.
   - Surface sync status history fields (status/time/changed count/error category) with actionable retry context.
   - Make conflict copies visible and reviewable (no silent background artifacts).
4. Slice 3 - Privacy baseline (no lock claims yet)
   - Add default-safe option to hide reminder content.
   - Reduce sensitive previews in recents/snapshots where platform APIs allow.

For each slice, include acceptance criteria in your PR/notes and do not mark complete unless all criteria pass:
- User-visible behavior is truthful (no dead controls, no fake capability claims).
- Data-loss-sensitive paths preserve existing notes on failure cases.
- New/changed flows are deterministic across restart/reopen paths.
- UX is clear enough for first-time users without hidden caveats.

Required validation per non-trivial slice:
1. Build/test gate
   - `assembleDebug`
   - `testDebugUnitTest`
   - Targeted `connectedAndroidTest` coverage for touched flows when applicable.
2. Emulator regression validation (Android Studio emulator required where flow applies)
   - Run primary happy path + one failure/edge path for the slice.
   - Capture concise evidence (screenshots and/or short run log summary).
3. Independent code review gate (mandatory, not self-approval)
   - Run separate review pass after implementation for non-trivial code changes.
   - Reviewer must check regression risk, data-loss risk, UX breakage, and missing tests.
   - Do not skip this gate; fix findings or explicitly document accepted risk before finalizing.

Execution rules:
- Implement in small, reviewable commits/slices; avoid broad refactors.
- Preserve unrelated local changes and files already modified by others.
- If a slice expands unexpectedly, stop and re-scope into a smaller first increment.
- Keep feature flags or guarded rollouts where useful to reduce blast radius.

Do not:
- Add API-cost AI writing features.
- Make fake billing/premium claims or imply paid features are active without real entitlement logic.
- Clone/copy ColorNote assets, branding, or proprietary content.
- Use destructive git commands (`reset --hard`, `checkout --`, force-cleaning unrelated files, etc.).
- Skip independent review after non-trivial code changes.

If you reach a stage that produces a final debug APK for handoff, copy it to:
`G:\\我的雲端硬碟\\01_android_app\\01_note_app\\app-debug.apk`

Commit/push rules:
- After gates pass, commit all validated changes for the implemented slice(s) (do not cherry-pick partial validated work without explicit reason).
- Summarize exact scope, risks, and evidence in commit/PR notes.
- Push only after validation + independent review gate are complete.

