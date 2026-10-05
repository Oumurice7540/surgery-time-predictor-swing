package tw.edu.nkust.surgerytime.service;

import tw.edu.nkust.surgerytime.model.ProcessingTable;
import tw.edu.nkust.surgerytime.model.TrainingRun;
import tw.edu.nkust.surgerytime.model.AlgorithmConfiguration;
import tw.edu.nkust.surgerytime.model.ProcessingStep;
import tw.edu.nkust.surgerytime.model.PredictionRequest;
import tw.edu.nkust.surgerytime.model.PredictionResult;
import tw.edu.nkust.surgerytime.model.TrainingConfiguration;
import tw.edu.nkust.surgerytime.model.TrainingResult;
import tw.edu.nkust.surgerytime.model.SyntheticSurgeryCase;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 三個 Swing 頁面共用的應用服務。
 *
 * <p>它協調「產生 CSV -> 讀回 CSV -> 訓練 -> 評估 -> 預測」流程，並保留最近一次
 * 成功模型。View 不需要知道反向傳播、one-hot、WEKA API 或檔案讀寫細節。</p>
 */
public final class SurgeryModelService {
    /** 十個可選原始特徵的固定順序，必須與 WEKA 編碼器一致。 */
    public static final List<String> ALL_FEATURE_GROUPS = List.of(
            "病患年齡", "生理性別", "手術名稱", "虛擬醫師", "手術室別",
            "緊急程度", "ASA 分級", "麻醉方式", "共病數量", "手術複雜度"
    );
    private final SyntheticSurgeryDataService dataService;
    /** 真正呼叫 WEKA MultilayerPerceptron 的 BPNN 訓練服務。 */
    private final WekaBpnnTrainingService trainingService = new WekaBpnnTrainingService();

    /** volatile 讓 Swing 執行緒能看到背景 SwingWorker 完成後寫入的最新物件。 */
    private volatile TrainingConfiguration configuration;
    private volatile TrainingResult trainingResult;
    private volatile WekaBpnnTrainingService.Predictor predictor;
    /** 最近一次「只生成 CSV」後重新讀回的案例。 */
    private volatile List<tw.edu.nkust.surgerytime.model.SyntheticSurgeryCase> generatedCases = List.of();
    /** 用來判斷畫面上的資料參數是否與已生成 CSV 相符。 */
    private volatile int generatedSampleCount;
    private volatile long generatedRandomSeed;
    private volatile boolean importedData;
    private volatile Path trainingDataPath;
    /** 特徵選取頁決定哪些欄位真正送入 BPNN。 */
    private volatile List<String> selectedFeatureGroups = ALL_FEATURE_GROUPS;
    /** 資料拆分頁保存的方法設定初始值。 */
    private volatile double pendingTrainingRatio = TrainingConfiguration.DEFAULT.trainingRatio();
    private volatile long pendingRandomSeed = TrainingConfiguration.DEFAULT.randomSeed();
    /** 顯示目前模型是剛訓練完成，或是從哪一個 .model 檔載入。 */
    private volatile List<String> availableFeatureGroups = ALL_FEATURE_GROUPS;
    private volatile String preparedTarget = "手術時間";
    private final DataProcessingService processingService = new DataProcessingService();
    private record ProcessingState(long revision, ProcessingTable original, DataProcessingService.Result current,
                                   List<ProcessingStep> steps) { }
    private volatile ProcessingState processingState;
    private long revisionCounter;
    private record StoredRun(TrainingRun info, WekaBpnnTrainingService.Predictor predictor) { }
    private volatile List<StoredRun> history = List.of();
    private volatile long activeRunId;
    private long nextRunId;
    private volatile String modelSource = "本次程式訓練";

    /** 使用預設資料位置建立服務，適合正式程式啟動。 */
    public SurgeryModelService() {
        this(new SyntheticSurgeryDataService());
    }

