可以，你現在已經成功產生：

```text
D:\AndroidStudioProjects\app\release\app-release.aab
```

這就是要上傳到 **Play Console > Internal testing** 的檔案。你現在不要上 Production，只走 Internal testing。

---

# 0. 先確認現在狀態

你這邊已經 OK：

```text
applicationId = com.brianyeh.justnotes
AAB = D:\AndroidStudioProjects\app\release\app-release.aab
git status = clean
```

這點很重要，因為 Google 官方說：**一旦在 Play Console 上傳 artifact，package name 就會固定，不能再改。** 你現在已經從 `com.example.notepad` 改成 `com.brianyeh.justnotes`，這是正確方向。([Google 支援][1])

---

# 1. 可以，你現在已經成功產生：

```text
D:\AndroidStudioProjects\app\release\app-release.aab
```

這就是要上傳到 **Play Console > Internal testing** 的檔案。你現在不要上 Production，只走 Internal testing。

---

# 0. 先確認現在狀態

你這邊已經 OK：

```text
applicationId = com.brianyeh.justnotes
AAB = D:\AndroidStudioProjects\app\release\app-release.aab
git status = clean
```

這點很重要，因為 Google 官方說：**一旦在 Play Console 上傳 artifact，package name 就會固定，不能再改。** 你現在已經從 `com.example.notepad` 改成 `com.brianyeh.justnotes`，這是正確方向。([Google 支援][1])

---

# 1. 進入 Play Console

打開：

```text
https://play.google.com/console
```

然後進去你的 Developer account。

---

# 2. 建立 App

如果還沒建立 Just Notes app：

1. 點 **Create app**。
2. App name 填：

```text
Just Notes
```

3. Default language：
   建議先選：

```text
English (United States)
```

或你想主打台灣，也可以選：

```text
Chinese (Traditional)
```

4. App or game：選 **App**。
5. Free or paid：選 **Free**。
   你的 Premium 用 Google Play Billing / subscription 來收費，不要把整個 app 設成付費下載。
6. 勾選下面的宣告。
7. 按 **Create app**。

---

# 3. 進 Internal testing

左側選單找：

```text
Test and release
```

或：

```text
Testing
```

然後點：

```text
Internal testing
```

Google 官方說 internal testing 是用來快速給最多 100 位 trusted testers 測試，而且建議先從 internal test 開始。([Google 支援][1])

---

# 4. 先建立測試人員名單

在 **Internal testing** 頁面：

1. 點 **Testers** 分頁。
2. 找 **Create email list**。
3. List name 填：

```text
JustNotes Internal Testers
```

4. Email 加你手機 Play Store 使用的 Gmail。
   例如你的測試 Gmail。
5. 按 **Save changes** / **Create**。
6. 回到 Testers 頁面，勾選這個 email list。
7. Feedback email 可以填你的 Gmail。
8. 先按 **Save changes**。

官方流程也是在 Internal testing 的 Testers tab 建 email list、加入 email、選擇 tester list、填 feedback email，然後儲存。([Google 支援][1])

---

# 5. 建立 Internal testing release

在 **Internal testing** 頁面：

1. 點 **Releases** 分頁。
2. 點：

```text
Create new release
```

或：

```text
Create release
```

3. 如果看到 **Play App Signing** 畫面，選預設的：

```text
Use Google-generated app signing key
```

或類似意思：讓 Google 管理 app signing key。

這是正常的。Google 官方說新 app 使用 Android App Bundle 時，需要簽好的 bundle 上傳到 Play Console，Play App Signing 會處理後續發佈簽章。([Android Developers][2])

---

# 6. 上傳 AAB

在 **App bundles** 區域，點：

```text
Upload
```

選這個檔案：

```text
D:\AndroidStudioProjects\app\release\app-release.aab
```

注意：
不要選 debug APK。
不要選 unsigned release APK。
你現在要上傳的是：

```text
app-release.aab
```

Google 官方也說，上傳到 Play Console 前要先 sign release version，之後上傳 app bundle 到 Play Console。([Android Developers][3])

---

# 7. 填 Release notes

如果畫面要求 **Release name**，可以填：

```text
Just Notes internal test 1
```

如果畫面要求 **What’s new in this release?**，填：

