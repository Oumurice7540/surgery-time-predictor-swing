package tw.edu.nkust.surgerytime;

/**
 * 程式的最外層啟動入口。
 *
 * <p>Java 執行一個程式時，會先尋找 {@code public static void main(String[] args)}。
 * Maven、IDE 與打包後的 JAR 都設定從這個類別開始執行。這個類別刻意保持簡單，
 * 真正建立 Swing 視窗的工作交由 {@link SurgeryTimeApplication} 負責。</p>
 *
 * <p>多保留這一層的好處，是未來即使更換畫面啟動方式，也不必修改 Maven/JAR 的入口設定。</p>
 */
public final class Launcher {
    /**
     * 工具型啟動類別不需要建立物件，因此使用 private 建構子阻止外部寫出
     * {@code new Launcher()}。
     */
    private Launcher() {
    }

    /**
     * JVM 呼叫的程式入口。
     *
     * @param args 從命令列傳入的參數；目前專案沒有使用，但 Java main 方法必須保留此參數
     */
    public static void main(String[] args) {
        // 將原始命令列參數原封不動交給真正的應用程式啟動類別。
        SurgeryTimeApplication.main(args);
    }
}