    /** 注入資料服務，讓單元測試可以把 CSV 放在暫存資料夾。 */
    public SurgeryModelService(SyntheticSurgeryDataService dataService) {
        this.dataService = Objects.requireNonNull(dataService, "虛擬資料服務不可為空");
        this.trainingDataPath = dataService.outputPath();
    }

    /**
     * 完整重新訓練並以原子方式替換目前模型。
     * synchronized 防止使用者連按兩次造成兩次訓練互相覆蓋。
     */
    public synchronized TrainingResult train(TrainingConfiguration newConfiguration) {
        Objects.requireNonNull(newConfiguration, "訓練設定不可為空");
        generateData(newConfiguration);
        return trainGeneratedData(newConfiguration);
    }

    /**
     * 第一階段：只產生 CSV 並重新讀回驗證，不訓練也不替換現有模型。
     */
    public synchronized int generateData(TrainingConfiguration newConfiguration) {
        Objects.requireNonNull(newConfiguration, "資料生成設定不可為空");
        var cases = dataService.generateSaveAndReload(newConfiguration);
        generatedCases = List.copyOf(cases);
        generatedSampleCount = newConfiguration.sampleCount();
        generatedRandomSeed = newConfiguration.randomSeed();
        importedData = false;
        availableFeatureGroups = ALL_FEATURE_GROUPS;
        selectedFeatureGroups = ALL_FEATURE_GROUPS;
        preparedTarget = "手術時間";
        trainingDataPath = dataService.outputPath();
        resetProcessing(ProcessingTable.fromCases(generatedCases, availableFeatureGroups, preparedTarget, List.of(), java.util.Map.of()));
        return generatedCases.size();
    }

    /**
     * 第一階段的另一個入口：匯入使用者準備的同格式病例 CSV，仍不會自動訓練。
     */
    public synchronized int importTrainingData(Path sourcePath) {
        Path normalized = Objects.requireNonNull(sourcePath, "匯入 CSV 路徑不可為空")
                .toAbsolutePath().normalize();
        var cases = dataService.importCsv(normalized);
        if (cases.size() < 200 || cases.size() > 100_000) {
            throw new IllegalArgumentException("匯入病例必須介於 200 到 100,000 筆");
        }
        generatedCases = List.copyOf(cases);
        generatedSampleCount = cases.size();
        generatedRandomSeed = 0L;
        importedData = true;
        trainingDataPath = normalized;
        availableFeatureGroups = ALL_FEATURE_GROUPS; selectedFeatureGroups = ALL_FEATURE_GROUPS; preparedTarget = "手術時間";
        resetProcessing(ProcessingTable.fromCases(generatedCases, availableFeatureGroups, preparedTarget, List.of(), java.util.Map.of()));
        return cases.size();
    }

    /** 匯入並合併多份月報表，輸出位置固定在 generated-data，方便再次檢視。 */
    public synchronized int importTrainingData(List<Path> sourcePaths) {
        Path mergedPath = Path.of("generated-data", "merged_surgery_cases.csv")
                .toAbsolutePath().normalize();
        var cases = dataService.mergeCsvFiles(sourcePaths, mergedPath);
        installPreparedCases(cases, mergedPath, true);
        availableFeatureGroups = ALL_FEATURE_GROUPS; selectedFeatureGroups = ALL_FEATURE_GROUPS; preparedTarget = "手術時間";
        resetProcessing(ProcessingTable.fromCases(generatedCases, availableFeatureGroups, preparedTarget, List.of(), java.util.Map.of()));
        return cases.size();
    }

    /** 共用安裝資料狀態，避免單檔匯入、多檔合併與清理流程各自漏掉欄位。 */
    private void installPreparedCases(List<SyntheticSurgeryCase> cases, Path path, boolean imported) {
        if (cases.size() < 200 || cases.size() > 100_000) {
            throw new IllegalArgumentException("病例必須介於 200 到 100,000 筆");
        }
        generatedCases = List.copyOf(cases);
        generatedSampleCount = cases.size();
        generatedRandomSeed = imported ? 0L : pendingRandomSeed;
        importedData = imported;
        trainingDataPath = path.toAbsolutePath().normalize();
    }