```text
Initial internal testing build for Just Notes.
```

然後按：

```text
Next
```

或：

```text
Save
```

再按：

```text
Review release
```

---

# 8. 看錯誤或警告

這一步很重要。

如果畫面出現：

```text
Errors summary
```

你要點進去看。
**Error 一定要修，Warning 可以先看情況。**

Google 官方說 release rollout 前如果有 Errors summary，要先解決錯誤；有錯誤時不能 publish。([Google 支援][4])

常見錯誤可能是：

| 錯誤                        | 意思                       |
| ------------------------- | ------------------------ |
| App content incomplete    | App content 問卷還沒填        |
| Privacy policy required   | 隱私權政策 URL 還沒填            |
| App access missing        | 如果 app 需要登入，要告訴 reviewer |
| Version code already used | versionCode 重複           |
| Package name conflict     | package 已被其他 app 使用      |
| Signing issue             | 簽章或 keystore 問題          |

你現在第一次上 Internal testing，有些設定可以之後補；但如果 Play Console 擋住你，就要先照畫面補。

---

# 9. Start rollout to Internal testing

如果沒有 Error，按：

```text
Start rollout to Internal testing
```

或：

```text
Roll out release
```

確認。

這不是正式上架，不是 Production。
只是把 app 發給你設定的 internal testers。

Google 官方說 internal testing 可以在 app 還沒完全設定好時使用，只要有 valid app bundle 就能快速發給少量 testers。([Google 支援][1])

---

# 10. 拿測試連結

回到：

```text
Internal testing > Testers
```

找：

```text
Copy link
```

或：

```text
Join on Android
```

把連結複製下來。

官方流程也說，Internal testing 的 Testers tab 會有 shareable link，可以分享給 testers。([Google 支援][1])

---

# 11. 手機測試安裝

用手機操作：

1. 確認手機 Play Store 登入的是剛剛加進 tester list 的 Gmail。
2. 用手機 Chrome 打開 opt-in 測試連結。
3. 按：

```text
Become a tester
```

或：

```text
Join the test
```

4. 之後點 Play Store 安裝。

第一次 internal test 有時候要等幾分鐘到幾小時，Google 官方也提醒第一次發布 testing track 後，測試連結可能需要幾小時才可用。([Google 支援][1])

---

# 12. 手機如果已經裝過 debug APK，要注意

如果你手機已經裝過側載版，而且 package 也是：

```text
com.brianyeh.justnotes
```

那 Play Store 版本可能無法覆蓋安裝，因為 debug 簽章跟 Play 簽章不同。

處理方式：

1. 先確認筆記資料是否需要備份。
2. 解除安裝手機上的 Just Notes debug 版。
3. 再從 Play Store 測試連結安裝 internal testing 版。

---

# 13. Billing 測試還要加 License tester

Internal testing 只是讓手機可以從 Play 安裝。
要測 Premium / Subscription，還要做這一步：

Play Console 左側：

```text
Settings > License testing
```

然後把你的測試 Gmail 加進去。

Google 官方說 license testing 可以設定 Gmail 或 Google Groups 來測 in-app billing 和 subscription；測試帳號可以做 test purchase，不會真的向帳號收費。([Google 支援][5])

同時 Google 也說：你的 app 要已經發布到 open / closed / internal test / production track，並且 tester 要有資格收到 release，才適合測 Billing。([Google 支援][5])

---

# 14. 你現在的實際操作順序

照這個順序做就好：

```text
1. Play Console > Create app
2. App name = Just Notes
3. App = App
4. Free = Free
5. 進 Testing > Internal testing
6. Testers tab > Create email list
7. 加你的手機 Gmail
8. Releases tab > Create new release
9. 上傳 D:\AndroidStudioProjects\app\release\app-release.aab
10. 填 Release notes
11. Review release
12. 沒 Error 就 Start rollout to Internal testing
13. 回 Testers tab 複製 opt-in link
14. 手機用 tester Gmail 打開連結
15. 從 Play Store 安裝
16. Settings > License testing 加同一個 Gmail
17. 再測 Premium / Billing
```

你下一張圖建議拍：**Play Console 左側選單或 Internal testing 頁面**。我可以照你畫面上的按鈕一步一步帶你點。

