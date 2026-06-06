package controllers;

import entities.CollectionOeuvre;
import entities.Livre;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import services.CollectionService;
import services.JdbcCollectionService;
import services.JdbcLivreService;
import services.LivreService;

import java.io.File;
import java.io.IOException;
import java.sql.SQLDataException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LivresController {
    private final LivreService livreService = new JdbcLivreService();
    private final CollectionService collectionService = new JdbcCollectionService();
    private final ObservableList<Livre> livres = FXCollections.observableArrayList();
    private final ObservableList<CollectionOeuvre> collections = FXCollections.observableArrayList();
    private final List<Livre> allLivres = new ArrayList<>();
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(250));

    // More books per page now that cards are smaller
    private static final int BOOKS_PER_PAGE = 6;
    private int currentPage = 0;

    private static final String[] PALETTE = {
            "#6366f1","#06b6d4","#10b981","#f59e0b",
            "#ef4444","#8b5cf6","#ec4899","#14b8a6",
            "#f97316","#84cc16","#0ea5e9","#a855f7"
    };
    private static final String[] AUTHOR_PALETTE = {
            "#f43f5e","#d946ef","#0ea5e9","#f97316",
            "#6366f1","#22c55e","#eab308","#14b8a6",
            "#a855f7","#fb923c","#34d399","#60a5fa"
    };

    @FXML private TextField searchField;
    @FXML private ScrollPane cardsScroll;
    @FXML private TilePane cardsTile;
    @FXML private Button addBookButton;
    @FXML private VBox statisticsContainer;
    @FXML private Button prevPageBtn;
    @FXML private Button nextPageBtn;
    @FXML private Label pageInfoLabel;

    @FXML
    public void initialize() {
        loadCollections();
        refresh();
        if (searchField != null) {
            searchDebounce.setOnFinished(e -> applyFilter(searchField.getText()));
            searchField.textProperty().addListener((obs, o, n) -> { searchDebounce.stop(); searchDebounce.playFromStart(); });
        }
        if (statisticsContainer != null) updateStatistics();
    }

    private void loadCollections() {
        try { collections.setAll(collectionService.getAll()); }
        catch (SQLDataException e) { showError("Erreur collections", e.getMessage()); }
    }

    @FXML private void onSearch()   { currentPage = 0; applyFilter(searchField.getText()); }
    @FXML private void onRefresh()  { currentPage = 0; refresh(); }
    @FXML private void onPrevPage() { if (currentPage > 0) { currentPage--; applyFilter(searchField.getText()); } }
    @FXML private void onNextPage() { currentPage++; applyFilter(searchField.getText()); }

    // Dialog made smaller: 560x500 instead of 760x680
    private void showFormDialog(Livre livre) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/pages/LivreForm.fxml"));
            Node formContent = loader.load();
            LivreFormController formController = loader.getController();
            formController.setCollections(new ArrayList<>(collections));
            formController.setLivre(livre);

            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle(livre == null ? "Ajouter un livre" : "Modifier le livre");
            dialog.getDialogPane().setContent(formContent);
            dialog.getDialogPane().getButtonTypes().clear();
            dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CANCEL);
            Button hidden = (Button) dialog.getDialogPane().lookupButton(javafx.scene.control.ButtonType.CANCEL);
            if (hidden != null) { hidden.setManaged(false); hidden.setVisible(false); }
            dialog.getDialogPane().getStyleClass().addAll("custom-dialog", "book-form-dialog");
            if (cardsTile != null && cardsTile.getScene() != null)
                dialog.getDialogPane().getStylesheets().setAll(cardsTile.getScene().getStylesheets());
            dialog.getDialogPane().setPrefSize(590, 580);
            dialog.getDialogPane().setMaxSize(590, 580);
            dialog.getDialogPane().setStyle(
                    "-fx-background-color: white; -fx-background-radius: 20;"
            );
            dialog.showAndWait();

            Livre result = formController.getResultLivre();
            if (result != null) persistLivre(result, livre == null);
        } catch (IOException e) {
            showError("Erreur", "Impossible d'ouvrir le formulaire: " + e.getMessage());
        }
    }

    private void persistLivre(Livre livre, boolean isCreate) {
        try {
            if (isCreate) { livreService.add(livre);    showInfo("Ajouté",   "Livre ajouté avec succès."); }
            else           { livreService.update(livre); showInfo("Modifié", "Livre modifié avec succès."); }
            refresh();
        } catch (SQLDataException e) { showError("Erreur", e.getMessage()); }
    }

    @FXML private void onShowForm() { showFormDialog(null); }
    @FXML private void onHideForm() { }

    private void refresh() {
        try {
            allLivres.clear();
            allLivres.addAll(livreService.getAll());
            applyFilter(searchField != null ? searchField.getText() : "");
            if (statisticsContainer != null) updateStatistics();
        } catch (SQLDataException e) { showError("Chargement impossible", e.getMessage()); }
    }

    private void applyFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        List<Livre> filtered = allLivres.stream()
                .filter(l -> q.isEmpty() || contains(l.getTitre(), q) || contains(l.getCategorie(), q) || contains(l.getAuteur(), q))
                .toList();
        livres.setAll(filtered);
        updateCards(filtered);
    }

    private void updateCards(List<Livre> items) {
        if (cardsTile == null) return;
        int totalPages = Math.max(1, (int) Math.ceil((double) items.size() / BOOKS_PER_PAGE));
        if (currentPage >= totalPages) currentPage = totalPages - 1;
        int start = currentPage * BOOKS_PER_PAGE;
        List<Livre> page = items.subList(start, Math.min(start + BOOKS_PER_PAGE, items.size()));
        cardsTile.getChildren().setAll(page.stream().map(this::createBookCard).toList());
        if (prevPageBtn   != null) prevPageBtn.setDisable(currentPage <= 0);
        if (nextPageBtn   != null) nextPageBtn.setDisable(currentPage >= totalPages - 1);
        if (pageInfoLabel != null) pageInfoLabel.setText(String.format("Page %d / %d", currentPage + 1, totalPages));
    }

    // Cards are now half-size: 150px wide, compact cover, icon-only buttons
    private Node createBookCard(Livre livre) {
        VBox card = new VBox(10);
        card.setPrefWidth(190);
        card.setMaxWidth(190);
        card.setMinWidth(190);
        card.setPadding(new Insets(10));
        card.setAlignment(Pos.TOP_CENTER);
        card.setStyle(cardStyle(false));

        ImageView imageView = new ImageView();
        imageView.setFitWidth(120); imageView.setFitHeight(160);
        imageView.setPreserveRatio(true);
        imageView.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 5, 0, 0, 2);");
        if (livre.getImage() != null && !livre.getImage().isBlank())
            imageView.setImage(toImage(livre.getImage()));

        StackPane imgBox = new StackPane(imageView);
        imgBox.setPrefSize(120, 160); imgBox.setMaxSize(90, 120);
        imgBox.setStyle("-fx-background-color: #f3f4f6; -fx-background-radius: 6; -fx-alignment: center;");

        String cat = livre.getCategorie() != null ? livre.getCategorie() : "—";
        Label catBadge = new Label(cat);
        catBadge.setStyle("-fx-background-color: #eff6ff; -fx-text-fill: #3b82f6; -fx-font-size: 9; "
                + "-fx-font-weight: bold; -fx-padding: 2 7; -fx-background-radius: 20;");
        catBadge.setMaxWidth(130);

        Label title = new Label(livre.getTitre());
        title.setWrapText(true); title.setAlignment(Pos.CENTER);
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #111827;");
        title.setMaxWidth(130); title.setMaxHeight(34);

        Label author = new Label(livre.getAuteur() != null ? livre.getAuteur() : "N/A");
        author.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");
        author.setMaxWidth(130);

        Label price = new Label(String.format("%.2f TND", livre.getPrixLocation() != null ? livre.getPrixLocation() : 0));
        price.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #4f46e5;");

        Button editBtn   = new Button("Modifier"); styleSmallBtn(editBtn,   false);
        Button deleteBtn = new Button("Supprimer"); styleSmallBtn(deleteBtn, true);
        editBtn.setOnAction(e   -> showFormDialog(livre));
        deleteBtn.setOnAction(e -> onDeleteLivre(livre));

        HBox actions = new HBox(6, editBtn, deleteBtn);
        actions.setAlignment(Pos.CENTER);
        card.getChildren().addAll(imgBox, catBadge, title, author, price, actions);

        ScaleTransition si = new ScaleTransition(Duration.millis(150), card); si.setToX(1.05); si.setToY(1.05);
        ScaleTransition so = new ScaleTransition(Duration.millis(150), card); so.setToX(1.0);  so.setToY(1.0);
        card.setOnMouseEntered(e -> { card.setStyle(cardStyle(true));  si.play(); });
        card.setOnMouseExited (e -> { card.setStyle(cardStyle(false)); so.play(); });

        card.setOpacity(0); card.setTranslateY(8);
        FadeTransition ft = new FadeTransition(Duration.millis(250), card); ft.setToValue(1); ft.play();
        TranslateTransition tt = new TranslateTransition(Duration.millis(250), card); tt.setToY(0); tt.play();
        return card;
    }

    private String cardStyle(boolean hovered) {
        if (hovered)
            return "-fx-background-color: #ffffff; "
                    + "-fx-border-color: transparent; "
                    + "-fx-background-radius: 16; "
                    + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 20, 0, 0, 6);";

        return "-fx-background-color: #ffffff; "
                + "-fx-border-color: transparent; "
                + "-fx-background-radius: 16; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 10, 0, 0, 3);";
    }

    private void styleSmallBtn(Button btn, boolean danger) {
        String base = danger
                ? "-fx-background-color: #ef4444; -fx-text-fill: white; "
                + "-fx-font-size: 12px; -fx-font-weight: bold; "
                + "-fx-background-radius: 10; -fx-padding: 6 14; -fx-cursor: hand;"
                : "-fx-background-color: #6366f1; -fx-text-fill: white; "
                + "-fx-font-size: 12px; -fx-font-weight: bold; "
                + "-fx-background-radius: 10; -fx-padding: 6 14; -fx-cursor: hand;";

        String hover = danger
                ? "-fx-background-color: #dc2626; -fx-text-fill: white;"
                : "-fx-background-color: #4f46e5; -fx-text-fill: white;";

        btn.setStyle(base);
        btn.setOnMouseEntered(e -> btn.setStyle(base + hover));
        btn.setOnMouseExited(e -> btn.setStyle(base));
    }

    private void onDeleteLivre(Livre livre) {
        if (livre == null || livre.getId() == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Supprimer"); confirm.setHeaderText(null);
        confirm.setContentText("Supprimer '" + livre.getTitre() + "' ?");
        confirm.showAndWait().ifPresent(t -> {
            if (t == javafx.scene.control.ButtonType.OK) {
                try { livreService.delete(livre.getId()); showInfo("Supprimé", "Livre supprimé."); refresh(); }
                catch (SQLDataException e) { showError("Erreur", e.getMessage()); }
            }
        });
    }

    private Image toImage(String source) {
        if (source == null || source.isBlank()) return null;
        if (source.startsWith("http://") || source.startsWith("https://") || source.startsWith("file:"))
            return new Image(source, true);
        return new Image(new File(source).toURI().toString(), true);
    }

    private static boolean contains(String v, String q) { return v != null && v.toLowerCase().contains(q); }
    private static void showError(String t, String m) { Alert a = new Alert(Alert.AlertType.ERROR);       a.setTitle(t); a.setHeaderText(null); a.setContentText(m); a.showAndWait(); }
    private static void showInfo (String t, String m) { Alert a = new Alert(Alert.AlertType.INFORMATION); a.setTitle(t); a.setHeaderText(null); a.setContentText(m); a.showAndWait(); }

    // =========================================================================
    //  STATISTICS
    // =========================================================================

    private void updateStatistics() {
        if (statisticsContainer == null) return;
        statisticsContainer.getChildren().clear();

        HBox hdr = new HBox(10);
        hdr.setAlignment(Pos.CENTER_LEFT); hdr.setPadding(new Insets(6, 0, 2, 0));
        Rectangle accent = new Rectangle(4, 26); accent.setStyle("-fx-fill: #6366f1;");
        VBox htxt = new VBox(2);
        Label h1 = new Label("Tableau de Bord — Catalogue");
        h1.setStyle("-fx-font-size: 19; -fx-font-weight: bold; -fx-text-fill: #1f2937;");
        Label h2 = new Label("Aperçu analytique de la collection");
        h2.setStyle("-fx-font-size: 12; -fx-text-fill: #6b7280;");
        htxt.getChildren().addAll(h1, h2);
        hdr.getChildren().addAll(accent, htxt);

        HBox kpiRow = buildKpiRow();

        // Row 1: category pie + bar chart
        HBox row1 = new HBox(16); row1.setPrefWidth(Double.MAX_VALUE);
        VBox catPie = createCategoryBreakdown();
        HBox.setHgrow(catPie, Priority.ALWAYS);
        row1.getChildren().addAll(catPie);

        // Row 2: interactive author pie on the LEFT + category detail table on the RIGHT
        HBox row2 = new HBox(16); row2.setPrefWidth(Double.MAX_VALUE);
        VBox authorPie = createInteractiveAuthorPie();
        VBox catTable  = createCategoryDetailTable();
        authorPie.setPrefWidth(340); authorPie.setMinWidth(320); authorPie.setMaxWidth(380);
        HBox.setHgrow(catTable, Priority.ALWAYS);
        row2.getChildren().addAll(authorPie, catTable);

        statisticsContainer.getChildren().addAll(hdr, kpiRow, row1, row2);
        statisticsContainer.setPrefWidth(Double.MAX_VALUE);
    }

    private HBox buildKpiRow() {
        HBox row = new HBox(12); row.setPrefWidth(Double.MAX_VALUE);
        Map<String, Long>   cats    = getCategoryCounts();
        Map<String, Double> profits = getAuthorProfit();
        double total = profits.values().stream().mapToDouble(Double::doubleValue).sum();
        String topCat    = cats.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("—");
        String topAuthor = profits.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("—");
        List<Node> cards = List.of(
                kpiCard("📚", String.valueOf(allLivres.size()), "Livres au total",    "#6366f1", "#ede9fe"),
                kpiCard("🗂️", String.valueOf(cats.size()),      "Catégories",         "#06b6d4", "#ecfeff"),
                kpiCard("💰", String.format("%.0f TND", total), "Revenu total",       "#10b981", "#d1fae5"),
                kpiCard("🏆", topCat,    "Top catégorie", "#f59e0b", "#fef3c7"),
                kpiCard("✍️", topAuthor, "Top auteur",    "#ec4899", "#fce7f3")
        );
        for (Node k : cards) { HBox.setHgrow((Region)k, Priority.ALWAYS); row.getChildren().add(k); }
        return row;
    }

    private Node kpiCard(String icon, String value, String label, String accentColor, String bgAccent) {
        VBox card = new VBox(5);
        card.setAlignment(Pos.CENTER_LEFT); card.setPadding(new Insets(14, 16, 14, 16));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle("-fx-background-color: white; -fx-border-radius: 12; -fx-background-radius: 12; "
                + "-fx-border-color: #f3f4f6; -fx-border-width: 1.5; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 6, 0, 0, 2);");
        Label ico = new Label(icon);
        ico.setStyle("-fx-font-size: 17; -fx-background-color: " + bgAccent + "; -fx-padding: 6; -fx-background-radius: 8;");
        Label val = new Label(value);
        val.setStyle("-fx-font-size: 17; -fx-font-weight: bold; -fx-text-fill: #1f2937;");
        val.setWrapText(true); val.setMaxWidth(140);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 10; -fx-text-fill: #9ca3af; -fx-font-weight: bold;");
        Region bar = new Region(); bar.setPrefHeight(3); bar.setMaxWidth(34);
        bar.setStyle("-fx-background-color: " + accentColor + "; -fx-background-radius: 4;");
        card.getChildren().addAll(ico, val, lbl, bar);
        return card;
    }

    private VBox createCategoryBreakdown() {
        VBox c = chartContainer("📊 Livres par Catégorie");
        Map<String, Long> counts = getCategoryCounts();
        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
        counts.forEach((k, v) -> data.add(new PieChart.Data(k + " (" + v + ")", v)));
        PieChart chart = new PieChart(data);
        chart.setLegendSide(javafx.geometry.Side.BOTTOM);
        chart.setLabelsVisible(true); chart.setStartAngle(90); chart.setAnimated(true);
        chart.setPrefHeight(270); chart.setPrefWidth(Double.MAX_VALUE); chart.setMaxWidth(Double.MAX_VALUE);
        chart.setStyle("-fx-font-family: Arial; -fx-font-size: 11;");
        applyPieColors(data, PALETTE);
        c.getChildren().add(chart);
        return c;
    }


    // Interactive pie: click a slice to highlight it + see detailed stats in info panel
    private VBox createInteractiveAuthorPie() {
        VBox container = chartContainer("💰 Revenu par Auteur  •  cliquez pour détailler");

        Map<String, Double> profits = getAuthorProfit();
        double total = profits.values().stream().mapToDouble(Double::doubleValue).sum();

        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
        // Keep original names for lookup; truncate only for display
        Map<String, String> displayToReal = new LinkedHashMap<>();
        profits.forEach((author, profit) -> {
            String display = author.length() > 14 ? author.substring(0, 14) + "…" : author;
            displayToReal.put(display, author);
            data.add(new PieChart.Data(display, profit));
        });

        PieChart chart = new PieChart(data);
        chart.setLegendSide(javafx.geometry.Side.BOTTOM);
        chart.setLabelsVisible(false); // hidden; info panel shows detail instead
        chart.setStartAngle(90); chart.setAnimated(true);
        chart.setPrefHeight(250); chart.setPrefWidth(Double.MAX_VALUE); chart.setMaxWidth(Double.MAX_VALUE);
        chart.setStyle("-fx-font-family: Arial; -fx-font-size: 11;");

        // Info panel
        Label infoName = new Label("Cliquez sur une tranche");
        infoName.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: #374151;");
        Label infoRev  = new Label(""); infoRev.setStyle("-fx-font-size: 11; -fx-text-fill: #6b7280;");
        Label infoPct  = new Label(""); infoPct.setStyle("-fx-font-size: 11; -fx-text-fill: #9ca3af;");

        HBox infoRow = new HBox(16, new VBox(3, infoName, infoRev, infoPct));
        infoRow.setPadding(new Insets(9, 12, 9, 12));
        infoRow.setAlignment(Pos.CENTER_LEFT);
        infoRow.setStyle("-fx-background-color: #f9fafb; -fx-background-radius: 9; "
                + "-fx-border-color: #f3f4f6; -fx-border-radius: 9; -fx-border-width: 1;");

        Label totalLabel = new Label(String.format("Total : %.2f TND", total));
        totalLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #10b981; "
                + "-fx-background-color: #d1fae5; -fx-padding: 4 12; -fx-background-radius: 20;");

        container.getChildren().addAll(chart, totalLabel, infoRow);

        // Wire colors + interactions after nodes exist
        Platform.runLater(() -> {
            String[] selectedColor = {null};
            int[] ci = {0};

            for (PieChart.Data d : data) {
                final String color = AUTHOR_PALETTE[ci[0]++ % AUTHOR_PALETTE.length];
                if (d.getNode() == null) continue;

                d.getNode().setStyle("-fx-pie-color: " + color + ";");

                d.getNode().setOnMouseEntered(e -> {
                    if (!color.equals(selectedColor[0]))
                        d.getNode().setStyle("-fx-pie-color: " + color
                                + "; -fx-effect: dropshadow(gaussian,rgba(0,0,0,0.2),6,0,0,2); -fx-cursor: hand;");
                });
                d.getNode().setOnMouseExited(e -> {
                    if (!color.equals(selectedColor[0]))
                        d.getNode().setStyle("-fx-pie-color: " + color + "; -fx-cursor: hand;");
                });

                d.getNode().setOnMouseClicked(e -> {
                    // Dim all slices
                    int[] ri = {0};
                    for (PieChart.Data other : data)
                        if (other.getNode() != null)
                            other.getNode().setStyle("-fx-pie-color: " + AUTHOR_PALETTE[ri[0]++ % AUTHOR_PALETTE.length]
                                    + "; -fx-opacity: 0.4;");
                    // Pop the clicked slice
                    d.getNode().setStyle("-fx-pie-color: " + color
                            + "; -fx-opacity: 1; -fx-scale-x: 1.09; -fx-scale-y: 1.09; "
                            + "-fx-effect: dropshadow(gaussian,rgba(0,0,0,0.28),10,0,0,3);");
                    selectedColor[0] = color;

                    double pct = total > 0 ? (d.getPieValue() / total) * 100 : 0;
                    infoName.setText(d.getName());
                    infoName.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
                    infoRev.setText(String.format("Revenu : %.2f TND", d.getPieValue()));
                    infoPct.setText(String.format("Part : %.1f%% du total", pct));
                });
            }

            // Click on chart bg -> reset
            chart.setOnMouseClicked(e -> {
                if (!(e.getTarget() instanceof javafx.scene.shape.Arc)) {
                    int[] ri = {0};
                    for (PieChart.Data other : data)
                        if (other.getNode() != null)
                            other.getNode().setStyle("-fx-pie-color: " + AUTHOR_PALETTE[ri[0]++ % AUTHOR_PALETTE.length]
                                    + "; -fx-opacity: 1; -fx-scale-x: 1; -fx-scale-y: 1;");
                    selectedColor[0] = null;
                    infoName.setText("Cliquez sur une tranche");
                    infoName.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: #374151;");
                    infoRev.setText(""); infoPct.setText("");
                }
            });
        });

        return container;
    }

    // Category detail table — now placed to the right of the author pie chart
    private VBox createCategoryDetailTable() {
        VBox container = new VBox(9);
        container.setPadding(new Insets(16));
        container.setStyle("-fx-background-color: white; -fx-border-radius: 14; -fx-background-radius: 14; "
                + "-fx-border-color: #f3f4f6; -fx-border-width: 1.5; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 8, 0, 0, 2);");

        Label title = new Label("🗂️ Détail des Catégories");
        title.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #1f2937;");

        HBox hdr = new HBox();
        hdr.setPadding(new Insets(0, 0, 5, 0));
        hdr.setStyle("-fx-border-color: #f3f4f6; -fx-border-width: 0 0 1 0;");
        hdr.getChildren().addAll(
                makeTableHeader("Catégorie",       150),
                makeTableHeader("Livres",           55),
                makeTableHeader("%",                55),
                makeTableHeader("Répartition",     180),
                makeTableHeader("Revenu (TND)",    110)
        );
        container.getChildren().addAll(title, hdr);

        Map<String, Long> counts = getCategoryCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        long totalBooks = allLivres.size();
        Map<String, Double> catRevenue = allLivres.stream().collect(Collectors.groupingBy(
                l -> l.getCategorie() != null ? l.getCategorie() : "Non spécifié",
                Collectors.summingDouble(l -> l.getPrixLocation() != null ? l.getPrixLocation() : 0)));

        int[] idx = {0};
        counts.forEach((cat, count) -> {
            double pct = totalBooks > 0 ? (count * 100.0) / totalBooks : 0;
            double rev = catRevenue.getOrDefault(cat, 0.0);
            String color = PALETTE[idx[0] % PALETTE.length];

            HBox row = new HBox();
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(7, 4, 7, 4));
            if (idx[0] % 2 == 0)
                row.setStyle("-fx-background-color: #fafafa; -fx-background-radius: 7;");

            HBox catCell = new HBox(6);
            catCell.setAlignment(Pos.CENTER_LEFT);
            catCell.setMinWidth(150); catCell.setPrefWidth(150);
            Circle dot = new Circle(5); dot.setFill(Color.web(color));
            Label catLbl = new Label(cat);
            catLbl.setStyle("-fx-font-size: 11; -fx-text-fill: #374151; -fx-font-weight: bold;");
            catLbl.setMaxWidth(130); catLbl.setWrapText(true);
            catCell.getChildren().addAll(dot, catLbl);

            Label cntLbl = makeTableCell(String.valueOf(count), 55);
            Label pctLbl = makeTableCell(String.format("%.1f%%", pct), 55);
            pctLbl.setStyle("-fx-font-size: 11; -fx-text-fill: " + color + "; -fx-font-weight: bold; -fx-min-width: 55;");

            HBox barCell = new HBox();
            barCell.setMinWidth(180); barCell.setPrefWidth(180);
            barCell.setAlignment(Pos.CENTER_LEFT); barCell.setPadding(new Insets(0, 8, 0, 0));
            ProgressBar pb = new ProgressBar(pct / 100.0);
            pb.setPrefWidth(165); pb.setPrefHeight(8);
            pb.setStyle("-fx-accent: " + color + "; -fx-background-radius: 5; -fx-control-inner-background: #f3f4f6;");
            barCell.getChildren().add(pb);

            Label revLbl = makeTableCell(String.format("%.2f", rev), 110);
            row.getChildren().addAll(catCell, cntLbl, pctLbl, barCell, revLbl);
            container.getChildren().add(row);
            idx[0]++;
        });
        return container;
    }

    // =========================================================================
    //  Helpers
    // =========================================================================

    private VBox chartContainer(String titleText) {
        VBox c = new VBox(10);
        c.setPadding(new Insets(15)); c.setPrefWidth(Double.MAX_VALUE); c.setMaxWidth(Double.MAX_VALUE);
        c.setStyle("-fx-background-color: white; -fx-border-radius: 14; -fx-background-radius: 14; "
                + "-fx-border-color: #f3f4f6; -fx-border-width: 1.5; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 8, 0, 0, 2);");
        Label t = new Label(titleText);
        t.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #374151;");
        c.getChildren().add(t);
        return c;
    }

    private Label makeTableHeader(String text, double width) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 10; -fx-font-weight: bold; -fx-text-fill: #9ca3af; -fx-min-width: " + width + ";");
        l.setMinWidth(width); return l;
    }

    private Label makeTableCell(String text, double minWidth) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 11; -fx-text-fill: #374151; -fx-min-width: " + minWidth + ";");
        l.setMinWidth(minWidth); return l;
    }

    private Map<String, Long> getCategoryCounts() {
        return allLivres.stream().collect(Collectors.groupingBy(
                l -> l.getCategorie() != null ? l.getCategorie() : "Non spécifié", Collectors.counting()));
    }

    private Map<String, Double> getAuthorProfit() {
        return allLivres.stream().collect(Collectors.groupingBy(
                l -> l.getAuteur() != null ? l.getAuteur() : "Non spécifié",
                Collectors.summingDouble(l -> l.getPrixLocation() != null ? l.getPrixLocation() : 0)));
    }

    private void applyPieColors(ObservableList<PieChart.Data> data, String[] colors) {
        int[] ci = {0};
        for (PieChart.Data d : data) {
            final String color = colors[ci[0]++ % colors.length];
            Platform.runLater(() -> {
                if (d.getNode() == null) return;
                d.getNode().setStyle("-fx-pie-color: " + color + ";");
                d.getNode().setOnMouseEntered(e -> d.getNode().setStyle(
                        "-fx-pie-color: " + color + "; -fx-effect: dropshadow(gaussian,rgba(0,0,0,0.2),5,0,0,2); -fx-cursor: hand;"));
                d.getNode().setOnMouseExited(e -> d.getNode().setStyle("-fx-pie-color: " + color + ";"));
            });
        }
    }
}