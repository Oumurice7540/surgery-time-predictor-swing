package tw.edu.nkust.surgerytime.service;

import tw.edu.nkust.surgerytime.model.EpochRmsePoint;
import tw.edu.nkust.surgerytime.model.FeatureImportance;
import tw.edu.nkust.surgerytime.model.PredictionComparison;
import tw.edu.nkust.surgerytime.model.PredictionRequest;
import tw.edu.nkust.surgerytime.model.PredictionResult;
import tw.edu.nkust.surgerytime.model.SyntheticSurgeryCase;
import tw.edu.nkust.surgerytime.model.TrainingConfiguration;
import tw.edu.nkust.surgerytime.model.TrainingResult;
import tw.edu.nkust.surgerytime.model.TrainingSummary;
import weka.classifiers.functions.MultilayerPerceptron;
import weka.classifiers.Classifier;
import weka.classifiers.trees.RandomForest;
import weka.classifiers.functions.LinearRegression;
import weka.classifiers.functions.SMOreg;
import weka.classifiers.functions.supportVector.RBFKernel;
import tw.edu.nkust.surgerytime.model.AlgorithmConfiguration;
import tw.edu.nkust.surgerytime.model.AlgorithmConfiguration.Algorithm;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Utils;
import weka.core.SerializationHelper;

import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Map;
import tw.edu.nkust.surgerytime.model.ProcessingTable;
import tw.edu.nkust.surgerytime.model.ProcessingStep;
import tw.edu.nkust.surgerytime.model.EvaluationMetrics;

/**
 * 共用 WEKA 迴歸訓練流程；保留類別名稱以相容既有序列化模型。
 *
 * <p>這個類別負責資料切分、one-hot 編碼、WEKA Instances 建立、網路訓練、
 * 測試指標、預測區間及 permutation importance。Swing 畫面完全不碰 WEKA API。</p>
 */
final class WekaBpnnTrainingService {
    /** 十個原始輸入群組，也是 permutation importance 的分組。 */
    private static final List<String> GROUP_NAMES =
            List.of("病患年齡", "生理性別", "手術名稱", "虛擬醫師", "手術室別",
                    "緊急程度", "ASA 分級", "麻醉方式", "共病數量", "手術複雜度");

    /** 同時回傳畫面資料與能預測新案例的已訓練網路。 */
    record TrainingOutput(TrainingResult result, Predictor predictor) {
        TrainingOutput {
            Objects.requireNonNull(result, "訓練結果不可為空");
            Objects.requireNonNull(predictor, "預測器不可為空");
        }
    }

