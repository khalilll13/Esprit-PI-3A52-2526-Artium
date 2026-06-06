package controllers.amateur;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.animation.*;
import javafx.util.Duration;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import entities.Livre;
import javafx.application.Platform;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BookReaderController {

    static {
        Logger.getLogger("org.apache.pdfbox").setLevel(Level.SEVERE);
    }

    // ── FXML bindings ────────────────────────────────────────────────────────
    // rootPane is now a StackPane (was BorderPane – that was causing the dark overlay)
    @FXML private StackPane rootPane;
    @FXML private HBox      bookContainer;      // the true side-by-side HBox
    @FXML private StackPane leftPageWrapper;
    @FXML private StackPane rightPageWrapper;
    @FXML private javafx.scene.image.ImageView leftPageImage;
    @FXML private javafx.scene.image.ImageView rightPageImage;
    @FXML private Label     leftPageNumber;
    @FXML private Label     rightPageNumber;
    @FXML private StackPane bookmarkRibbon;
    @FXML private StackPane loadingOverlay;     // layer 4 – solid cover, hidden after load
    @FXML private StackPane controlsOverlay;
    @FXML private Label     pageLabel;
    @FXML private Label     titleLabel;
    @FXML private Button    prevButton;
    @FXML private Button    nextButton;
    @FXML private Button    bookmarkButton;
    @FXML private Button    exitButton;
    @FXML private StackPane leftPageArea;
    @FXML private StackPane rightPageArea;

    // ── State ────────────────────────────────────────────────────────────────
    private PDDocument  document;
    private PDFRenderer renderer;
    private int         currentPagePair;
    private int         pageCount;
    private int         totalPairs;
    private Stage       currentStage;
    private Runnable    backHandler;
    private Livre       currentLivre;
    private boolean     isBookmarked = false;
    private boolean     isAnimating  = false;

    private static final String STATE_DIR =
            System.getProperty("user.home") + File.separator + ".artium" + File.separator + "reader";

    // ── Lifecycle ────────────────────────────────────────────────────────────

    public void initialize() {
        ensureStateDir();
        // Controls overlay starts invisible; CSS :hover on reader-root reveals it.
        // We also make sure loading overlay is on top by setting it managed/visible.
        if (loadingOverlay != null) loadingOverlay.setVisible(true);
        if (controlsOverlay != null) controlsOverlay.setOpacity(0);

        // Make rootPane fill the window completely
        if (rootPane != null) {
            rootPane.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);
        }

        // Add bindings to make images fill their half of the screen
        if (leftPageImage != null && leftPageWrapper != null) {
            leftPageImage.fitWidthProperty().bind(leftPageWrapper.widthProperty());
            leftPageImage.fitHeightProperty().bind(leftPageWrapper.heightProperty());
        }
        if (rightPageImage != null && rightPageWrapper != null) {
            rightPageImage.fitWidthProperty().bind(rightPageWrapper.widthProperty());
            rightPageImage.fitHeightProperty().bind(rightPageWrapper.heightProperty());
        }
    }

    private void ensureStateDir() {
        try { Files.createDirectories(Paths.get(STATE_DIR)); }
        catch (IOException e) { e.printStackTrace(); }
    }

    // ── Public API (all signatures preserved for external callers) ───────────

    /** Primary entry point called by the catalogue/list page. */
    public void setLivre(Livre livre) {
        if (livre == null) return;
        this.currentLivre = livre;
        if (titleLabel != null) titleLabel.setText(livre.getTitre());

        String pdfSource = livre.getFichierPdf();
        if (pdfSource == null || pdfSource.isBlank()) {
            showError("Erreur : Aucun fichier PDF trouvé.");
            return;
        }

        showLoading();

        CompletableFuture.runAsync(() -> {
            try {
                byte[] bytes = loadPdfBytes(pdfSource);
                Platform.runLater(() -> {
                    try {
                        initDocument(bytes);
                        hideLoading();        // ← this was the missing call
                    } catch (IOException e) {
                        showError("Erreur lors du rendu du PDF.");
                    }
                });
            } catch (IOException e) {
                Platform.runLater(() -> showError("Erreur lors du téléchargement du PDF."));
            }
        });
    }

    public void setBackHandler(Runnable handler) {
        this.backHandler = handler;
    }

    /** Load from a file path or URL string. */
    public void setPdfSource(String pdfSource) throws IOException {
        close();
        this.document   = loadPdfDocument(pdfSource);
        this.renderer   = new PDFRenderer(document);
        this.pageCount  = document.getNumberOfPages();
        this.totalPairs = (int) Math.ceil((double) pageCount / 2);
        this.currentPagePair = 0;
        loadSavedState();
        renderCurrentPagePair();
    }

    /** Load from raw bytes (called internally and usable externally). */
    public void setPdfBytes(byte[] pdfBytes) throws IOException {
        if (pdfBytes == null || pdfBytes.length == 0) throw new IOException("PDF vide");
        close();
        initDocument(pdfBytes);
    }

    /** Attach the Stage; forces true fullscreen immediately. */
    public void setStage(Stage stage) {
        this.currentStage = stage;

        // Force fullscreen at the Stage level
        stage.setFullScreen(true);
        stage.setFullScreenExitHint("");     // hide "press ESC" hint
        
        // Listen for ESC key to trigger onBack()
        stage.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                onBack();
            }
        });

        stage.setOnCloseRequest(e -> { saveState(); close(); });
    }

    /** Convenience setter used by some callers to update just the title label. */
    public void setBookTitle(String title) {
        if (titleLabel != null && title != null && !title.isEmpty())
            titleLabel.setText(title);
    }

    /** Release the PDF document resources. */
    public void close() {
        if (document != null) {
            try { document.close(); } catch (IOException ignored) {
            } finally {
                document        = null;
                renderer        = null;
                pageCount       = 0;
                totalPairs      = 0;
                currentPagePair = 0;
            }
        }
    }

    // ── FXML event handlers ──────────────────────────────────────────────────

    @FXML private void onBack() {
        saveState();
        close();
        // Restore windowed mode before handing back to parent
        if (currentStage != null) currentStage.setFullScreen(false);
        if (backHandler != null) backHandler.run();
        else if (currentStage != null) currentStage.close();
    }

    @FXML private void onPrev() {
        if (isAnimating || currentPagePair <= 0) return;
        animatePageFlip(true, () -> { currentPagePair--; renderCurrentPagePair(); });
    }

    @FXML private void onNext() {
        if (isAnimating || currentPagePair >= totalPairs - 1) return;
        animatePageFlip(false, () -> { currentPagePair++; renderCurrentPagePair(); });
    }

    @FXML private void onLeftPageClick()  { onPrev(); }
    @FXML private void onRightPageClick() { onNext(); }

    @FXML private void onHoverLeft() {
        if (currentPagePair > 0)
            leftPageArea.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 100% 0%, rgba(255,255,255,0.04), transparent);");
    }
    @FXML private void onExitLeft()  { leftPageArea.setStyle("-fx-background-color: transparent;"); }

    @FXML private void onHoverRight() {
        if (currentPagePair < totalPairs - 1)
            rightPageArea.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 100% 0%, transparent, rgba(255,255,255,0.04));");
    }
    @FXML private void onExitRight() { rightPageArea.setStyle("-fx-background-color: transparent;"); }

    @FXML private void toggleBookmark() {
        isBookmarked = !isBookmarked;
        applyBookmarkVisuals();
        saveBookmarkState();
    }

    // ── Animation ────────────────────────────────────────────────────────────

    /**
     * Scale + fade the outgoing page out, swap content, scale + fade the
     * incoming page in.  onComplete is called ONCE to advance the page index.
     */
    private void animatePageFlip(boolean toPrev, Runnable onComplete) {
        isAnimating = true;

        javafx.scene.Node exitNode  = toPrev ? rightPageWrapper : leftPageWrapper;
        javafx.scene.Node enterNode = toPrev ? leftPageWrapper  : rightPageWrapper;

        // Phase 1 – exit
        ScaleTransition so = new ScaleTransition(Duration.millis(160), exitNode);
        so.setToX(0.88); so.setToY(0.94);
        FadeTransition  fo = new FadeTransition(Duration.millis(160), exitNode);
        fo.setToValue(0.0);
        ParallelTransition exitAnim = new ParallelTransition(so, fo);
        exitAnim.setInterpolator(Interpolator.EASE_IN);

        exitAnim.setOnFinished(e -> {
            // Swap content (advance page pair)
            onComplete.run();

            // Reset both nodes
            exitNode.setOpacity(1); exitNode.setScaleX(1); exitNode.setScaleY(1);
            enterNode.setOpacity(0); enterNode.setScaleX(0.88); enterNode.setScaleY(0.94);

            // Phase 2 – enter
            ScaleTransition si = new ScaleTransition(Duration.millis(200), enterNode);
            si.setToX(1.0); si.setToY(1.0);
            FadeTransition  fi = new FadeTransition(Duration.millis(200), enterNode);
            fi.setToValue(1.0);
            ParallelTransition enterAnim = new ParallelTransition(si, fi);
            enterAnim.setInterpolator(Interpolator.EASE_OUT);
            enterAnim.setOnFinished(ev -> isAnimating = false);
            enterAnim.play();
        });

        exitAnim.play();
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    private void renderCurrentPagePair() {
        if (document == null || renderer == null) return;

        CompletableFuture.runAsync(() -> {
            int leftIdx  = currentPagePair * 2;
            int rightIdx = leftIdx + 1;
            Image leftImg  = renderPage(leftIdx);
            Image rightImg = renderPage(rightIdx);

            Platform.runLater(() -> {
                applyPageImage(leftPageImage,  leftImg);
                applyPageImage(rightPageImage, rightImg);

                leftPageNumber.setText(leftIdx  < pageCount ? String.valueOf(leftIdx  + 1) : "");
                rightPageNumber.setText(rightIdx < pageCount ? String.valueOf(rightIdx + 1) : "");

                updatePageInfo();
                updateNavigationButtons();
                updateBookmarkState();
            });
        });
    }

    private Image renderPage(int idx) {
        if (idx < 0 || idx >= pageCount) return null;
        try {
            BufferedImage buf = renderer.renderImageWithDPI(idx, 150);
            return SwingFXUtils.toFXImage(buf, null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void applyPageImage(javafx.scene.image.ImageView iv, Image img) {
        iv.setImage(img);
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void updatePageInfo() {
        int l = currentPagePair * 2 + 1;
        int r = Math.min(l + 1, pageCount);
        pageLabel.setText(l == r
                ? "Page " + l + " de " + pageCount
                : "Pages " + l + " – " + r + " de " + pageCount);
    }

    private void updateNavigationButtons() {
        prevButton.setDisable(currentPagePair <= 0);
        nextButton.setDisable(currentPagePair >= totalPairs - 1);
    }

    private void updateBookmarkState() {
        if (currentLivre == null) return;
        isBookmarked = (loadBookmarkedPage() == currentPagePair * 2);
        applyBookmarkVisuals();
    }

    private void applyBookmarkVisuals() {
        bookmarkRibbon.setVisible(isBookmarked);
        if (isBookmarked) {
            bookmarkButton.setText("★");
            bookmarkButton.setStyle("-fx-text-fill: #fbbf24;");
            setTooltip("Retirer le signet");
        } else {
            bookmarkButton.setText("☆");
            bookmarkButton.setStyle("-fx-text-fill: #8888a8;");
            setTooltip("Ajouter un signet");
        }
    }

    private void setTooltip(String text) {
        Tooltip t = bookmarkButton.getTooltip();
        if (t != null) t.setText(text);
    }

    private void showLoading() {
        if (loadingOverlay != null) loadingOverlay.setVisible(true);
    }

    private void hideLoading() {
        if (loadingOverlay == null) return;
        // Fade the loading screen out smoothly
        FadeTransition ft = new FadeTransition(Duration.millis(400), loadingOverlay);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ft.setOnFinished(e -> loadingOverlay.setVisible(false));
        ft.play();
    }

    private void showError(String message) {
        hideLoading();
        if (titleLabel != null) titleLabel.setText(message);
    }

    // ── PDF loading helpers ───────────────────────────────────────────────────

    /** Shared internal initialiser after bytes are available. */
    private void initDocument(byte[] bytes) throws IOException {
        close();
        this.document   = PDDocument.load(new ByteArrayInputStream(bytes));
        this.renderer   = new PDFRenderer(document);
        this.pageCount  = document.getNumberOfPages();
        this.totalPairs = (int) Math.ceil((double) pageCount / 2);
        this.currentPagePair = 0;
        loadSavedState();
        renderCurrentPagePair();
    }

    private byte[] loadPdfBytes(String src) throws IOException {
        if (src.startsWith("http://") || src.startsWith("https://")) {
            try (InputStream is = new URL(src).openStream()) { return is.readAllBytes(); }
        }
        if (src.startsWith("file:")) {
            try { return Files.readAllBytes(Paths.get(URI.create(src))); }
            catch (Exception ex) { return Files.readAllBytes(Paths.get(new URL(src).getPath())); }
        }
        return Files.readAllBytes(Paths.get(src));
    }

    private PDDocument loadPdfDocument(String src) throws IOException {
        if (src == null || src.isBlank()) throw new IOException("Source PDF vide");
        if (src.startsWith("http://") || src.startsWith("https://")) {
            try (InputStream is = new URL(src).openStream()) { return PDDocument.load(is.readAllBytes()); }
        }
        if (src.startsWith("file:")) {
            try { return PDDocument.load(new File(URI.create(src))); }
            catch (Exception ex) { return PDDocument.load(new File(new URL(src).getPath())); }
        }
        return PDDocument.load(new File(src));
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void saveState() {
        if (currentLivre == null) return;
        Path p = Paths.get(STATE_DIR, stateFile() + ".txt");
        try (BufferedWriter w = Files.newBufferedWriter(p)) {
            w.write("page=" + currentPagePair); w.newLine();
            w.write("bookmarked=" + isBookmarked); w.newLine();
            w.write("timestamp=" + System.currentTimeMillis());
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void loadSavedState() {
        if (currentLivre == null) return;
        Path p = Paths.get(STATE_DIR, stateFile() + ".txt");
        if (!Files.exists(p)) return;
        try (BufferedReader r = Files.newBufferedReader(p)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("page=")) {
                    int v = Integer.parseInt(line.substring(5));
                    if (v >= 0 && v < totalPairs) currentPagePair = v;
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void saveBookmarkState() {
        if (currentLivre == null) return;
        Path p = Paths.get(STATE_DIR, stateFile() + "_bookmark.txt");
        try (BufferedWriter w = Files.newBufferedWriter(p)) {
            w.write(isBookmarked ? String.valueOf(currentPagePair * 2) : "-1");
        } catch (IOException e) { e.printStackTrace(); }
    }

    private int loadBookmarkedPage() {
        if (currentLivre == null) return -1;
        Path p = Paths.get(STATE_DIR, stateFile() + "_bookmark.txt");
        if (!Files.exists(p)) return -1;
        try { return Integer.parseInt(Files.readString(p).trim()); }
        catch (Exception e) { return -1; }
    }

    private String stateFile() {
        if (currentLivre != null && currentLivre.getId() != null)
            return "book_" + currentLivre.getId();
        return "book_temp";
    }
}