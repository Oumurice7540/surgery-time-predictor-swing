# PPT → 需求 → 實作 → 驗證

參考：`參考_20220704_實作成果展示 (手術時間預測, 現場簡報版).pptx`。下列頁碼為 PowerPoint **實際投影片索引（從 1 起算）**，非畫面底部重複使用的版型頁碼。已檢查原始投影片渲染與目前 Swing 渲染。配色／圖示沿用本程式，主要工作區依參考配置。

| PPT 投影片 | 需求／可確認配置 | 實作位置（src/main/java/tw/edu/nkust/surgerytime/ 下） | 驗證結果／差異 |
|---|---|---|---|
| 3–5 | 系統上方橫幅、下方流程導覽 | view/ApplicationShellView.java | 九頁渲染；1366×900 及 1100×740，保留捲動 |
| 6–11 | 檔案清單、個別來源內容預覽、流程說明 | view/DataSourceView.java、service/MappedCsvImportService.java | PptParityViewTest 切換兩個檔案，預覽跟隨選取，已套用病例不變；只支援已確認的 CSV 流程 |
| 12–18 | 轉檔設定左／結果表右，DRG 與麻醉時間 | view/DataConversionView.java、CsvImportPanel.java、ReportPreparationDialog.java；service/ReportConversionService.java | 240 人工病例；重複鍵拒絕、未匹配保留列；跨午夜明示假設，日期／秒數／空白／歧義測試；完整欄位及全部列匯出 |
| 19–21 | 六項統計，上方選項、左表右圖 | view/DataStatisticsView.java、StatisticsChartPanel.java；service/DataStatisticsService.java | 既有六項測試保留；長條／圓餅／折線，缺欄提示；圖表主題、單位、母數皆標示 |
| 22–29 | 左步驟管理、右上下資料；類別數值化、正規化、Z 分數 | model/ProcessingStep.java；service/DataProcessingService.java、FittedTransforms.java、TrainingPartition.java；view/DataProcessingView.java | 步驟保存載入／排序／修改保留；探索與正式估計分離；IQR／均值／尺度／字典只用訓練列，測試極端值不影響界線 |
| 30–33 | 左上選取前、左下選取後，右勾選／名稱／重要度 | view/FeatureSelectionView.java；service/SurgeryModelService.java#scoreFeatures | 訓練前 r²／η² 標明自訂方法，保留測試集不參與；新增輔助欄跨訓練／保存／預測；PPT 原始評分公式未確認 |
| 34–37 | 拆分比例／種子，訓練及測試並排 | view/DataSplitView.java、ReferenceDataPreview.java；service/TrainingPartition.java | 相同種子可重現；原始／模型數值欄切換，holdout ID 與真實結果一致；IQR 僅縮減訓練集 |
| 38–42 | 左方法參數及訓練操作，右上下訓練／測試 | view/MethodSettingsView.java；service/WekaBpnnTrainingService.java | BPNN、RandomForest、LinearRegression、RBF SVR 真正迴歸訓練；只顯示適用參數；單一／四種方法比較保留；自主 BPNN 未重現 |
| 43–44 | 左指標／重要度、右實際／預測／差值 | model/EvaluationMetrics.java、TrainingResult.java；view/TrainingResultsView.java | 同時列兩組指標與筆數／評估耗時；兩組完整逐筆值保存；重要度為測試 permutation；無中間 epoch，明示無法取得 |
| 45–49 | 左條件、右模型資訊及結果、結果視窗 | view/SurgeryPredictionView.java；service/FittedTransforms.java、WekaBpnnTrainingService.java | 模型動態表單；新欄位／移除特徵／未知類別／缺值／常數欄與模型 roundtrip 通過；輸入與分鐘結果視窗可捲動 |

## 不能據截圖推定的項目

- 原自主 BPNN 權重更新公式、初始化、停止條件與訓練歷程沒有原碼，未確認也未宣稱等價。
- 原 `.model`／`.step` 實際格式無樣本，不能驗證相容。拒絕非本程式格式測試不等於證明原檔相容；本專案舊版模型另做實檔載入 smoke。
- 類別排序／缺值／標準差分母／特徵分數為本程式明訂規則，非從 PPT 猜測的原始實作。
- 保留既有測試殘差區間校準；該區間涵蓋率來自同一測試集，不能當成另外獨立資料的校準驗證。
- 本交付驗證的是程式與人工測試資料流程，非臨床模型效度。
