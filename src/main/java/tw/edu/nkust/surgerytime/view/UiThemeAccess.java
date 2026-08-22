package tw.edu.nkust.surgerytime.view;

/**
 * Public bridge that keeps theme implementation package-private while allowing
 * the application and visual QA utility to install Swing defaults.
 */
public final class UiThemeAccess {
    private UiThemeAccess() {
    }

    public static void install() {
        UiTheme.install();
    }
}
