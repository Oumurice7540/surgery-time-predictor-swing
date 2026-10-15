## GitHub 介面修正版釋出（2026-10-15）

- 標籤：`v1.7.0-textfix-20261014`；包含資料處理頁重排與底部狀態文字裁切修正，視窗內版本號維持 1.7.0。
- `.\mvnw.cmd --batch-mode verify`：74 tests、0 failures、0 errors、0 skipped，BUILD SUCCESS。
- 可攜包內 124 個專案 class 與本次建置逐一 SHA-256 比對一致。
- ZIP SHA-256：`f15c178439c3136105f5576334f2496d2e02345ff8f3ee70c1ece6e462175551`。
- 保留原 v1.7.0 Release；附件為 `SurgeryTimePredictor-1.7.0-textfix-20261014.zip` 與校驗檔。

## 2026-10-14：底部狀態文字裁切修正

ApplicationShellView 移除狀態列固定 30px 高度，改由字體與邊框自動計算；縮減上下留白並以 BorderLayout 配置狀態文字，避免原本僅剩 11px 的內容高度。資料處理頁新排版保留。新版目錄／ZIP：`output/exe/SurgeryTimePredictor-1.7.0-textfix-20261014`，前版保留。

驗證：`target/footer-text-verify.log`；九頁渲染沿用 `target/algorithm-preview/parity-min-*.png`。

## 2026-10-14：資料處理頁依原版截圖重排

僅修改第四頁介面：左側七個直排工具（IQR、刪除特定值、刪欄、選取特定值、數值化、0～1、Z 分數），中央逐列處理流程及底部儲存／載入／預覽，右側上下處理前後表格。各流程列可直接改欄位／條件、排序與移除；範圍、包含文字、空白等完整操作仍在「詳細條件編輯」。保留套用確認、執行紀錄、探索與正式訓練的區分。配色與全程九頁導覽沿用目前程式。

修改：`DataProcessingView.java`、`DataProcessingViewTest.java`。新增工具列操作／條件修改／排序移除驗證；版面斷言改為檢查按鈕位於實際視窗範圍。完整測試與渲染紀錄：`target/processing-layout-verify.log`、`target/algorithm-preview/parity-min-DATA_PROCESSING.png`。

交付：`output/exe/SurgeryTimePredictor-1.7.0-processing-layout-20261014/SurgeryTimePredictor.exe` 與同名 `.zip`，原 1.7.0 目錄與 ZIP 保留。此為 1.7.0 的介面修訂，視窗版本號維持 1.7.0。

## GitHub v1.7.0 釋出檢查（2026-10-05）

- Maven verify：73 tests、0 failures、0 errors、0 skipped，BUILD SUCCESS。
- Windows 可攜 ZIP SHA-256：cbec2632d76f752f161d99db224551809d8f294c8cee490e2b87c586c9fecada。
- 原始碼提交排除 output、tmp、target 與 generated-data；Release 附件使用既有完整可攜包及 SHA-256 檔。

## 1.7.0 交付（2026-10-05）

- 新版：`output/exe/SurgeryTimePredictor-1.7.0/SurgeryTimePredictor.exe`。
- 可攜包：`output/exe/SurgeryTimePredictor-Windows-x64-1.7.0.zip`；請完整解壓，保留 EXE 旁的 `app` 與 `runtime`。
- 舊版完整備份：`output/exe/previous-SurgeryTimePredictor-20261004-before-ppt-parity/`；原 `output/exe/SurgeryTimePredictor/` 也保留，請使用上方新版路徑。
- 原版、備份、新版的既有病例 CSV SHA-256 相同。可攜 ZIP 只含程式、說明與人工範例，不包含使用者工作資料。
- 2026-10-05 再次啟動新版，視窗標題含 v1.7.0、Responding=true；正式 JAR、打包暫存與交付安裝目錄 JAR 雜湊一致。
- 73 項測試與附帶 Java 21.0.9 的四模型保存／載入驗證紀錄隨包附於 `說明文件/驗證紀錄/`。PPT 差異見 `PPT_PARITY.md`。

# 1.7.0 驗證紀錄（2026-10-04）

## 自動測試

執行 `.\mvnw.cmd verify`：**73 tests，0 failures，0 errors，0 skipped，BUILD SUCCESS**。完整輸出：`target/parity-verify.log`。

新增 `PptParityWorkflowTest`（8 項）及 `PptParityViewTest`（2 項），保留原 63 項測試。既有測試中的固定特徵與 IQR 套用筆數斷言依新契約更新：可用特徵包含合適輔助欄，IQR 在正式拆分後只移除訓練列。

