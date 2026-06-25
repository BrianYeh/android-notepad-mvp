
這畫面是正確的，現在已經到最後一步了。

## 你現在做這樣

1. **確認 Build Variants 選的是 `release`**
   你現在已經選對了。

2. **Destination Folder 可以先維持這個：**

```text
D:\AndroidStudioProjects\app
```

3. 直接按右下角藍色按鈕：

```text
Create
```

---

## 按下 Create 後會發生什麼？

Android Studio 會開始產生 signed release 檔案。等它跑完。

跑完後，通常會跳出訊息，例如：

```text
Signed bundle generated successfully
```

或右下角會有通知，可以點 **Locate** / **Show in Explorer**。

你要找的檔案通常會是：

```text
app-release.aab
```

可能會在這附近：

```text
D:\AndroidStudioProjects\app\app-release.aab
```

或：

```text
D:\AndroidStudioProjects\app\release\app-release.aab
```

---

## 這個檔案用途

這個：

```text
app-release.aab
```

就是等等要上傳到：

```text
Play Console > Internal testing
```

的檔案。

不是 APK，不是手機直接安裝用。
`.aab` 是給 Google Play 用的。

---

## 注意一件很重要的事

如果你的 app 現在 package name 還是：

```text
com.example.notepad
```

那**先不要正式上 Play Production**。

但你現在只是產生 signed AAB、準備 internal testing，可以先做。
下一步等檔案產生後，我再帶你檢查：

1. `.aab` 是否成功產生
2. package name 是什麼
3. 要不要先改成正式名稱
4. Play Console internal testing 怎麼上傳

你現在先按 **Create**。

===============================================================
"""
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
"""
你可以照上面步驟做嗎?
我已經 打開 Android Studio
      and 1. 開啟專案。
	  
	  
======================================
你這個畫面**快完成了**，但有一個地方要改：

## 這裡要改

**Country Code (XX)：不要填 `+886`。**

這不是電話國碼，是 **2 個英文字母的國家/地區代碼**。Android/Java 相關欄位使用的是 ISO 3166 兩碼國家代碼格式。台灣請填：

```text
TW
```

Android 文件也提到 country code 是 two-letter ISO 3166-1 alpha-2 code。([Android Developers][1])

---

## 你這頁建議填法

照這樣改：

```text
Key store path:
D:\AndroidKeys\justnotes-upload-key.jks

Alias:
justnotes_upload

Validity:
25

First and Last Name:
Brian Yeh

Organizational Unit:
brian

Organization:
brian

City or Locality:
New Taipei City

State or Province:
New Taipei City
或 Taiwan
或留空也可以

Country Code (XX):
TW
```

`Validity = 25` 可以，2026 + 25 = 2051，已經超過 Google 要求的 2033 年後有效期限。Google 文件有提醒簽署憑證有效期要晚於 2033-10-22。([Android Developers][2])

---

## 現在你下一步

1. 把 `+886` 改成：

```text
TW
```

2. 確認兩組密碼都有記下來：

   * Key store password
   * Key password

3. 按 **OK**。

4. 回到上一頁後，應該會看到：

   * Key store path 已選好
   * Key alias 是 `justnotes_upload`
   * 密碼已填好

5. 下一頁選：

   * **Build Variant：release**
   * 勾選：**Export encrypted key for enrolling published apps in Google Play App Signing** 如果畫面有出現；沒有也沒關係。

6. 按 **Finish** 產生 `.aab`。

---

## 很重要：這個檔案要保護好

這個檔案：

```text
D:\AndroidKeys\justnotes-upload-key.jks
```

是你的 **upload key**。不要丟到 GitHub、不要傳給別人。建議備份到：

```text
G:\我的雲端硬碟\01_openClaw\02_apps\JustNotes\keys\
```

但密碼不要放在同一個地方。

Google Play App Signing 下，如果 upload key 遺失或外洩，可以向 Play Console 申請 reset，但還是要盡量保管好。([Android Developers][2])

你現在只要先做一件事：**把 `+886` 改成 `TW`，然後按 OK。**

[1]: https://developer.android.com/reference/java/util/Locale.IsoCountryCode?utm_source=chatgpt.com "Locale.IsoCountryCode | API reference - Android Developers"
[2]: https://developer.android.com/studio/publish/app-signing?utm_source=chatgpt.com "Sign your app | Android Studio"

	  
	  