    /**
     * 資料處理頁的實際清理動作：依手術時間 IQR 規則移除極端值並輸出新 CSV。
     * 若未勾選移除，仍會輸出一份標準欄位順序的 processed CSV。
     */
    public synchronized int processPreparedData(boolean removeTimeOutliers) {
        List<ProcessingStep> steps = removeTimeOutliers
                ? List.of(new ProcessingStep(ProcessingStep.Operation.IQR, "actual_minutes", "1.5", "")) : List.of();
        return applyProcessing(previewProcessing(new DataProcessingService.Recipe(preparedTarget, steps)));
    }

    /**
     * 第二階段：使用已生成並驗證的 CSV 資料訓練，不會暗中重新生成另一份資料。
     */
    public synchronized TrainingResult trainGeneratedData(TrainingConfiguration newConfiguration) {
        return trainGeneratedData(newConfiguration, AlgorithmConfiguration.DEFAULT);
    }
    public synchronized TrainingResult trainGeneratedData(TrainingConfiguration newConfiguration, AlgorithmConfiguration method) {
        Objects.requireNonNull(newConfiguration, "訓練設定不可為空");
        if (!generatedDataMatches(newConfiguration.sampleCount(), newConfiguration.randomSeed())) {
            throw new IllegalStateException("請先使用目前的資料筆數與亂數種子生成虛擬病例 CSV");
        }
        String dataVersion = importedData
                ? "匯入 CSV · " + trainingDataPath.getFileName()
                : "SYNTHETIC-v2 · 生成 seed " + generatedRandomSeed;
        var output = trainingService.train(
                generatedCases, newConfiguration, dataVersion, selectedFeatureGroups, preparedTarget, method, processingCurrent().table(), processingSteps());
        // 只有所有步驟成功後才一起替換；若中途失敗，舊模型仍然可用。
        configuration = newConfiguration;
        trainingResult = output.result();
        predictor = output.predictor();
        var run = new TrainingRun(++nextRunId, java.time.Instant.now(), comparisonGroup(newConfiguration),
                preparedTarget, selectedFeatureGroups, newConfiguration, method, trainingResult);
        var updated = new java.util.ArrayList<>(history);
        updated.add(new StoredRun(run, predictor)); history = List.copyOf(updated); activeRunId = run.id();
        modelSource = "本次訓練 #" + run.id();
        return trainingResult;
    }

    /** @return 是否已有可供檢視與訓練的 CSV 案例 */
    public boolean hasGeneratedData() {
        return !generatedCases.isEmpty();
    }

    /**
     * 判斷目前資料筆數是否可供這次訓練使用。
     *
     * <p>第二個參數保留給既有呼叫介面，但不再用來判定資料失效：資料生成種子與
     * 訓練／測試拆分種子是兩件事，使用者在資料拆分頁改種子時不應被迫重建 CSV。</p>
     */
    public boolean generatedDataMatches(int sampleCount, long randomSeed) {
        return hasGeneratedData() && generatedSampleCount == sampleCount;
    }

    /** @return 最近一次成功生成的 CSV 案例數 */
    public int generatedDataCount() {
        return generatedCases.size();
    }

    /** @return 目前已轉成標準格式、準備供統計與訓練使用的病例唯讀副本 */
    public List<SyntheticSurgeryCase> preparedCases() {
        return List.copyOf(generatedCases);
    }

    /** 儲存特徵選取結果；至少保留一項，且不能傳入系統不認得的名稱。 */
    public synchronized void setSelectedFeatureGroups(List<String> groups) {
        Objects.requireNonNull(groups, "特徵清單不可為空");
        List<String> normalized = availableFeatureGroups.stream().filter(groups::contains).toList();
        if (!availableFeatureGroups.containsAll(normalized)) throw new IllegalArgumentException("選取的欄位未提供資料");
        if (normalized.isEmpty()) throw new IllegalArgumentException("至少選擇一項特徵");
        if (normalized.size() != groups.stream().distinct().count()) {
            throw new IllegalArgumentException("特徵清單包含重複或不支援的項目");
        }
        selectedFeatureGroups = normalized;
    }

