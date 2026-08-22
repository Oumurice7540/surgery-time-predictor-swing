package tw.edu.nkust.surgerytime.view;

import tw.edu.nkust.surgerytime.service.MockPredictionService;
import tw.edu.nkust.surgerytime.service.MockTrainingResultService;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Shared Swing application chrome for exactly two retained page instances. */
public final class ApplicationShellView extends JPanel {
    public enum Page {
        TRAINING_RESULTS,
        PREDICTION
    }

    private static final String TRAINING_CARD = "training";
    private static final String PREDICTION_CARD = "prediction";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel pageCards = new JPanel(cardLayout);
    private final JButton trainingButton = new JButton("訓練結果");
    private final JButton predictionButton = new JButton("手術時間預測");
    private final JLabel footerStatus = UiTheme.label("", 11f, Font.PLAIN, new Color(86, 107, 129));
    private final JLabel footerPage = UiTheme.label("", 10f, Font.BOLD, new Color(138, 154, 171));
    private final Map<Page, String> statusByPage = new EnumMap<>(Page.class);

    private final TrainingResultsView trainingView;
    private final SurgeryPredictionView predictionView;
    private Page currentPage;

    public ApplicationShellView(
            MockPredictionService predictionService,
            MockTrainingResultService trainingResultService
    ) {
        Objects.requireNonNull(predictionService, "預測服務不可為空");
        Objects.requireNonNull(trainingResultService, "訓練結果服務不可為空");
        setName("application-shell");
        setLayout(new BorderLayout());
        setBackground(UiTheme.BACKGROUND);

        statusByPage.put(Page.TRAINING_RESULTS, "CSV 模擬訓練結果已就緒");
        statusByPage.put(Page.PREDICTION, "請先載入 CSV 模擬資料");

        trainingView = new TrainingResultsView(
                trainingResultService,
                () -> showPage(Page.PREDICTION),
                status -> setStatus(Page.TRAINING_RESULTS, status),
                this::refreshDefaultButton
        );
        predictionView = new SurgeryPredictionView(
                predictionService,
                status -> setStatus(Page.PREDICTION, status),
                this::refreshDefaultButton
        );
        buildView();
        showPage(Page.TRAINING_RESULTS);
    }

    public Page currentPage() {
        return currentPage;
    }

    public TrainingResultsView trainingView() {
        return trainingView;
    }

    public SurgeryPredictionView predictionView() {
        return predictionView;
    }

    public void showPage(Page page) {
        currentPage = Objects.requireNonNull(page, "頁面不可為空");
        if (page == Page.TRAINING_RESULTS) {
            cardLayout.show(pageCards, TRAINING_CARD);
            footerPage.setText("訓練結果 · CSV DEMO");
        } else {
            cardLayout.show(pageCards, PREDICTION_CARD);
            footerPage.setText("手術時間預測 · CSV DEMO");
        }
        footerStatus.setText(statusByPage.get(page));
        refreshNavigation();
        refreshDefaultButton();
        SwingUtilities.invokeLater(this::scrollCurrentPageToTop);
    }

    public void refreshDefaultButton() {
        SwingUtilities.invokeLater(() -> {
            JRootPane rootPane = SwingUtilities.getRootPane(this);
            if (rootPane == null) {
                return;
            }
            rootPane.setDefaultButton(currentPage == Page.TRAINING_RESULTS
                    ? trainingView.defaultButton()
                    : predictionView.defaultButton());
        });
    }

