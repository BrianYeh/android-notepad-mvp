# Agent C - Plan Review

Role: review Agent B's plan from a senior code review and product risk stance.

No files were edited and no tests were run.

## Verdict

The plan is close, but should not be handed to implementation unchanged.

## Required Modifications

1. Premium screen third benefit must be reminders/calendar tools, not stale "icons/planning" naming. The current Premium set is exactly folders, text formatting, and reminders/calendar. Import/export and writing assistant must stay absent.

2. Commerce fallback needs a precise state: hide plan rows/subscribe/restore only when billing is unavailable or either price is missing, but do not regress existing entitlement display/access. Paid/debug users must still keep premium gates unlocked.

3. Folder gating needs preservation tests. `hasNonDefaultFolders` should use active folders, clear hidden `selectedFolderId` via effect, and never leave notes filtered by an invisible folder. Free users with existing folders must be able to see those notes and move them back to default, but not create/rename/delete/move into folders.

4. Calendar view should be hidden for free users, not shown as a Premium upsell chip. Also verify premium loss while Calendar is selected resets to List.

5. Reminder free-state needs existing-reminder coverage. Set reminder routes to Premium after saving, Clear reminder remains free, repeat controls are hidden/blocked, and existing reminder status remains visible.

6. Formatting tests should assert free users still get checkbox/bullet/hide keyboard plus one Premium entry button, and that existing formatted content is not silently stripped when controls are hidden.

7. Settings split is fine, but Import/Export must remain fully free: text export, drawing export, batch import/export, and backup/restore should not be pulled behind premium by accident.

## Required Test Coverage For Agent D

- Free default-only folder UI hidden, Move hidden, editor folder picker hidden.
- Free with existing non-default folder: filters visible, no create/rename/delete, move-to-default only.
- Debug premium/premium keeps folder creation/move/rename/delete.
- Free calendar chip absent; debug premium calendar test enabled.
- Free reminder clear works on an existing reminder.
- Premium fallback screen hides commerce UI while still showing exactly the three allowed benefits.
