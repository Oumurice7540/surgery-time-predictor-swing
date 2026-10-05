package tw.edu.nkust.surgerytime.view;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Window;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.swing.SwingUtilities;

/**
 * 集中建立作業系統原生的檔案開啟與儲存視窗。
 *
 * <p>Swing 的 JFileChooser 會依 Look and Feel 畫出 Java 版介面；AWT FileDialog
 * 則委託 Windows 顯示原生檔案總管視窗，因此能保留快速存取、搜尋、檢視方式等
 * Windows 使用習慣。</p>
 */
final class NativeFileDialogs {
    private NativeFileDialogs() {
    }

    /** 顯示原生開啟視窗，可選擇一份或多份指定副檔名的檔案。 */
    static List<Path> openFiles(
            Component owner,
            String title,
            String extension,
            boolean multiple
    ) {
        String normalizedExtension = normalizeExtension(extension);
        FileDialog dialog = create(owner, title, FileDialog.LOAD);
        dialog.setMultipleMode(multiple);
        dialog.setFilenameFilter((directory, name) -> hasExtension(name, normalizedExtension));
        // Windows 會把萬用字元當成初始篩選條件，而不是檔名。
        dialog.setFile("*." + normalizedExtension);
        dialog.setVisible(true);
        return Arrays.stream(dialog.getFiles())
                .map(file -> file.toPath().toAbsolutePath().normalize())
                .toList();
    }

    /** 顯示原生另存新檔視窗；取消時回傳 Optional.empty()。 */
    static Optional<Path> saveFile(
            Component owner,
            String title,
            String defaultFileName,
            String extension
    ) {
        String normalizedExtension = normalizeExtension(extension);
        FileDialog dialog = create(owner, title, FileDialog.SAVE);
        dialog.setFilenameFilter((directory, name) -> hasExtension(name, normalizedExtension));
        dialog.setFile(defaultFileName);
        dialog.setVisible(true);
        if (dialog.getFile() == null || dialog.getDirectory() == null) return Optional.empty();
        Path selected = Path.of(dialog.getDirectory(), dialog.getFile()).toAbsolutePath().normalize();
        return Optional.of(ensureExtension(selected, normalizedExtension));
    }

    /** 讓原生視窗跟隨目前 Swing 主視窗，不會躲到程式後面。 */
    private static FileDialog create(Component owner, String title, int mode) {
        Window window = SwingUtilities.getWindowAncestor(owner);
        if (window instanceof Dialog parentDialog) return new FileDialog(parentDialog, title, mode);
        return new FileDialog(window instanceof Frame frame ? frame : null, title, mode);
    }

    static Path ensureExtension(Path path, String extension) {
        String normalizedExtension = normalizeExtension(extension);
        if (hasExtension(path.getFileName().toString(), normalizedExtension)) return path;
        return path.resolveSibling(path.getFileName() + "." + normalizedExtension);
    }

    static boolean hasExtension(String fileName, String extension) {
        return fileName.toLowerCase(Locale.ROOT)
                .endsWith("." + normalizeExtension(extension));
    }

    private static String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            throw new IllegalArgumentException("副檔名不可為空");
        }
        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith(".") ? normalized.substring(1) : normalized;
    }
}