- 240 筆人工資料：CSV 讀取、完整欄位匯出、DRG 合併、跨午夜麻醉分鐘、欄位對應、套用、處理步驟保存／載入、特徵選取、80/20 拆分、四方法訓練、模型保存／載入、動態預測。
- 缺值：數值以訓練平均補值；未見類別採模型固定未知值，不修改字典；常數欄轉換為 0。
- 時間：跨午夜明確允許／拒絕、帶日期跨日、秒數四捨五入、缺值、相同時刻無日期、混合格式、無效時刻。
- DRG：病例端重複鍵不擴增紀錄；對照端重複鍵／空白鍵拒絕；未匹配列保留，新增欄空白，回報未匹配鍵；欄名衝突拒絕。
- CSV：引號、逗號、換行與所有欄位 roundtrip；原有 Big5／多檔／映射／錯誤檢查測試保留。
- 隔離：測試集極端值不改變訓練 IQR 界線或已擬合尺度；測試列完整保留。正式模型字典與補值統計取自訓練列。
- 特徵：新增、移除、僅使用輔助欄（不需要原十欄）、保存／重新載入後預測一致；ID／目標不列入輸入。
- 指標：完整訓練集逐筆值重新計算 RMSE 等於訓練指標；既有測試重算完整測試集 RMSE。原四種方法共用拆分的測試保留。
- 外來格式：拒絕非本程式 `.steps`／`.model`；修正模型載入失敗時檔案串流未關閉。這不是原軟體格式相容性驗證。

## Swing 畫面與操作

`ReferenceLayoutTest`：九頁 1366×900 渲染於 `target/reference-preview/`。
`PptParityViewTest`：九頁 1100×740，以及來源預覽、探索處理、特徵評分、模型輸入拆分、動態預測、折線圖及轉檔視窗，位於 `target/algorithm-preview/parity-*.png`。

實際呼叫 Swing 按鈕事件完成來源切換、預覽、評分與預測；已查看渲染檔。第七頁隱藏不適用參數，避免小視窗訓練操作落到下方；結果頁兩組指標及保存操作固定可見。長欄位／長列表使用捲軸與可調整欄寬；表格原始預覽最多 100 筆，逐筆結果及匯出不截斷。尚未逐一測量所有 Windows DPI／字體縮放組合。

## EXE 附帶 runtime smoke

使用 jpackage 建立測試啟動器，採交付相同 runtime 與相同 JAR，**Java 21.0.9**；結果 `target/parity-smoke/run/result.txt`：

| 方法 | 訓練／測試 | 同一人工輸入預測分鐘 | 保存載入 |
|---|---|---|---|
| WEKA BPNN | 192／48 | 93 | 相同 |
| RandomForest | 192／48 | 88 | 相同 |
| LinearRegression | 192／48 | 85 | 相同 |
| RBF SVR | 192／48 | 85 | 相同 |

測試使用未知科別、空白檢驗值及常數欄；四個模型皆能重建相同字典／轉換。使用此 runtime 再渲染九頁，檔案 `target/parity-smoke/run/packaged-*.png`。

實際載入本專案既存 `target/phase2-package-smoke/run/phase2.model` 與 `target/phase3-package-smoke/bundled-run/LINEAR_REGRESSION.model`，兩者完整測試結果皆 80 筆，成功；舊模型缺少新訓練指標時顯示「未保存」。未提供 PPT 原軟體模型與步驟樣本，無法測試其相容性。

## 人工操作資料

`examples/PPT流程驗收/操作說明.md` 提供可重跑的輸入檔、DRG 對照及各頁操作。自動測試用人工合成病例而非真實病歷；結果不代表臨床效度。

下方保留先前版本驗證紀錄；現行結果以本節為準。

---

## 1.6.0 介面排版驗證（2026-10-02）

- mvnw.cmd verify：63 tests、0 failures/errors/skipped，BUILD SUCCESS。紀錄 target/reference-verify.log。
- ReferenceLayoutTest：逐一切換九頁並渲染 1366×900 圖，確認頁首／底部導覽、訓練及比較入口仍存在，第七頁未重加資料生成按鈕。
- ReferenceDataPreview 的測試集案例 ID 與實際模型預測對照完全相同；拆分設定改變後預覽更新，來源資料保持不變。
- 檢視 target/reference-preview 的九頁輸出，修正轉檔表單拉高、說明文字高度不足與處理前後表格位置。
- 既有 61 項測試涵蓋匯入、處理套用、統計、四演算法、歷史比較、模型存取及第七頁資料流程。

## 1.5.1（2026-10-01）：第七頁流程收斂與統計文字裁切