    /**
     * 將已訓練模型寫成 WEKA 模型檔。
     *
     * <p>檔案中的第一個物件是 WEKA MultilayerPerceptron，第二個物件是訓練欄位
     * 結構，這符合 WEKA 常見的 classifier＋header 儲存方式。第三個物件再保存本
     * 系統需要的 one-hot 字典、區間校準與超參數，未來重新載入時不必猜測前處理。</p>
     */
    void saveModel(Predictor predictor, TrainingResult trainingResult, Path requestedPath) {
        Objects.requireNonNull(predictor, "要儲存的 BPNN 不可為空");
        Objects.requireNonNull(trainingResult, "要儲存的訓練結果不可為空");
        Objects.requireNonNull(requestedPath, "模型檔路徑不可為空");
        Path path = requestedPath.toAbsolutePath().normalize();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = Files.createTempFile(parent, "model-", ".tmp");
            try {
                predictor.save(temporary, trainingResult);
                try { Files.move(temporary, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
                catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
        } catch (Exception exception) {
            throw new IllegalStateException("無法儲存 WEKA 模型：" + path, exception);
        }
    }

    /**
     * 讀取由本系統儲存的 .model，並逐項確認內容不是其他 WEKA 演算法或不完整檔案。
     */
    TrainingOutput loadModel(Path requestedPath) {
        Objects.requireNonNull(requestedPath, "模型檔路徑不可為空");
        Path path = requestedPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("找不到模型檔：" + path);
        }
        try {
            Object[] saved;
            try(var input=Files.newInputStream(path)) {saved=SerializationHelper.readAll(input);}
            if (saved.length != 3) {
                throw new IllegalArgumentException("模型檔內容不完整，必須由本系統的儲存功能建立");
            }
            if (!(saved[0] instanceof Classifier network)) {
                throw new IllegalArgumentException("選取的檔案不是 WEKA 模型");
            }
            if (!(saved[1] instanceof Instances header) || header.classIndex() < 0) {
                throw new IllegalArgumentException("模型檔缺少有效的 WEKA 欄位結構");
            }
            if (!(saved[2] instanceof SavedModelMetadata metadata)) {
                throw new IllegalArgumentException("模型檔缺少本系統的編碼與評估資訊");
            }
            if (metadata.encoder() == null
                    || metadata.configuration() == null
                    || metadata.trainingResult() == null) {
                throw new IllegalArgumentException("模型檔缺少完整訓練結果，請使用新版程式重新訓練並儲存");
            }
            if (header.numAttributes() != metadata.encoder().dimension() + 1) {
                throw new IllegalArgumentException("模型欄位與 one-hot 編碼規則不一致");
            }
            if (header.classIndex() != header.numAttributes() - 1 || !header.classAttribute().isNumeric()
                    || !header.classAttribute().name().equals("actual_minutes")) throw new IllegalArgumentException("模型目標欄位不一致");
            for (int i = 0; i < metadata.encoder().dimension(); i++) {
                if (!header.attribute(i).isNumeric() || !header.attribute(i).name().equals(metadata.encoder().encodedFeatureNames().get(i)))
                    throw new IllegalArgumentException("模型輸入欄位順序或名稱不一致");
            }
            if (metadata.intervalMargin() < 1 || !Double.isFinite(metadata.intervalCoverage())
                    || metadata.intervalCoverage() < 0 || metadata.intervalCoverage() > 1)
                throw new IllegalArgumentException("模型預測區間資訊無效");
            AlgorithmConfiguration method = metadata.method();
            if (method == null) {
                if (!(network instanceof MultilayerPerceptron)) throw new IllegalArgumentException("模型缺少演算法設定");
                method = AlgorithmConfiguration.DEFAULT;
            }
            if (!matches(network, method.algorithm())) throw new IllegalArgumentException("模型演算法與儲存設定不一致");
            var predictor = new Predictor(
                    network,
                    header,
                    metadata.encoder(),
                    metadata.intervalMargin(),
                    metadata.intervalCoverage(),
                    metadata.configuration(),
                    metadata.target(), method
            );
            return new TrainingOutput(metadata.trainingResult(), predictor);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("無法載入 WEKA 模型：" + path, exception);
        }
    }

    /**
     * 執行完整 BPNN 訓練與評估。
     *
     * @param sourceCases 從生成 CSV 重新讀回的全部虛擬案例
     * @param configuration 方法設定頁提供的資料與 BPNN 超參數
     */
    TrainingOutput train(
            List<SyntheticSurgeryCase> sourceCases,
            TrainingConfiguration configuration,
            String dataVersion,
            List<String> selectedGroups,
            String target,
            AlgorithmConfiguration method
    ) {
        return train(sourceCases, configuration, dataVersion, selectedGroups, target, method, null, List.of());
    }

    TrainingOutput train(List<SyntheticSurgeryCase> sourceCases, TrainingConfiguration configuration,
            String dataVersion, List<String> selectedGroups, String target, AlgorithmConfiguration method,
            ProcessingTable table, List<ProcessingStep> steps) {
        Objects.requireNonNull(sourceCases, "虛擬案例不可為空");
        Objects.requireNonNull(configuration, "訓練設定不可為空");
        Objects.requireNonNull(dataVersion, "資料來源說明不可為空");
        Objects.requireNonNull(selectedGroups, "選取特徵不可為空");
        if (sourceCases.size() != configuration.sampleCount()) {
            throw new IllegalArgumentException("CSV 筆數與方法設定不一致");
        }

        long startNanos = System.nanoTime();
        ProcessingTable partitionTable=table==null?ProcessingTable.fromCases(sourceCases,selectedGroups,target,List.of(),Map.of()):table;
        var partition=TrainingPartition.split(partitionTable,steps,configuration.trainingRatio(),configuration.randomSeed());
        List<SyntheticSurgeryCase> trainingCases=partition.training().stream().map(ProcessingTable.Row::caseData).toList();
        List<SyntheticSurgeryCase> testCases=partition.test().stream().map(ProcessingTable.Row::caseData).toList();

        FeatureEncoder encoder = table == null || (steps.stream().noneMatch(FittedTransforms::transformation) && selectedGroups.stream().noneMatch(g->g.startsWith("extra:"))) ? FeatureEncoder.from(trainingCases, selectedGroups)
                : FeatureEncoder.fromTable(table, trainingCases, selectedGroups, steps);
        encoder.preparation=partition.audit();
        Instances trainingData = createDataset("synthetic_surgery_training", trainingCases, encoder);
        Instances testData = createDataset("synthetic_surgery_test", testCases, encoder);

        // Epoch 0 尚未學習任何權重，因此以訓練集平均分鐘作為基準預測。
        double trainingMean = trainingCases.stream()
                .mapToInt(SyntheticSurgeryCase::actualMinutes)
                .average()
                .orElseThrow();
        double[] actual = actualMinutes(testCases);
        double[] baselinePredictions = new double[testCases.size()];
        java.util.Arrays.fill(baselinePredictions, trainingMean);
        double baselineRmse = rmse(actual, baselinePredictions);

        Classifier network = configuredClassifier(configuration, Objects.requireNonNull(method));
        try {
            network.buildClassifier(trainingData);
        } catch (Exception exception) {
            throw new IllegalStateException("WEKA 模型訓練失敗", exception);
        }

        long evaluationStart = System.nanoTime();
        double[] trainingPredictions = predictDataset(network, trainingData);
        double[] trainingActual = actualMinutes(trainingCases);
        var trainingMetrics = new EvaluationMetrics(trainingCases.size(), mae(trainingActual,trainingPredictions),
                rmse(trainingActual,trainingPredictions),rSquared(trainingActual,trainingPredictions),
                (System.nanoTime()-evaluationStart)/1_000_000);
        evaluationStart = System.nanoTime();
        double[] predictions = predictDataset(network, testData);
        long testEvaluationMillis = (System.nanoTime()-evaluationStart)/1_000_000;
        double mae = mae(actual, predictions);
        double finalRmse = rmse(actual, predictions);
        double rSquared = rSquared(actual, predictions);
        int intervalMargin = residualPercentile(
                actual,
                predictions,
                configuration.predictionIntervalTarget()
        );
        double intervalCoverage = intervalCoverage(actual, predictions, intervalMargin);
        List<FeatureImportance> importances = permutationImportances(
                network,
                testCases,
                actual,
                finalRmse,
                encoder,
                configuration.randomSeed(),
                selectedGroups
        );
        // 保留完整測試集的每筆實際值與預測值，讓訓練結果頁可像參考原版逐列展示。
        List<PredictionComparison> comparisons = allPredictionComparisons(testCases, predictions);

        // WEKA 的 MultilayerPerceptron 不公開每個 Epoch 的 callback，因此只呈現訓練前與完成後。
        List<EpochRmsePoint> curve = List.of(
                new EpochRmsePoint(0, baselineRmse),
                new EpochRmsePoint(method.algorithm() == Algorithm.BPNN ? configuration.epochs() : 1, finalRmse)
        );
        long durationMillis = Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);
        var summary = new TrainingSummary(
                "WEKA " + method.algorithm().name(),
                method.algorithm() == Algorithm.BPNN ? "WEKA MultilayerPerceptron（反向傳播）" : "WEKA " + method.algorithm(),
                trainingCases.size(),
                testCases.size(),
                selectedGroups.size(),
                encoder.dimension(),
                method.algorithm() == Algorithm.BPNN ? configuration.epochs() : 1,
                mae,
                finalRmse,
                rSquared,
                durationMillis,
                dataVersion + "；" + partition.audit() + "；預測目標：" + target + "；" + method.description(configuration)
        );
        var result = new TrainingResult(summary, curve, importances, comparisons, trainingMetrics,
                new EvaluationMetrics(testCases.size(),mae,finalRmse,rSquared,testEvaluationMillis),allPredictionComparisons(trainingCases,trainingPredictions));
        var predictor = new Predictor(
                network,
                new Instances(trainingData, 0),
                encoder,
                intervalMargin,
                intervalCoverage,
                configuration,
                target, method
        );
        return new TrainingOutput(result, predictor);
    }