    public List<String> selectedFeatureGroups() {
        return List.copyOf(selectedFeatureGroups);
    }

    /** 保存資料拆分設定，方法設定頁會同步顯示相同數值。 */
    public synchronized void configureSplit(double trainingRatio, long randomSeed) {
        // 借用 TrainingConfiguration 的完整驗證規則，其他超參數使用既有預設值。
        new TrainingConfiguration(Math.max(200, generatedDataCount()), trainingRatio, randomSeed,
                TrainingConfiguration.DEFAULT.hiddenNeuronCount(), TrainingConfiguration.DEFAULT.epochs(),
                TrainingConfiguration.DEFAULT.momentumAlpha(), TrainingConfiguration.DEFAULT.learningRate(),
                TrainingConfiguration.DEFAULT.predictionIntervalTarget());
        pendingTrainingRatio = trainingRatio;
        pendingRandomSeed = randomSeed;
    }

    public double pendingTrainingRatio() {
        return pendingTrainingRatio;
    }

    public long pendingRandomSeed() {
        return pendingRandomSeed;
    }

    /** @return 目前待訓練病例是否來自使用者匯入 */
    public boolean isImportedData() {
        return importedData;
    }

    /** 使用最近一次成功訓練的模型進行預測。 */
    public PredictionResult predict(PredictionRequest request) {
        var current = predictor;
        if (current == null) {
            throw new IllegalStateException("尚未完成模型訓練，請先到方法設定頁執行訓練");
        }
        return current.predict(Objects.requireNonNull(request, "預測輸入不可為空"));
    }

    /**
     * 將最近一次成功訓練的真正 WEKA BPNN 寫入使用者指定的模型檔。
     * 檔案包含神經網路、欄位結構、編碼規則、預測區間與超參數。
     */
    public synchronized void saveModel(Path outputPath) {
        var current = predictor;
        if (current == null) {
            throw new IllegalStateException("尚未完成模型訓練，沒有可儲存的 BPNN");
        }
        trainingService.saveModel(
                current,
                trainingResult(),
                Objects.requireNonNull(outputPath, "模型檔路徑不可為空")
        );
    }

    /**
     * 載入由本應用程式儲存的 WEKA BPNN，成功驗證後才一次替換目前模型與結果。
     * synchronized 防止載入與重新訓練同時改寫共用狀態。
     */
    public synchronized TrainingResult loadModel(Path modelPath) {
        Path selectedPath = Objects.requireNonNull(modelPath, "模型檔路徑不可為空")
                .toAbsolutePath()
                .normalize();
        var output = trainingService.loadModel(selectedPath);
        // 先完成讀取和格式驗證，再一起替換，失敗時仍保留原本可用模型。
        predictor = output.predictor();
        configuration = output.predictor().configuration();
        trainingResult = output.result();
        activeRunId = 0;
        modelSource = selectedPath.getFileName().toString();
        return trainingResult;
    }

