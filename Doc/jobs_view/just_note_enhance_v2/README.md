# Just Notes enhance v2：Premium unavailable preview

日期：2026-06-21

## 文件索引

- `01_agent_a_jobs_review.md`：Agent A 以 Steve Jobs / 賈伯斯眼光做產品評審。
- `02_agent_b_implementation_plan.md`：Agent B 實作計畫。
- `03_agent_c_plan_review.md`：Agent C 計畫審查。
- `04_agent_d_implementation.md`：Agent D / parent implementation report。
- `05_agent_f_code_review.md`：Agent F code review findings 與修正。
- `06_agent_g_validation.md`：Agent G validation、emulator gate、APK 交付與 hash。

## 本輪功能結論

Premium 尚未可購買時，頁面改成乾淨的進階功能預覽。它不再像壞掉的商店，不顯示 plan rows、subscribe、restore、法律 chrome、Google Play 或 backend verification raw copy；同時保留資料夾、文字格式、提醒／日曆工具三個明確 benefits。

已保護例外狀態：

- 已有 Premium entitlement 時仍顯示 active。
- Pending purchase / verification pending 時保留帳號狀態與 restore。
- Billing 商品資料短暫尚未回來時先顯示 checking，再 timeout fallback 到 preview。