    SurgeryModelService.ModelDataPreview preview(ProcessingTable table,List<ProcessingStep> steps,List<String> selected,double ratio,long seed) {
        var partition=TrainingPartition.split(table,steps,ratio,seed);
        var training=partition.training().stream().map(ProcessingTable.Row::caseData).toList();
        var cases=new ArrayList<>(training);partition.test().forEach(r->cases.add(r.caseData()));int cut=training.size();
        if(training.isEmpty()) throw new IllegalArgumentException("請先準備資料");
        var encoder=steps.stream().noneMatch(FittedTransforms::transformation)&&selected.stream().noneMatch(g->g.startsWith("extra:"))
                ?FeatureEncoder.from(training,selected):FeatureEncoder.fromTable(table,training,selected,steps);
        var names=new ArrayList<String>();names.add("案例編號（不輸入模型）");names.addAll(encoder.encodedFeatureNames());names.add("目標分鐘");
        var rows=new ArrayList<List<Object>>();
        for(var item:cases){var row=new ArrayList<Object>();row.add(item.caseId());for(double v:encoder.encode(item))row.add(v);row.add(item.actualMinutes());rows.add(List.copyOf(row));}
        return new SurgeryModelService.ModelDataPreview(List.copyOf(names),List.copyOf(rows.subList(0,cut)),List.copyOf(rows.subList(cut,rows.size())),
                partition.audit()+(encoder.dynamic==null?"訓練集建立 one-hot 字典；未見類別為全零。":encoder.dynamic.description()));
    }