    public synchronized void trainAllAlgorithms(TrainingConfiguration config, AlgorithmConfiguration settings, java.util.function.Consumer<String> progress) {
        for (var algorithm : AlgorithmConfiguration.Algorithm.values()) {
            progress.accept("正在訓練：" + algorithm);
            trainGeneratedData(config, new AlgorithmConfiguration(algorithm,settings.treeCount(),settings.maxDepth(),settings.ridge(),settings.cost(),settings.gamma()));
        }
    }
    public List<TrainingRun> trainingHistory() { return history.stream().map(StoredRun::info).toList(); }
    public long activeRunId() { return activeRunId; }
    public synchronized void activateRun(long id) {
        var run = storedRun(id);
        predictor = run.predictor(); trainingResult = run.info().result(); configuration = run.info().configuration();
        activeRunId = id; modelSource = "歷史訓練 #" + id;
    }
    public synchronized void saveRunModel(long id, Path path) {
        var run = storedRun(id); trainingService.saveModel(run.predictor(), run.info().result(), path);
    }
    private StoredRun storedRun(long id) {
        return history.stream().filter(r -> r.info().id() == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("找不到訓練紀錄 #" + id));
    }
    private String comparisonGroup(TrainingConfiguration config) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            java.util.function.Consumer<String> add = value -> {
                byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
            };
            add.accept("comparison-v1"); add.accept(preparedTarget);
            selectedFeatureGroups.forEach(add);
            processingSteps().forEach(step->add.accept(step.toString()));
            for(var row:processingCurrent().table().rows()) new java.util.TreeMap<>(row.values()).forEach((key,value)->{add.accept(key);add.accept(value);});
            add.accept(Double.toString(config.trainingRatio())); add.accept(Long.toString(config.randomSeed()));
            generatedCases.forEach(c -> add.accept(c.toString()));
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    /** @return 最近一次成功訓練結果 */
    public TrainingResult trainingResult() {
        if (trainingResult == null) {
            throw new IllegalStateException("尚未完成模型訓練");
        }
        return trainingResult;
    }

    /** @return 最近一次成功使用的訓練設定 */
    public AlgorithmConfiguration algorithmConfiguration() { return predictor == null ? AlgorithmConfiguration.DEFAULT : predictor.method(); }

    public TrainingConfiguration configuration() {
        if (configuration == null) {
            throw new IllegalStateException("尚未完成模型訓練");
        }
        return configuration;
    }

    /** @return 是否至少有一個可用模型 */
    public boolean isTrained() {
        return predictor != null;
    }

    /** @return 畫面用來辨識目前模型來源的文字，不包含完整磁碟路徑 */
    public String modelSource() {
        return modelSource;
    }

    /** @return 虛擬資料 CSV 的完整路徑 */
    public Path generatedCsvPath() {
        return trainingDataPath;
    }

    /** @return 預測表單可選的手術名稱 */
    public List<String> supportedProcedures() {
        return predictionOptions("手術名稱", dataService.supportedProcedures());
    }

    /** @return 預測表單可選的手術室 */
    public List<String> supportedOperatingRooms() {
        return predictionOptions("手術室別", dataService.supportedOperatingRooms());
    }

    /** @return 預測表單可選的虛擬醫師 */
    public List<String> supportedSurgeons() {
        return predictionOptions("虛擬醫師", dataService.supportedSurgeons());
    }

    /** @return 最近模型依使用者指定百分位算出的區間半寬，單位為分鐘 */
    public int predictionIntervalMargin() {
        var current = predictor;
        if (current == null) {
            throw new IllegalStateException("尚未完成模型訓練");
        }
        return current.intervalMargin();
    }
    public List<String> availableFeatureGroups() { return availableFeatureGroups; }
    public String preparedTarget() { return preparedTarget; }
    public String predictionTarget() { return predictor == null ? "手術時間" : predictor.target(); }
    public List<String> predictionFeatures() { return predictor == null ? ALL_FEATURE_GROUPS : predictor.features(); }
    public List<String> predictionOptions(String group, List<String> fallback) {
        return predictor == null ? fallback : predictor.options(group);
    }
    /** 只套用完整通過的快照；驗證或寫檔失敗不取代現有資料及模型。 */
    public synchronized int applyImport(MappedCsvImportService.Report report) {
        if (!report.valid()) throw new IllegalArgumentException("請先修正匯入檢查報告中的問題");
        Path directory = dataService.outputPath().toAbsolutePath().normalize().getParent();
        try {
            java.nio.file.Files.createDirectories(directory);
            Path output = java.nio.file.Files.createTempFile(directory, "imported-", ".csv");
            ProcessingTable table = ProcessingTable.fromCases(report.cases(), report.features(), report.target(),
                    report.auxiliaryColumns(), report.auxiliaryByCase());
            if(eligibleFeatures(table).isEmpty())throw new IllegalArgumentException("沒有可用輸入欄位；識別碼與時間欄不可作特徵");
            processingService.writeCsv(table, output);
            installPreparedCases(report.cases(), output, true);
            availableFeatureGroups = eligibleFeatures(table);
            selectedFeatureGroups = report.features().isEmpty()?availableFeatureGroups:report.features();
            preparedTarget = report.target();
            resetProcessing(table);
            return report.cases().size();
        } catch (java.io.IOException e) { throw new IllegalStateException("無法建立匯入檔案", e); }
    }

