package controllers.amateur;

import entities.LocationLivre;
import entities.Livre;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ComboBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.FadeTransition;
import services.JdbcLivreService;
import services.JdbcLocationLivreService;
import services.LivreService;
import utils.StripePaymentHandler;
import utils.EnvLoader;
import com.stripe.exception.CardException;

import javafx.application.Platform;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.sql.SQLDataException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import java.time.Duration;
import java.time.LocalDateTime;
import javafx.scene.control.Tooltip;

public class BibliofrontController {
    private final LivreService livreService = new JdbcLivreService();
    private final JdbcLocationLivreService locationLivreService = new JdbcLocationLivreService();
    private int getCurrentUserId() {
        entities.User u = utils.SessionManager.getCurrentUser();
        return (u != null) ? u.getId() : 1;
    }
    private List<Livre> livres = new ArrayList<>();
    private java.util.function.Consumer<Livre> readerNavigationHandler;

    public void setReaderNavigationHandler(java.util.function.Consumer<Livre> handler) {
        this.readerNavigationHandler = handler;
    }

    @FXML
    private TextField searchField;

    @FXML
    private ScrollPane cardsScroll;

    @FXML
    private TilePane cardsTile;

    @FXML
    private Button btnTousLivres;

    @FXML
    private Button btnMesLocations;

    private boolean isRentedFilterActive = false;

    @FXML
    private ComboBox<String> authorCombo;

    @FXML
    private ComboBox<String> priceSortCombo;



    @FXML
    public void initialize() {
        if (searchField != null) {
            searchField.textProperty().addListener((obs, old, newV) -> applyFilter());
        }
        if (authorCombo != null) {
            authorCombo.valueProperty().addListener((obs, old, newV) -> applyFilter());
        }
        if (priceSortCombo != null) {
            priceSortCombo.valueProperty().addListener((obs, old, newV) -> applyFilter());
        }
        if (searchField != null) {
            searchField.setTooltip(new Tooltip("Rechercher dans le titre, la catégorie ou l'auteur."));
        }
        if (authorCombo != null) {
            authorCombo.setTooltip(new Tooltip("Filtrer les livres par auteur spécifique."));
        }
        if (priceSortCombo != null) {
            priceSortCombo.setTooltip(new Tooltip("Trier les livres par prix de location : décroissant ou croissant."));
        }
        refresh();
    }