    private static double checkedPrediction(double value) {
        if (!Double.isFinite(value) || value > Integer.MAX_VALUE / 2.0) throw new IllegalStateException("模型回傳無效或超出範圍的時間");
        return Math.max(1.0, value);
    }
    private static boolean matches(Classifier classifier, Algorithm algorithm) {
        return switch(algorithm) {
            case BPNN -> classifier.getClass() == MultilayerPerceptron.class;
            case RANDOM_FOREST -> classifier.getClass() == RandomForest.class;
            case LINEAR_REGRESSION -> classifier.getClass() == LinearRegression.class;
            case SVR -> classifier.getClass() == SMOreg.class;
        };
    }
    private Classifier configuredClassifier(TrainingConfiguration training, AlgorithmConfiguration method) {
        return switch(method.algorithm()) {
            case BPNN -> configuredNetwork(training);
            case RANDOM_FOREST -> {
                var forest = new RandomForest(); forest.setNumIterations(method.treeCount());
                forest.setMaxDepth(method.maxDepth()); forest.setSeed((int)training.randomSeed());
                forest.setNumExecutionSlots(1); yield forest;
            }
            case LINEAR_REGRESSION -> {
                var linear = new LinearRegression(); linear.setRidge(method.ridge());
                linear.setAttributeSelectionMethod(new weka.core.SelectedTag(LinearRegression.SELECTION_NONE, LinearRegression.TAGS_SELECTION));
                yield linear;
            }
            case SVR -> {
                var svr = new SMOreg(); svr.setC(method.cost());
                var kernel = new RBFKernel(); kernel.setGamma(method.gamma()); svr.setKernel(kernel);
                svr.setFilterType(new weka.core.SelectedTag(SMOreg.FILTER_NORMALIZE, SMOreg.TAGS_FILTER)); yield svr;
            }
        };
    }

    /** 將畫面設定逐項套用到 WEKA 的 MultilayerPerceptron。 */
    private MultilayerPerceptron configuredNetwork(TrainingConfiguration configuration) {
        var network = new MultilayerPerceptron();
        network.setHiddenLayers(Integer.toString(configuration.hiddenNeuronCount()));
        network.setTrainingTime(configuration.epochs());
        network.setMomentum(configuration.momentumAlpha());
        network.setLearningRate(configuration.learningRate());
        network.setSeed((int) Math.floorMod(configuration.randomSeed(), Integer.MAX_VALUE));
        network.setGUI(false);
        network.setAutoBuild(true);
        network.setValidationSetSize(0);
        network.setNormalizeAttributes(true);
        network.setNormalizeNumericClass(true);
        network.setReset(true);
        network.setDecay(false);
        return network;
    }

    /** 建立 WEKA 表格結構，最後一欄 actual_minutes 被指定為要預測的 class。 */
    private Instances createDataset(
            String relationName,
            List<SyntheticSurgeryCase> cases,
            FeatureEncoder encoder
    ) {
        var attributes = new ArrayList<Attribute>();
        for (String featureName : encoder.encodedFeatureNames()) {
            attributes.add(new Attribute(featureName));
        }
        attributes.add(new Attribute("actual_minutes"));

        var dataset = new Instances(relationName, attributes, cases.size());
        dataset.setClassIndex(attributes.size() - 1);
        for (SyntheticSurgeryCase item : cases) {
            double[] encoded = encoder.encode(item);
            double[] values = new double[attributes.size()];
            System.arraycopy(encoded, 0, values, 0, encoded.length);
            values[dataset.classIndex()] = item.actualMinutes();
            dataset.add(new DenseInstance(1.0, values));
        }
        return dataset;
    }

    /** 對 WEKA 測試表格逐列呼叫 classifyInstance。 */
    private double[] predictDataset(Classifier network, Instances dataset) {
        double[] predictions = new double[dataset.numInstances()];
        try {
            for (int index = 0; index < dataset.numInstances(); index++) {
                predictions[index] = checkedPrediction(network.classifyInstance(dataset.instance(index)));
            }
            return predictions;
        } catch (Exception exception) {
            throw new IllegalStateException("WEKA 模型測試預測失敗", exception);
        }
    }

    /** 取出每筆案例的答案 y。 */
    private double[] actualMinutes(List<SyntheticSurgeryCase> cases) {
        double[] values = new double[cases.size()];
        for (int index = 0; index < cases.size(); index++) {
            values[index] = cases.get(index).actualMinutes();
        }
        return values;
    }