    private void resetProcessing(ProcessingTable source) {
        processingState = new ProcessingState(++revisionCounter, source, new DataProcessingService.Result(source, List.of()), List.of());
    }
    public long processingRevision() { var state = processingState; return state == null ? 0 : state.revision(); }
    public ProcessingTable processingOriginal() {
        var state = processingState;
        return state == null ? new ProcessingTable(List.of(), List.of(), preparedTarget) : state.original();
    }
    public DataProcessingService.Result processingCurrent() {
        var state = processingState;
        return state == null ? new DataProcessingService.Result(processingOriginal(), List.of()) : state.current();
    }
    public List<ProcessingStep> processingSteps() { var state = processingState; return state == null ? List.of() : state.steps(); }
    public static final class ProcessingPreview {
        private final SurgeryModelService owner;
        private final long revision;
        private final DataProcessingService.Recipe recipe;
        private final DataProcessingService.Result result;
        private ProcessingPreview(SurgeryModelService owner, long revision, DataProcessingService.Recipe recipe, DataProcessingService.Result result) {
            this.owner = owner; this.revision = revision; this.recipe = recipe; this.result = result;
        }
        public DataProcessingService.Result result() { return result; }
        public long revision() { return revision; }
        public DataProcessingService.Recipe recipe() { return recipe; }
    }
    /** 預覽從最近匯入／生成的快照重做全部步驟，不在目前結果上反覆移除。 */
    public ProcessingPreview previewProcessing(DataProcessingService.Recipe recipe) {
        ProcessingState state = processingState;
        if (state == null) throw new IllegalStateException("請先匯入或生成資料");
        return new ProcessingPreview(this, state.revision(), recipe, processingService.process(state.original(), recipe));
    }
    /** 只套用此服務針對目前版本建立的預覽，寫檔成功後才替換待訓練資料。 */
    public synchronized int applyProcessing(ProcessingPreview preview) {
        Objects.requireNonNull(preview);
        if (preview.owner != this || processingState == null || preview.revision != processingState.revision())
            throw new IllegalStateException("來源或已套用資料已改變，請重新預覽");
        if (!preview.result.trainable()) throw new IllegalArgumentException("處理後須保留 200–100,000 筆及至少一個輸入特徵才能套用訓練");
        var applied=preview.result;
        if(preview.recipe.steps().stream().anyMatch(st->st.operation()==ProcessingStep.Operation.IQR)) {
            var baseRecipe=new DataProcessingService.Recipe(preview.recipe.target(),preview.recipe.steps().stream().filter(st->st.operation()!=ProcessingStep.Operation.IQR).toList());
            applied=processingService.process(processingState.original(),baseRecipe);
            for(var st:preview.recipe.steps())if(st.operation()==ProcessingStep.Operation.IQR)applied.table().column(st.column());
        }
        Path output = null; Path recipeFile = null;
        try {
            Path directory = dataService.outputPath().toAbsolutePath().normalize().getParent();
            java.nio.file.Files.createDirectories(directory);
            output = java.nio.file.Files.createTempFile(directory, "processed-", ".csv");
            recipeFile = output.resolveSibling(output.getFileName().toString().replace(".csv", ".steps"));
            processingService.writeCsv(applied.table(), output);
            processingService.saveRecipe(preview.recipe, recipeFile);
            var features = eligibleFeatures(applied.table());
            var selected = selectedFeatureGroups.stream().filter(features::contains).toList();
            installPreparedCases(applied.table().cases(), output, importedData);
            availableFeatureGroups = features;
            selectedFeatureGroups = selected.isEmpty() ? features : selected;
            processingState = new ProcessingState(++revisionCounter, processingState.original(), applied, preview.recipe.steps());
            return generatedCases.size();
        } catch (java.io.IOException e) {
            try { if (output != null) java.nio.file.Files.deleteIfExists(output); if (recipeFile != null) java.nio.file.Files.deleteIfExists(recipeFile); }
            catch (java.io.IOException cleanup) { e.addSuppressed(cleanup); }
            throw new IllegalStateException("處理結果寫入失敗；原資料與模型仍保留", e);
        }
    }

