package components.studio;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

public final class SpectrumPane extends Region {
    private final Canvas canvas = new Canvas();
    private float[] spectrum = new float[0];
    private float[][] spectrogram;

    public SpectrumPane() {
        getChildren().add(canvas);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        setPrefHeight(80);
        setStyle("-fx-background-color: #0a1020;");
    }

    public void setSpectrum(float[] spectrum) {
        this.spectrum = spectrum != null ? spectrum : new float[0];
        redraw();
    }

    public void setSpectrogram(float[][] spectrogram) {
        this.spectrogram = spectrogram;
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
        if (spectrogram != null && spectrogram.length > 0) {
            int tw = spectrogram.length;
            int fh = spectrogram[0].length;
            for (int t = 0; t < tw; t++) {
                for (int f = 0; f < fh; f++) {
                    float v = spectrogram[t][f];
                    double hue = 240 - v * 120;
                    g.setFill(Color.hsb(hue, 0.8, 0.2 + v * 0.7));
                    g.fillRect(t * w / tw, f * h / fh, w / tw + 1, h / fh + 1);
                }
            }
            return;
        }
        if (spectrum.length == 0) {
            return;
        }
        float max = 0.001f;
        for (float v : spectrum) {
            max = Math.max(max, v);
        }
        double barW = w / spectrum.length;
        for (int i = 0; i < spectrum.length; i++) {
            double bh = (spectrum[i] / max) * h;
            g.setFill(Color.web("#2563eb"));
            g.fillRect(i * barW, h - bh, barW, bh);
        }
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        redraw();
    }
}