    private void buildView() {
        var top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(buildHeader());
        top.add(buildNavigation());
        add(top, BorderLayout.NORTH);

        pageCards.setBackground(UiTheme.BACKGROUND);
        pageCards.add(trainingView, TRAINING_CARD);
        pageCards.add(predictionView, PREDICTION_CARD);
        add(pageCards, BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
    }

    private JPanel buildHeader() {
        var header = new GradientPanel(UiTheme.NAVY, UiTheme.NAVY_LIGHT);
        header.setLayout(new BorderLayout(14, 0));
        header.setBorder(UiTheme.padding(14, 28, 14, 28));
        header.setPreferredSize(new Dimension(900, 80));

        var brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        brand.setOpaque(false);
        var icon = UiTheme.label("+", 29f, Font.BOLD, Color.WHITE);
        icon.setOpaque(true);
        icon.setBackground(new Color(38, 188, 159));
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        icon.setPreferredSize(new Dimension(48, 48));
        brand.add(icon);
        var brandText = ViewSupport.box(BoxLayout.Y_AXIS);
        brandText.add(UiTheme.label("手術時間智慧系統", 20f, Font.BOLD, Color.WHITE));
        brandText.add(UiTheme.label("Training Insights & Surgical Duration Prediction",
                10f, Font.PLAIN, new Color(176, 204, 225)));
        brand.add(brandText);
        header.add(brand, BorderLayout.WEST);

        var controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 6));
        controls.setOpaque(false);
        var badge = UiTheme.label("CSV · DEMO 模擬資料", 11f, Font.BOLD, new Color(121, 234, 211));
        badge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(41, 132, 132)),
                UiTheme.padding(7, 12, 7, 12)
        ));
        controls.add(badge);
        var help = new JButton("?");
        help.setFont(UiTheme.FONT.deriveFont(Font.BOLD, 15f));
        help.setForeground(Color.WHITE);
        help.setBackground(new Color(37, 74, 111));
        help.setFocusPainted(false);
        help.setBorder(UiTheme.padding(0, 0, 0, 0));
        help.setMargin(new java.awt.Insets(0, 0, 0, 0));
        help.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        help.setPreferredSize(new Dimension(38, 38));
        help.setToolTipText("關於 Swing CSV 模擬系統");
        help.addActionListener(event -> showAbout());
        controls.add(help);
        header.add(controls, BorderLayout.EAST);
        return header;
    }

    private JPanel buildNavigation() {
        var nav = new JPanel(new BorderLayout());
        nav.setBackground(Color.WHITE);
        nav.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER),
                UiTheme.padding(8, 28, 8, 28)
        ));
        nav.setPreferredSize(new Dimension(900, 57));

        var workflow = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        workflow.setOpaque(false);
        workflow.add(UiTheme.label("工作流程", 10f, Font.BOLD, UiTheme.MUTED));
        workflow.add(stepNumber("1"));
        configureNavButton(trainingButton, Page.TRAINING_RESULTS);
        workflow.add(trainingButton);
        workflow.add(UiTheme.label("→", 15f, Font.BOLD, new Color(164, 179, 194)));
        workflow.add(stepNumber("2"));
        configureNavButton(predictionButton, Page.PREDICTION);
        workflow.add(predictionButton);
        nav.add(workflow, BorderLayout.WEST);
        nav.add(UiTheme.label("先檢視 CSV 訓練結果，再進行時間預測",
                10f, Font.PLAIN, new Color(130, 146, 164)), BorderLayout.EAST);
        return nav;
    }

    private JLabel stepNumber(String number) {
        var label = UiTheme.label(number, 10f, Font.BOLD, UiTheme.TEAL_DARK);
        label.setOpaque(true);
        label.setBackground(UiTheme.TEAL_PALE);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setPreferredSize(new Dimension(23, 23));
        return label;
    }

    private void configureNavButton(JButton button, Page page) {
        button.setFont(UiTheme.FONT.deriveFont(Font.BOLD, 13f));
        button.setBorder(UiTheme.padding(8, 12, 8, 12));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(event -> showPage(page));
    }

    private JPanel buildFooter() {
        var footer = new JPanel(new BorderLayout());
        footer.setBackground(Color.WHITE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.BORDER),
                UiTheme.padding(9, 28, 9, 28)
        ));
        var status = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        status.setOpaque(false);
        status.add(UiTheme.label("●", 9f, Font.BOLD, UiTheme.TEAL));
        status.add(footerStatus);
        footer.add(status, BorderLayout.WEST);
        footer.add(footerPage, BorderLayout.EAST);
        footer.setPreferredSize(new Dimension(900, 40));
        return footer;
    }

    private void refreshNavigation() {
        boolean training = currentPage == Page.TRAINING_RESULTS;
        styleNavButton(trainingButton, training);
        styleNavButton(predictionButton, !training);
    }

    private void styleNavButton(JButton button, boolean selected) {
        button.setOpaque(true);
        button.setBackground(selected ? UiTheme.TEAL_PALE : Color.WHITE);
        button.setForeground(selected ? UiTheme.TEAL_DARK : new Color(80, 102, 125));
    }

    private void setStatus(Page page, String status) {
        statusByPage.put(page, status);
        if (currentPage == page) {
            footerStatus.setText(status);
        }
    }

    private void scrollCurrentPageToTop() {
        if (currentPage == Page.TRAINING_RESULTS) {
            trainingView.scrollToTop();
        } else {
            predictionView.scrollToTop();
        }
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(
                this,
                "本系統僅包含「訓練結果」與「手術時間預測」兩頁。\n\n"
                        + "介面使用 Java 21 Swing，資料由 UTF-8 CSV 載入；"
                        + "所有訓練指標與預測皆為模擬結果，不可用於臨床判斷。",
                "關於手術時間智慧系統",
                JOptionPane.INFORMATION_MESSAGE
        );
    }
}