    /** MAE：所有絕對誤差的平均。 */
    private double mae(double[] actual, double[] predicted) {
        double total = 0.0;
        for (int index = 0; index < actual.length; index++) {
            total += Math.abs(actual[index] - predicted[index]);
        }
        return total / actual.length;
    }

    /** RMSE：平方誤差平均後開根號，會加重大誤差。 */
    private double rmse(double[] actual, double[] predicted) {
        double total = 0.0;
        for (int index = 0; index < actual.length; index++) {
            double error = actual[index] - predicted[index];
            total += error * error;
        }
        return Math.sqrt(total / actual.length);
    }

    /** R²：比較神經網路殘差與全部猜平均值的總變異。 */
    private double rSquared(double[] actual, double[] predicted) {
        double mean = 0.0;
        for (double value : actual) mean += value;
        mean /= actual.length;
        double residualSquares = 0.0;
        double totalSquares = 0.0;
        for (int index = 0; index < actual.length; index++) {
            double error = actual[index] - predicted[index];
            residualSquares += error * error;
            double deviation = actual[index] - mean;
            totalSquares += deviation * deviation;
        }
        return totalSquares == 0.0 ? 0.0 : 1.0 - residualSquares / totalSquares;
    }

    /** 取得測試集絕對殘差的指定百分位作為區間半寬。 */
    private int residualPercentile(double[] actual, double[] predicted, double percentile) {
        var residuals = new ArrayList<Double>(actual.length);
        for (int index = 0; index < actual.length; index++) {
            residuals.add(Math.abs(actual[index] - predicted[index]));
        }
        residuals.sort(Double::compareTo);
        int percentileIndex = Math.max(0, (int) Math.ceil(percentile * residuals.size()) - 1);
        return Math.max(1, (int) Math.ceil(residuals.get(percentileIndex)));
    }

    /** 計算所選區間在測試集真正涵蓋答案的比例。 */
    private double intervalCoverage(double[] actual, double[] predicted, int margin) {
        int covered = 0;
        for (int index = 0; index < actual.length; index++) {
            if (Math.abs(actual[index] - predicted[index]) <= margin) covered++;
        }
        return covered / (double) actual.length;
    }

    /** 建立完整測試集逐筆對照，不抽樣、不截斷，也不使用預先寫好的展示數字。 */
    private List<PredictionComparison> allPredictionComparisons(
            List<SyntheticSurgeryCase> testCases,
            double[] predictions
    ) {
        var comparisons = new ArrayList<PredictionComparison>(testCases.size());
        for (int index = 0; index < testCases.size(); index++) {
            comparisons.add(new PredictionComparison(
                    testCases.get(index).caseId(),
                    testCases.get(index).actualMinutes(),
                    Math.max(0.01, predictions[index])
            ));
        }
        return List.copyOf(comparisons);
    }

    /**
     * 計算 permutation importance：依序打亂一組特徵，觀察 RMSE 增加多少。
     * 增加越多代表網路越依賴該欄位；這仍是預測關係，不等於醫療因果關係。
     */
    private List<FeatureImportance> permutationImportances(
            Classifier network,
            List<SyntheticSurgeryCase> testCases,
            double[] actual,
            double baselineRmse,
            FeatureEncoder encoder,
            long randomSeed,
            List<String> selectedGroups
    ) {
        double[] increases = new double[selectedGroups.size()];
        for (int selectedIndex = 0; selectedIndex < selectedGroups.size(); selectedIndex++) {
            if (encoder.dynamic != null) {
                Instances original = createDataset("importance", testCases, encoder);
                Instances permuted = new Instances(original);
                var field = encoder.dynamic.fields().get(selectedIndex);
                var donors = new ArrayList<Integer>();
                for(int i=0;i<testCases.size();i++) donors.add(i);
                Collections.shuffle(donors,new Random(randomSeed+10_007L*(selectedIndex+1)));
                for(int i=0;i<testCases.size();i++) for(int j=0;j<field.width();j++)
                    permuted.instance(i).setValue(field.offset()+j,original.instance(donors.get(i)).value(field.offset()+j));
                increases[selectedIndex]=Math.max(0,rmse(actual,predictDataset(network,permuted))-baselineRmse);
                continue;
            }
            int group = GROUP_NAMES.indexOf(selectedGroups.get(selectedIndex));
            if (group < 0) throw new IllegalArgumentException("不支援的特徵群組：" + selectedGroups.get(selectedIndex));
            var donorIndexes = new ArrayList<Integer>();
            for (int index = 0; index < testCases.size(); index++) donorIndexes.add(index);
            Collections.shuffle(donorIndexes, new Random(randomSeed + 10_007L * (group + 1)));

            var permutedCases = new ArrayList<SyntheticSurgeryCase>(testCases.size());
            for (int index = 0; index < testCases.size(); index++) {
                permutedCases.add(withPermutedGroup(
                        testCases.get(index),
                        testCases.get(donorIndexes.get(index)),
                        group
                ));
            }
            Instances permutedData = createDataset("permuted_" + group, permutedCases, encoder);
            double permutedRmse = rmse(actual, predictDataset(network, permutedData));
            increases[selectedIndex] = Math.max(0.0, permutedRmse - baselineRmse);
        }

        double total = java.util.Arrays.stream(increases).sum();
        if (total <= 1e-12) {
            total = 1.0; // No measurable increase: preserve zero, do not invent equal importance.
        }
        var result = new ArrayList<FeatureImportance>();
        for (int index = 0; index < selectedGroups.size(); index++) {
            result.add(new FeatureImportance(selectedGroups.get(index), increases[index] / total));
        }
        result.sort(Comparator.comparingDouble(FeatureImportance::importance).reversed());
        return List.copyOf(result);
    }