    @FXML
    private void onSearch() {
        applyFilter();
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    @FXML
    private void handleShowTousLivres() {
        isRentedFilterActive = false;
        if (btnTousLivres != null) btnTousLivres.setStyle("-fx-background-color: transparent; -fx-text-fill: #111827; -fx-font-weight: bold; -fx-font-size: 15px; -fx-border-color: transparent transparent #111827 transparent; -fx-border-width: 0 0 2px 0; -fx-padding: 8px 12px; -fx-cursor: hand;");
        if (btnMesLocations != null) btnMesLocations.setStyle("-fx-background-color: transparent; -fx-text-fill: #6b7280; -fx-font-weight: bold; -fx-font-size: 15px; -fx-border-color: transparent; -fx-padding: 8px 12px; -fx-cursor: hand;");
        applyFilter();
    }

    @FXML
    private void handleShowMesLocations() {
        isRentedFilterActive = true;
        if (btnMesLocations != null) btnMesLocations.setStyle("-fx-background-color: transparent; -fx-text-fill: #111827; -fx-font-weight: bold; -fx-font-size: 15px; -fx-border-color: transparent transparent #111827 transparent; -fx-border-width: 0 0 2px 0; -fx-padding: 8px 12px; -fx-cursor: hand;");
        if (btnTousLivres != null) btnTousLivres.setStyle("-fx-background-color: transparent; -fx-text-fill: #6b7280; -fx-font-weight: bold; -fx-font-size: 15px; -fx-border-color: transparent; -fx-padding: 8px 12px; -fx-cursor: hand;");
        applyFilter();
    }

    private void applyFilter() {
        List<Livre> result = new ArrayList<>(livres);
        String query = searchField.getText();
        if (query != null && !query.trim().isEmpty()) {
            String q = query.trim().toLowerCase();
            result = result.stream().filter(l -> containsIgnoreCase(l.getTitre(), q) || containsIgnoreCase(l.getCategorie(), q) || containsIgnoreCase(l.getAuteur(), q)).collect(Collectors.toList());
        }
        String author = authorCombo.getValue();
        if (author != null && !author.isEmpty()) {
            result = result.stream().filter(l -> author.equals(l.getAuteur())).collect(Collectors.toList());
        }
        String priceSort = priceSortCombo.getValue();
        if ("💰 Prix décroissant".equals(priceSort)) {
            result.sort((a, b) -> Double.compare(b.getPrixLocation() != null ? b.getPrixLocation() : 0, a.getPrixLocation() != null ? a.getPrixLocation() : 0));
        } else if ("💰 Prix croissant".equals(priceSort)) {
            result.sort((a, b) -> Double.compare(a.getPrixLocation() != null ? a.getPrixLocation() : 0, b.getPrixLocation() != null ? a.getPrixLocation() : 0));
        }
        if (isRentedFilterActive) {
            try {
                List<Integer> rentedIds = locationLivreService.getRentedLivreIds(getCurrentUserId());
                result = result.stream().filter(l -> rentedIds.contains(l.getId())).collect(Collectors.toList());
            } catch (SQLDataException e) {
                showError("Filtrage locations", e.getMessage());
            }
        }
        updateView(result);
    }

    private void refresh() {
        try {
            livres = livreService.getAll();
            if (authorCombo != null) {
                authorCombo.getItems().clear();
                livres.stream().map(Livre::getAuteur).filter(Objects::nonNull).filter(a -> !a.isBlank()).distinct().forEach(authorCombo.getItems()::add);
            }
            if (priceSortCombo != null) {
                priceSortCombo.getItems().setAll("💰 Prix décroissant", "💰 Prix croissant");
            }
            applyFilter();
        } catch (SQLDataException e) {
            showError("Chargement", e.getMessage());
        }
    }

    private void updateView(List<Livre> items) {
        updateCards(items);
    }

    private void updateCards(List<Livre> items) {
        if (cardsTile == null) {
            return;
        }
        cardsTile.getChildren().setAll(items.stream().map(this::createBookCard).toList());
    }

    private VBox createBookCard(Livre livre) {
        ImageView imageView = new ImageView();
        imageView.setFitWidth(180);
        imageView.setFitHeight(240);
        imageView.setPreserveRatio(true);
        imageView.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 5, 0, 0, 2);");
        if (livre.getImage() != null && !livre.getImage().isBlank()) {
            imageView.setImage(toImage(livre.getImage()));
        }

        HBox imageContainer = new HBox();
        imageContainer.setAlignment(javafx.geometry.Pos.CENTER);
        imageContainer.getChildren().add(imageView);

        Label title = new Label(livre.getTitre() == null ? "" : livre.getTitre());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13; -fx-text-fill: #1f2937; -fx-wrap-text: true; -fx-text-alignment: center;");
        title.setMaxWidth(160);
        title.setWrapText(true);

        Label categorie = new Label(livre.getCategorie() == null ? "N/A" : livre.getCategorie());
        categorie.setStyle("-fx-font-size: 11; -fx-text-fill: #6b7280; -fx-font-style: italic; -fx-text-alignment: center;");
        categorie.setMaxWidth(160);
        categorie.setWrapText(true);

        Label prix = new Label((livre.getPrixLocation() == null ? "0" : livre.getPrixLocation()) + " DT");
        prix.setStyle("-fx-font-size: 12; -fx-text-fill: #3b82f6; -fx-font-weight: bold;");

        Label dispo = new Label(Boolean.TRUE.equals(livre.getDisponibilite()) ? "✓ Disponible" : "✗ Indisponible");
        dispo.setStyle("-fx-font-size: 11; -fx-text-fill: " + (Boolean.TRUE.equals(livre.getDisponibilite()) ? "#10b981" : "#ef4444") + "; -fx-font-weight: bold;");

        Button details = new Button("📖 Détails");
        details.setStyle("-fx-padding: 8 12; -fx-font-size: 11; -fx-background-color: #f3f4f6; -fx-text-fill: #374151; -fx-border-color: #d1d5db; -fx-border-radius: 5; -fx-background-radius: 5; -fx-cursor: hand;");
        details.setMaxWidth(Double.MAX_VALUE);
        details.setOnAction(e -> showDetailsDialog(livre));
        details.setOnMouseEntered(e -> details.setStyle("-fx-padding: 8 12; -fx-font-size: 11; -fx-background-color: #e5e7eb; -fx-text-fill: #1f2937; -fx-border-color: #9ca3af; -fx-border-radius: 5; -fx-background-radius: 5; -fx-cursor: hand;"));
        details.setOnMouseExited(e -> details.setStyle("-fx-padding: 8 12; -fx-font-size: 11; -fx-background-color: #f3f4f6; -fx-text-fill: #374151; -fx-border-color: #d1d5db; -fx-border-radius: 5; -fx-background-radius: 5; -fx-cursor: hand;"));

        Button rent = new Button("🎯 Louer");
        rent.setDisable(!Boolean.TRUE.equals(livre.getDisponibilite()));
        rent.setStyle("-fx-padding: 8 12; -fx-font-size: 11; -fx-background-color: " + (Boolean.TRUE.equals(livre.getDisponibilite()) ? "#10b981" : "#d1d5db") + "; -fx-text-fill: white; -fx-border-radius: 5; -fx-background-radius: 5; -fx-cursor: hand; -fx-font-weight: bold;");
        rent.setMaxWidth(Double.MAX_VALUE);
        rent.setOnAction(e -> louerLivreAvecDialog(livre));
        if (Boolean.TRUE.equals(livre.getDisponibilite())) {
            rent.setOnMouseEntered(e -> rent.setStyle("-fx-padding: 8 12; -fx-font-size: 11; -fx-background-color: #059669; -fx-text-fill: white; -fx-border-radius: 5; -fx-background-radius: 5; -fx-cursor: hand; -fx-font-weight: bold; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 5, 0, 0, 2);"));
            rent.setOnMouseExited(e -> rent.setStyle("-fx-padding: 8 12; -fx-font-size: 11; -fx-background-color: #10b981; -fx-text-fill: white; -fx-border-radius: 5; -fx-background-radius: 5; -fx-cursor: hand; -fx-font-weight: bold;"));
        }

        HBox actions = new HBox(8, details, rent);
        HBox.setHgrow(details, Priority.ALWAYS);
        HBox.setHgrow(rent, Priority.ALWAYS);
        actions.setStyle("-fx-spacing: 8;");

        VBox infoBox = new VBox(6);
        infoBox.getChildren().addAll(title, categorie, prix, dispo);
        infoBox.setStyle("-fx-padding: 0;");
        infoBox.setAlignment(javafx.geometry.Pos.CENTER);

        VBox card = new VBox(12, imageContainer, infoBox, actions);
        card.setStyle("-fx-padding: 15; -fx-background-color: white; -fx-border-color: #e5e7eb; -fx-border-radius: 12; -fx-background-radius: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 8, 0, 0, 2);");
        card.setPrefWidth(220);
        card.setMaxWidth(220);
        card.setAlignment(javafx.geometry.Pos.CENTER);
        VBox.setVgrow(imageContainer, Priority.NEVER);

        card.setOnMouseEntered(e -> {
            card.setStyle("-fx-padding: 15; -fx-background-color: white; -fx-border-color: #3b82f6; -fx-border-radius: 12; -fx-background-radius: 12; -fx-effect: dropshadow(gaussian, rgba(59,130,246,0.3), 15, 0, 0, 5);");
            card.setScaleX(1.05);
            card.setScaleY(1.05);
        });
        card.setOnMouseExited(e -> {
            card.setStyle("-fx-padding: 15; -fx-background-color: white; -fx-border-color: #e5e7eb; -fx-border-radius: 12; -fx-background-radius: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 8, 0, 0, 2);");
            card.setScaleX(1.0);
            card.setScaleY(1.0);
        });

        return card;
    }

    private void louerLivreAvecDialog(Livre livre) {
        if (!Boolean.TRUE.equals(livre.getDisponibilite())) {
            showError("Location", "Ce livre est indisponible.");
            return;
        }
        if (livre.getId() == null) {
            showError("Location", "Livre invalide.");
            return;
        }
        Integer jours = askNombreDeJours(livre);
        if (jours == null) {
            return;
        }
        try {
            locationLivreService.louerLivre(livre.getId(), getCurrentUserId(), jours);
            showInfo("Location", "Livre loué avec succès.");
            refresh();
        } catch (SQLDataException e) {
            showError("Location impossible", e.getMessage());
        }
    }

