package tw.edu.nkust.surgerytime.service;

import tw.edu.nkust.surgerytime.model.SyntheticSurgeryCase;
import tw.edu.nkust.surgerytime.model.TrainingConfiguration;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * 產生、輸出並重新讀取完全虛構的手術 CSV 資料。
 *
 * <p>這個類別是整個資料流程的起點。它只使用專案內的手術名稱、基準分鐘與
 * 虛擬代碼，不接觸醫院資料庫，也不包含真實病人的姓名、病歷號或生日。</p>
 *
 * <p>新版不再將每個類別完全獨立、等機率抽樣：年齡、性別、手術室、醫師、緊急度與麻醉方式都會依
 * 手術種類使用不同的合成分布。公式仍只用來製造可學習的教學資料，不能解讀為臨床因果關係。</p>
 */
public final class SyntheticSurgeryDataService {
    /** classpath 內兩份基礎設定 CSV 的共同目錄。 */
    private static final String DATA_ROOT = "/tw/edu/nkust/surgerytime/data/";
    /** 預設將生成結果放在執行目錄下，老師可以直接開啟檢查。 */
    private static final Path DEFAULT_OUTPUT =
            Path.of("generated-data", "synthetic_surgery_cases.csv").toAbsolutePath().normalize();

    /** 手術名稱對應的虛擬基準分鐘，保留 CSV 原本順序。 */
    private final Map<String, Integer> procedureBaselines;
    /** 可用手術室代碼。 */
    private final List<String> operatingRooms;
    /** 可用虛擬醫師代碼。 */
    private final List<String> surgeons;
    /** 實際輸出檔案位置；測試可注入暫存路徑。 */
    private Path outputPath;
    /** 預設產出被 Excel 鎖定時，是否允許改存帶時間的新檔。 */
    private final boolean fallbackWhenLocked;

    /** 使用專案預設輸出位置建立服務。 */
    public SyntheticSurgeryDataService() {
        this(DEFAULT_OUTPUT, true);
    }

    /**
     * @param outputPath 要寫入的 CSV 完整路徑，主要供測試或特殊執行環境使用
     */
    public SyntheticSurgeryDataService(Path outputPath) {
        this(outputPath, false);
    }

    private SyntheticSurgeryDataService(Path outputPath, boolean fallbackWhenLocked) {
        this.outputPath = Objects.requireNonNull(outputPath, "CSV 輸出路徑不可為空")
                .toAbsolutePath().normalize();
        this.fallbackWhenLocked = fallbackWhenLocked;

        var baselines = new LinkedHashMap<String, Integer>();
        for (var row : CsvResourceReader.read(DATA_ROOT + "procedure_baselines.csv")) {
            baselines.put(required(row, "procedure"), integer(row, "base_minutes"));
        }
        procedureBaselines = Collections.unmodifiableMap(baselines);
        operatingRooms = readOptions("ROOM");
        surgeons = readOptions("SURGEON");
    }

    /** @return 所有可選手術名稱，順序與設定 CSV 相同 */
    public List<String> supportedProcedures() {
        return List.copyOf(procedureBaselines.keySet());
    }

    /** @return 所有可選手術室代碼 */
    public List<String> supportedOperatingRooms() {
        return operatingRooms;
    }

    /** @return 所有可選虛擬醫師代碼 */
    public List<String> supportedSurgeons() {
        return surgeons;
    }

    /** @return 最近一次生成資料會使用的檔案位置 */
    public Path outputPath() {
        return outputPath;
    }

    /**
     * 依設定產生資料、寫成 UTF-8 CSV，然後再從該檔案讀回。
     *
     * <p>刻意「寫出再讀回」可證明真正餵給模型的是 CSV 內容，而不是畫面中的
     * 預先寫死數字。若 CSV 寫壞或欄位缺漏，讀回階段會立即報錯。</p>
     */
    public List<SyntheticSurgeryCase> generateSaveAndReload(TrainingConfiguration configuration) {
        Objects.requireNonNull(configuration, "訓練設定不可為空");
        List<SyntheticSurgeryCase> generated = generate(configuration);
        writeCsv(generated);
        return readCsv();
    }