    /** 建立一筆只替換指定欄位群組的案例，答案 actualMinutes 保持原案例值。 */
    private SyntheticSurgeryCase withPermutedGroup(
            SyntheticSurgeryCase original,
            SyntheticSurgeryCase donor,
            int group
    ) {
        return new SyntheticSurgeryCase(
                original.caseId(),
                group == 4 ? donor.operatingRoom() : original.operatingRoom(),
                group == 0 ? donor.age() : original.age(),
                group == 1 ? donor.sex() : original.sex(),
                group == 2 ? donor.procedure() : original.procedure(),
                group == 3 ? donor.surgeon() : original.surgeon(),
                group == 5 ? donor.urgency() : original.urgency(),
                group == 6 ? donor.asaClass() : original.asaClass(),
                group == 7 ? donor.anesthesia() : original.anesthesia(),
                group == 8 ? donor.comorbidityCount() : original.comorbidityCount(),
                group == 9 ? donor.complexity() : original.complexity(),
                original.actualMinutes()
        );
    }

    /** 訓練完成後保存 WEKA 網路、表格結構、編碼字典與區間校準資料。 */
    static final class Predictor {
        private final Classifier network;
        private final Instances header;
        private final FeatureEncoder encoder;
        private final int intervalMargin;
        private final double intervalCoverage;
        private final TrainingConfiguration configuration;
        private final String target;
        private final AlgorithmConfiguration method;

        Predictor(
                Classifier network,
                Instances header,
                FeatureEncoder encoder,
                int intervalMargin,
                double intervalCoverage,
                TrainingConfiguration configuration,
                String target, AlgorithmConfiguration method
        ) {
            this.network = network;
            this.method = method;
            this.header = header;
            this.encoder = encoder;
            this.intervalMargin = intervalMargin;
            this.intervalCoverage = intervalCoverage;
            this.configuration = configuration;
            this.target = target == null || target.isBlank() ? "手術時間" : target;
        }

        /** 將預測表單轉成和訓練資料完全相同的 WEKA Instance。 */
        synchronized PredictionResult predict(PredictionRequest request) {
            return predictEncoded(encoder.encode(request));
        }
        synchronized PredictionResult predict(Map<String,String> request) {
            if (encoder.dynamic == null) throw new IllegalArgumentException("舊版模型請使用原始欄位表單");
            return predictEncoded(encoder.dynamic.encode(encoder.dynamic.byFeature(request)));
        }
        FittedTransforms transforms() { return encoder.dynamic; }
        private PredictionResult predictEncoded(double[] encoded) {
            double[] values = new double[header.numAttributes()];
            System.arraycopy(encoded, 0, values, 0, encoded.length);
            values[header.classIndex()] = Utils.missingValue();
            Instance instance = new DenseInstance(1.0, values);
            instance.setDataset(header);
            try {
                int predicted = Math.max(1, (int) Math.round(checkedPrediction(network.classifyInstance(instance))));
                return new PredictionResult(
                        predicted,
                        Math.max(1, predicted - intervalMargin),
                        predicted + intervalMargin,
                        intervalCoverage,
                        "WEKA " + method.algorithm() + " 預測" + target + "；" + method.description(configuration) + "。區間使用測試殘差第 "
                                + Math.round(configuration.predictionIntervalTarget() * 100) + " 百分位。"
                );
            } catch (Exception exception) {
                throw new IllegalStateException("WEKA 模型無法完成預測", exception);
            }
        }

        /**
         * 依 WEKA 序列化格式依序寫入網路、欄位表頭與本系統附加資訊。
         * synchronized 確保儲存期間不會同時使用同一個網路進行預測。
         */
        synchronized void save(Path path, TrainingResult trainingResult) throws Exception {
            SerializationHelper.writeAll(path.toString(), new Object[]{
                    network,
                    header,
                    new SavedModelMetadata(
                            encoder,
                            intervalMargin,
                            intervalCoverage,
                            configuration,
                            trainingResult,
                            target, method
                    )
            });
        }

