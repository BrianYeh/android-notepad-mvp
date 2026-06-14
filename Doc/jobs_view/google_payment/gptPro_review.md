結論：**你可以先用手機測一般功能，但不能只靠手機/側載 APK 完成真正 Google Play Billing 測試。**
下一步不要直接上 Production；你要做的是：**先 Internal testing release**。

## 先分析 OpenClaw 的回答

OpenClaw 的判斷大致正確：

| 項目                          | 判斷                                                 |
| --------------------------- | -------------------------------------------------- |
| `debug APK`                 | 可以手機安裝，測一般功能                                       |
| `unsigned release APK/AAB`  | 只能證明 release build 編得過，不能正式上 Play                  |
| 真正 Billing 測試               | 要 Play Console、tester、license tester、從 Play 測試連結安裝 |
| backend / RTDN / legal URLs | 不是手機側載能完成的事                                        |

補充一點：Google 文件其實說 **license tester 在特定條件下可以側載 debug build 測一部分 Billing**，但 package name 必須跟 Play Console 設定一致、Google 帳號也要是 license tester。可是你現在要驗證的是「像真正使用者一樣從 Play 安裝、購買、取消、恢復權益」，所以還是應該走 **Internal testing track**。Google 也明確建議 Billing 測試要把 app 發到 testing track，tester 再 opt-in 測試。([Android Developers][1])

---

# 你接下來照這樣做

## 第 1 階段：今天先用手機測 APK

先不要管付費。你先測 app 本身穩不穩。

1. 手機打開 Google Drive。
2. 找這個檔案：
   `JustNotes-debug-2026-06-13.apk`
3. 點開安裝。
4. Android 若跳出「不允許安裝未知應用」，去設定允許 Google Drive 或檔案管理器安裝。
5. 如果出現「無法安裝 / 簽章衝突」，代表手機上可能已經有同 package、不同簽名的舊版。
   先不要急著刪，因為刪掉可能會把本機 note 資料清掉。確認資料能備份後再移除舊版。
6. 安裝後測這些：

| 功能                | 要測什麼                       |
| ----------------- | -------------------------- |
| 開啟 app            | 會不會 crash                  |
| 新增 note           | 文字可否保存                     |
| 編輯 note           | 關 app 再開是否還在               |
| 刪除 note           | 刪除流程是否正常                   |
| 搜尋                | 找不找得到筆記                    |
| drawing note      | 畫圖、保存、重開                   |
| reminder          | 通知權限、提醒是否出現                |
| widget            | 桌面 widget 是否正常             |
| 分享文字到 app         | 從瀏覽器/LINE 分享文字進 Just Notes |
| 圖片/OCR            | 可否選圖、辨識、保存                 |
| Google Drive sync | 登入、同步、登出、失敗提示              |

Billing / Premium 頁面現在只測：**UI 顯示、未購買狀態、錯誤提示**。不要把這次側載當成真正付費驗證。

---

## 第 2 階段：先檢查 package name，這一步很重要

在正式丟 Play Console 前，先確認：

```gradle
applicationId
```

位置大概在：

```text
D:\AndroidStudioProjects\app\build.gradle
```

或：

```text
D:\AndroidStudioProjects\app\build.gradle.kts
```

如果現在還是：

```text
com.example.notepad
```

我建議**不要直接上 Play**。

因為 `applicationId` 上架後基本上不能亂改。Android 官方也提醒，發布後改 `applicationId`，Google Play 會把它當成完全不同的 app。([Android Developers][2])

你應該先決定正式 package，例如：

```text
com.brianyeh.justnotes
```

或：

```text
tw.brianyeh.justnotes
```

一旦改 package，Google Drive OAuth / Google Sign-in 也要重新設定 Android OAuth client。

---

## 第 3 階段：建立正式簽名 key

這一步用電腦做，不建議用手機。

用 Android Studio：

1. 開啟專案。
2. 點上方選單：
   **Build > Generate Signed Bundle / APK**
3. 選：
   **Android App Bundle**
4. 選：
   **Create new keystore**
5. 建議路徑放在專案外，例如：

```text
D:\AndroidKeys\justnotes-upload-key.jks
```

6. Alias 可以填：

```text
justnotes_upload
```

7. 密碼要記下來，建議放密碼管理器。
8. 產生 signed release AAB。

Google Play 現在新 app 要用 Play App Signing；你本機保管的是 **upload key**，Google 會用 Play App Signing 的 app signing key 幫使用者端簽 APK。([Android Developers][3])
而且 app 上傳前也必須先簽名，Android 文件也提醒簽章憑證有效期要晚於 2033-10-22。([Android Developers][4])

---

## 第 4 階段：建立 Play Console app，但只做 Internal testing

目標是：**不是正式上架，是內部測試版。**

Play Console 路線：

1. 打開 Play Console。
2. Create app。
3. App name：
   `Just Notes`
4. Default language：
   可選 English 或 Chinese Traditional。
5. App or game：
   App。
6. Free or paid：
   建議選 Free，之後靠 in-app purchase / subscription 做 Premium。
7. 建立 app。
8. 到：
   **Test and release > Testing > Internal testing**