    public static List<String> eligibleFeatures(ProcessingTable table) {
        var groups=new java.util.ArrayList<>(table.features());
        for(var col:table.columns()) if(col.key().startsWith("extra:")) {
            if(!eligibleExtraName(col.key().substring(6)))continue;
            groups.add(col.key());
        }
        return List.copyOf(groups);
    }
    public static boolean eligibleExtraName(String name) {
        String label=name.toLowerCase(java.util.Locale.ROOT);
        return !label.matches(".*(識別|編號|病歷|身分|身份|目標|target|actual|minutes|時間|duration|開始|結束|起訖|start|end|identifier|case.?number).*" )
                && !label.matches("(^|.*[_ -])(id|uuid|key)($|[_ -].*)");
    }
    public FittedTransforms predictionTransforms() { return predictor==null?null:predictor.transforms(); }
    public PredictionResult predict(java.util.Map<String,String> values) {
        var current=predictor;
        if(current==null) throw new IllegalStateException("請先訓練或載入模型");
        return current.predict(values);
    }

    public record ModelDataPreview(List<String> columns,List<List<Object>> training,List<List<Object>> test,String settings) { }
    public ModelDataPreview modelDataPreview(double ratio,long seed) {
        return trainingService.preview(processingCurrent().table(),processingSteps(),selectedFeatureGroups,ratio,seed);
    }
    public record FeatureScore(String feature,String method,double score,int count) { }
    public List<FeatureScore> scoreFeatures() {
        var table=processingCurrent().table();
        var rows=TrainingPartition.split(table,processingSteps(),pendingTrainingRatio(),pendingRandomSeed()).training();
        var scores=new java.util.ArrayList<FeatureScore>();
        for(String name:availableFeatureGroups) {
            var col=table.columns().stream().filter(c->name.equals(c.feature())||name.equals(c.key())).findFirst().orElseThrow();
            var present=rows.stream().filter(r->!r.values().getOrDefault(col.key(),"").isBlank()).toList();
            double yMean=present.stream().mapToDouble(r->r.caseData().actualMinutes()).average().orElse(0),total=0;
            for(var r:present) total+=Math.pow(r.caseData().actualMinutes()-yMean,2);
            boolean numeric=present.stream().allMatch(r->{try{return Double.isFinite(Double.parseDouble(r.values().get(col.key())));}catch(Exception e){return false;}});
            double score=Double.NaN;
            if(!present.isEmpty()&&total>0) {
                if(numeric) {
                    double xMean=present.stream().mapToDouble(r->Double.parseDouble(r.values().get(col.key()))).average().orElse(0),xx=0,xy=0;
                    for(var r:present){double x=Double.parseDouble(r.values().get(col.key()))-xMean;xx+=x*x;xy+=x*(r.caseData().actualMinutes()-yMean);}
                    score=xx==0?0:xy*xy/(xx*total);
                } else {
                    var groups=present.stream().collect(java.util.stream.Collectors.groupingBy(r->r.values().get(col.key())));double between=0;
                    for(var group:groups.values()){double mean=group.stream().mapToDouble(r->r.caseData().actualMinutes()).average().orElse(0);between+=group.size()*Math.pow(mean-yMean,2);}
                    score=between/total;
                }
            }
            scores.add(new FeatureScore(name,numeric?"Pearson r²":"相關比 η²",score,present.size()));
        }
        return List.copyOf(scores);
    }

}
