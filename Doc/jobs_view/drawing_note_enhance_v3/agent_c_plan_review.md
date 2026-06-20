# Agent C 計畫審核：drawing_note_enhance_v3

日期：2026-06-20

## 結論

Agent B 的範圍可以接受，而且比重做畫布模型更適合 v3：小、可測、感知強。

本輪應只做「有筆畫 drawing note 重新開啟直接進 fullscreen canvas」。不要順手改 metadata、PNG、pan/zoom、或新增 fullscreen bottom sheet。

## Required guardrails

1. **不要讓 title-only drawing note 失去快速改標題路徑。**
   - 有標題但無 strokes 的 drawing note 應維持一般詳細模式。

2. **不要改 blank draft cleanup。**
   - 新空白 drawing 的第一秒乾淨畫布與返回清理行為必須維持。
   - existing blank drawing 不應因重新開啟或返回被刪。

3. **有筆畫 saved drawing 在 fullscreen 不應套用 pristine hide chrome。**
   - title/status 應可見。
   - Details button 應可見。
   - save failure / retry / PNG message 仍不可被隱藏。

4. **測試要同步改產品期待。**
   - 以前開 saved drawing 後等 `drawing_note_title` 的測試，現在應先等 fullscreen，再點 Details。

## Acceptance tests

最低 focused connected coverage：

- 有筆畫 drawing note 從列表開啟直接進 fullscreen。
- Details button 回一般模式且 share/export controls 可見。
- 有筆畫 + title 的 drawing note 重新開啟後仍保留 title/strokes。
- title-only drawing note 重新開啟仍先到一般詳細模式。
- 新空白 drawing v2 行為不退化。

## Status

PASS。可交給 Agent D 實作。
