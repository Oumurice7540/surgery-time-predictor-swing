package tw.edu.nkust.surgerytime;

import tw.edu.nkust.surgerytime.service.SurgeryModelService;
import tw.edu.nkust.surgerytime.view.ApplicationShellView;
import tw.edu.nkust.surgerytime.view.UiThemeAccess;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Dimension;

/**
 * Swing 應用程式的主要啟動類別。
 *
 * <p>本類別只負責「組裝」程式：安裝共用主題、建立主視窗、建立兩個資料服務，
 * 再把 {@link ApplicationShellView} 放進視窗。它不處理 CSV 細節，也不執行預測公式。</p>
 */
public final class SurgeryTimeApplication {
    /** 此類別只提供靜態啟動方法，不需要被實例化。 */
    private SurgeryTimeApplication() {
    }

    /**
     * Swing 應用程式入口。
     *
     * <p>{@link SwingUtilities#invokeLater(Runnable)} 會把建立畫面的工作排進
     * Event Dispatch Thread（Swing 事件派送執行緒）。Swing 元件應在這條專用執行緒中
     * 建立與更新，避免多條執行緒同時改動畫面而發生不穩定狀況。</p>
     *
     * @param args 命令列參數；目前沒有使用
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(SurgeryTimeApplication::showWindow);
    }

    /** 建立、設定並顯示主視窗。此方法會在 Swing 事件派送執行緒中執行。 */
    private static void showWindow() {
        // 將共用中文字型、按鈕、輸入欄位等 Swing 預設樣式安裝到 UIManager。
        UiThemeAccess.install();

        // JFrame 是桌面程式最外層的視窗；字串會顯示在視窗標題列。
        var frame = new JFrame("手術時間智慧系統｜Java WEKA｜多模型比較 v1.7.0");

        // 使用者關閉主視窗時，同時結束 Java 程序，避免程式留在背景。
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        // 啟動時只建立空的模型服務，不生成資料也不訓練。
        // 使用者在第一、二頁準備資料，再於方法設定按下訓練按鈕。
        var modelService = new SurgeryModelService();
        frame.setContentPane(new ApplicationShellView(modelService));

        // 限制最小尺寸，避免視窗過小造成表單與按鈕無法使用。
        frame.setMinimumSize(new Dimension(1100, 740));
        // 1366 x 900 是啟動時的預設尺寸，使用者之後仍可自由縮放。
        frame.setSize(1366, 900);
        // null 代表相對於整個螢幕置中。
        frame.setLocationRelativeTo(null);
        // 前面完成所有設定後，最後才讓視窗出現在螢幕上。
        frame.setVisible(true);
    }
}