- MethodSettingsView：移除本頁生成／匯入／開啟 CSV 按鈕及僅供本頁使用的事件處理；資料狀態保留檔名、完整路徑 tooltip、病例筆數，顯示特徵數、拆分比例與種子。
- 訓練區改名「使用目前資料訓練」。訓練按下時直接讀取目前已套用資料筆數、特徵與第五／六頁設定；無資料時停用單一／四種訓練及 Enter 預設動作，提示回第一、二頁準備。
- 共用 SurgeryModelService、資料生成／匯入服務、第一／二頁均未改動；保留演算法選擇、適用參數、兩種訓練及訓練前檢查。
- DataStatisticsView：只調整下拉選單邊框內距、高度及容器高度，修正中文字上下裁切；不變更統計計算。
- `.\mvnw.cmd verify`：61 tests，0 failures/errors/skipped。target/method-workflow-verify.log。
- 新增 MethodSettingsWorkflowTest：無資料狀態與移除控制項；239 筆已處理資料、兩項特徵及 UI 更新後再次變更的拆分設定，實際訓練沿用正確數值。
- 已檢視 target/algorithm-preview/statistics-cross.png 與 method-workflow-desktop.png 的渲染畫面，統計選單文字完整，第七頁資料摘要與訓練按鈕可見。

# 1.5.0 資料統計驗證（2026-09-30）

- `.\mvnw.cmd verify`：59 tests、0 failures、0 errors、0 skipped；BUILD SUCCESS。記錄：target/statistics-verify.log。
- 新增 DataStatisticsServiceTest 3 項：人次、加權平均、最小最大、缺值占比分母、交叉分組、數值年資排序、無資料與錯誤欄位保護。
- 新增 DataStatisticsViewTest 2 項：六項選單、欄位辨識、圖表切換、數值排序、套用刪欄後清除舊結果、手動選欄及相同雙欄防呆。
- 視覺檢查：target/algorithm-preview/statistics-pie.png、statistics-cross.png；1280×1000，可見欄名、分組選單、圖表、占比與操作按鈕；表格／圖表有捲動。
- 使用相同已打包 JAR 與 bundled runtime 的 StatisticsSmoke 隱藏 launcher；實際匯入 240 筆範例、操作六選單並渲染畫面，兩科各 120 人次／50%，套用刪除一筆後有效人次 239；Big5 可用。
- 打包驗證：target/statistics-smoke/run/result.txt，輸出 STATISTICS PACKAGED PASS topics=6 imported=240 groups=2 counts=120/120 shares=50/50 applied=239 charts=PASS Big5=PASS。
- 模型訓練與比較流程由既有 54 項測試回歸；本次未變更其演算法或評估方式。

---
# 多模型比較驗證（2026-09-30，1.4.0）

`.\mvnw.cmd verify`：54 tests，0 failures，0 errors，0 skipped。新增 TrainingHistoryTest 4 項及 TrainingHistoryViewTest 1 項；既有 49 項全部通過。

新增覆蓋：一鍵訓練保留四份模型；條件相同歸同組，改資料／特徵／種子／比例／目標分組不同；歷史模型切換後設定及預測正確、待訓練資料不變；任一歷史模型存取一致；失敗操作不追加錯誤紀錄；比較 CSV 完整列數與預測完整精度；Swing 真實一鍵按鈕、表格排序後選取正確模型、同條件篩選。

Bundled runtime smoke：target/comparison-package-smoke/bundled-run/result.txt。
結果：COMPARISON PACKAGED PASS methods=4 groups=1 exports=4x80 legacy=PASS。

使用科別處理範例 800 → 399 筆、兩個特徵，四種方法各有 319 筆訓練、80 筆測試；比較表 4 筆、預測明細每份 80 筆；四份模型各自可存取，舊模型仍可載入。

已檢視 target/comparison-package-smoke/bundled-run/comparison-page.png 及 target/algorithm-preview/comparison-table.png。訓練結果頁上方同時顯示四份指標，模型操作與匯出按鈕可見；額外參數可水平捲動查看。

CSV 匯出 UTF-8 BOM、文字引號轉義，保留完整數值精度；先寫同目錄暫存檔再取代。歷史僅在本次程式記憶體保存，沒有宣稱跨次啟動還原。

以下保留先前版本驗證記錄。

---

# 第三階段驗證（2026-09-29）

Java 21／Windows x64。`.\mvnw.cmd verify`：49 tests、0 failures、0 errors、0 skipped，BUILD SUCCESS。

新增 MultiAlgorithmTest 9 項與 MultiAlgorithmViewTest 1 項，既有 39 項全部保留並通過。