[1]: https://support.google.com/googleplay/android-developer/answer/9845334?hl=en "Set up an open, closed, or internal test - Play Console Help"
[2]: https://developer.android.com/studio/publish/app-signing "Sign your app  |  Android Studio  |  Android Developers"
[3]: https://developer.android.com/studio/publish/upload-bundle "Upload your app to the Play Console  |  Android Studio  |  Android Developers"
[4]: https://support.google.com/googleplay/android-developer/answer/9859348?hl=en "Prepare and roll out a release - Play Console Help"
[5]: https://support.google.com/googleplay/android-developer/answer/6062777?hl=en&utm_source=chatgpt.com "Test in-app billing with application licensing - Play Console ..."


打開：

```text
https://play.google.com/console
```

然後進去你的 Developer account。

---

# 2. 建立 App

如果還沒建立 Just Notes app：

1. 點 **Create app**。
2. App name 填：

```text
Just Notes
```

3. Default language：
   建議先選：

```text
English (United States)
```

或你想主打台灣，也可以選：

```text
Chinese (Traditional)
```

4. App or game：選 **App**。
5. Free or paid：選 **Free**。
   你的 Premium 用 Google Play Billing / subscription 來收費，不要把整個 app 設成付費下載。
6. 勾選下面的宣告。
7. 按 **Create app**。

---

# 3. 進 Internal testing

左側選單找：

```text
Test and release
```

或：

```text
Testing
```

然後點：

```text
Internal testing
```

Google 官方說 internal testing 是用來快速給最多 100 位 trusted testers 測試，而且建議先從 internal test 開始。([Google 支援][1])

---

# 4. 先建立測試人員名單

在 **Internal testing** 頁面：

1. 點 **Testers** 分頁。
2. 找 **Create email list**。
3. List name 填：

```text
JustNotes Internal Testers
```

4. Email 加你手機 Play Store 使用的 Gmail。
   例如你的測試 Gmail。
5. 按 **Save changes** / **Create**。
6. 回到 Testers 頁面，勾選這個 email list。
7. Feedback email 可以填你的 Gmail。
8. 先按 **Save changes**。

官方流程也是在 Internal testing 的 Testers tab 建 email list、加入 email、選擇 tester list、填 feedback email，然後儲存。([Google 支援][1])

---

# 5. 建立 Internal testing release

在 **Internal testing** 頁面：

1. 點 **Releases** 分頁。
2. 點：

```text
Create new release
```

或：

```text
Create release
```

3. 如果看到 **Play App Signing** 畫面，選預設的：

```text
Use Google-generated app signing key
```

或類似意思：讓 Google 管理 app signing key。

這是正常的。Google 官方說新 app 使用 Android App Bundle 時，需要簽好的 bundle 上傳到 Play Console，Play App Signing 會處理後續發佈簽章。([Android Developers][2])

---

# 6. 上傳 AAB

在 **App bundles** 區域，點：

```text
Upload
```

選這個檔案：

```text
D:\AndroidStudioProjects\app\release\app-release.aab
```

注意：
不要選 debug APK。
不要選 unsigned release APK。
你現在要上傳的是：

```text
app-release.aab
```

Google 官方也說，上傳到 Play Console 前要先 sign release version，之後上傳 app bundle 到 Play Console。([Android Developers][3])

---

# 7. 填 Release notes

如果畫面要求 **Release name**，可以填：

```text
Just Notes internal test 1
```

如果畫面要求 **What’s new in this release?**，填：

```text
Initial internal testing build for Just Notes.
```

然後按：

```text
Next
```

或：

```text
Save
```

再按：

```text
Review release
```

---

# 8. 看錯誤或警告

這一步很重要。

如果畫面出現：

```text
Errors summary
```

你要點進去看。
**Error 一定要修，Warning 可以先看情況。**

Google 官方說 release rollout 前如果有 Errors summary，要先解決錯誤；有錯誤時不能 publish。([Google 支援][4])

常見錯誤可能是：

| 錯誤                        | 意思                       |
| ------------------------- | ------------------------ |
| App content incomplete    | App content 問卷還沒填        |
| Privacy policy required   | 隱私權政策 URL 還沒填            |
| App access missing        | 如果 app 需要登入，要告訴 reviewer |
| Version code already used | versionCode 重複           |
| Package name conflict     | package 已被其他 app 使用      |
| Signing issue             | 簽章或 keystore 問題          |

