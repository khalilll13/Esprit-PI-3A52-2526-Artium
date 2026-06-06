package components.studio;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import java.util.Locale;

public final class TimeRulerPane extends Region {
    private final Canvas canvas = new Canvas();
    private long totalFrames;
    private int sampleRate = 44100;
    private long playheadFrame;
    private double zoom = 1.0;

    public TimeRulerPane() {
        getChildren().add(canvas);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        setPrefHeight(28);
        setStyle("-fx-background-color: #0f172a;");
    }

    public void configure(long totalFrames, int sampleRate, long playhead, double zoom) {
        this.totalFrames = totalFrames;
        this.sampleRate = sampleRate;
        this.playheadFrame = playhead;
        this.zoom = zoom;
        redraw();
    }

    private void redraw() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0) {
            return;
        }
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        g.setFill(Color.web("#1a1a1a"));
        g.fillRect(0, 0, w, h);
        if (totalFrames <= 0 || sampleRate <= 0) {
            return;
        }
        double duration = totalFrames / (double) sampleRate;
        double visible = duration / zoom;
        int marks = (int) Math.max(4, w / 80);
        g.setStroke(Color.web("#404040"));
        g.setFill(Color.web("#a3a3a3"));
        for (int i = 0; i <= marks; i++) {
            double x = i * w / marks;
            double t = i * visible / marks;
            g.strokeLine(x, h - 6, x, h);
            g.fillText(formatTime(t), x + 2, 12);
        }
        double px = (playheadFrame / (double) sampleRate / visible) * w;
        g.setStroke(Color.web("#38bdf8"));
        g.strokeLine(px, 0, px, h);
    }

    private static String formatTime(double sec) {
        int m = (int) (sec / 60);
        int s = (int) (sec % 60);
        return String.format(Locale.ROOT, "%d:%02d", m, s);
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        redraw();
    }
}
