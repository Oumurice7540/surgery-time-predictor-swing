package tw.edu.nkust.surgerytime;

import tw.edu.nkust.surgerytime.service.MockPredictionService;
import tw.edu.nkust.surgerytime.service.MockTrainingResultService;
import tw.edu.nkust.surgerytime.view.ApplicationShellView;
import tw.edu.nkust.surgerytime.view.UiThemeAccess;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Dimension;

public final class SurgeryTimeApplication {
    private SurgeryTimeApplication() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SurgeryTimeApplication::showWindow);
    }

    private static void showWindow() {
        UiThemeAccess.install();
        var frame = new JFrame("手術時間智慧系統｜Swing CSV DEMO");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setContentPane(new ApplicationShellView(
                new MockPredictionService(),
                new MockTrainingResultService()
        ));
        frame.setMinimumSize(new Dimension(760, 640));
        frame.setSize(1366, 900);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
