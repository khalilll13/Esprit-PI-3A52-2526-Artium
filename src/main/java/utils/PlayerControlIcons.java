package utils;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;

/**
 * Spotify-style play / pause vector icons for the global player button.
 */
public final class PlayerControlIcons {

    private static final Color ICON_FILL = Color.BLACK;
    private static final double BUTTON_SIZE = 32;
    private static final double BAR_WIDTH = 3;
    private static final double BAR_HEIGHT = 12;
    private static final double BAR_GAP = 4;

    private PlayerControlIcons() {
    }

    public static Node createPlayGraphic() {
        Polygon triangle = new Polygon(
                12.0, 10.0,
                12.0, 22.0,
                22.0, 16.0
        );
        triangle.setFill(ICON_FILL);
        return wrap(triangle);
    }

    public static Node createPauseGraphic() {
        Rectangle leftBar = new Rectangle(BAR_WIDTH, BAR_HEIGHT, ICON_FILL);
        Rectangle rightBar = new Rectangle(BAR_WIDTH, BAR_HEIGHT, ICON_FILL);
        HBox bars = new HBox(BAR_GAP, leftBar, rightBar);
        bars.setAlignment(Pos.CENTER);
        return wrap(bars);
    }

    private static StackPane wrap(Node icon) {
        StackPane pane = new StackPane(icon);
        pane.setMinSize(BUTTON_SIZE, BUTTON_SIZE);
        pane.setPrefSize(BUTTON_SIZE, BUTTON_SIZE);
        pane.setMaxSize(BUTTON_SIZE, BUTTON_SIZE);
        pane.setAlignment(Pos.CENTER);
        pane.setMouseTransparent(true);
        return pane;
    }
}
