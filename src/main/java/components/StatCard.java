package components;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class StatCard extends VBox {

    public StatCard(String iconText, String iconCssClass, int count, String labelText) {
        getStyleClass().add("dashboard-card");
        setSpacing(8);
        setPadding(new Insets(14));

        StackPane iconCircle = new StackPane();
        iconCircle.getStyleClass().addAll("icon-circle", iconCssClass);
        Label iconLabel = new Label(iconText);
        iconLabel.getStyleClass().add("card-icon-text");
        iconCircle.getChildren().add(iconLabel);

        Label countLabel = new Label(String.valueOf(count));
        countLabel.getStyleClass().add("stat-number");

        Label titleLabel = new Label(labelText);
        titleLabel.getStyleClass().add("stat-label");

        getChildren().addAll(iconCircle, countLabel, titleLabel);
    }
}

