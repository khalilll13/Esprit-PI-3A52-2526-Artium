package controllers;

import services.DashboardService;
import services.GroqAiService;
import services.UserReportService;
import services.VoiceCommandService;
import components.CalendarPane;
import components.StatCard;
import entities.UserReport;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import utils.CardAnimator;

public class DashboardController {

    @FXML private HBox      statsContainer;
    @FXML private StackPane lineChartContainer;
    @FXML private StackPane rolePieContainer;
    @FXML private VBox      signupsList;
    @FXML private VBox      reclamationsList;
    @FXML private VBox      topArtistesList;
    @FXML private Label     reclamationsBadge;
    @FXML private StackPane calendarContainer;
    @FXML private HBox      headerChipsContainer;
    @FXML private Button    generateReportButton;

    private final DashboardService    dashboardService = new DashboardService();
    private final VoiceCommandService voiceService     = new VoiceCommandService();
    private final GroqAiService       groqService      = new GroqAiService();
    private final UserReportService   userReportService = new UserReportService();
    private Label voiceFeedbackLabel;
    private ProgressIndicator reportLoadingIndicator;

    private static final int MAX_RECENT_ITEMS = 5;
    private static final DateTimeFormatter REPORT_FILE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String REPORT_BUTTON_DEFAULT_TEXT = "Genérer rapport";

    @FXML
    public void initialize() {
        if (generateReportButton != null) {
            if (!generateReportButton.getStyleClass().contains("primary-action-button")) {
                generateReportButton.getStyleClass().add("primary-action-button");
            }
            if (!generateReportButton.getStyleClass().contains("report-action-button")) {
                generateReportButton.getStyleClass().add("report-action-button");
            }
            initializeReportLoadingIndicator();
        }
        calendarContainer.getChildren().add(new CalendarPane());
        showLoadingState();
        loadDashboardDataAsync();
        buildVoiceBar();
    }