        AlgorithmConfiguration method() { return method; }
        String target() { return target; }
        List<String> features() { return encoder.selectedGroups; }
        List<String> options(String group) {
            if (!features().contains(group)) return List.of("未使用");
            if (encoder.dynamic != null) return encoder.dynamic.fields().stream().filter(f->f.name().equals(group))
                    .findFirst().map(FittedTransforms.Field::categories).orElse(List.of());
            return switch (group) {
                case "生理性別" -> encoder.sexes;
                case "手術名稱" -> encoder.procedures;
                case "虛擬醫師" -> encoder.surgeons;
                case "手術室別" -> encoder.rooms;
                case "緊急程度" -> encoder.urgencies;
                case "ASA 分級" -> encoder.asaClasses;
                case "麻醉方式" -> encoder.anesthesiaTypes;
                case "手術複雜度" -> encoder.complexities;
                default -> List.of("未使用");
            };
        }
        int intervalMargin() {
            return intervalMargin;
        }

        /** @return 儲存模型時一併保留的原始訓練設定 */
        TrainingConfiguration configuration() {
            return configuration;
        }
    }

    /**
     * WEKA 標準分類器以外，本應用程式重新建立完整預測流程所需的附加資料。
     * 實作 Serializable 才能與神經網路一起寫進二進位模型檔。
     */
    private record SavedModelMetadata(
            FeatureEncoder encoder,
            int intervalMargin,
            double intervalCoverage,
            TrainingConfiguration configuration,
            TrainingResult trainingResult,
            String target,
            AlgorithmConfiguration method
    ) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    /** 將十個原始欄位展開成數值與 one-hot 欄位。 */
    private static final class FeatureEncoder implements Serializable {
        private static final long serialVersionUID = 1L;
        private final List<String> sexes;
        private final List<String> procedures;
        private final List<String> surgeons;
        private final List<String> rooms;
        private final List<String> urgencies;
        private final List<String> asaClasses;
        private final List<String> anesthesiaTypes;
        private final List<String> complexities;
        private final List<String> selectedGroups;
        private final List<String> encodedFeatureNames;
        private FittedTransforms dynamic;
        private String preparation;
        private transient Map<String,Map<String,String>> rawRows;
        static FeatureEncoder fromTable(ProcessingTable table,List<SyntheticSurgeryCase> training,
                List<String> groups,List<ProcessingStep> steps) {
            var ids=training.stream().map(SyntheticSurgeryCase::caseId).collect(java.util.stream.Collectors.toSet());
            var fitted=FittedTransforms.fit(table,table.rows().stream().filter(r->ids.contains(r.caseData().caseId())).toList(),groups,steps);
            var encoder=new FeatureEncoder(List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),groups);
            encoder.dynamic=fitted;
            encoder.rawRows=table.rows().stream().collect(java.util.stream.Collectors.toMap(r->r.caseData().caseId(),ProcessingTable.Row::values));
            return encoder;
        }

        private FeatureEncoder(
                List<String> sexes,
                List<String> procedures,
                List<String> surgeons,
                List<String> rooms,
                List<String> urgencies,
                List<String> asaClasses,
                List<String> anesthesiaTypes,
                List<String> complexities,
                List<String> selectedGroups
        ) {
            this.sexes = sexes;
            this.procedures = procedures;
            this.surgeons = surgeons;
            this.rooms = rooms;
            this.urgencies = urgencies;
            this.asaClasses = asaClasses;
            this.anesthesiaTypes = anesthesiaTypes;
            this.complexities = complexities;
            this.selectedGroups = List.copyOf(selectedGroups);
            this.encodedFeatureNames = buildFeatureNames();
        }

        static FeatureEncoder from(List<SyntheticSurgeryCase> cases, List<String> selectedGroups) {
            return new FeatureEncoder(
                    distinctSorted(cases.stream().map(SyntheticSurgeryCase::sex).toList()),
                    distinctSorted(cases.stream().map(SyntheticSurgeryCase::procedure).toList()),
                    distinctSorted(cases.stream().map(SyntheticSurgeryCase::surgeon).toList()),
                    distinctSorted(cases.stream().map(SyntheticSurgeryCase::operatingRoom).toList()),
                    distinctSorted(cases.stream().map(SyntheticSurgeryCase::urgency).toList()),
                    distinctSorted(cases.stream().map(SyntheticSurgeryCase::asaClass).toList()),
                    distinctSorted(cases.stream().map(SyntheticSurgeryCase::anesthesia).toList()),
                    distinctSorted(cases.stream().map(SyntheticSurgeryCase::complexity).toList()),
                    selectedGroups
            );
        }