你現在第一次上 Internal testing，有些設定可以之後補；但如果 Play Console 擋住你，就要先照畫面補。

---

# 9. Start rollout to Internal testing

如果沒有 Error，按：

```text
Start rollout to Internal testing
```

或：

```text
Roll out release
```

確認。

這不是正式上架，不是 Production。
只是把 app 發給你設定的 internal testers。

Google 官方說 internal testing 可以在 app 還沒完全設定好時使用，只要有 valid app bundle 就能快速發給少量 testers。([Google 支援][1])

---

# 10. 拿測試連結

回到：

```text
Internal testing > Testers
```

找：

```text
Copy link
```

或：

```text
Join on Android
```

把連結複製下來。

官方流程也說，Internal testing 的 Testers tab 會有 shareable link，可以分享給 testers。([Google 支援][1])

---

# 11. 手機測試安裝

用手機操作：

1. 確認手機 Play Store 登入的是剛剛加進 tester list 的 Gmail。
2. 用手機 Chrome 打開 opt-in 測試連結。
3. 按：

```text
Become a tester
```

或：

```text
Join the test
```

4. 之後點 Play Store 安裝。

第一次 internal test 有時候要等幾分鐘到幾小時，Google 官方也提醒第一次發布 testing track 後，測試連結可能需要幾小時才可用。([Google 支援][1])

---

# 12. 手機如果已經裝過 debug APK，要注意

如果你手機已經裝過側載版，而且 package 也是：

```text
com.brianyeh.justnotes
```

那 Play Store 版本可能無法覆蓋安裝，因為 debug 簽章跟 Play 簽章不同。

處理方式：

1. 先確認筆記資料是否需要備份。
2. 解除安裝手機上的 Just Notes debug 版。
3. 再從 Play Store 測試連結安裝 internal testing 版。

---

# 13. Billing 測試還要加 License tester

Internal testing 只是讓手機可以從 Play 安裝。
要測 Premium / Subscription，還要做這一步：

Play Console 左側：

```text
Settings > License testing
```

然後把你的測試 Gmail 加進去。

Google 官方說 license testing 可以設定 Gmail 或 Google Groups 來測 in-app billing 和 subscription；測試帳號可以做 test purchase，不會真的向帳號收費。([Google 支援][5])

同時 Google 也說：你的 app 要已經發布到 open / closed / internal test / production track，並且 tester 要有資格收到 release，才適合測 Billing。([Google 支援][5])

---

# 14. 你現在的實際操作順序

照這個順序做就好：

```text
1. Play Console > Create app
2. App name = Just Notes
3. App = App
4. Free = Free
5. 進 Testing > Internal testing
6. Testers tab > Create email list
7. 加你的手機 Gmail
8. Releases tab > Create new release
9. 上傳 D:\AndroidStudioProjects\app\release\app-release.aab
10. 填 Release notes
11. Review release
12. 沒 Error 就 Start rollout to Internal testing
13. 回 Testers tab 複製 opt-in link
14. 手機用 tester Gmail 打開連結
15. 從 Play Store 安裝
16. Settings > License testing 加同一個 Gmail
17. 再測 Premium / Billing
```

你下一張圖建議拍：**Play Console 左側選單或 Internal testing 頁面**。我可以照你畫面上的按鈕一步一步帶你點。

[1]: https://support.google.com/googleplay/android-developer/answer/9845334?hl=en "Set up an open, closed, or internal test - Play Console Help"
[2]: https://developer.android.com/studio/publish/app-signing "Sign your app  |  Android Studio  |  Android Developers"
[3]: https://developer.android.com/studio/publish/upload-bundle "Upload your app to the Play Console  |  Android Studio  |  Android Developers"
[4]: https://support.google.com/googleplay/android-developer/answer/9859348?hl=en "Prepare and roll out a release - Play Console Help"
[5]: https://support.google.com/googleplay/android-developer/answer/6062777?hl=en&utm_source=chatgpt.com "Test in-app billing with application licensing - Play Console ..."
