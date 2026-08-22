# 手術時間智慧系統（Java 21 Swing）

依參考簡報第 43～49 張製作，僅包含兩個可切換頁面：

- 訓練結果：摘要指標、RMSE 趨勢、特徵重要度與實際／預測值對照。
- 手術時間預測：五項條件表單、模擬資料載入、預測分鐘、合理區間與結果摘要。

## 技術規格

- 程式語言：Java 21
- 圖形介面：Swing（不含 JavaFX）
- 專案管理：Maven Wrapper
- 版本控制：Git，並提供 GitHub Actions Maven 驗證流程
- 資料格式：UTF-8 CSV

所有訓練紀錄與預測結果都是固定模擬資料，不包含 Python，也不會建立或執行真正的機器學習模型。

## CSV 資料

CSV 位於 src/main/resources/tw/edu/nkust/surgerytime/data/：

- training_summary.csv
- epoch_rmse.csv
- feature_importance.csv
- prediction_comparisons.csv
- procedure_baselines.csv
- prediction_options.csv

程式由 classpath 讀取上述 CSV，因此打包成 JAR 後仍可正常執行。

## 編譯與測試

~~~powershell
.\mvnw.cmd clean verify
~~~

## 啟動 Swing 介面

使用 Maven：

~~~powershell
.\mvnw.cmd exec:java
~~~

或先打包後直接執行：

~~~powershell
.\mvnw.cmd package
java -jar target\surgery-time-predictor-swing-1.0.0-SNAPSHOT.jar
~~~

啟動後預設顯示「訓練結果」。按「完成並前往預測」或使用上方導覽切換到「手術時間預測」，再依序按「載入 CSV」與「開始預測」。

兩個頁面會保留各自狀態；視窗縮窄時，摘要卡會改為兩欄，左右內容會改成上下排列並提供捲動。

## 視覺預覽

以下工具會輸出兩頁的桌面及窄版 PNG 到 target/ui-preview：

~~~powershell
.\mvnw.cmd --% test-compile exec:java -Dexec.mainClass=tw.edu.nkust.surgerytime.view.UiPreviewRenderer -Dexec.classpathScope=test
~~~

## Git 與 GitHub

.gitignore、.gitattributes 與 .github/workflows/maven.yml 已備妥。建立 GitHub 儲存庫後，可設定遠端並推送：

~~~powershell
git remote add origin <GitHub 儲存庫網址>
git push -u origin main
~~~

目前不會自動建立 GitHub 儲存庫或推送，以免使用錯誤帳號或遠端位置。

## 主要結構

~~~text
src/main/
├── java/tw/edu/nkust/surgerytime/
│   ├── Launcher.java
│   ├── SurgeryTimeApplication.java
│   ├── model/
│   ├── service/
│   └── view/
└── resources/tw/edu/nkust/surgerytime/data/
~~~
