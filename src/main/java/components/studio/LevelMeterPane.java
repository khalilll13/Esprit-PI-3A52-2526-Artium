package components.studio;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

public final class LevelMeterPane extends Region {
    private final Canvas canvas = new Canvas();
    private float peakDb;
    private float rmsDb;
    private float lufs;
    private boolean clipping;

    public LevelMeterPane() {
        getChildren().add(canvas);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        setPrefWidth(48);
        setStyle("-fx-background-color: #1a1a1a;");
    }

    public void update(float peakDb, float rmsDb, float lufs, boolean clipping) {
        this.peakDb = peakDb;
        this.rmsDb = rmsDb;
        this.lufs = lufs;
        this.clipping = clipping;
        redraw();
    }

    private void redraw() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        drawBar(g, w * 0.2, peakDb, Color.web("#ef4444"));
        drawBar(g, w * 0.5, rmsDb, Color.web("#22c55e"));
        drawBar(g, w * 0.8, lufs + 60, Color.web("#3b82f6"));
        if (clipping) {
            g.setFill(Color.web("#ef4444"));
            g.fillRect(0, 0, w, 8);
        }
    }

    private void drawBar(GraphicsContext g, double x, float db, Color color) {
        double h = getHeight();
        double norm = Math.max(0, Math.min(1, (db + 60) / 60));
        g.setFill(Color.web("#262626"));
        g.fillRect(x - 8, 0, 16, h);
        g.setFill(color);
        g.fillRect(x - 8, h * (1 - norm), 16, h * norm);
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        redraw();
    }
}
