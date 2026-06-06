package controllers.artist;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.scene.effect.DropShadow;
import javafx.util.Duration;
import services.OeuvreService;
import utils.ImageUrlUtils;
import utils.UserSession;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatistiquesArtisteController {

    @FXML
    private Label chartTitleLabel;

    @FXML
    private Label attributeLabel;

    @FXML
    private Button prevButton;

    @FXML
    private Button nextButton;

    @FXML
    private StackPane pieChartContainer;

    @FXML
    private Button prevMonthButton;

    @FXML
    private Button nextMonthButton;

    @FXML
    private Label monthLabel;

    @FXML
    private StackPane commentsLineChartContainer;

    @FXML
    private VBox topAmateursContainer;

    private final OeuvreService oeuvreService = new OeuvreService();

    private Integer currentArtistId;
    private List<Map<String, Object>> collectionsStats = new ArrayList<>();
    private boolean isAnimating = false;
    private boolean isLoadingMonthChart = false;

    private int currentAttributeMode = 0; // 0=Oeuvres, 1=Commentaires, 2=Likes, 3=Favoris
    private YearMonth currentCommentsMonth = YearMonth.now();

    private static final String[] ATTRIBUTES = {"Oeuvres", "Commentaires", "Likes", "Favoris"};
    private static final String[] CHART_COLORS = {
            "#6366F1", "#EC4899", "#F59E0B", "#10B981",
            "#3B82F6", "#8B5CF6", "#06B6D4", "#EF4444",
            "#14B8A6", "#F97316"
    };
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    @FXML
    public void initialize() {
        try {
            currentArtistId = UserSession.getCurrentUserId();
            if (currentArtistId != null) {
                prevButton.setOnAction(e -> switchAttribute(-1));
                nextButton.setOnAction(e -> switchAttribute(1));

                prevMonthButton.setOnAction(e -> switchCommentsMonth(-1));
                nextMonthButton.setOnAction(e -> switchCommentsMonth(1));

                loadStatistics();
            }
        } catch (Exception e) {
            System.err.println("Erreur init statistiques: " + e.getMessage());
        }
    }

    private void loadStatistics() {
        new Thread(() -> {
            try {
                collectionsStats = oeuvreService.getCollectionsStatsForArtiste(currentArtistId);
                Platform.runLater(() -> {
                    currentAttributeMode = 0;
                    renderAttributeState(false, 0);
                    updateMonthLabel();
                    loadCommentsChartForMonth();
                    loadTopAmateurs();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    pieChartContainer.getChildren().setAll(buildEmptyChartNode("Erreur chargement statistiques."));
                    commentsLineChartContainer.getChildren().setAll(buildEmptyChartNode("Erreur chargement commentaires."));
                    topAmateursContainer.getChildren().setAll(new Label("Erreur chargement amateurs."));
                });
            }
        }).start();
    }

    private void loadTopAmateurs() {
        new Thread(() -> {
            try {
                List<Map<String, Object>> topAmateurs = oeuvreService.getTop3AmateursForArtiste(currentArtistId);
                Platform.runLater(() -> renderTopAmateurs(topAmateurs));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    topAmateursContainer.getChildren().setAll(new Label("Aucune donnée d'engagement."));
                });
            }
        }).start();
    }

    private void renderTopAmateurs(List<Map<String, Object>> amateurs) {
        topAmateursContainer.getChildren().clear();
        if (amateurs == null || amateurs.isEmpty()) {
            topAmateursContainer.getChildren().setAll(new Label("Aucun amateur pour le moment."));
            return;
        }

        for (int i = 0; i < amateurs.size(); i++) {
            Map<String, Object> amateur = amateurs.get(i);
            HBox row = buildAmateurRow(amateur, i + 1);
            topAmateursContainer.getChildren().add(row);
        }
    }

    private HBox buildAmateurRow(Map<String, Object> amateur, int rank) {
        HBox row = new HBox(15);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("amateur-stat-row");
        row.setPadding(new Insets(8, 12, 8, 12));
        row.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 10; -fx-border-color: #e2e8f0; -fx-border-width: 1; -fx-border-radius: 10;");

        // Rank Label
        Label rankLabel = new Label("#" + rank);
        rankLabel.setStyle("-fx-font-weight: 800; -fx-font-size: 16px; -fx-text-fill: " + getRankColor(rank) + "; -fx-min-width: 30;");

        // Avatar
        StackPane avatar = buildAmateurAvatar(amateur);

        // Name and Stats
        VBox infoBox = new VBox(4);
        Label nameLabel = new Label(amateur.get("prenom") + " " + amateur.get("nom"));
        nameLabel.setStyle("-fx-font-weight: 700; -fx-font-size: 14px; -fx-text-fill: #1e293b;");

        HBox statsRow = new HBox(12);
        statsRow.getChildren().addAll(
                buildStatMiniChip("M12.1 18.55 10.55 17.14C5.4 12.47 2 9.39 2 5.6 2 2.52 4.42 0 7.5 0c1.74 0 3.41.81 4.5 2.09C13.09.81 14.76 0 16.5 0 19.58 0 22 2.52 22 5.6c0 3.79-3.4 6.87-8.55 11.55z", (Integer) amateur.get("likes_count"), "#ef4444"),
                buildStatMiniChip("M20 2H4c-1.1 0-1.99.9-1.99 2L2 22l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z", (Integer) amateur.get("comments_count"), "#3b82f6"),
                buildStatMiniChip("M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z", (Integer) amateur.get("favoris_count"), "#eab308")
        );

        infoBox.getChildren().addAll(nameLabel, statsRow);

        // Total Score Badge
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox scoreBox = new VBox(2);
        scoreBox.setAlignment(Pos.CENTER);
        Label scoreVal = new Label(String.valueOf(amateur.get("total_score")));
        scoreVal.setStyle("-fx-font-weight: 800; -fx-font-size: 18px; -fx-text-fill: #6366f1;");
        Label scoreLbl = new Label("points");
        scoreLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b; -fx-font-weight: 600; -fx-text-transform: uppercase;");
        scoreBox.getChildren().addAll(scoreVal, scoreLbl);

        row.getChildren().addAll(rankLabel, avatar, infoBox, spacer, scoreBox);
        return row;
    }

    private String getRankColor(int rank) {
        return switch (rank) {
            case 1 -> "#fbbf24"; // Gold
            case 2 -> "#94a3b8"; // Silver
            case 3 -> "#92400e"; // Bronze
            default -> "#64748b";
        };
    }

    private StackPane buildAmateurAvatar(Map<String, Object> amateur) {
        StackPane container = new StackPane();
        container.setPrefSize(44, 44);
        container.setMinSize(44, 44);

        String photoUrl = (String) amateur.get("photo_profil");
        String prenom = (String) amateur.get("prenom");
        String initials = prenom != null && !prenom.isEmpty() ? prenom.substring(0, 1).toUpperCase() : "?";

        if (photoUrl != null && !photoUrl.isEmpty()) {
            try {
                javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView(new javafx.scene.image.Image(photoUrl, true));
                imageView.setFitWidth(44);
                imageView.setFitHeight(44);
                imageView.setPreserveRatio(true);
                Circle clip = new Circle(22, 22, 22);
                imageView.setClip(clip);
                container.getChildren().add(imageView);
            } catch (Exception e) {
                container.getChildren().add(buildFallbackAvatar(initials));
            }
        } else {
            container.getChildren().add(buildFallbackAvatar(initials));
        }

        return container;
    }

    private StackPane buildFallbackAvatar(String initials) {
        StackPane fallback = new StackPane();
        Circle circle = new Circle(22, Color.web("#e0e7ff"));
        circle.setStroke(Color.web("#c7d2fe"));
        circle.setStrokeWidth(1);
        Label label = new Label(initials);
        label.setStyle("-fx-font-weight: 700; -fx-text-fill: #4338ca; -fx-font-size: 14px;");
        fallback.getChildren().addAll(circle, label);
        return fallback;
    }

    private HBox buildStatMiniChip(String svgPath, int count, String color) {
        SVGPath icon = new SVGPath();
        icon.setContent(svgPath);
        icon.setFill(Color.web(color));
        icon.setScaleX(0.7);
        icon.setScaleY(0.7);

        Label label = new Label(String.valueOf(count));
        label.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #475569;");

        HBox chip = new HBox(2, icon, label);
        chip.setAlignment(Pos.CENTER_LEFT);
        return chip;
    }

    private void switchAttribute(int direction) {
        if (isAnimating) {
            return;
        }
        int nextMode = (currentAttributeMode + direction + ATTRIBUTES.length) % ATTRIBUTES.length;
        renderAttributeState(true, direction > 0 ? 1 : -1, nextMode);
    }

    private void renderAttributeState(boolean animated, int direction) {
        renderAttributeState(animated, direction, currentAttributeMode);
    }

    private void renderAttributeState(boolean animated, int direction, int targetMode) {
        String attributeName = ATTRIBUTES[targetMode];
        chartTitleLabel.setText("🎨 Repartition des " + attributeName.toLowerCase(Locale.ROOT) + " par collection");

        if (!animated) {
            currentAttributeMode = targetMode;
            attributeLabel.setText(attributeName);
            pieChartContainer.getChildren().setAll(buildAttributeChartNode(targetMode));
            return;
        }

        isAnimating = true;
        prevButton.setDisable(true);
        nextButton.setDisable(true);

        animateAttributeLabel(attributeName, direction);
        animateChartSwitch(buildAttributeChartNode(targetMode), direction, () -> {
            currentAttributeMode = targetMode;
            isAnimating = false;
            prevButton.setDisable(false);
            nextButton.setDisable(false);
        });
    }

    private void switchCommentsMonth(int direction) {
        if (isLoadingMonthChart) {
            return;
        }
        currentCommentsMonth = currentCommentsMonth.plusMonths(direction);
        updateMonthLabel();
        loadCommentsChartForMonth();
    }

    private void updateMonthLabel() {
        monthLabel.setText(currentCommentsMonth.format(MONTH_FORMATTER));
    }

    private void loadCommentsChartForMonth() {
        isLoadingMonthChart = true;
        prevMonthButton.setDisable(true);
        nextMonthButton.setDisable(true);

        commentsLineChartContainer.getChildren().setAll(buildEmptyChartNode("Chargement..."));

        final YearMonth targetMonth = currentCommentsMonth;
        new Thread(() -> {
            try {
                Map<Integer, Integer> dailyComments = oeuvreService.getDailyCommentsForArtisteByMonth(currentArtistId, targetMonth);
                Platform.runLater(() -> {
                    commentsLineChartContainer.getChildren().setAll(buildCommentsLineChart(targetMonth, dailyComments));
                    isLoadingMonthChart = false;
                    prevMonthButton.setDisable(false);
                    nextMonthButton.setDisable(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    commentsLineChartContainer.getChildren().setAll(buildEmptyChartNode("Aucune donnee pour ce mois."));
                    isLoadingMonthChart = false;
                    prevMonthButton.setDisable(false);
                    nextMonthButton.setDisable(false);
                });
            }
        }).start();
    }

    private Node buildCommentsLineChart(YearMonth month, Map<Integer, Integer> dailyComments) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Jour du mois");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Commentaires");
        yAxis.setForceZeroInRange(true);

        LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.getStyleClass().add("stats-comments-line-chart");
        lineChart.setTitle("Evolution - " + month.format(MONTH_FORMATTER));
        lineChart.setLegendVisible(false);
        lineChart.setAnimated(false);
        lineChart.setCreateSymbols(true);
        lineChart.setHorizontalGridLinesVisible(true);
        lineChart.setVerticalGridLinesVisible(false);
        lineChart.setAlternativeColumnFillVisible(false);
        lineChart.setAlternativeRowFillVisible(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            int count = dailyComments.getOrDefault(day, 0);
            series.getData().add(new XYChart.Data<>(String.valueOf(day), count));
        }

        lineChart.getData().setAll(series);
        return lineChart;
    }

    private void animateAttributeLabel(String nextText, int direction) {
        TranslateTransition out = new TranslateTransition(Duration.millis(140), attributeLabel);
        out.setToX(direction > 0 ? -14 : 14);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(140), attributeLabel);
        fadeOut.setToValue(0.15);

        ParallelTransition exit = new ParallelTransition(out, fadeOut);
        exit.setOnFinished(e -> {
            attributeLabel.setText(nextText);
            attributeLabel.setTranslateX(direction > 0 ? 14 : -14);

            TranslateTransition in = new TranslateTransition(Duration.millis(180), attributeLabel);
            in.setToX(0);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(180), attributeLabel);
            fadeIn.setToValue(1);
            new ParallelTransition(in, fadeIn).play();
        });
        exit.play();
    }

    private void animateChartSwitch(Node newChart, int direction, Runnable onFinished) {
        Node oldChart = pieChartContainer.getChildren().isEmpty() ? null : pieChartContainer.getChildren().get(0);

        if (oldChart == null) {
            pieChartContainer.getChildren().setAll(newChart);
            onFinished.run();
            return;
        }

        newChart.setOpacity(0);
        newChart.setTranslateX(direction > 0 ? 44 : -44);
        pieChartContainer.getChildren().add(newChart);

        TranslateTransition outMove = new TranslateTransition(Duration.millis(220), oldChart);
        outMove.setToX(direction > 0 ? -44 : 44);
        FadeTransition outFade = new FadeTransition(Duration.millis(220), oldChart);
        outFade.setToValue(0);

        TranslateTransition inMove = new TranslateTransition(Duration.millis(220), newChart);
        inMove.setToX(0);
        FadeTransition inFade = new FadeTransition(Duration.millis(220), newChart);
        inFade.setToValue(1);

        ParallelTransition transition = new ParallelTransition(outMove, outFade, inMove, inFade);
        transition.setOnFinished(e -> {
            pieChartContainer.getChildren().setAll(newChart);
            onFinished.run();
        });
        transition.play();
    }

    private Node buildAttributeChartNode(int attributeMode) {
        if (collectionsStats.isEmpty()) {
            return buildEmptyChartNode("Aucune collection");
        }

        String metricKey = switch (attributeMode) {
            case 0 -> "oeuvresCount";
            case 1 -> "commentairesCount";
            case 2 -> "likesCount";
            default -> "favorisCount";
        };

        int totalMetric = collectionsStats.stream()
                .mapToInt(stat -> (Integer) stat.get(metricKey))
                .sum();

        if (totalMetric <= 0) {
            return buildEmptyChartNode("Aucune donnee pour " + ATTRIBUTES[attributeMode].toLowerCase(Locale.ROOT));
        }

        PieChart pieChart = new PieChart();
        pieChart.getStyleClass().add("stats-pie-chart");
        pieChart.setLegendVisible(false);
        pieChart.setLabelsVisible(false);
        pieChart.setPrefSize(420, 280);
        pieChart.setMinSize(420, 280);

        List<PieChart.Data> dataItems = collectionsStats.stream()
                .map(stat -> new PieChart.Data(
                        stat.get("collectionTitre").toString(),
                        (Integer) stat.get(metricKey)
                ))
                .toList();

        pieChart.setData(FXCollections.observableArrayList(dataItems));

        VBox legendBox = buildCustomLegend(dataItems, totalMetric);
        HBox chartRow = new HBox(10, pieChart, legendBox);
        chartRow.setAlignment(Pos.CENTER_LEFT);
        chartRow.setPadding(new Insets(4, 0, 0, 0));
        HBox.setHgrow(pieChart, Priority.ALWAYS);

        Platform.runLater(() -> applySliceColors(dataItems, totalMetric));

        return chartRow;
    }

    private VBox buildCustomLegend(List<PieChart.Data> dataItems, double totalMetric) {
        VBox legendBox = new VBox(7);
        legendBox.getStyleClass().add("stats-pie-legend");
        legendBox.setMinWidth(220);
        legendBox.setPrefWidth(220);

        for (int i = 0; i < dataItems.size(); i++) {
            PieChart.Data data = dataItems.get(i);
            String color = CHART_COLORS[i % CHART_COLORS.length];
            double percentage = totalMetric > 0 ? (data.getPieValue() / totalMetric) * 100 : 0;

            Circle dot = new Circle(5);
            dot.setStyle("-fx-fill: " + color + ";");

            Label nameLabel = new Label(data.getName());
            nameLabel.getStyleClass().add("stats-pie-legend-label");
            nameLabel.setMaxWidth(140);
            nameLabel.setEllipsisString("...");

            Label percentLabel = new Label(String.format("%.1f%% (%d)", percentage, (int)data.getPieValue()));
            percentLabel.setStyle("-fx-font-weight: 700; -fx-text-fill: #64748b; -fx-font-size: 11px;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox item = new HBox(8, dot, nameLabel, spacer, percentLabel);
            item.getStyleClass().add("stats-pie-legend-item");
            item.setAlignment(Pos.CENTER_LEFT);
            legendBox.getChildren().add(item);
        }

        return legendBox;
    }

    private void applySliceColors(List<PieChart.Data> dataItems, double totalMetric) {
        for (int i = 0; i < dataItems.size(); i++) {
            PieChart.Data data = dataItems.get(i);
            String color = CHART_COLORS[i % CHART_COLORS.length];
            Node node = data.getNode();

            if (node != null) {
                node.setStyle("-fx-pie-color: " + color + "; -fx-cursor: hand;");
                
                double percentage = totalMetric > 0 ? (data.getPieValue() / totalMetric) * 100 : 0;
                String tooltipText = String.format("%s\n%.1f%% (%d)", data.getName(), percentage, (int)data.getPieValue());
                Tooltip tooltip = new Tooltip(tooltipText);
                tooltip.setShowDelay(Duration.millis(100));
                tooltip.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
                Tooltip.install(node, tooltip);

                node.setOnMouseEntered(e -> {
                      node.setScaleX(1.05);
                      node.setScaleY(1.05);
                      node.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.15)));
                      node.toFront();
                  });

                 node.setOnMouseExited(e -> {
                     node.setScaleX(1);
                     node.setScaleY(1);
                     node.setEffect(null);
                 });
            }
        }
    }

    private Node buildEmptyChartNode(String message) {
        Label emptyLabel = new Label(message);
        emptyLabel.setStyle("-fx-text-fill: #9CA3AF; -fx-font-size: 14;");
        StackPane pane = new StackPane(emptyLabel);
        pane.setPrefHeight(280);
        return pane;
    }
}