        private static List<String> distinctSorted(List<String> values) {
            return values.stream().distinct().sorted().toList();
        }

        int dimension() {
            return encodedFeatureNames().size();
        }

        List<String> encodedFeatureNames() {
            return dynamic == null ? encodedFeatureNames : dynamic.names();
        }

        double[] encode(SyntheticSurgeryCase item) {
            if(dynamic!=null) return dynamic.encode(rawRows.get(item.caseId()));
            return encode(item.age(), item.comorbidityCount(), item.sex(), item.procedure(), item.surgeon(),
                    item.operatingRoom(), item.urgency(), item.asaClass(), item.anesthesia(), item.complexity(), true);
        }

        double[] encode(PredictionRequest item) {
            if(dynamic!=null) return dynamic.encode(Map.of("age",Integer.toString(item.age()),"comorbidity_count",Integer.toString(item.comorbidityCount()),
                    "sex",item.sex(),"procedure",item.procedure(),"surgeon_code",item.surgeon(),"operating_room",item.operatingRoom(),
                    "urgency",item.urgency(),"asa_class",item.asaClass(),"anesthesia",item.anesthesia(),"complexity",item.complexity()));
            return encode(item.age(), item.comorbidityCount(), item.sex(), item.procedure(), item.surgeon(),
                    item.operatingRoom(), item.urgency(), item.asaClass(), item.anesthesia(), item.complexity(), false);
        }

        private double[] encode(
                int age,
                int comorbidityCount,
                String sex,
                String procedure,
                String surgeon,
                String room,
                String urgency,
                String asaClass,
                String anesthesia,
                String complexity, boolean allowUnknown
        ) {
            double[] values = new double[dimension()];
            int offset = 0;
            if (selectedGroups.contains("病患年齡")) values[offset++] = age;
            if (selectedGroups.contains("生理性別")) offset = oneHot(values, offset, sexes, sex, allowUnknown);
            if (selectedGroups.contains("手術名稱")) offset = oneHot(values, offset, procedures, procedure, allowUnknown);
            if (selectedGroups.contains("虛擬醫師")) offset = oneHot(values, offset, surgeons, surgeon, allowUnknown);
            if (selectedGroups.contains("手術室別")) offset = oneHot(values, offset, rooms, room, allowUnknown);
            if (selectedGroups.contains("緊急程度")) offset = oneHot(values, offset, urgencies, urgency, allowUnknown);
            if (selectedGroups.contains("ASA 分級")) offset = oneHot(values, offset, asaClasses, asaClass, allowUnknown);
            if (selectedGroups.contains("麻醉方式")) offset = oneHot(values, offset, anesthesiaTypes, anesthesia, allowUnknown);
            if (selectedGroups.contains("共病數量")) values[offset++] = comorbidityCount;
            if (selectedGroups.contains("手術複雜度")) oneHot(values, offset, complexities, complexity, allowUnknown);
            return values;
        }

        private int oneHot(double[] values, int offset, List<String> categories, String selected, boolean allowUnknown) {
            int categoryIndex = categories.indexOf(selected);
            if (categoryIndex < 0) {
                if (allowUnknown) return offset + categories.size(); // 測試集中未見類別用全零，不擴充訓練字典。
                throw new IllegalArgumentException("模型不支援的類別：" + selected);
            }
            values[offset + categoryIndex] = 1.0;
            return offset + categories.size();
        }

        private List<String> buildFeatureNames() {
            var names = new ArrayList<String>();
            if (selectedGroups.contains("病患年齡")) names.add("age");
            if (selectedGroups.contains("生理性別")) sexes.forEach(value -> names.add("sex=" + value));
            if (selectedGroups.contains("手術名稱")) procedures.forEach(value -> names.add("procedure=" + value));
            if (selectedGroups.contains("虛擬醫師")) surgeons.forEach(value -> names.add("surgeon=" + value));
            if (selectedGroups.contains("手術室別")) rooms.forEach(value -> names.add("room=" + value));
            if (selectedGroups.contains("緊急程度")) urgencies.forEach(value -> names.add("urgency=" + value));
            if (selectedGroups.contains("ASA 分級")) asaClasses.forEach(value -> names.add("asa=" + value));
            if (selectedGroups.contains("麻醉方式")) anesthesiaTypes.forEach(value -> names.add("anesthesia=" + value));
            if (selectedGroups.contains("共病數量")) names.add("comorbidity_count");
            if (selectedGroups.contains("手術複雜度")) complexities.forEach(value -> names.add("complexity=" + value));
            return List.copyOf(names);
        }
    }
}