    /**
     * 讀取使用者選擇的病例 CSV。匯入檔必須與系統產生檔使用相同欄位，
     * 每列都會通過 {@link SyntheticSurgeryCase} 的型態與範圍驗證。
     */
    public List<SyntheticSurgeryCase> importCsv(Path sourcePath) {
        Path normalized = Objects.requireNonNull(sourcePath, "匯入 CSV 路徑不可為空")
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("找不到匯入 CSV：" + normalized);
        }
        return readCsv(normalized);
    }

    /**
     * 將多份相同格式的月報表合併成一份 CSV。
     * 每份檔案先各自通過欄位與類別驗證，合併後再檢查案例編號是否重複。
     */
    public List<SyntheticSurgeryCase> mergeCsvFiles(List<Path> sourcePaths, Path mergedOutputPath) {
        Objects.requireNonNull(sourcePaths, "來源 CSV 清單不可為空");
        if (sourcePaths.isEmpty()) {
            throw new IllegalArgumentException("至少要選擇一份病例 CSV");
        }
        var merged = new ArrayList<SyntheticSurgeryCase>();
        for (Path sourcePath : sourcePaths) {
            merged.addAll(importCsv(sourcePath));
        }
        long distinctIds = merged.stream().map(SyntheticSurgeryCase::caseId).distinct().count();
        if (distinctIds != merged.size()) {
            throw new IllegalStateException("合併後的病例包含重複 case_id，請先修正來源檔案");
        }
        writeCases(mergedOutputPath, merged);
        return readCsv(mergedOutputPath.toAbsolutePath().normalize());
    }

    /** 將已驗證案例輸出成可供下一階段使用的 UTF-8 CSV。 */
    public void writeCases(Path requestedPath, List<SyntheticSurgeryCase> cases) {
        Path path = Objects.requireNonNull(requestedPath, "CSV 輸出路徑不可為空")
                .toAbsolutePath().normalize();
        Objects.requireNonNull(cases, "病例清單不可為空");
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("病例清單不可為空");
        }
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            writeCsvFile(cases, path);
        } catch (IOException exception) {
            throw new IllegalStateException("無法寫入病例 CSV：" + path, exception);
        }
    }

    /** 使用固定種子的 java.util.Random 建立可重現的虛擬案例。 */
    private List<SyntheticSurgeryCase> generate(TrainingConfiguration configuration) {
        var random = new Random(configuration.randomSeed());
        var cases = new ArrayList<SyntheticSurgeryCase>(configuration.sampleCount());

        for (int index = 0; index < configuration.sampleCount(); index++) {
            ProcedureProfile profile = chooseProcedure(random);
            String procedure = profile.procedure();
            int age = truncatedAge(random, profile);
            String sex = random.nextDouble() < profile.femaleProbability() ? "女性 F" : "男性 M";
            String room = weightedChoice(random, operatingRooms, roomWeights(procedure));
            String surgeon = weightedChoice(random, surgeons, surgeonWeights(procedure));
            int comorbidityCount = comorbidityCount(random, age);
            String asaClass = asaClass(random, age, comorbidityCount);
            String urgency = random.nextDouble() < profile.emergencyProbability() ? "緊急 Emergency" : "常規 Elective";
            String anesthesia = weightedChoice(random,
                    List.of("全身麻醉 General", "區域麻醉 Regional", "鎮靜麻醉 Sedation"),
                    profile.anesthesiaWeights());
            String complexity = complexity(random, profile);

            double minutes = procedureBaselines.get(procedure) * complexityMultiplier(complexity);
            minutes += Math.max(0, age - 45) * 0.18;
            minutes += roomEffect(room);
            minutes += surgeonEffect(surgeon, procedure);
            minutes += asaEffect(asaClass);
            minutes += comorbidityCount * 2.5;
            minutes += urgency.startsWith("緊急") ? 7.0 : 0.0;
            minutes += anesthesiaEffect(anesthesia);
            if (urgency.startsWith("緊急") && complexity.startsWith("高複雜")) minutes += 8.0;

            // 不同手術使用不同變異；複雜或緊急案例的不確定性更大。
            double noiseScale = profile.noiseStandardDeviation()
                    * (complexity.startsWith("標準") ? 1.0 : 1.20)
                    * (urgency.startsWith("緊急") ? 1.15 : 1.0);
            minutes += random.nextGaussian() * noiseScale;
            // 少數案例有單邊向上的意外延誤，讓時間分布不會過度對稱。
            if (random.nextDouble() < 0.04) minutes += -Math.log(1.0 - random.nextDouble()) * 22.0;
            int actualMinutes = Math.clamp((int) Math.round(minutes), 20, 480);
            cases.add(new SyntheticSurgeryCase(
                    "SYN-%06d".formatted(index + 1),
                    room,
                    age,
                    sex,
                    procedure,
                    surgeon,
                    urgency,
                    asaClass,
                    anesthesia,
                    comorbidityCount,
                    complexity,
                    actualMinutes
            ));
        }
        return List.copyOf(cases);
    }

    /** 根據合成病例量權重選擇手術，不再假設五種手術數量相同。 */
    private ProcedureProfile chooseProcedure(Random random) {
        List<ProcedureProfile> profiles = procedureBaselines.entrySet().stream()
                .map(entry -> profile(entry.getKey(), entry.getValue()))
                .toList();
        double[] weights = profiles.stream().mapToDouble(ProcedureProfile::caseWeight).toArray();
        return weightedChoice(random, profiles, weights);
    }

    /** 每種手術的分布參數均是可公開說明的合成資料假設。 */
    private ProcedureProfile profile(String procedure, int baseMinutes) {
        if (procedure.startsWith("顎下腺")) return new ProcedureProfile(procedure, baseMinutes, .12, 52, 16, .48, .03, .22, .06, 12, new double[]{.95, 0, .05});
        if (procedure.startsWith("腹腔鏡膽囊")) return new ProcedureProfile(procedure, baseMinutes, .30, 55, 15, .62, .12, .20, .05, 11, new double[]{.98, .02, 0});
        if (procedure.startsWith("全膝")) return new ProcedureProfile(procedure, baseMinutes, .20, 69, 8, .62, .01, .27, .08, 15, new double[]{.25, .75, 0});
        if (procedure.startsWith("腹腔鏡闌尾")) return new ProcedureProfile(procedure, baseMinutes, .23, 36, 16, .50, .55, .18, .05, 12, new double[]{.98, 0, .02});
        // 剖腹產合成案例限定為女性，年齡另在 truncatedAge 限定於 18–50 歲。
        return new ProcedureProfile(procedure, baseMinutes, .15, 32, 5, 1.0, .28, .18, .04, 10, new double[]{.15, .85, 0});
    }

    private int truncatedAge(Random random, ProcedureProfile profile) {
        int maximum = profile.procedure().startsWith("剖腹產") ? 50 : 90;
        int value;
        do {
            value = (int) Math.round(profile.ageMean() + random.nextGaussian() * profile.ageStandardDeviation());
        } while (value < 18 || value > maximum);
        return value;
    }

    private int comorbidityCount(Random random, int age) {
        double probability = Math.clamp(0.06 + Math.max(0, age - 35) * 0.011, 0.06, 0.62);
        int count = 0;
        for (int index = 0; index < 4; index++) {
            if (random.nextDouble() < probability * (1.0 - index * 0.12)) count++;
        }
        return count;
    }

    private String asaClass(Random random, int age, int comorbidities) {
        int riskScore = comorbidities + (age >= 65 ? 1 : 0) + (age >= 80 ? 1 : 0);
        if (random.nextDouble() < 0.10) riskScore++;
        if (riskScore == 0) return "ASA I";
        if (riskScore <= 2) return "ASA II";
        if (riskScore <= 4) return "ASA III";
        return "ASA IV";
    }

    private String complexity(Random random, ProcedureProfile profile) {
        double draw = random.nextDouble();
        if (draw < profile.veryComplexProbability()) return "高複雜 High";
        if (draw < profile.veryComplexProbability() + profile.complexProbability()) return "複雜 Complex";
        return "標準 Standard";
    }

    private double[] roomWeights(String procedure) {
        if (procedure.startsWith("顎下腺")) return new double[]{.45, .35, .15, .05};
        if (procedure.startsWith("腹腔鏡膽囊")) return new double[]{.20, .55, .15, .10};
        if (procedure.startsWith("全膝")) return new double[]{.10, .10, .70, .10};
        if (procedure.startsWith("腹腔鏡闌尾")) return new double[]{.20, .45, .10, .25};
        return new double[]{.05, .05, .10, .80};
    }

    private double[] surgeonWeights(String procedure) {
        if (procedure.startsWith("顎下腺")) return new double[]{.65, .15, .10, .10};
        if (procedure.startsWith("腹腔鏡膽囊")) return new double[]{.10, .55, .25, .10};
        if (procedure.startsWith("全膝")) return new double[]{.08, .10, .72, .10};
        if (procedure.startsWith("腹腔鏡闌尾")) return new double[]{.10, .45, .30, .15};
        return new double[]{.05, .05, .10, .80};
    }

    private <T> T weightedChoice(Random random, List<T> values, double[] weights) {
        if (values.size() != weights.length) throw new IllegalArgumentException("加權選項與權重數量不一致");
        double total = java.util.Arrays.stream(weights).sum();
        double draw = random.nextDouble() * total;
        for (int index = 0; index < weights.length; index++) {
            draw -= weights[index];
            if (draw <= 0) return values.get(index);
        }
        return values.getLast();
    }

    /** 手術室效果使用明確表格，不使用名稱雜湊或隱藏規則。 */
    private double roomEffect(String room) {
        return switch (room) {
            case "A02" -> -3.0;
            case "B01" -> 2.0;
            case "B03" -> -1.0;
            default -> 0.0;
        };
    }

    /**
     * 虛擬醫師效果依 CSV 排列位置對應 -6、-2、3、7 分鐘。
     * 這些是資料生成假設，不代表任何真人能力或醫療品質。
     */
    private double surgeonEffect(String surgeon, String procedure) {
        int surgeonIndex = surgeons.indexOf(surgeon);
        int specialistIndex = procedure.startsWith("顎下腺") ? 0
                : procedure.startsWith("腹腔鏡膽囊") || procedure.startsWith("腹腔鏡闌尾") ? 1
                : procedure.startsWith("全膝") ? 2 : 3;
        return surgeonIndex == specialistIndex ? -6.0 : 4.0;
    }

    private double complexityMultiplier(String complexity) {
        if (complexity.startsWith("高複雜")) return 1.38;
        if (complexity.startsWith("複雜")) return 1.18;
        return 1.0;
    }

    private double asaEffect(String asaClass) {
        return switch (asaClass) {
            case "ASA II" -> 3.0;
            case "ASA III" -> 9.0;
            case "ASA IV" -> 18.0;
            default -> 0.0;
        };
    }

    private double anesthesiaEffect(String anesthesia) {
        if (anesthesia.startsWith("區域")) return 7.0;
        if (anesthesia.startsWith("全身")) return 4.0;
        return 0.0;
    }

    /** 將案例逐列寫入 CSV；文字欄位會依 CSV 規則處理雙引號與逗號。 */
    private void writeCsv(List<SyntheticSurgeryCase> cases) {
        try {
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            writeCsvFile(cases, outputPath);
        } catch (IOException exception) {
            if (fallbackWhenLocked) {
                Path alternate = outputPath.resolveSibling("synthetic_surgery_cases_"
                        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv");
                try {
                    writeCsvFile(cases, alternate);
                    outputPath = alternate;
                    return;
                } catch (IOException alternateException) {
                    exception.addSuppressed(alternateException);
                }
            }
            throw new IllegalStateException("無法寫入虛擬手術 CSV：" + outputPath, exception);
        }
    }

    private void writeCsvFile(List<SyntheticSurgeryCase> cases, Path path) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            // UTF-8 BOM 讓 Windows Excel 雙擊 CSV 時也能正確辨識中文編碼。
            writer.write('\uFEFF');
            writer.write("case_id,operating_room,age,sex,procedure,surgeon_code,urgency,asa_class,anesthesia,comorbidity_count,complexity,actual_minutes");
            writer.newLine();
            for (var item : cases) {
                writer.write(String.join(",",
                        csv(item.caseId()),
                        csv(item.operatingRoom()),
                        Integer.toString(item.age()),
                        csv(item.sex()),
                        csv(item.procedure()),
                        csv(item.surgeon()),
                        csv(item.urgency()),
                        csv(item.asaClass()),
                        csv(item.anesthesia()),
                        Integer.toString(item.comorbidityCount()),
                        csv(item.complexity()),
                        Integer.toString(item.actualMinutes())
                ));
                writer.newLine();
            }
        }
    }

    /** 從剛輸出的 CSV 逐列讀回並轉成強型別案例。 */
    private List<SyntheticSurgeryCase> readCsv() {
        return readCsv(outputPath);
    }

    private List<SyntheticSurgeryCase> readCsv(Path sourcePath) {
        try (BufferedReader reader = Files.newBufferedReader(sourcePath, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalStateException("虛擬手術 CSV 缺少標題列");
            }
            // 產生檔含 UTF-8 BOM，讀回時先移除，避免第一欄變成 "\uFEFFcase_id"。
            List<String> headers = CsvResourceReader.parseLine(headerLine.replaceFirst("^\uFEFF", ""));
            var result = new ArrayList<SyntheticSurgeryCase>();
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = CsvResourceReader.parseLine(line);
                if (values.size() != headers.size()) {
                    throw new IllegalStateException("虛擬手術 CSV 第 " + lineNumber + " 列欄位數不符");
                }
                var row = new LinkedHashMap<String, String>();
                for (int index = 0; index < headers.size(); index++) {
                    row.put(headers.get(index), values.get(index));
                }
                var item = new SyntheticSurgeryCase(
                        required(row, "case_id"),
                        required(row, "operating_room"),
                        integer(row, "age"),
                        required(row, "sex"),
                        required(row, "procedure"),
                        required(row, "surgeon_code"),
                        required(row, "urgency"),
                        required(row, "asa_class"),
                        required(row, "anesthesia"),
                        integer(row, "comorbidity_count"),
                        required(row, "complexity"),
                        integer(row, "actual_minutes")
                );
                validateImportedCase(item, lineNumber);
                result.add(item);
            }
            if (result.isEmpty()) {
                throw new IllegalStateException("病例 CSV 沒有任何資料列");
            }
            long distinctIds = result.stream().map(SyntheticSurgeryCase::caseId).distinct().count();
            if (distinctIds != result.size()) {
                throw new IllegalStateException("病例 CSV 包含重複的 case_id");
            }
            return List.copyOf(result);
        } catch (IOException exception) {
            throw new IllegalStateException("無法讀取手術病例 CSV：" + sourcePath, exception);
        }
    }

    /** 從 prediction_options.csv 篩選 ROOM 或 SURGEON 類型。 */
    private List<String> readOptions(String type) {
        return CsvResourceReader.read(DATA_ROOT + "prediction_options.csv").stream()
                .filter(row -> type.equals(required(row, "type")))
                .map(row -> required(row, "value"))
                .toList();
    }

    /** 確保匯入類別能與預測頁選項及合成資料規則一致。 */
    private void validateImportedCase(SyntheticSurgeryCase item, int lineNumber) {
        if (!operatingRooms.contains(item.operatingRoom())) {
            throw new IllegalStateException("第 " + lineNumber + " 列手術室不受支援：" + item.operatingRoom());
        }
        if (!surgeons.contains(item.surgeon())) {
            throw new IllegalStateException("第 " + lineNumber + " 列虛擬醫師不受支援：" + item.surgeon());
        }
        if (!procedureBaselines.containsKey(item.procedure())) {
            throw new IllegalStateException("第 " + lineNumber + " 列手術名稱不受支援：" + item.procedure());
        }
        if (!List.of("女性 F", "男性 M").contains(item.sex())) {
            throw new IllegalStateException("第 " + lineNumber + " 列性別必須為「女性 F」或「男性 M」");
        }
        if (item.procedure().startsWith("剖腹產") && !item.sex().startsWith("女性")) {
            throw new IllegalStateException("第 " + lineNumber + " 列剖腹產案例的生理性別必須為女性");
        }
        if (!List.of("常規 Elective", "緊急 Emergency").contains(item.urgency())) {
            throw new IllegalStateException("第 " + lineNumber + " 列緊急程度格式不正確");
        }
        if (!List.of("ASA I", "ASA II", "ASA III", "ASA IV").contains(item.asaClass())) {
            throw new IllegalStateException("第 " + lineNumber + " 列 ASA 分級格式不正確");
        }
        if (!List.of("全身麻醉 General", "區域麻醉 Regional", "鎮靜麻醉 Sedation").contains(item.anesthesia())) {
            throw new IllegalStateException("第 " + lineNumber + " 列麻醉方式格式不正確");
        }
        if (!List.of("標準 Standard", "複雜 Complex", "高複雜 High").contains(item.complexity())) {
            throw new IllegalStateException("第 " + lineNumber + " 列手術複雜度格式不正確");
        }
    }

    /** CSV 欄位含逗號、雙引號或換行時，以雙引號包住並將內部雙引號加倍。 */
    private String csv(String value) {
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    /** 取得必要欄位；缺少或空白時提供欄位名稱。 */
    private String required(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("CSV 缺少必要欄位：" + column);
        }
        return value.trim();
    }

    /** 將 CSV 文字轉成整數並保留原始錯誤原因。 */
    private int integer(Map<String, String> row, String column) {
        try {
            return Integer.parseInt(required(row, column));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("CSV 欄位必須是整數：" + column, exception);
        }
    }

    /** 單一手術的合成分布設定；所有數值均為教學假設。 */
    private record ProcedureProfile(
            String procedure,
            int baseMinutes,
            double caseWeight,
            double ageMean,
            double ageStandardDeviation,
            double femaleProbability,
            double emergencyProbability,
            double complexProbability,
            double veryComplexProbability,
            double noiseStandardDeviation,
            double[] anesthesiaWeights
    ) {
    }
}
