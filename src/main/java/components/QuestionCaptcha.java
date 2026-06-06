package components;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Random;

public class QuestionCaptcha extends VBox {

    public record Question(String text, int answer) {}

    private static final List<Question> POOL = List.of(
            new Question("Combien font 7 × 8 ?", 56),
            new Question("Quel est le résultat de 144 ÷ 12 ?", 12),
            new Question("Combien font 15 + 27 ?", 42),
            new Question("9 au carré vaut ?", 81),
            new Question("100 − 37 = ?", 63),
            new Question("Combien de minutes dans 3 heures ?", 180),
            new Question("Quel est le double de 64 ?", 128)
    );

    private final BooleanProperty verified = new SimpleBooleanProperty(false);
    private final Question question;

    public QuestionCaptcha() {
        setSpacing(10);
        question = POOL.get(new Random().nextInt(POOL.size()));

        Label qLabel = new Label(question.text());
        qLabel.setWrapText(true);
        qLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #334155; -fx-font-weight: 700;");

        TextField answerField = new TextField();
        answerField.setPromptText("Votre réponse");
        answerField.getStyleClass().add("auth-input");
        answerField.setPrefWidth(140);

        Button validateBtn = new Button("Valider");
        validateBtn.getStyleClass().add("auth-primary-button");

        Label msgLabel = new Label();
        msgLabel.setStyle("-fx-font-size: 12; -fx-font-weight: 600;");

        HBox row = new HBox(10, answerField, validateBtn);
        row.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(qLabel, row, msgLabel);

        Runnable check = () -> {
            String raw = answerField.getText().trim();
            try {
                int val = Integer.parseInt(raw);
                if (val == question.answer()) {
                    verified.set(true);
                    msgLabel.setText("Bonne réponse ✓");
                    msgLabel.setStyle("-fx-text-fill: #16a34a; -fx-font-size: 12; -fx-font-weight: 700;");
                    answerField.setDisable(true);
                    validateBtn.setDisable(true);
                } else {
                    msgLabel.setText("Réponse incorrecte. Réessayez.");
                    msgLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12;");
                    answerField.clear();
                    answerField.requestFocus();
                }
            } catch (NumberFormatException ex) {
                msgLabel.setText("Entrez un nombre entier.");
                msgLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12;");
            }
        };

        validateBtn.setOnAction(e -> check.run());
        answerField.setOnAction(e -> check.run());
    }

    public BooleanProperty verifiedProperty() { return verified; }
    public boolean isVerified() { return verified.get(); }
}