    @FXML
    private void onGenerateReport() {
        generateUsersReportAsync();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // COMMANDE VOCALE IA
    // ══════════════════════════════════════════════════════════════════════════

    private void buildVoiceBar() {
        TextField liveField = new TextField();
        liveField.setPromptText("Parlez à l'IA...");
        liveField.setEditable(false);
        liveField.getStyleClass().add("voice-live-field");
        liveField.setPrefWidth(240);
        HBox.setHgrow(liveField, Priority.ALWAYS);

        Label aiLabel = new Label("IA prête");
        aiLabel.getStyleClass().add("voice-ai-response");
        aiLabel.setMinWidth(180);
        this.voiceFeedbackLabel = aiLabel;

        Button micBtn = new Button("🎙");
        micBtn.getStyleClass().add("voice-mic-btn");

        voiceService.init(
                partial -> liveField.setText(partial),

                finalText -> {
                    liveField.setText(finalText);
                    aiLabel.setText("⏳ LLaMA 3 analyse...");
                    micBtn.setText("🎙");
                    micBtn.getStyleClass().remove("voice-mic-btn-active");

                    new Thread(() -> {
                        String context = buildContextSummary();
                        GroqAiService.AiCommand cmd = groqService.analyze(finalText, context);
                        Platform.runLater(() -> {
                            aiLabel.setText("✓ " + cmd.response);
                            executeAiCommand(cmd);
                        });
                    }).start();
                },

                error -> {
                    aiLabel.setText("⚠ " + error);
                    micBtn.setText("🎙");
                    micBtn.getStyleClass().remove("voice-mic-btn-active");
                }
        );

        micBtn.setOnAction(e -> {
            if (voiceService.isListening()) {
                voiceService.stopListening();
                micBtn.setText("🎙");
                micBtn.getStyleClass().remove("voice-mic-btn-active");
                aiLabel.setText("IA prête");
            } else {
                micBtn.setText("⏹");
                micBtn.getStyleClass().add("voice-mic-btn-active");
                aiLabel.setText("● Écoute...");
                voiceService.startListening();
            }
        });

        headerChipsContainer.getChildren().addAll(liveField, aiLabel, micBtn);
    }

    // ── executeAiCommand ────────────────────────────────────────────────────────
    private void executeAiCommand(GroqAiService.AiCommand cmd) {
        System.out.println("=== AI CMD ===");
        System.out.println("action   : " + cmd.action);
        System.out.println("target   : " + cmd.target);
        System.out.println("argument : " + cmd.argument);
        System.out.println("response : " + cmd.response);
        System.out.println("==============");

        MainController main = MainController.getInstance();
        if (main == null) {
            System.err.println("⚠ MainController.getInstance() == null");
            return;
        }

        voiceService.stopListening();

        switch (cmd.action) {

            case "navigate" -> {
                switch (cmd.target) {
                    case "artistes"     -> main.navigateTo("artistes");
                    case "amateurs"     -> main.navigateTo("amateurs");
                    case "users"        -> main.navigateTo("amateurs");
                    case "reclamations" -> main.navigateTo("reclamations");
                    case "dashboard"    -> main.navigateTo("dashboard");
                    case "oeuvres"      -> main.navigateTo("oeuvres");
                    case "evenements"   -> main.navigateTo("evenements");
                    default             -> System.out.println("⚠ Target inconnu : " + cmd.target);
                }
            }

            case "search" -> {
                switch (cmd.target) {
                    case "artistes"              -> main.navigateTo("artistes");
                    case "amateurs", "users"     -> main.navigateTo("amateurs");
                    case "reclamations"          -> main.navigateTo("reclamations");
                    default                      -> main.navigateTo("amateurs");
                }
                main.triggerSearch(cmd.argument);
            }

            case "create" -> {
                switch (cmd.target) {
                    case "artistes"          -> main.navigateTo("artistes");
                    case "amateurs", "users" -> main.navigateTo("amateurs");
                    default                  -> main.navigateTo("amateurs");
                }
                main.triggerCreate(cmd.argument);
            }

            case "delete" -> {
                switch (cmd.target) {
                    case "artistes"          -> main.navigateTo("artistes");
                    case "amateurs", "users" -> main.navigateTo("amateurs");
                    default                  -> main.navigateTo("amateurs");
                }
                main.triggerDelete(cmd.argument);
            }

            case "block" -> {
                switch (cmd.target) {
                    case "artistes"          -> main.navigateTo("artistes");
                    case "amateurs", "users" -> main.navigateTo("amateurs");
                    default                  -> main.navigateTo("amateurs");
                }
                main.triggerBlock(cmd.argument);
            }

            case "activate" -> {
                switch (cmd.target) {
                    case "artistes"          -> main.navigateTo("artistes");
                    case "amateurs", "users" -> main.navigateTo("amateurs");
                    default                  -> main.navigateTo("amateurs");
                }
                main.triggerActivate(cmd.argument);
            }

            case "stats"  -> loadDashboardDataAsync();
            case "report" -> generateUsersReportAsync();
            default       -> System.out.println("⚠ Action inconnue : " + cmd.action);
        }
    }
    // ─────────────────────────────────────────────────────────────────────────

    private void generateUsersReportAsync() {
        if (generateReportButton != null && generateReportButton.isDisabled()) {
            return;
        }
        setReportLoadingState(true);

        Task<UserReport> reportTask = new Task<>() {
            @Override
            protected UserReport call() throws Exception {
                return userReportService.generateActiveUsersMonthlyReport();
            }
        };

        reportTask.setOnSucceeded(event -> {
            setReportLoadingState(false);
            UserReport report = reportTask.getValue();
            if (voiceFeedbackLabel != null) {
                voiceFeedbackLabel.setText("✓ Rapport genere (" + (report.isAiEnhanced() ? "IA" : "local") + ")");
            }
            showReportDialog(report);
        });

        reportTask.setOnFailed(event -> {
            setReportLoadingState(false);
            Throwable error = reportTask.getException();
            String message = (error == null || error.getMessage() == null) ? "Erreur de generation du rapport." : error.getMessage();
            if (voiceFeedbackLabel != null) {
                voiceFeedbackLabel.setText("⚠ " + message);
            }
        });

        reportTask.setOnCancelled(event -> setReportLoadingState(false));

        Thread worker = new Thread(reportTask, "user-report-generator");
        worker.setDaemon(true);
        worker.start();
    }

    private void initializeReportLoadingIndicator() {
        reportLoadingIndicator = new ProgressIndicator();
        reportLoadingIndicator.setProgress(-1);
        reportLoadingIndicator.getStyleClass().add("report-button-spinner");
        reportLoadingIndicator.setMaxSize(14, 14);
    }

    private void setReportLoadingState(boolean loading) {
        if (generateReportButton == null) {
            return;
        }
        generateReportButton.setDisable(loading);
        generateReportButton.setText(loading ? "Generation..." : REPORT_BUTTON_DEFAULT_TEXT);
        generateReportButton.setGraphic(loading ? reportLoadingIndicator : null);
    }

    private void showReportDialog(UserReport report) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Rapport utilisateurs");
        alert.setHeaderText(report.getTitle());
        alert.setResizable(true);

        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStyleClass().add("report-dialog-pane");
        String dashboardCss = getClass().getResource("/views/styles/dashboard.css").toExternalForm();
        if (!dialogPane.getStylesheets().contains(dashboardCss)) {
            dialogPane.getStylesheets().add(dashboardCss);
        }

        TextArea area = new TextArea(report.getReportText());
        area.setWrapText(true);
        area.setEditable(false);
        area.getStyleClass().add("report-preview-area");
        area.setPrefWidth(720);
        area.setPrefHeight(460);
        dialogPane.setContent(area);

        ButtonType exportPdfBtn = new ButtonType("Exporter PDF");
        ButtonType closeBtn = new ButtonType("Fermer", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(exportPdfBtn, closeBtn);

        Button exportButton = (Button) dialogPane.lookupButton(exportPdfBtn);
        exportButton.getStyleClass().addAll("primary-action-button", "report-dialog-export-button");

        Button closeButton = (Button) dialogPane.lookupButton(closeBtn);
        closeButton.getStyleClass().addAll("card-action-button", "report-dialog-close-button");

        alert.showAndWait().ifPresent(result -> {
            if (result == exportPdfBtn) {
                exportReportPdfWithChooser(report);
            }
        });
    }

    private void exportReportPdfWithChooser(UserReport report) {
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Enregistrer le rapport PDF");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            chooser.setInitialFileName("rapport-utilisateurs-" + LocalDateTime.now().format(REPORT_FILE_FORMAT) + ".pdf");

            Window owner = generateReportButton != null && generateReportButton.getScene() != null
                    ? generateReportButton.getScene().getWindow()
                    : null;

            java.io.File selected = chooser.showSaveDialog(owner);
            if (selected == null) {
                return;
            }

            Path outputPath = userReportService.exportReportAsPdf(report, selected.toPath());
            if (voiceFeedbackLabel != null) {
                voiceFeedbackLabel.setText("✓ PDF exporte: " + outputPath.getFileName());
            }
        } catch (Exception e) {
            if (voiceFeedbackLabel != null) {
                voiceFeedbackLabel.setText("⚠ Export PDF echoue: " + e.getMessage());
            }
        }
    }

