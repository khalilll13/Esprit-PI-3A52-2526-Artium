package components.studio;

import audio.model.TimeSelection;
import audio.model.WaveformPeaks;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * Central waveform with zoom, playhead, and selection.
 */
public final class WaveformPane extends Region {
    private final Canvas canvas = new Canvas();
    private WaveformPeaks peaks;
    private long playheadFrame;
    private TimeSelection selection;
    private double zoom = 1.0;
    private double scrollRatio;
    private LongConsumer playheadCallback;
    private Consumer<TimeSelection> selectionCallback;
    private boolean selecting;
    private long selectStart;
    private List<TimeSelection> slices;

    public WaveformPane() {
        setFocusTraversable(true);
        getChildren().add(canvas);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        canvas.setOnMousePressed(this::onPress);
        canvas.setOnMouseDragged(this::onDrag);
        canvas.setOnMouseReleased(this::onRelease);
        canvas.setOnScroll(e -> {
            if (e.isControlDown()) {
                zoom = Math.max(0.5, Math.min(32, zoom * (e.getDeltaY() > 0 ? 1.1 : 0.9)));
            } else {
                scrollRatio = Math.max(0, Math.min(1, scrollRatio - e.getDeltaY() * 0.001));
            }
            redraw();
        });
        setStyle("-fx-background-color: #080f1e;");
    }

    public void setPeaks(WaveformPeaks peaks) {
        this.peaks = peaks;
        redraw();
    }

    public void setPlayheadFrame(long frame) {
        this.playheadFrame = frame;
        redraw();
    }

    public void setSelection(TimeSelection selection) {
        this.selection = selection;
        redraw();
    }

    public void setSlices(List<TimeSelection> slices) {
        this.slices = slices;
        redraw();
    }

    public void setZoom(double zoom) {
        this.zoom = zoom;
        redraw();
    }

    public double getZoom() {
        return zoom;
    }

    public void setOnPlayheadChanged(LongConsumer c) {
        this.playheadCallback = c;
    }

    public void setOnSelectionChanged(Consumer<TimeSelection> c) {
        this.selectionCallback = c;
    }

    private void onPress(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY) {
            return;
        }
        requestFocus();
        long frame = xToFrame(e.getX());
        if (e.isShiftDown()) {
            selecting = true;
            selectStart = frame;
        } else {
            if (slices != null && !slices.isEmpty()) {
                TimeSelection clickedSlice = null;
                for (TimeSelection slice : slices) {
                    if (frame >= slice.getStartSample() && frame <= slice.getEndSample()) {
                        clickedSlice = slice;
                        break;
                    }
                }
                if (clickedSlice != null) {
                    selection = clickedSlice;
                    playheadFrame = frame;
                    if (selectionCallback != null) {
                        selectionCallback.accept(selection);
                    }
                } else {
                    playheadFrame = frame;
                    selection = null;
                    if (selectionCallback != null) {
                        selectionCallback.accept(null);
                    }
                }
            } else {
                playheadFrame = frame;
            }
            
            if (playheadCallback != null) {
                playheadCallback.accept(playheadFrame);
            }
        }
        redraw();
    }

    private void onDrag(MouseEvent e) {
        if (!selecting) {
            return;
        }
        long end = xToFrame(e.getX());
        selection = new TimeSelection(selectStart, end);
        if (selectionCallback != null) {
            selectionCallback.accept(selection);
        }
        redraw();
    }

    private void onRelease(MouseEvent e) {
        selecting = false;
        setCursor(Cursor.DEFAULT);
    }

    private long xToFrame(double x) {
        if (peaks == null || peaks.getTotalFrames() == 0) {
            return 0;
        }
        double w = getWidth();
        if (w <= 0) {
            return 0;
        }
        double visible = peaks.getTotalFrames() / zoom;
        long start = (long) (scrollRatio * Math.max(0, peaks.getTotalFrames() - visible));
        return start + (long) ((x / w) * visible);
    }

    public void redraw() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        g.setFill(Color.web("#121212"));
        g.fillRect(0, 0, w, h);

        if (peaks == null || peaks.getBucketCount() == 0) {
            g.setFill(Color.web("#737373"));
            g.fillText("Forme d'onde — en attente du fichier audio", 20, h / 2);
            return;
        }

        int buckets = peaks.getBucketCount();
        long total = peaks.getTotalFrames();
        double visible = total / zoom;
        long startFrame = (long) (scrollRatio * Math.max(0, total - visible));
        int startBucket = (int) (startFrame * buckets / total);
        int endBucket = (int) ((startFrame + visible) * buckets / total);
        endBucket = Math.min(buckets, Math.max(startBucket + 1, endBucket));

        double midY = h / 2;
        double amp = h * 0.42;
        g.setStroke(Color.web("#3b82f6"));
        g.setLineWidth(1.0);
        for (int b = startBucket; b < endBucket; b++) {
            double x = (b - startBucket) * w / (endBucket - startBucket);
            float min = peaks.getMins()[b];
            float max = peaks.getMaxs()[b];
            g.strokeLine(x, midY - min * amp, x, midY - max * amp);
        }

        if (selection != null && !selection.isEmpty()) {
            double x1 = frameToX(selection.getStartSample(), total, visible, startFrame, w);
            double x2 = frameToX(selection.getEndSample(), total, visible, startFrame, w);
            g.setFill(Color.web("#3b82f6", 0.22));
            g.fillRect(Math.min(x1, x2), 0, Math.abs(x2 - x1), h);
        }

        // Dessiner les limites des tranches (diviseurs verticaux en orange pointillé)
        if (slices != null && !slices.isEmpty()) {
            g.setStroke(Color.web("#f59e0b")); // Orange ambré professionnel
            g.setLineWidth(1.5);
            g.setLineDashes(new double[]{4, 4});
            for (int i = 1; i < slices.size(); i++) {
                TimeSelection slice = slices.get(i);
                double sx = frameToX(slice.getStartSample(), total, visible, startFrame, w);
                if (sx >= 0 && sx <= w) {
                    g.strokeLine(sx, 0, sx, h);
                }
            }
            g.setLineDashes(null); // Réinitialiser le style de ligne
        }

        double px = frameToX(playheadFrame, total, visible, startFrame, w);
        g.setStroke(Color.web("#38bdf8"));
        g.setLineWidth(2);
        g.strokeLine(px, 0, px, h);
    }

    private double frameToX(long frame, long total, double visible, long startFrame, double w) {
        return ((frame - startFrame) / visible) * w;
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        redraw();
    }
}
