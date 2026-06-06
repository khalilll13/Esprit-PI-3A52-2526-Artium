package components;

import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class SliderCaptcha extends StackPane {

    private final BooleanProperty verified = new SimpleBooleanProperty(false);
    private static final double TRACK_HEIGHT = 44;
    private static final double THUMB_SIZE  = 36;
    private static final double THRESHOLD   = 0.88;

    public SliderCaptcha() {
        setMaxWidth(Double.MAX_VALUE);
        setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 22; -fx-border-radius: 22; -fx-border-color: #cbd5e1; -fx-border-width: 1;");
        setPrefHeight(TRACK_HEIGHT);

        // Fill (progress)
        Rectangle fill = new Rectangle(THUMB_SIZE, TRACK_HEIGHT);
        fill.setFill(javafx.scene.paint.Color.web("#1fc4d7"));
        fill.setArcWidth(44); fill.setArcHeight(44);
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);

        // Thumb
        StackPane thumb = new StackPane();
        thumb.setPrefSize(THUMB_SIZE, THUMB_SIZE);
        thumb.setMaxSize(THUMB_SIZE, THUMB_SIZE);
        thumb.setStyle("-fx-background-color: white; -fx-background-radius: 999; -fx-border-radius: 999; -fx-border-color: #c7d2fe; -fx-border-width: 2; -fx-cursor: hand;");
        Label arrow = new Label("→");
        arrow.setStyle("-fx-font-size: 14; -fx-text-fill: #4f46e5;");
        thumb.getChildren().add(arrow);
        StackPane.setAlignment(thumb, Pos.CENTER_LEFT);

        // Hint label
        Label hint = new Label("Faites glisser jusqu'au bout →");
        hint.setStyle("-fx-font-size: 13; -fx-text-fill: #94a3b8;");

        getChildren().addAll(fill, hint, thumb);

        // Drag logic
        final double[] startX = {0};
        final double[] thumbX = {4};

        thumb.setOnMousePressed(e -> startX[0] = e.getSceneX() - thumbX[0]);
        thumb.setOnMouseDragged(e -> {
            if (verified.get()) return;
            double trackW = getWidth() - THUMB_SIZE - 8;
            double newX = Math.min(Math.max(e.getSceneX() - startX[0], 4), trackW + 4);
            thumbX[0] = newX;
            thumb.setTranslateX(newX - 4);
            fill.setWidth(newX + THUMB_SIZE - 4);

            if ((newX - 4) / trackW >= THRESHOLD) {
                onVerified(thumb, fill, hint, arrow);
            }
        });
        thumb.setOnMouseReleased(e -> {
            if (!verified.get()) {
                // Snap back
                TranslateTransition snap = new TranslateTransition(Duration.millis(200), thumb);
                snap.setToX(0);
                snap.play();
                fill.setWidth(THUMB_SIZE);
                thumbX[0] = 4;
            }
        });
    }

    private void onVerified(StackPane thumb, Rectangle fill, Label hint, Label arrow) {
        verified.set(true);
        double trackW = getWidth() - THUMB_SIZE - 8;
        thumb.setTranslateX(trackW);
        fill.setWidth(getWidth());
        thumb.setStyle("-fx-background-color: #22c55e; -fx-background-radius: 999; -fx-border-radius: 999;");
        arrow.setText("✓");
        arrow.setStyle("-fx-font-size: 14; -fx-text-fill: white;");
        hint.setVisible(false);
    }

    public BooleanProperty verifiedProperty() { return verified; }
    public boolean isVerified() { return verified.get(); }
}