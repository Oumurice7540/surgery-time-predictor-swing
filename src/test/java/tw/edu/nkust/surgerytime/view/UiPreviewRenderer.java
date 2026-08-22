package tw.edu.nkust.surgerytime.view;

import tw.edu.nkust.surgerytime.service.MockPredictionService;
import tw.edu.nkust.surgerytime.service.MockTrainingResultService;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manual Swing visual-QA utility. It captures both pages at desktop and compact sizes.
 */
public final class UiPreviewRenderer {
    private static final Path OUTPUT = Path.of("target", "ui-preview");

    private JFrame frame;
    private ApplicationShellView shell;

    public static void main(String[] args) throws Exception {
        System.setProperty("surgerytime.suppressDialogs", "true");
        new UiPreviewRenderer().render();
    }

    private void render() throws Exception {
        onEdt(() -> {
            UiThemeAccess.install();
            frame = new JFrame("Swing CSV 視覺測試");
            shell = new ApplicationShellView(new MockPredictionService(), new MockTrainingResultService());
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.setContentPane(shell);
            frame.setSize(1366, 900);
            frame.setLocation(20, 20);
            frame.setVisible(true);
        });
        pause(450);

        onEdt(() -> shell.trainingView().scrollToTop());
        pause(180);
        capture("training-desktop-top.png");
        onEdt(() -> shell.trainingView().scrollToBottom());
        pause(250);
        capture("training-desktop-bottom.png");

        resize(820, 760);
        onEdt(() -> shell.trainingView().scrollToTop());
        pause(350);
        capture("training-compact-top.png");
        onEdt(() -> shell.trainingView().scrollToBottom());
        pause(250);
        capture("training-compact-bottom.png");

        resize(1366, 900);
        onEdt(() -> {
            shell.showPage(ApplicationShellView.Page.PREDICTION);
            shell.predictionView().loadModelForPreview();
        });
        pause(780);
        onEdt(() -> shell.predictionView().predictForPreview());
        pause(650);
        capture("prediction-desktop-result.png");

        resize(820, 760);
        onEdt(() -> shell.predictionView().scrollToTop());
        pause(350);
        capture("prediction-compact-top.png");
        onEdt(() -> shell.predictionView().scrollToBottom());
        pause(250);
        capture("prediction-compact-bottom.png");

        onEdt(() -> frame.dispose());
    }

    private void resize(int width, int height) throws Exception {
        onEdt(() -> frame.setSize(new Dimension(width, height)));
        pause(350);
    }

    private void capture(String fileName) throws Exception {
        onEdt(() -> {
            try {
                Files.createDirectories(OUTPUT);
                Component component = frame.getContentPane();
                var image = new BufferedImage(
                        component.getWidth(),
                        component.getHeight(),
                        BufferedImage.TYPE_INT_ARGB
                );
                Graphics2D graphics = image.createGraphics();
                component.printAll(graphics);
                graphics.dispose();
                ImageIO.write(image, "png", OUTPUT.resolve(fileName).toFile());
            } catch (IOException exception) {
                throw new IllegalStateException("無法輸出 Swing 視覺測試圖片", exception);
            }
        });
    }

    private void onEdt(Runnable task) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeAndWait(task);
        }
    }

    private void pause(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}