- BPNN、RandomForest、LinearRegression、SVR 各自真正訓練、重現相同逐筆結果、保存／重載模型與參數，預測一致。
- 直接檢查保存的 WEKA 物件類別、樹數／深度、Ridge、C／Gamma，確認沒有只切換顯示文字。
- 四種模型使用完全相同測試案例編號；畫面逐筆值重新計算的 RMSE 與摘要一致。
- 不支援或與中繼資料不符的 classifier、錯誤欄名、無效參數、失敗訓練均被拒絕，保留目前可用模型。
- 只有測試集出現的類別不加入訓練字典，評估時以該群組全零向量處理；手動輸入未知類別仍會拒絕。
- Swing 真實選單與按鈕測試：參數按方法啟用、背景訓練時鎖定、完成後還原；實際選取 SVR 並核對服務收到 C=4／Gamma=0.05。
- 已檢視 target/algorithm-preview 的方法設定、SVR 結果、預測畫面。擴充參數表所需捲動高度，加入訓練按鈕不可被父容器裁切的檢查。

## 1.3.0 打包驗證

以 jpackage 建立 app-image，另用相同 bundled runtime 和 packaged JAR 建立隱藏 smoke launcher。

記錄：target/phase3-package-smoke/bundled-run/result.txt。

| 方法 | 處理後資料／特徵 | 測試 MAE | 測試 RMSE | 測試輸入預測 |
| --- | --- | --- | --- | --- |
| BPNN | 399／2 | 9.9869 | 11.5929 | 80 分鐘 |
| RandomForest | 399／2 | 10.9346 | 12.1919 | 75 分鐘 |
| LinearRegression | 399／2 | 9.9035 | 11.4628 | 80 分鐘 |
| SVR | 399／2 | 9.9750 | 11.5866 | 80 分鐘 |

這是流程驗收的合成範例，不是演算法在真實醫院資料上的排名。四種模型的儲存／還原、相同測試集、兩項選定特徵均已驗證。

舊版 audit.model 預測保持 150 分鐘；第二階段 phase2.model 預測保持 80 分鐘；Big5 可用。最終輸出：PHASE3 PACKAGED PASS methods=4 legacy=PASS phase2=80 charset=Big5。

原有歷史驗證記錄如下。

---

# 第二階段驗證

日期：2026-09-28；Java 21；Windows x64。

## 自動測試

執行 `.\mvnw.cmd verify`：39 tests、0 failures、0 errors、0 skipped，BUILD SUCCESS。

新增 DataProcessingServiceTest 10 項、DataProcessingViewTest 2 項，其餘既有 27 項保留。

涵蓋：
- 未對應的科別／檢驗值保留，多檔缺少輔助欄補空白。
- 文字等於／包含及其刪除條件、大小寫、包含上下界的數值範圍、空白列。
- IQR 排除目標離群值、空白保留、文字拒絕、數值不足拒絕。
- 順序执行、重複預覽不修改來源、刪欄後使用該欄會報錯。
- 案例編號／目標／最後一項特徵保護；不足 200 筆拒絕套用。
- 過期或其他服務的預覽不能套用；清空規則後可還原原始資料。
- CSV 完整輸出、輔助欄名衝突處理、步驟檔中文與特殊符號 roundtrip、格式／版本／必要欄位檢查。
- 實際 399 筆訓練，刪除性別後只使用年齡與手術名稱，模型存取預測一致；新資料準備不污染舊模型。
- Swing 按鈕新增、預覽、套用、清空；無資料／不足筆數時按鈕保護。

## 畫面檢查

已檢視 target/processing-preview/preview.png 與 insufficient.png，涵蓋 1200×1100、1000×900。修正文字區最小高度造成控制項移出卡片的問題，並加入按鈕位置檢查。資料表有欄名、水平捲動與每表 100 筆預覽限制。

## 打包驗證

jpackage app-image 1.2.0，沿用 Java 21 runtime（含 jdk.charsets）。另建立隱藏測試 launcher，以同一 runtime 及已打包 JAR 執行實際服務流程。

記錄：target/phase2-package-smoke/bundled-run/result.txt
結果：PHASE2 PACKAGED PASS，imported=800、filtered=399、features=2、prediction=80 分鐘、模型儲存／載入相同、legacy=PASS、Big5=PASS。

舊模型測試檔：../.codex-temp/current-ppt-audit/run/audit.model。

合成資料與規則範例：examples/科別資料處理範例.csv、examples/科別資料處理範例.steps。這些驗證確認軟體流程與一致性，不代表真实醫院資料上的預測準確度。



1.6.0 bundled runtime + packaged JAR smoke: target/reference-smoke/result.txt；STATISTICS PACKAGED PASS，六選單、240 筆匯入、50/50 占比、套用處理後 239 筆及 Big5 通過。