9. 建立 tester list。
10. 加你的測試 Gmail。
11. 上傳 signed `.aab`。
12. 建立 release。
13. Roll out to internal testing。

Internal testing 是 Google 官方提供給最多 100 位內部 tester 快速 QA 的測試軌，官方也建議先從 internal test 開始。([Google 支援][5])
而且 internal track 比 Firebase/側載更接近 Play Store 發布，並且能測 Subscriptions / In-app purchases。([Android Developers][6])

---

## 第 5 階段：設定 license tester，才不會真的扣錢

Play Console：

1. 左側：
   **Settings > License testing**
2. 加入你的測試 Gmail。
3. Save。

Google 文件說 license tester 可以測 in-app billing / subscription，而且測試購買不會真的向帳號收費；但 one-time products / subscriptions 本身也要先 publish 才能測。([Google 支援][7])

建議你手機上測試時，用「測試 Gmail」登入 Play Store。最好不要用 Play Console owner 主帳號混在一起，避免帳號判斷混亂。

---

## 第 6 階段：用手機從 Play 測試連結安裝

等 internal release 出來後：

1. Play Console 會給一個 tester opt-in link。
2. 手機用測試 Gmail 打開那個連結。
3. 按加入測試。
4. 從 Play Store 安裝 Just Notes。
5. 這時候再測 Premium / Billing。

要注意：Google 文件說測試軌發布後，有時需要幾小時才會讓 tester 看到。([Android Developers][1])

---

## 第 7 階段：Billing 測試清單

你要測這些：

| 測試項目                 | 預期                  |
| -------------------- | ------------------- |
| Premium 商品是否查得到      | app 顯示價格/方案         |
| 點購買                  | 跳出 Google Play 購買視窗 |
| 測試卡成功                | Premium 解鎖          |
| 關掉 app 再開            | Premium 狀態仍存在       |
| 換手機/重裝               | 可恢復購買               |
| 測試卡失敗                | 不應解鎖                |
| pending payment      | 等狀態變成 purchased 才解鎖 |
| subscription renewal | 測試訂閱週期會被加速          |
| refund/revoke        | 權益要被收回              |

Google 的測試購買會走跟實際購買相同的 purchase flow，license tester 也會看到測試付款方式。([Android Developers][1])

---

## 第 8 階段：Google Drive sync 要另外補 OAuth 設定

如果 Just Notes 有 Google Drive sync，Play 安裝版的簽章會跟 debug APK 不同。

你要去：

```text
Play Console > Release > Setup > App signing
```

找到：

```text
App signing certificate SHA-1
```

然後去 Google Cloud Console 建 Android OAuth client：

```text
Package name = 你的正式 applicationId
SHA-1 = Play Console 的 App signing certificate SHA-1
```

Android 官方也說 Play Console 的 App signing / upload certificates 常用來註冊 API provider，例如 OAuth 這種服務。([Android Developers][3])

---

## 第 9 階段：legal URLs / App content，不急但一定要做

正式 review 前要補：

| 項目                 | 說明                         |
| ------------------ | -------------------------- |
| Privacy Policy URL | 隱私權政策網址                    |
| Data safety        | 宣告收集哪些資料                   |
| Ads                | 有沒有廣告                      |
| App access         | 如果 reviewer 需要登入，要給測試帳密/說明 |
| Target audience    | 目標年齡                       |
| Content rating     | 內容分級                       |

Play Console 的 App content 頁面就是用來填這些合規資訊，包含 privacy policy、ads、登入方式、target audience、敏感權限等。([Google 支援][8])

---

# 你現在最安全的下一步

照順序做：

1. **今天先手機安裝 debug APK，測一般功能。**
2. **確認 `applicationId` 不要是 `com.example.notepad`。**
3. **建立 release upload key。**
4. **產生 signed release AAB。**
5. **Play Console 建 app。**
6. **上 Internal testing，不上 Production。**
7. **加 tester + license tester。**
8. **手機從 Play 測試連結安裝。**
9. **再測 Premium / Billing。**

目前不要碰 Production。你現在的目標只有一句話：

> 先做出一版「Play Console Internal Testing 可安裝、可測 Billing」的 Just Notes。

[1]: https://developer.android.com/google/play/billing/test "Test your Google Play Billing Library integration  |  Android Developers"
[2]: https://developer.android.com/build/configure-app-module?utm_source=chatgpt.com "Configure the app module | Android Studio"
[3]: https://developer.android.com/studio/publish/app-signing "Sign your app  |  Android Studio  |  Android Developers"
[4]: https://developer.android.com/studio/publish/preparing "Prepare your app for release  |  Android Studio  |  Android Developers"
[5]: https://support.google.com/googleplay/android-developer/answer/9845334?hl=en "Set up an open, closed, or internal test - Play Console Help"
[6]: https://developer.android.com/studio/publish/upload-bundle "Upload your app to the Play Console  |  Android Studio  |  Android Developers"
[7]: https://support.google.com/googleplay/android-developer/answer/6062777?hl=en "Test in-app billing with application licensing - Play Console Help"
[8]: https://support.google.com/googleplay/android-developer/answer/9859455?hl=en "Prepare your app for review - Play Console Help"