    private String buildContextSummary() {
        try {
            DashboardService.DashboardData data = dashboardService.loadDashboardData();
            return String.format(
                    "Page: Dashboard. Utilisateurs: %d, Artistes: %d, Amateurs: %d, Réclamations: %d",
                    data.getTotalUsers(), data.getTotalArtistes(),
                    data.getTotalAmateurs(), data.getTotalReclamations()
            );
        } catch (Exception e) {
            return "Page: Dashboard";
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DASHBOARD
    // ══════════════════════════════════════════════════════════════════════════

    private void loadDashboardDataAsync() {
        Task<DashboardService.DashboardData> loadTask = new Task<>() {
            @Override
            protected DashboardService.DashboardData call() throws Exception {
                return dashboardService.loadDashboardData();
            }
        };
        loadTask.setOnSucceeded(event -> renderDashboard(loadTask.getValue()));
        loadTask.setOnFailed(event -> showErrorState(loadTask.getException()));
        Thread worker = new Thread(loadTask, "dashboard-loader");
        worker.setDaemon(true);
        worker.start();
    }

    private void renderDashboard(DashboardService.DashboardData data) {
        buildStatCards(data);
        buildLineChart(data);
        buildRolePie(data);
        fillRecentLists(data);

        // Animation légère d'affichage (après injection des nodes dans les containers)
        Platform.runLater(this::animateDashboardCards);
    }

    private void animateDashboardCards() {
        // Stat cards (effet "pop")
        CardAnimator.animateScalePop(statsContainer.getChildren(), 40);

        // Sections principales (chart/pie) en fade+slide up
        CardAnimator.animateFadeSlideUp(java.util.List.of(lineChartContainer, rolePieContainer), 140);

        // Items des listes récentes (stagger latéral)
        CardAnimator.animateStaggerList(signupsList.getChildren(), 180);
        CardAnimator.animateStaggerList(reclamationsList.getChildren(), 220);
        CardAnimator.animateStaggerList(topArtistesList.getChildren(), 260);
    }

    private void showLoadingState() {
        buildStatCards(new DashboardService.DashboardData(
                0, 0, 0, 0, 0, 0,
                java.util.List.of(),
                java.util.Map.of("Admin", 0, "Artistes", 0, "Amateurs", 0),
                java.util.List.of("Chargement..."),
                java.util.List.of("Chargement..."),
                java.util.List.of("Chargement...")
        ));
    }

    private void showErrorState(Throwable error) {
        String message = error == null || error.getMessage() == null
                ? "Erreur de chargement."
                : error.getMessage();
        buildStatCards(new DashboardService.DashboardData(
                0, 0, 0, 0, 0, 0,
                java.util.List.of(),
                java.util.Map.of("Admin", 0, "Artistes", 0, "Amateurs", 0),
                java.util.List.of("Erreur: " + message),
                java.util.List.of("Erreur: " + message),
                java.util.List.of("Erreur: " + message)
        ));
        lineChartContainer.getChildren().clear();
        rolePieContainer.getChildren().clear();
    }

    private void buildStatCards(DashboardService.DashboardData data) {
        statsContainer.getChildren().setAll(
                new StatCard("U", "icon-users",    data.getTotalUsers(),        "Utilisateurs"),
                new StatCard("A", "icon-artistes", data.getTotalArtistes(),     "Artistes"),
                new StatCard("M", "icon-amateurs", data.getTotalAmateurs(),     "Amateurs"),
                new StatCard("O", "icon-oeuvres",  data.getTotalOeuvres(),      "Oeuvres"),
                new StatCard("R", "icon-reclam",   data.getTotalReclamations(), "Reclamations"),
                new StatCard("E", "icon-events",   data.getTotalEvenements(),   "Evenements")
        );
    }

    private void buildLineChart(DashboardService.DashboardData data) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis   yAxis = new NumberAxis();
        xAxis.setLabel("Mois");
        yAxis.setLabel("Inscriptions");
        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setCreateSymbols(true);
        chart.setMinHeight(250.0);
        chart.setPrefHeight(250.0);
        chart.setMaxHeight(Double.MAX_VALUE);
        chart.getStyleClass().add("dashboard-line-chart");
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        if (data.getSignupsByMonth().isEmpty()) {
            series.getData().add(new XYChart.Data<>("Aucune donnée", 0));
        } else {
            for (DashboardService.MonthlySignupPoint point : data.getSignupsByMonth()) {
                series.getData().add(new XYChart.Data<>(point.getMonthLabel(), point.getTotal()));
            }
        }
        chart.getData().add(series);
        lineChartContainer.getChildren().setAll(chart);
    }

    private void buildRolePie(DashboardService.DashboardData data) {
        Map<String, Integer> dist = data.getRoleDistribution();
        PieChart pieChart = new PieChart();
        pieChart.getData().addAll(
                new PieChart.Data("Admin",    dist.getOrDefault("Admin",    0)),
                new PieChart.Data("Artistes", dist.getOrDefault("Artistes", 0)),
                new PieChart.Data("Amateurs", dist.getOrDefault("Amateurs", 0))
        );
        pieChart.getStyleClass().add("dashboard-pie");
        rolePieContainer.getChildren().setAll(pieChart);
    }

    private void fillRecentLists(DashboardService.DashboardData data) {
        fillSignups(data.getRecentSignups());
        fillReclamations(data.getRecentReclamations());
        fillTopArtistes(data.getTopArtistes());
    }

    private void fillSignups(List<String> items) {
        signupsList.getChildren().clear();
        if (items == null || items.isEmpty()) {
            signupsList.getChildren().add(
                    makeEmptyState("👤", "Aucune inscription récente", "Les nouveaux membres apparaîtront ici", null)
            );
            return;
        }
        items.stream().limit(MAX_RECENT_ITEMS).forEach(item -> {
            String[] parts    = splitItem(item);
            String   initiale = parts[0].isBlank() ? "?" : parts[0].substring(0, 1).toUpperCase();
            Label    avatar   = makeAvatar(initiale, "dash-item-avatar");
            signupsList.getChildren().add(makeItemRow(avatar, parts[0], parts[1], null));
        });
    }

    private void fillReclamations(List<String> items) {
        reclamationsList.getChildren().clear();
        int count = (items == null) ? 0 : items.size();
        if (reclamationsBadge != null) reclamationsBadge.setText(String.valueOf(count));
        if (items == null || items.isEmpty()) {
            reclamationsList.getChildren().add(
                    makeEmptyState("✅", "Aucune réclamation", "Tout est en ordre pour le moment", "red")
            );
            return;
        }
        items.stream().limit(MAX_RECENT_ITEMS).forEach(item -> {
            String[] parts    = splitItem(item);
            String   initiale = parts[0].isBlank() ? "!" : parts[0].substring(0, 1).toUpperCase();
            Label    avatar   = makeAvatar(initiale, "dash-item-avatar", "dash-item-avatar-red");
            reclamationsList.getChildren().add(makeItemRow(avatar, parts[0], parts[1], null));
        });
    }

    private void fillTopArtistes(List<String> items) {
        topArtistesList.getChildren().clear();
        if (items == null || items.isEmpty()) {
            topArtistesList.getChildren().add(
                    makeEmptyState("🎨", "Aucun artiste classé", "Les artistes les plus actifs apparaîtront ici", "purple")
            );
            return;
        }
        for (String item : items) {
            String[] parts    = splitItem(item);
            String   initiale = parts[0].isBlank() ? "A" : parts[0].substring(0, 1).toUpperCase();
            Label    avatar   = makeAvatar(initiale, "dash-item-avatar", "dash-item-avatar-purple");
            Label    badge    = null;
            if (!parts[1].isBlank()) {
                badge = new Label(parts[1]);
                badge.getStyleClass().add("dash-item-badge");
            }
            topArtistesList.getChildren().add(makeItemRow(avatar, parts[0], null, badge));
        }
    }

    private Node makeEmptyState(String icon, String title, String hint, String colorVariant) {
        VBox container = new VBox(10);
        container.getStyleClass().add("dash-empty-state");
        container.setAlignment(Pos.CENTER);
        container.setMaxWidth(Double.MAX_VALUE);
        Region topLine = new Region();
        topLine.getStyleClass().add("dash-empty-divider");
        topLine.setMaxWidth(Double.MAX_VALUE);
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("dash-empty-icon");
        StackPane iconCircle = new StackPane(iconLabel);
        iconCircle.getStyleClass().add("dash-empty-icon-circle");
        if (colorVariant != null && !colorVariant.isBlank())
            iconCircle.getStyleClass().add(colorVariant);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dash-empty-title");
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setAlignment(Pos.CENTER);
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("dash-empty-hint");
        hintLabel.setMaxWidth(Double.MAX_VALUE);
        hintLabel.setAlignment(Pos.CENTER);
        hintLabel.setWrapText(true);
        Region bottomLine = new Region();
        bottomLine.getStyleClass().add("dash-empty-divider");
        bottomLine.setMaxWidth(Double.MAX_VALUE);
        container.getChildren().addAll(topLine, iconCircle, titleLabel, hintLabel, bottomLine);
        return container;
    }

    private Node makeItemRow(Label avatar, String title, String subtitle, Node rightNode) {
        HBox row = new HBox(10);
        row.getStyleClass().add("dash-item-row");
        row.setAlignment(Pos.CENTER_LEFT);
        VBox textBox = new VBox(2);
        HBox.setHgrow(textBox, Priority.ALWAYS);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("premium-list-title");
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setWrapText(true);
        textBox.getChildren().add(titleLabel);
        if (subtitle != null && !subtitle.isBlank()) {
            Label sub = new Label(subtitle);
            sub.getStyleClass().add("premium-list-subtitle");
            sub.setMaxWidth(Double.MAX_VALUE);
            sub.setWrapText(true);
            textBox.getChildren().add(sub);
        }
        row.getChildren().addAll(avatar, textBox);
        if (rightNode != null) row.getChildren().add(rightNode);
        return row;
    }

    private Label makeAvatar(String text, String... styleClasses) {
        Label avatar = new Label(text);
        avatar.getStyleClass().addAll(styleClasses);
        return avatar;
    }

    private String[] splitItem(String value) {
        if (value == null) return new String[]{"", ""};
        int sep = value.lastIndexOf(" - ");
        if (sep <= 0 || sep >= value.length() - 3) return new String[]{value.trim(), ""};
        return new String[]{value.substring(0, sep).trim(), value.substring(sep + 3).trim()};
    }
}