package tw.edu.nkust.surgerytime.view;

/**
 * 讓 view 套件外部可以安裝 Swing 主題的公開橋接類別。
 *
 * <p>{@link UiTheme} 刻意保持 package-private，只讓畫面套件內部使用；
 * 但外層的 {@code SurgeryTimeApplication} 仍需要呼叫主題安裝方法，因此透過
 * 這個很小的公開類別轉接。</p>
 */
public final class UiThemeAccess {
    /** 工具類別不需要建立物件。 */
    private UiThemeAccess() {
    }

    /** 對外公開的主題安裝入口。 */
    public static void install() {
        UiTheme.install();
    }
}