    private Integer askNombreDeJours(Livre livre) {
        Dialog<Integer> dialog = new Dialog<>();
        dialog.setTitle("📅 Réserver votre livre");
        dialog.setWidth(550);
        dialog.setHeight(600);

        javafx.scene.control.ButtonType confirmButton = new javafx.scene.control.ButtonType("Confirmer la réservation", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmButton, javafx.scene.control.ButtonType.CANCEL);

        Button confirmBtn = (Button) dialog.getDialogPane().lookupButton(confirmButton);
        Button cancelBtn  = (Button) dialog.getDialogPane().lookupButton(javafx.scene.control.ButtonType.CANCEL);

        String buttonStyle = "-fx-padding: 12 28; -fx-font-size: 14; -fx-background-color: #0891b2; -fx-text-fill: white; -fx-font-weight: bold; -fx-border-radius: 8; -fx-cursor: hand;";
        confirmBtn.setStyle(buttonStyle);
        cancelBtn.setStyle("-fx-padding: 12 28; -fx-font-size: 14; -fx-background-color: #cbd5e1; -fx-text-fill: #334155; -fx-font-weight: bold; -fx-border-radius: 8; -fx-cursor: hand;");

        confirmBtn.setOnMouseEntered(e -> confirmBtn.setStyle("-fx-padding: 12 28; -fx-font-size: 14; -fx-background-color: #0891b2; -fx-text-fill: white; -fx-font-weight: bold; -fx-border-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 8, 0, 0, 3);"));
        confirmBtn.setOnMouseExited(e -> confirmBtn.setStyle(buttonStyle));

        confirmBtn.setDisable(true);

        java.time.LocalDate[] selectedStart = {null};
        java.time.LocalDate[] selectedEnd   = {null};
        int[] currentStep = {1};

        // ── STEP 1: calendar ────────────────────────────────────────────────────
        VBox calendarStep = new VBox(20);
        calendarStep.setStyle("-fx-padding: 30; -fx-background-color: linear-gradient(to bottom, #ffffff, #f8f9fa);");
        Label stepTitleCal = new Label("📅 Sélectionnez vos dates de location");
        stepTitleCal.setStyle("-fx-font-size: 22; -fx-font-weight: bold; -fx-text-fill: #1e3a8a;");
        VBox calendarBox = createPremiumCalendarUI(selectedStart, selectedEnd, confirmBtn);
        calendarStep.getChildren().addAll(stepTitleCal, calendarBox);
        calendarStep.setFillWidth(true);

        // ── FIX 1: mainContainer declared FIRST so it can be passed to createModernPaymentForm ──
        VBox mainContainer = new VBox();
        mainContainer.getChildren().add(calendarStep);

        // ── STEP 2: payment ─────────────────────────────────────────────────────
        VBox paymentStep = new VBox(20);
        paymentStep.setStyle("-fx-padding: 30; -fx-background-color: linear-gradient(to bottom, #ffffff, #f8f9fa);");

        Label stepTitlePay = new Label("💳 Confirmation de la réservation");
        stepTitlePay.setStyle("-fx-font-size: 22; -fx-font-weight: bold; -fx-text-fill: #1e3a8a;");

        VBox summaryBox = new VBox(15);
        summaryBox.setStyle("-fx-padding: 20; -fx-background-color: white; -fx-border-color: #e0e7ff; -fx-border-radius: 12; -fx-background-radius: 12; -fx-border-width: 2; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 8, 0, 0, 2);");

        Label dateRangeLabel = new Label();
        dateRangeLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #1e293b; -fx-font-weight: bold;");
        Label daysLabel = new Label();
        daysLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #0891b2;");
        Label priceLabel = new Label();
        priceLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #10b981;");

        summaryBox.getChildren().addAll(dateRangeLabel, daysLabel, priceLabel);

        // mainContainer now exists — no compile error
        VBox modernPaymentBox = createModernPaymentForm(livre, selectedStart, selectedEnd, mainContainer, paymentStep, confirmBtn);

        javafx.scene.control.CheckBox termsBox = new javafx.scene.control.CheckBox("J'accepte les conditions de location et la politique de paiement");
        termsBox.setStyle("-fx-font-size: 12; -fx-text-fill: #334155; -fx-padding: 10;");

        paymentStep.getChildren().addAll(stepTitlePay, summaryBox, modernPaymentBox, termsBox);
        paymentStep.setFillWidth(true);

        ScrollPane scrollPane = new ScrollPane(mainContainer);
        scrollPane.setStyle("-fx-background-color: white;");
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(false);

        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().setStyle("-fx-background-color: white;");

        confirmBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (currentStep[0] == 1) {
                if (selectedStart[0] == null || selectedEnd[0] == null) {
                    event.consume();
                    showError("Validation", "Veuillez sélectionner les dates de début et de fin.");
                    return;
                }

                event.consume();
                currentStep[0] = 2;

                long days = java.time.temporal.ChronoUnit.DAYS.between(selectedStart[0], selectedEnd[0]) + 1;
                double bookPrice  = livre.getPrixLocation() != null ? livre.getPrixLocation() : 0;
                double totalPrice = days * bookPrice;

                dateRangeLabel.setText("📅 Du " + selectedStart[0] + " au " + selectedEnd[0]);
                daysLabel.setText("⏱️ Durée : " + days + " jour(s)");
                priceLabel.setText("💳 Total : " + String.format("%.2f", totalPrice) + " DT");

                javafx.animation.FadeTransition fadeOut = new javafx.animation.FadeTransition(javafx.util.Duration.millis(250), calendarStep);
                fadeOut.setFromValue(1.0);
                fadeOut.setToValue(0.0);
                fadeOut.setOnFinished(e -> {
                    mainContainer.getChildren().clear();
                    mainContainer.getChildren().add(paymentStep);
                    scrollPane.setVvalue(0);
                    javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(javafx.util.Duration.millis(250), paymentStep);
                    fadeIn.setFromValue(0.0);
                    fadeIn.setToValue(1.0);
                    fadeIn.play();
                });
                fadeOut.play();

                confirmBtn.setText("✅ Payer maintenant");

            } else if (currentStep[0] == 2) {
                if (!termsBox.isSelected()) {
                    event.consume();
                    showError("Validation", "Veuillez accepter les conditions de location.");
                }
                // Payment handled by the pay button inside modernPaymentBox
            }
        });

        dialog.setResultConverter(bt -> {
            if (bt == confirmButton) {
                if (selectedStart[0] != null && selectedEnd[0] != null) {
                    long days = java.time.temporal.ChronoUnit.DAYS.between(selectedStart[0], selectedEnd[0]);
                    return (int) days;
                }
            }
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }

    private VBox createPremiumCalendarUI(java.time.LocalDate[] selectedStart, java.time.LocalDate[] selectedEnd, Button confirmBtn) {
        VBox calendarContainer = new VBox(15);
        calendarContainer.setAlignment(javafx.geometry.Pos.CENTER);

        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.YearMonth[] currentMonth = {java.time.YearMonth.now()};

        HBox headerBox = new HBox(20);
        headerBox.setAlignment(javafx.geometry.Pos.CENTER);
        headerBox.setStyle("-fx-padding: 15;");

        Button prevBtn = new Button("◀");
        prevBtn.setStyle("-fx-font-size: 14; -fx-padding: 8 16; -fx-background-color: #e0e7ff; -fx-text-fill: #1e3a8a; -fx-border-radius: 6; -fx-cursor: hand; -fx-font-weight: bold;");

        Label monthLabel = new Label();
        monthLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #1e3a8a; -fx-min-width: 200;");
        monthLabel.setAlignment(javafx.geometry.Pos.CENTER);

        Button nextBtn = new Button("▶");
        nextBtn.setStyle("-fx-font-size: 14; -fx-padding: 8 16; -fx-background-color: #e0e7ff; -fx-text-fill: #1e3a8a; -fx-border-radius: 6; -fx-cursor: hand; -fx-font-weight: bold;");

        headerBox.getChildren().addAll(prevBtn, monthLabel, nextBtn);

        javafx.scene.layout.GridPane calendarGrid = new javafx.scene.layout.GridPane();
        calendarGrid.setHgap(5);
        calendarGrid.setVgap(5);
        calendarGrid.setAlignment(javafx.geometry.Pos.CENTER);
        calendarGrid.setStyle("-fx-padding: 20; -fx-background-color: white; -fx-border-color: #e0e7ff; -fx-border-radius: 12; -fx-background-radius: 12; -fx-border-width: 2;");

        String[] dayNames = {"DIM", "LUN", "MAR", "MER", "JEU", "VEN", "SAM"};
        for (int i = 0; i < dayNames.length; i++) {
            Label dayLabel = new Label(dayNames[i]);
            dayLabel.setPrefWidth(60);
            dayLabel.setPrefHeight(40);
            dayLabel.setStyle("-fx-text-alignment: center; -fx-font-weight: bold; -fx-text-fill: white; -fx-font-size: 12; -fx-background-color: #1e3a8a; -fx-border-radius: 5; -fx-background-radius: 5;");
            dayLabel.setAlignment(javafx.geometry.Pos.CENTER);
            calendarGrid.add(dayLabel, i, 0);
        }

        final java.util.function.Consumer<java.time.YearMonth>[] updateCalendarHolder = new java.util.function.Consumer[1];
        updateCalendarHolder[0] = month -> {
            String[] monthsInFrench = {"JANVIER", "FÉVRIER", "MARS", "AVRIL", "MAI", "JUIN", "JUILLET", "AOÛT", "SEPTEMBRE", "OCTOBRE", "NOVEMBRE", "DÉCEMBRE"};
            monthLabel.setText(monthsInFrench[month.getMonthValue() - 1] + " " + month.getYear());

            calendarGrid.getChildren().removeIf(node -> {
                Integer rowIndex = javafx.scene.layout.GridPane.getRowIndex(node);
                return rowIndex != null && rowIndex > 0;
            });

            java.time.LocalDate firstDay = month.atDay(1);
            java.time.LocalDate lastDay  = month.atEndOfMonth();
            int firstDayOfWeek = firstDay.getDayOfWeek().getValue() % 7;

            int currentColumn = firstDayOfWeek;
            int currentRow    = 1;

            for (int day = 1; day <= lastDay.getDayOfMonth(); day++) {
                java.time.LocalDate date   = month.atDay(day);
                Button dayBtn = new Button(String.valueOf(day));
                dayBtn.setPrefWidth(60);
                dayBtn.setPrefHeight(60);

                boolean isPast    = date.isBefore(today);
                boolean isStart   = selectedStart[0] != null && date.equals(selectedStart[0]);
                boolean isEnd     = selectedEnd[0]   != null && date.equals(selectedEnd[0]);
                boolean isInRange = (selectedStart[0] != null && selectedEnd[0] != null &&
                        !date.isBefore(selectedStart[0]) && !date.isAfter(selectedEnd[0]));

                if (isPast) {
                    dayBtn.setStyle("-fx-background-color: #e2e8f0; -fx-text-fill: #94a3b8; -fx-cursor: not-allowed; -fx-font-size: 13; -fx-font-weight: bold; -fx-border-radius: 5;");
                    dayBtn.setDisable(true);
                } else if (isStart || isEnd) {
                    dayBtn.setStyle("-fx-background-color: #06b6d4; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: 13; -fx-border-radius: 5;");
                } else if (isInRange) {
                    dayBtn.setStyle("-fx-background-color: #cffafe; -fx-text-fill: #0c4a6e; -fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: 13; -fx-border-radius: 5;");
                } else {
                    dayBtn.setStyle("-fx-background-color: white; -fx-text-fill: #334155; -fx-border-color: #e2e8f0; -fx-border-radius: 5; -fx-cursor: hand; -fx-font-size: 13; -fx-border-width: 1;");
                }

                java.time.LocalDate finalDate = date;
                dayBtn.setOnAction(e -> {
                    if (selectedStart[0] == null) {
                        selectedStart[0] = finalDate;
                        selectedEnd[0]   = finalDate;
                    } else if (selectedEnd[0] == null || finalDate.isBefore(selectedStart[0])) {
                        selectedStart[0] = finalDate;
                        selectedEnd[0]   = finalDate;
                    } else {
                        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(selectedStart[0], finalDate);
                        if (daysBetween > 30) {
                            showError("Limite dépassée", "La location ne peut pas dépasser 30 jours.");
                            return;
                        }
                        selectedEnd[0] = finalDate;
                    }
                    confirmBtn.setDisable(selectedStart[0] == null || selectedEnd[0] == null);
                    updateCalendarHolder[0].accept(month);
                });

                calendarGrid.add(dayBtn, currentColumn, currentRow);
                currentColumn++;
                if (currentColumn > 6) { currentColumn = 0; currentRow++; }
            }
        };

        updateCalendarHolder[0].accept(currentMonth[0]);

        prevBtn.setOnAction(e -> { currentMonth[0] = currentMonth[0].minusMonths(1); updateCalendarHolder[0].accept(currentMonth[0]); });
        nextBtn.setOnAction(e -> { currentMonth[0] = currentMonth[0].plusMonths(1);  updateCalendarHolder[0].accept(currentMonth[0]); });

        calendarContainer.getChildren().addAll(headerBox, calendarGrid);
        return calendarContainer;
    }

    private static Integer parseJours(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            int v = Integer.parseInt(value.trim());
            if (v < 1 || v > 30) return null;
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void showDetailsDialog(Livre livre) {
        Dialog<javafx.scene.control.ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Détails du livre");

        javafx.scene.control.ButtonType openPdf = new javafx.scene.control.ButtonType("📖 Ouvrir PDF", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(openPdf, javafx.scene.control.ButtonType.CLOSE);
        Button openPdfButton = (Button) dialog.getDialogPane().lookupButton(openPdf);
        openPdfButton.setStyle("-fx-padding: 8 16; -fx-font-size: 12; -fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");

        ImageView imageView = new ImageView();
        imageView.setFitWidth(140);
        imageView.setFitHeight(180);
        imageView.setPreserveRatio(true);
        imageView.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 8, 0, 0, 3);");
        if (livre.getImage() != null && !livre.getImage().isBlank()) {
            imageView.setImage(toImage(livre.getImage()));
        }

        javafx.scene.control.Label title = new javafx.scene.control.Label(livre.getTitre() == null ? "" : livre.getTitre());
        title.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #1f2937; -fx-wrap-text: true;");
        title.setMaxWidth(280);
        title.setWrapText(true);

        HBox authorBox = new HBox(8);
        Label authorIcon = new Label("✍️");
        authorIcon.setStyle("-fx-font-size: 14;");
        javafx.scene.control.Label authorLabel = new javafx.scene.control.Label(livre.getAuteur() == null ? "Non spécifié" : livre.getAuteur());
        authorLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #6b7280;");
        authorBox.getChildren().addAll(authorIcon, authorLabel);
        authorBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        HBox categoryBox = new HBox(8);
        Label categoryIcon = new Label("📚");
        categoryIcon.setStyle("-fx-font-size: 14;");
        javafx.scene.control.Label categoryLabel = new javafx.scene.control.Label(livre.getCategorie() == null ? "Non spécifiée" : livre.getCategorie());
        categoryLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #6b7280;");
        categoryBox.getChildren().addAll(categoryIcon, categoryLabel);
        categoryBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        HBox priceDispoBox = new HBox(20);
        HBox priceBox = new HBox(6);
        Label priceIcon = new Label("💰");
        priceIcon.setStyle("-fx-font-size: 14;");
        javafx.scene.control.Label priceLabel = new javafx.scene.control.Label((livre.getPrixLocation() == null ? "0" : livre.getPrixLocation()) + " DT");
        priceLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #3b82f6; -fx-font-weight: bold;");
        priceBox.getChildren().addAll(priceIcon, priceLabel);
        priceBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        HBox dispoBox = new HBox(6);
        Label dispoIcon = new Label(Boolean.TRUE.equals(livre.getDisponibilite()) ? "✅" : "❌");
        dispoIcon.setStyle("-fx-font-size: 14;");
        javafx.scene.control.Label dispoLabel = new javafx.scene.control.Label(Boolean.TRUE.equals(livre.getDisponibilite()) ? "Disponible" : "Indisponible");
        dispoLabel.setStyle("-fx-font-size: 12; -fx-text-fill: " + (Boolean.TRUE.equals(livre.getDisponibilite()) ? "#10b981" : "#ef4444") + "; -fx-font-weight: bold;");
        dispoBox.getChildren().addAll(dispoIcon, dispoLabel);
        dispoBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        priceDispoBox.getChildren().addAll(priceBox, dispoBox);

        VBox rightColumn = new VBox(8);
        rightColumn.getChildren().addAll(title, authorBox, categoryBox, priceDispoBox);
        rightColumn.setStyle("-fx-padding: 0;");

        HBox headerBox = new HBox(15);
        headerBox.getChildren().addAll(imageView, rightColumn);
        headerBox.setStyle("-fx-padding: 15; -fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-radius: 10; -fx-background-radius: 10;");
        headerBox.setAlignment(javafx.geometry.Pos.TOP_LEFT);

        VBox descSection = new VBox(8);
        descSection.setStyle("-fx-padding: 15; -fx-border-color: #e5e7eb; -fx-border-radius: 10; -fx-background-radius: 10; -fx-background-color: white;");
        Label descTitle = new Label("📖 Description");
        descTitle.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #1f2937;");
        TextArea desc = new TextArea(livre.getDescription() == null || livre.getDescription().isEmpty() ? "Aucune description disponible" : livre.getDescription());
        desc.setEditable(false);
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 12; -fx-text-fill: #4b5563; -fx-control-inner-background: #f9fafb; -fx-padding: 8;");
        int lineCount = (livre.getDescription() == null ? 1 : livre.getDescription().split("\n").length) + 2;
        int minHeight = Math.max(60, Math.min(150, lineCount * 18));
        desc.setPrefRowCount(minHeight / 18);
        desc.setMinHeight(minHeight);
        descSection.getChildren().addAll(descTitle, desc);

        VBox mainContent = new VBox(12);
        mainContent.getChildren().addAll(headerBox, descSection);
        mainContent.setPrefWidth(500);
        mainContent.setStyle("-fx-padding: 15;");

        LocationLivre activeLocation = null;
        if (livre.getId() != null) {
            try { activeLocation = locationLivreService.getActiveLocation(livre.getId(), getCurrentUserId()); }
            catch (SQLDataException ignored) {}
        }

        if (activeLocation != null) {
            openPdfButton.setDisable(false);

            ProgressBar progressBar = new ProgressBar(0);
            progressBar.setPrefWidth(Double.MAX_VALUE);
            progressBar.setPrefHeight(8);
            progressBar.setStyle("-fx-accent: #3b82f6;");

            Label progressLabel = new Label();
            progressLabel.setStyle("-fx-text-fill: #4b5563; -fx-font-size: 12; -fx-font-weight: bold;");

            HBox progressHeader = new HBox(10);
            Label rentIcon  = new Label("⏱️"); rentIcon.setStyle("-fx-font-size: 14;");
            Label rentTitle = new Label("Location en cours"); rentTitle.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #1f2937;");
            progressHeader.getChildren().addAll(rentIcon, rentTitle);
            progressHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            VBox rentBox = new VBox(10, progressHeader, progressBar, progressLabel);
            rentBox.setStyle("-fx-padding: 12; -fx-background-color: #dbeafe; -fx-background-radius: 10; -fx-border-color: #3b82f6; -fx-border-radius: 10; -fx-border-width: 2;");
            mainContent.getChildren().add(rentBox);

            LocationLivre finalActiveLocation = activeLocation;
            Runnable updateProgress = () -> {
                LocalDateTime start = finalActiveLocation.getDateDebut();
                LocalDateTime end   = finalActiveLocation.getDateRetour();
                if (start == null || end == null) { progressBar.setProgress(0); progressLabel.setText(""); return; }
                LocalDateTime now = LocalDateTime.now();
                long totalSeconds   = Duration.between(start, end).getSeconds();
                long elapsedSeconds = Duration.between(start, now).getSeconds();
                double progress = totalSeconds > 0 ? (double) elapsedSeconds / totalSeconds : 1.0;
                progress = Math.max(0, Math.min(1, progress));
                progressBar.setProgress(progress);
                Duration left = Duration.between(now, end);
                if (left.isNegative() || left.isZero()) {
                    progressLabel.setText("⚠️ Location expirée");
                    progressLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12; -fx-font-weight: bold;");
                } else {
                    long daysLeft    = left.toDays();
                    long hoursLeft   = left.toHours()   % 24;
                    long minutesLeft = left.toMinutes()  % 60;
                    String timeRemaining = daysLeft > 0 ? daysLeft + "j " + hoursLeft + "h " + minutesLeft + "min"
                            : hoursLeft > 0 ? hoursLeft + "h " + minutesLeft + "min"
                            : minutesLeft + "min";
                    progressLabel.setText("⏳ " + timeRemaining + " restants");
                    progressLabel.setStyle("-fx-text-fill: #3b82f6; -fx-font-size: 12; -fx-font-weight: bold;");
                }
            };

            Timeline timeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> updateProgress.run()));
            timeline.setCycleCount(Timeline.INDEFINITE);
            dialog.setOnShown(e -> { updateProgress.run(); timeline.play(); });
            dialog.setOnHidden(e -> timeline.stop());
        } else {
            openPdfButton.setDisable(true);
            openPdfButton.setStyle("-fx-padding: 8 16; -fx-font-size: 12; -fx-background-color: #d1d5db; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: not-allowed;");
        }

        ScrollPane scrollPane = new ScrollPane(mainContent);
        scrollPane.setStyle("-fx-background-color: white; -fx-padding: 0;");
        scrollPane.setFitToWidth(true);
        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().setStyle("-fx-background-color: white;");
        dialog.setResultConverter(bt -> bt);
        dialog.showAndWait().ifPresent(result -> { if (result == openPdf) openPdfInApp(livre); });
    }

    private void openPdfInApp(Livre livre) {
        if (livre.getId() == null) { showError("PDF", "Livre invalide."); return; }
        try {
            LocationLivre loc = locationLivreService.getActiveLocation(livre.getId(), getCurrentUserId());
            if (loc == null) { showError("PDF", "Vous devez louer ce livre pour l'ouvrir."); return; }
        } catch (SQLDataException e) { showError("PDF", "Impossible de vérifier la location."); return; }

        if (readerNavigationHandler != null) { readerNavigationHandler.accept(livre); return; }

        String pdfSource = livre.getFichierPdf();
        if (pdfSource == null || pdfSource.isBlank()) { showError("PDF", "Aucun fichier PDF associé à ce livre."); return; }

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/amateur/BookReader.fxml"));
                Parent root = loader.load();
                BookReaderController readerCtrl = loader.getController();
                Scene readerScene = new Scene(root, 1280, 800);
                Stage readerStage = new Stage();
                readerStage.setTitle(livre.getTitre() != null ? livre.getTitre() : "Lecteur");
                readerStage.setScene(readerScene);
                readerCtrl.setStage(readerStage);
                readerCtrl.setBackHandler(() -> { readerStage.setFullScreen(false); readerStage.close(); });
                readerStage.show();
                readerCtrl.setLivre(livre);
            } catch (IOException e) {
                e.printStackTrace();
                showError("PDF", "Impossible de charger le lecteur : " + e.getMessage());
            }
        });
    }

    private Image toImage(String source) {
        if (source == null || source.isBlank()) return null;
        if (source.startsWith("http://") || source.startsWith("https://") || source.startsWith("file:"))
            return new Image(source, true);
        return new Image(new File(source).toURI().toString(), true);
    }

    private byte[] loadPdfBytes(String pdfSource) throws IOException {
        if (pdfSource == null || pdfSource.isBlank()) throw new IOException("Source PDF vide");
        if (pdfSource.startsWith("http://") || pdfSource.startsWith("https://")) {
            try (InputStream is = new URL(pdfSource).openStream()) { return is.readAllBytes(); }
        }
        if (pdfSource.startsWith("file:")) {
            try { return java.nio.file.Files.readAllBytes(Path.of(URI.create(pdfSource))); }
            catch (Exception ex) { return java.nio.file.Files.readAllBytes(Path.of(new URL(pdfSource).getPath())); }
        }
        return java.nio.file.Files.readAllBytes(Path.of(pdfSource));
    }

    private static void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(message); alert.showAndWait();
    }

    private static void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(message); alert.showAndWait();
    }

    private static boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
    }

    private VBox createModernPaymentForm(Livre livre, java.time.LocalDate[] selectedStart,
                                         java.time.LocalDate[] selectedEnd, VBox mainContainer,
                                         VBox paymentStep, Button confirmBtn) {
        VBox formContainer = new VBox(15);
        formContainer.setStyle("-fx-padding: 25; -fx-background-color: white; -fx-border-radius: 15; " +
                "-fx-background-radius: 15; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 12, 0, 0, 3);");

        Label cardSectionLabel = new Label("🎴 Informations de carte");
        cardSectionLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #1e3a8a; -fx-padding: 0 0 10 0;");

        Label cardNumberLabel = new Label("Numéro de carte");
        cardNumberLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #495057;");

        TextField cardNumber = new TextField();
        cardNumber.setPromptText("4242 4242 4242 4242");
        cardNumber.setStyle("-fx-font-size: 14; -fx-padding: 12 14; -fx-border-color: #dee2e6; -fx-border-radius: 8; -fx-border-width: 2; -fx-background-radius: 8; -fx-font-family: 'Courier New';");
        cardNumber.setMaxWidth(Double.MAX_VALUE);

        Label cardNumberError = new Label();
        cardNumberError.setStyle("-fx-font-size: 10; -fx-text-fill: #dc3545; -fx-padding: 4 0 0 0;");

        cardNumber.textProperty().addListener((obs, oldVal, newVal) -> {
            String cleaned = newVal.replaceAll("[^0-9]", "");
            cardNumber.setStyle("-fx-font-size: 14; -fx-padding: 12 14; -fx-border-color: " +
                    (cleaned.isEmpty() ? "#dee2e6" : (isValidCardNumber(cleaned) ? "#28a745" : "#dc3545")) +
                    "; -fx-border-radius: 8; -fx-border-width: 2; -fx-background-radius: 8; -fx-font-family: 'Courier New';");
            cardNumberError.setText(!cleaned.isEmpty() && !isValidCardNumber(cleaned) ? "Numéro de carte invalide" : "");
        });

        HBox expiryPanel = new HBox(12);

        Label expiryLabel = new Label("Date d'expiration");
        expiryLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #495057;");

        TextField cardExpiry = new TextField();
        cardExpiry.setPromptText("MM/YY");
        cardExpiry.setMaxWidth(150);
        cardExpiry.setStyle("-fx-font-size: 14; -fx-padding: 12 14; -fx-border-color: #dee2e6; -fx-border-radius: 8; -fx-border-width: 2; -fx-background-radius: 8;");

        Label expiryError = new Label();
        expiryError.setStyle("-fx-font-size: 10; -fx-text-fill: #dc3545;");

        cardExpiry.textProperty().addListener((obs, oldVal, newVal) -> {
            String cleaned = newVal.replaceAll("[^0-9/]", "");
            if (cleaned.length() == 2 && !cleaned.contains("/")) { cleaned = cleaned + "/"; cardExpiry.setText(cleaned); }
            cardExpiry.setStyle("-fx-font-size: 14; -fx-padding: 12 14; -fx-border-color: " +
                    (cleaned.isEmpty() ? "#dee2e6" : (isValidExpiry(cleaned) ? "#28a745" : "#dc3545")) +
                    "; -fx-border-radius: 8; -fx-border-width: 2; -fx-background-radius: 8;");
            expiryError.setText(!cleaned.isEmpty() && !isValidExpiry(cleaned) ? "MM/YY invalide ou expiré" : "");
        });

        Label cvvLabel = new Label("CVC");
        cvvLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #495057;");

        TextField cardCvv = new TextField();
        cardCvv.setPromptText("123");
        cardCvv.setMaxWidth(120);
        cardCvv.setStyle("-fx-font-size: 14; -fx-padding: 12 14; -fx-border-color: #dee2e6; -fx-border-radius: 8; -fx-border-width: 2; -fx-background-radius: 8; -fx-font-family: 'Courier New';");

        Label cvvError = new Label();
        cvvError.setStyle("-fx-font-size: 10; -fx-text-fill: #dc3545;");

        cardCvv.textProperty().addListener((obs, oldVal, newVal) -> {
            String cleaned = newVal.replaceAll("[^0-9]", "");
            if (cleaned.length() > 4) cleaned = cleaned.substring(0, 4);
            cardCvv.setText(cleaned);
            cardCvv.setStyle("-fx-font-size: 14; -fx-padding: 12 14; -fx-border-color: " +
                    (cleaned.isEmpty() ? "#dee2e6" : (cleaned.length() >= 3 ? "#28a745" : "#ffc107")) +
                    "; -fx-border-radius: 8; -fx-border-width: 2; -fx-background-radius: 8; -fx-font-family: 'Courier New';");
            cvvError.setText(!cleaned.isEmpty() && cleaned.length() < 3 ? "CVC invalide (3-4 chiffres)" : "");
        });

        VBox expirySection = new VBox(4, expiryLabel, cardExpiry, expiryError);
        VBox cvvSection    = new VBox(4, cvvLabel,    cardCvv,    cvvError);
        expiryPanel.getChildren().addAll(expirySection, cvvSection);
        HBox.setHgrow(expirySection, javafx.scene.layout.Priority.ALWAYS);

        Label nameLabel = new Label("Nom du titulaire");
        nameLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: #495057;");

        TextField cardholderName = new TextField();
        cardholderName.setPromptText("Jean Dupont");
        cardholderName.setStyle("-fx-font-size: 14; -fx-padding: 12 14; -fx-border-color: #dee2e6; -fx-border-radius: 8; -fx-border-width: 2; -fx-background-radius: 8;");
        cardholderName.setMaxWidth(Double.MAX_VALUE);

        Label nameError = new Label();
        nameError.setStyle("-fx-font-size: 10; -fx-text-fill: #dc3545;");

        cardholderName.textProperty().addListener((obs, oldVal, newVal) -> {
            String trimmed = newVal.trim();
            cardholderName.setStyle("-fx-font-size: 14; -fx-padding: 12 14; -fx-border-color: " +
                    (trimmed.isEmpty() ? "#dee2e6" : (trimmed.length() >= 3 ? "#28a745" : "#ffc107")) +
                    "; -fx-border-radius: 8; -fx-border-width: 2; -fx-background-radius: 8;");
            nameError.setText(!trimmed.isEmpty() && trimmed.length() < 3 ? "Le nom doit contenir au moins 3 caractères" : "");
        });

        formContainer.getChildren().addAll(
                cardSectionLabel,
                cardNumberLabel, cardNumber, cardNumberError,
                nameLabel, cardholderName, nameError,
                expiryPanel);

        Button payButton = new Button("💳 Payer maintenant");
        payButton.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 14 28; -fx-background-color: #0891b2; -fx-text-fill: white; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(8,145,178,0.3), 8, 0, 0, 2);");
        payButton.setMaxWidth(Double.MAX_VALUE);
        payButton.setOnMouseEntered(e -> payButton.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 14 28; -fx-background-color: #0a7fa7; -fx-text-fill: white; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(8,145,178,0.4), 12, 0, 0, 4);"));
        payButton.setOnMouseExited(e  -> payButton.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 14 28; -fx-background-color: #0891b2; -fx-text-fill: white; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(8,145,178,0.3), 8, 0, 0, 2);"));

        payButton.setOnAction(e -> {
            String errorMsg = validatePaymentForm(cardNumber, cardExpiry, cardCvv, cardholderName);
            if (errorMsg != null) { showError("Validation", errorMsg); return; }
            processPaymentWithModernForm(livre, selectedStart, selectedEnd, mainContainer,
                    paymentStep, cardNumber, cardExpiry, cardCvv, cardholderName, payButton);
        });

        VBox buttonBox = new VBox(payButton);
        buttonBox.setStyle("-fx-spacing: 15; -fx-padding: 15 0 0 0;");
        buttonBox.setPrefWidth(Double.MAX_VALUE);
        formContainer.getChildren().add(buttonBox);

        return formContainer;
    }

    private boolean isValidCardNumber(String cardNumber) {
        if (cardNumber.length() < 13 || cardNumber.length() > 19) return false;
        int sum = 0; boolean alternate = false;
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(cardNumber.charAt(i));
            if (alternate) { digit *= 2; if (digit > 9) digit -= 9; }
            sum += digit; alternate = !alternate;
        }
        return sum % 10 == 0;
    }

    private boolean isValidExpiry(String expiry) {
        if (!expiry.matches("\\d{2}/\\d{2}")) return false;
        String[] parts = expiry.split("/");
        int month = Integer.parseInt(parts[0]);
        int year  = Integer.parseInt(parts[1]) + 2000;
        if (month < 1 || month > 12) return false;
        return java.time.YearMonth.of(year, month).isAfter(java.time.YearMonth.now());
    }

    private String validatePaymentForm(TextField cardNum, TextField expiry, TextField cvv, TextField name) {
        String cardVal   = cardNum.getText().replaceAll("[^0-9]", "");
        String expiryVal = expiry.getText();
        String cvvVal    = cvv.getText().replaceAll("[^0-9]", "");
        String nameVal   = name.getText().trim();
        if (cardVal.isEmpty())                         return "Veuillez entrer le numéro de carte";
        if (!isValidCardNumber(cardVal))               return "Numéro de carte invalide";
        if (expiryVal.isEmpty())                       return "Veuillez entrer la date d'expiration";
        if (!isValidExpiry(expiryVal))                 return "Date d'expiration invalide ou expirée";
        if (cvvVal.isEmpty())                          return "Veuillez entrer le CVC";
        if (cvvVal.length() < 3 || cvvVal.length() > 4) return "CVC invalide (3-4 chiffres)";
        if (nameVal.isEmpty())                         return "Veuillez entrer le nom du titulaire";
        if (nameVal.length() < 3)                      return "Le nom doit contenir au moins 3 caractères";
        return null;
    }

    // ── FIX 2 + 3: Stripe confirmation + louerLivre() called on success ─────────
    private void processPaymentWithModernForm(Livre livre, java.time.LocalDate[] selectedStart,
                                              java.time.LocalDate[] selectedEnd, VBox mainContainer,
                                              VBox paymentStep, TextField cardNum, TextField expiry,
                                              TextField cvv, TextField name, Button payButton) {
        long days         = java.time.temporal.ChronoUnit.DAYS.between(selectedStart[0], selectedEnd[0]) + 1;
        double bookPrice  = livre.getPrixLocation() != null ? livre.getPrixLocation() : 0;
        double totalPrice = days * bookPrice;
        long amountInCents = Math.round(totalPrice * 100);

        payButton.setDisable(true);
        payButton.setText("⏳ Traitement en cours...");
        payButton.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 14 28; -fx-background-color: #6c757d; -fx-text-fill: white; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: wait;");

        // Capture field values now — must be read on FX thread before async
        String cardNumVal  = cardNum.getText().replaceAll("[^0-9]", "");
        String expiryVal   = expiry.getText();          // MM/YY
        String cvvVal      = cvv.getText().replaceAll("[^0-9]", "");

        CompletableFuture.runAsync(() -> {
            try {
                // Ensure Stripe is initialized using the utility
                StripePaymentHandler.initialize();
                
                // ── Map test card numbers to Stripe test tokens ──
                String testToken = mapCardToTestToken(cardNumVal);
                
                // Create a PaymentMethod from the test token
                Map<String, Object> pmParams = new HashMap<>();
                pmParams.put("type", "card");
                pmParams.put("card", Map.of("token", testToken));
                com.stripe.model.PaymentMethod pm = com.stripe.model.PaymentMethod.create(pmParams);

                // Create the intent
                com.stripe.model.PaymentIntent intent = StripePaymentHandler.createPaymentIntent(
                        amountInCents,
                        livre.getTitre() != null ? livre.getTitre() : "Livre",
                        getCurrentUserId());

                // Confirm it with the payment method
                Map<String, Object> confirmParams = new HashMap<>();
                confirmParams.put("payment_method", pm.getId());
                intent = intent.confirm(confirmParams);

                final String status = intent.getStatus();
                final long   daysF  = days;

                Platform.runLater(() -> {
                    if ("succeeded".equals(status)) {
                        // ── FIX 3: save the rental to the DB before showing success ──
                        try {
                            locationLivreService.louerLivre(livre.getId(), getCurrentUserId(), (int) daysF);
                            refresh();
                        } catch (SQLDataException dbEx) {
                            showError("Erreur", "Paiement réussi mais erreur d'enregistrement : " + dbEx.getMessage());
                            resetPaymentButton(payButton);
                            return;
                        }
                        showPaymentSuccess(mainContainer, livre, daysF);
                    } else {
                        showPaymentError(mainContainer, paymentStep, "Paiement non abouti. État : " + status);
                        resetPaymentButton(payButton);
                    }
                });

            } catch (com.stripe.exception.CardException e) {
                Platform.runLater(() -> { showPaymentError(mainContainer, paymentStep, "Carte refusée : " + e.getMessage()); resetPaymentButton(payButton); });
            } catch (Exception e) {
                Platform.runLater(() -> { showPaymentError(mainContainer, paymentStep, "Erreur : " + e.getMessage()); resetPaymentButton(payButton); });
            }
        });
    }

    private void showPaymentSuccess(VBox mainContainer, Livre livre, long days) {
        VBox successBox = new VBox(20);
        successBox.setStyle("-fx-padding: 40; -fx-alignment: center; -fx-background-color: white;");

        Label successIcon  = new Label("✅"); successIcon.setStyle("-fx-font-size: 64;");
        Label successTitle = new Label("Paiement réussi!");
        successTitle.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: #28a745;");
        Label successMsg = new Label("Votre location de '" + (livre.getTitre() != null ? livre.getTitre() : "Livre") + "' pour " + days + " jour(s) est confirmée.");
        successMsg.setStyle("-fx-font-size: 14; -fx-text-fill: #495057; -fx-wrap-text: true;");
        successMsg.setMaxWidth(400);
        successMsg.setAlignment(javafx.geometry.Pos.CENTER);
        Label refMsg = new Label("Référence : #" + System.currentTimeMillis());
        refMsg.setStyle("-fx-font-size: 12; -fx-text-fill: #6c757d;");

        Button closeBtn = new Button("Fermer et retourner");
        closeBtn.setStyle("-fx-font-size: 14; -fx-padding: 12 28; -fx-background-color: #28a745; -fx-text-fill: white; -fx-border-radius: 8; -fx-background-radius: 8;");
            closeBtn.setOnAction(e -> {
                if (mainContainer.getScene() != null) {
                    Stage stage = (Stage) mainContainer.getScene().getWindow();
                    stage.close();
                }
            });

        successBox.getChildren().addAll(successIcon, successTitle, successMsg, refMsg, closeBtn);
        mainContainer.getChildren().setAll(successBox);
    }

    private void showPaymentError(VBox mainContainer, VBox paymentStep, String errorMsg) {
        VBox errorBox = new VBox(20);
        errorBox.setStyle("-fx-padding: 40; -fx-alignment: center; -fx-background-color: white;");

        Label errorIcon  = new Label("❌"); errorIcon.setStyle("-fx-font-size: 64;");
        Label errorTitle = new Label("Paiement échoué");
        errorTitle.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: #dc3545;");
        Label errorContent = new Label(errorMsg);
        errorContent.setStyle("-fx-font-size: 14; -fx-text-fill: #495057; -fx-wrap-text: true;");
        errorContent.setMaxWidth(400);
        errorContent.setAlignment(javafx.geometry.Pos.CENTER);

        Button retryBtn = new Button("Réessayer");
        retryBtn.setStyle("-fx-font-size: 14; -fx-padding: 12 28; -fx-background-color: #0891b2; -fx-text-fill: white; -fx-border-radius: 8; -fx-background-radius: 8;");
        retryBtn.setOnAction(e -> mainContainer.getChildren().setAll(paymentStep));

        Button cancelBtn = new Button("Annuler");
        cancelBtn.setStyle("-fx-font-size: 14; -fx-padding: 12 28; -fx-background-color: #6c757d; -fx-text-fill: white; -fx-border-radius: 8; -fx-background-radius: 8;");
        cancelBtn.setOnAction(e -> {
                if (mainContainer.getScene() != null) {
                    Stage stage = (Stage) mainContainer.getScene().getWindow();
                    stage.close();
                }
            });

        HBox buttonBox = new HBox(12, retryBtn, cancelBtn);
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER);
        errorBox.getChildren().addAll(errorIcon, errorTitle, errorContent, buttonBox);
        mainContainer.getChildren().setAll(errorBox);
    }

    private void resetPaymentButton(Button payButton) {
        payButton.setDisable(false);
        payButton.setText("💳 Payer maintenant");
        payButton.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 14 28; -fx-background-color: #0891b2; -fx-text-fill: white; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(8,145,178,0.3), 8, 0, 0, 2);");
    }

    /**
     * Map test card numbers to Stripe test tokens
     */
    private static String mapCardToTestToken(String cardNumber) {
        String card = cardNumber.replaceAll("[^0-9]", "");
        
        // Stripe test card tokens (for JavaFX without Stripe Elements)
        if (card.equals("4242424242424242")) return "tok_visa";
        if (card.equals("4000000000000002")) return "tok_chargeDeclined";
        if (card.equals("4000002500003155")) return "tok_threeDSecure2Required";
        if (card.equals("5555555555554444")) return "tok_mastercard";
        if (card.equals("378282246310005"))  return "tok_amex";
        
        // Default to Visa token if card not recognized
        return "tok_visa";
    }
}