package controllers;

import services.UserService;
import entities.User;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import utils.CardAnimator;
import javafx.scene.layout.VBox;
import utils.InputValidator;

import java.sql.SQLDataException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public abstract class BaseUsersBackofficeController {

    private static final int MINIMUM_AGE = 13;
    private static final String STATUT_ACTIVE = "Activé";
    private static final String STATUT_BLOQUE = "Bloqué";
    private static final String[] CENTRES_INTERET = {
            "Peinture", "Sculpture", "Photographie", "Musique", "Lecture"
    };
    private static final String[] SPECIALITES_ARTISTE = {
            "Peintre", "Sculpteur", "Photographe", "Musicien", "Auteur"
    };

    @FXML protected TextField searchField;
    @FXML protected FlowPane  cardsContainer;
    @FXML protected Label     messageLabel;
    @FXML protected ComboBox<String> sortComboBox;

    private final UserService          userService = new UserService();
    private final ObservableList<User> users       = FXCollections.observableArrayList();

    protected abstract String managedRole();
    protected abstract String managedRoleLabel();

    private void applyStylesheets(DialogPane pane) {
        String css = getClass().getResource("/views/styles/dashboard.css").toExternalForm();
        pane.getStylesheets().add(css);
    }

    @FXML
    public void initialize() {
        cardsContainer.setHgap(18);
        cardsContainer.setVgap(18);
        cardsContainer.setPrefWrapLength(1140);
        searchField.textProperty().addListener((obs, oldValue, newValue) -> renderCards());

        if (sortComboBox != null) {
            sortComboBox.setItems(FXCollections.observableArrayList(
                    "Nom (A-Z)", "Nom (Z-A)", "Email (A-Z)",
                    "Date inscription (Récent)", "Date inscription (Ancien)",
                    "Statut (Activé)", "Statut (Bloqué)"));
            sortComboBox.setValue("Nom (A-Z)");
            sortComboBox.valueProperty().addListener((obs, oldValue, newValue) -> renderCards());
        }
        loadUsers();
    }

    @FXML
    protected void onAddUser() {
        Optional<User> dialogResult = showUserDialog(null);
        if (dialogResult.isEmpty()) return;
        try {
            userService.add(dialogResult.get());
            setMessage(managedRoleLabel() + " ajoute avec succes.", false);
            loadUsers();
        } catch (SQLDataException e) {
            setMessage("Erreur ajout: " + e.getMessage(), true);
        }
    }

    protected void onEditUser(User existingUser) {
        Optional<User> dialogResult = showUserDialog(existingUser);
        if (dialogResult.isEmpty()) return;
        try {
            userService.update(dialogResult.get());
            setMessage(managedRoleLabel() + " modifie avec succes.", false);
            loadUsers();
        } catch (SQLDataException e) {
            setMessage("Erreur modification: " + e.getMessage(), true);
        }
    }

    protected void onDeleteUser(User user) {
        if (!showDeleteConfirmationDialog(user)) return;
        try {
            userService.delete(user);
            setMessage(managedRoleLabel() + " supprime avec succes.", false);
            loadUsers();
        } catch (SQLDataException e) {
            setMessage("Erreur suppression: " + e.getMessage(), true);
        }
    }

    protected void onActivateUser(User user) {
        if (!showActivateConfirmationDialog(user)) return;
        try {
            userService.activateBlockedUser(user);
            setMessage(managedRoleLabel() + " active avec succes.", false);
            loadUsers();
        } catch (SQLDataException e) {
            setMessage("Erreur activation: " + e.getMessage(), true);
        }
    }

    protected void onBlockUser(User user) {
        if (!showBlockConfirmationDialog(user)) return;
        try {
            userService.blockUser(user);
            setMessage(managedRoleLabel() + " bloqué avec succès.", false);
            loadUsers();
        } catch (SQLDataException e) {
            setMessage("Erreur blocage: " + e.getMessage(), true);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // COMMANDES VOCALES
    // ══════════════════════════════════════════════════════════════════════════

    public void setSearchQuery(String query) {
        if (query == null || query.isBlank()) return;
        Platform.runLater(() -> searchField.setText(query));
    }

    public void openCreateForm(String prefillName) {
        Platform.runLater(() -> onAddUser());
    }

    public void deleteUserByName(String name) {
        if (name == null || name.isBlank()) return;
        Platform.runLater(() ->
                users.stream()
                        .filter(u -> matchesName(u, name))
                        .findFirst()
                        .ifPresentOrElse(
                                u -> onDeleteUser(u),
                                () -> setMessage("Utilisateur introuvable : " + name, true)
                        )
        );
    }

    public void blockUserByName(String name) {
        if (name == null || name.isBlank()) return;
        Platform.runLater(() ->
                users.stream()
                        .filter(u -> matchesName(u, name))
                        .findFirst()
                        .ifPresentOrElse(
                                u -> onBlockUser(u),
                                () -> setMessage("Utilisateur introuvable : " + name, true)
                        )
        );
    }

    public void activateUserByName(String name) {
        if (name == null || name.isBlank()) return;
        Platform.runLater(() ->
                users.stream()
                        .filter(u -> matchesName(u, name))
                        .findFirst()
                        .ifPresentOrElse(
                                u -> onActivateUser(u),
                                () -> setMessage("Utilisateur introuvable : " + name, true)
                        )
        );
    }

    private boolean matchesName(User u, String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        return safe(u.getNom()).toLowerCase(Locale.ROOT).contains(n)
                || safe(u.getPrenom()).toLowerCase(Locale.ROOT).contains(n)
                || (safe(u.getNom()) + " " + safe(u.getPrenom())).toLowerCase(Locale.ROOT).contains(n)
                || (safe(u.getPrenom()) + " " + safe(u.getNom())).toLowerCase(Locale.ROOT).contains(n);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DIALOGS
    // ══════════════════════════════════════════════════════════════════════════

    private boolean showDeleteConfirmationDialog(User user) {
        ButtonType deleteButtonType = new ButtonType("Supprimer", ButtonBar.ButtonData.OK_DONE);

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Suppression " + managedRoleLabel().toLowerCase(Locale.ROOT));
        confirmation.setHeaderText(null);
        confirmation.getDialogPane().getButtonTypes().setAll(deleteButtonType, ButtonType.CANCEL);
        confirmation.getDialogPane().setPrefSize(520, 330);

        applyStylesheets(confirmation.getDialogPane());
        confirmation.getDialogPane().getStyleClass().addAll("users-dialog-pane", "users-delete-dialog");

        Label modeChip = new Label("Action sensible");
        modeChip.getStyleClass().add("users-dialog-status-chip");
        Label roleChip = new Label(managedRoleLabel());
        roleChip.getStyleClass().add("users-dialog-role-chip");
        HBox chipRow = new HBox(8, modeChip, roleChip);
        chipRow.getStyleClass().add("users-dialog-chip-row");

        Label title = new Label("Supprimer définitivement ce profil ?");
        title.getStyleClass().addAll("users-dialog-title", "users-delete-title");
        Label subtitle = new Label("Cette action est irréversible et retire l'utilisateur du backoffice.");
        subtitle.getStyleClass().addAll("users-dialog-subtitle", "users-delete-subtitle");

        VBox hero = new VBox(6, chipRow, title, subtitle);
        hero.getStyleClass().addAll("users-dialog-hero", "users-delete-hero");

        Label identityTitle = new Label(safe(user.getNom()) + " " + safe(user.getPrenom()));
        identityTitle.getStyleClass().add("users-dialog-section-title");
        Label identityEmail = new Label(safe(user.getEmail()));
        identityEmail.getStyleClass().add("users-dialog-section-subtitle");
        Label identityHint = new Label("Role: " + managedRoleLabel() + "  |  Statut: " + formatStatusLabel(user.getStatut()));
        identityHint.getStyleClass().add("users-dialog-label");

        VBox identityCard = new VBox(6, identityTitle, identityEmail, identityHint);
        identityCard.getStyleClass().addAll("users-dialog-section-card", "users-delete-identity-card");

        VBox content = new VBox(12, hero, identityCard);
        content.getStyleClass().add("users-dialog-content");
        confirmation.getDialogPane().setContent(content);

        Button deleteButton = (Button) confirmation.getDialogPane().lookupButton(deleteButtonType);
        deleteButton.getStyleClass().addAll("card-action-button", "card-danger-button");
        Button cancelButton = (Button) confirmation.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.getStyleClass().addAll("card-action-button", "card-soft-button");

        Optional<ButtonType> choice = confirmation.showAndWait();
        return choice.isPresent() && choice.get() == deleteButtonType;
    }

    private boolean showActivateConfirmationDialog(User user) {
        ButtonType activateButtonType = new ButtonType("Activer le compte", ButtonBar.ButtonData.OK_DONE);

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Activer " + managedRoleLabel().toLowerCase(Locale.ROOT));
        confirmation.setHeaderText(null);
        confirmation.getDialogPane().getButtonTypes().setAll(activateButtonType, ButtonType.CANCEL);
        confirmation.getDialogPane().setPrefSize(560, 420);

        applyStylesheets(confirmation.getDialogPane());
        confirmation.getDialogPane().getStyleClass().addAll("users-dialog-pane", "users-activate-dialog");

        Label iconLabel = new Label("🔓");
        iconLabel.setStyle("-fx-font-size: 48;");

        Label modeChip = new Label("✓ Validation");
        modeChip.getStyleClass().add("users-dialog-status-chip");
        Label roleChip = new Label(managedRoleLabel());
        roleChip.getStyleClass().add("users-dialog-role-chip");
        HBox chipRow = new HBox(8, modeChip, roleChip);
        chipRow.getStyleClass().add("users-dialog-chip-row");

        Label title = new Label("Activer ce profil ?");
        title.getStyleClass().add("users-dialog-title");
        Label subtitle = new Label("Le compte sera activé et l'utilisateur pourra se connecter immédiatement.");
        subtitle.getStyleClass().add("users-dialog-subtitle");
        subtitle.setWrapText(true);

        VBox hero = new VBox(12, iconLabel, chipRow, title, subtitle);
        hero.setAlignment(Pos.TOP_CENTER);
        hero.getStyleClass().add("users-dialog-hero");

        VBox profileCard = new VBox(10);
        profileCard.getStyleClass().add("users-dialog-section-card");

        Label profileTitle = new Label("Profil à activer");
        profileTitle.getStyleClass().add("users-dialog-section-title");

        profileCard.getChildren().addAll(
                profileTitle,
                createDetailsRow("Utilisateur", safe(user.getNom()) + " " + safe(user.getPrenom())),
                createDetailsRow("Email",        safe(user.getEmail())),
                createDetailsRow("Rôle",         managedRoleLabel()),
                createDetailsRow("Statut actuel",formatStatusLabel(user.getStatut())));

        Label infoLabel = new Label("ℹ Une fois activé, ce compte aura un accès complet à la plateforme.");
        infoLabel.getStyleClass().add("users-dialog-label");
        infoLabel.setWrapText(true);

        VBox content = new VBox(14, hero, profileCard, infoLabel);
        content.getStyleClass().add("users-dialog-content");
        confirmation.getDialogPane().setContent(content);

        Button activateButton = (Button) confirmation.getDialogPane().lookupButton(activateButtonType);
        activateButton.getStyleClass().addAll("card-action-button", "card-success-button");
        Button cancelButton = (Button) confirmation.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.getStyleClass().addAll("card-action-button", "card-soft-button");

        Optional<ButtonType> choice = confirmation.showAndWait();
        return choice.isPresent() && choice.get() == activateButtonType;
    }

    private boolean showBlockConfirmationDialog(User user) {
        ButtonType blockButtonType = new ButtonType("Bloquer le compte", ButtonBar.ButtonData.OK_DONE);

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Bloquer " + managedRoleLabel().toLowerCase(Locale.ROOT));
        confirmation.setHeaderText(null);
        confirmation.getDialogPane().getButtonTypes().setAll(blockButtonType, ButtonType.CANCEL);
        confirmation.getDialogPane().setPrefSize(560, 420);

        applyStylesheets(confirmation.getDialogPane());
        confirmation.getDialogPane().getStyleClass().addAll("users-dialog-pane", "users-delete-dialog");

        Label iconLabel = new Label("🔒");
        iconLabel.setStyle("-fx-font-size: 48;");

        Label modeChip = new Label("⊘ Blocage");
        modeChip.getStyleClass().add("users-dialog-status-chip");
        Label roleChip = new Label(managedRoleLabel());
        roleChip.getStyleClass().add("users-dialog-role-chip");
        HBox chipRow = new HBox(8, modeChip, roleChip);
        chipRow.getStyleClass().add("users-dialog-chip-row");

        Label title = new Label("Bloquer ce profil ?");
        title.getStyleClass().add("users-dialog-title");
        Label subtitle = new Label("Le compte sera bloqué et l'utilisateur ne pourra plus se connecter.");
        subtitle.getStyleClass().add("users-dialog-subtitle");
        subtitle.setWrapText(true);

        VBox hero = new VBox(12, iconLabel, chipRow, title, subtitle);
        hero.setAlignment(Pos.TOP_CENTER);
        hero.getStyleClass().add("users-dialog-hero");

        VBox profileCard = new VBox(10);
        profileCard.getStyleClass().add("users-dialog-section-card");

        Label profileTitle = new Label("Profil à bloquer");
        profileTitle.getStyleClass().add("users-dialog-section-title");

        profileCard.getChildren().addAll(
                profileTitle,
                createDetailsRow("Utilisateur", safe(user.getNom()) + " " + safe(user.getPrenom())),
                createDetailsRow("Email",        safe(user.getEmail())),
                createDetailsRow("Rôle",         managedRoleLabel()),
                createDetailsRow("Statut actuel",formatStatusLabel(user.getStatut())));

        Label infoLabel = new Label("⚠ Une fois bloqué, cet utilisateur perdra l'accès à la plateforme.");
        infoLabel.getStyleClass().add("users-dialog-label");
        infoLabel.setWrapText(true);

        VBox content = new VBox(14, hero, profileCard, infoLabel);
        content.getStyleClass().add("users-dialog-content");
        confirmation.getDialogPane().setContent(content);

        Button blockButton = (Button) confirmation.getDialogPane().lookupButton(blockButtonType);
        blockButton.getStyleClass().addAll("card-action-button", "card-danger-button");
        Button cancelButton = (Button) confirmation.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.getStyleClass().addAll("card-action-button", "card-soft-button");

        Optional<ButtonType> choice = confirmation.showAndWait();
        return choice.isPresent() && choice.get() == blockButtonType;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CHARGEMENT & RENDU
    // ══════════════════════════════════════════════════════════════════════════

    private void loadUsers() {
        try {
            users.setAll(userService.getByRole(managedRole()));
            renderCards();
            setMessage(users.size() + " " + managedRoleLabel().toLowerCase(Locale.ROOT) + "(s) charges.", false);
        } catch (SQLDataException e) {
            users.clear();
            cardsContainer.getChildren().clear();
            setMessage("Erreur chargement: " + e.getMessage(), true);
        }
    }

    private void renderCards() {
        cardsContainer.getChildren().clear();

        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        List<User> filteredUsers = users.stream()
                .filter(user -> matchesQuery(user, query))
                .toList();

        String sortOption = sortComboBox != null ? sortComboBox.getValue() : "Nom (A-Z)";
        List<User> sortedUsers = applySorting(filteredUsers, sortOption);

        for (User user : sortedUsers) {
            cardsContainer.getChildren().add(createUserCard(user));
        }

        if (sortedUsers.isEmpty()) {
            Label emptyState = new Label("Aucun " + managedRoleLabel().toLowerCase(Locale.ROOT) + " trouve.");
            emptyState.getStyleClass().add("users-empty-state");
            cardsContainer.getChildren().add(emptyState);
        }

        // Animation d'affichage des cartes (limiter pour performance si liste très longue)
        int limit = Math.min(cardsContainer.getChildren().size(), 18);
        CardAnimator.animateFadeSlideUp(cardsContainer.getChildren().subList(0, limit), 60);
    }

    private List<User> applySorting(List<User> list, String sortOption) {
        return switch (sortOption) {
            case "Nom (A-Z)" -> list.stream()
                    .sorted((u1, u2) -> (u1.getNom() + " " + u1.getPrenom()).compareTo(u2.getNom() + " " + u2.getPrenom()))
                    .toList();
            case "Nom (Z-A)" -> list.stream()
                    .sorted((u1, u2) -> (u2.getNom() + " " + u2.getPrenom()).compareTo(u1.getNom() + " " + u1.getPrenom()))
                    .toList();
            case "Email (A-Z)" -> list.stream()
                    .sorted((u1, u2) -> safe(u1.getEmail()).compareTo(safe(u2.getEmail())))
                    .toList();
            case "Date inscription (Récent)" -> list.stream()
                    .sorted((u1, u2) -> u2.getDateInscription().compareTo(u1.getDateInscription()))
                    .toList();
            case "Date inscription (Ancien)" -> list.stream()
                    .sorted((u1, u2) -> u1.getDateInscription().compareTo(u2.getDateInscription()))
                    .toList();
            case "Statut (Activé)" -> list.stream()
                    .filter(u -> u.getStatut() != null && u.getStatut().toLowerCase(Locale.ROOT).contains("activ"))
                    .sorted((u1, u2) -> (u1.getNom() + " " + u1.getPrenom()).compareTo(u2.getNom() + " " + u2.getPrenom()))
                    .toList();
            case "Statut (Bloqué)" -> list.stream()
                    .filter(u -> u.getStatut() != null && u.getStatut().toLowerCase(Locale.ROOT).contains("bloqu"))
                    .sorted((u1, u2) -> (u1.getNom() + " " + u1.getPrenom()).compareTo(u2.getNom() + " " + u2.getPrenom()))
                    .toList();
            default -> list;
        };
    }

    private boolean matchesQuery(User user, String query) {
        if (query.isEmpty()) return true;
        String searchableText = String.join(" ",
                safe(user.getNom()), safe(user.getPrenom()),
                safe(user.getEmail()), safe(user.getVille()), safe(user.getNumTel()));
        return searchableText.toLowerCase(Locale.ROOT).contains(query);
    }

    private Node createUserCard(User user) {
        VBox card = new VBox(14);
        card.setPrefWidth(356);
        card.setMinHeight(248);
        card.getStyleClass().add("user-card");

        Label initials = new Label(buildInitials(user));
        initials.getStyleClass().add("user-avatar");

        Label nameLabel = new Label(safe(user.getNom()) + " " + safe(user.getPrenom()));
        nameLabel.getStyleClass().add("user-card-title");

        Label emailLabel = new Label(safe(user.getEmail()));
        emailLabel.getStyleClass().add("user-card-subtitle");

        VBox identity = new VBox(2, nameLabel, emailLabel);
        identity.getStyleClass().add("user-card-identity");

        Label statusBadge = new Label(formatStatusLabel(user.getStatut()));
        statusBadge.getStyleClass().addAll("badge-status", statusStyleClass(user.getStatut()));

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        HBox topRow = new HBox(12, initials, identity, topSpacer, statusBadge);
        topRow.getStyleClass().add("user-card-top");

        Label roleBadge = new Label(managedRoleLabel());
        roleBadge.getStyleClass().add("badge-role");

        Label roleSpecificBadge = new Label("Artiste".equalsIgnoreCase(managedRole()) ? "Specialite" : "Centre interet");
        roleSpecificBadge.getStyleClass().add("badge-role-secondary");

        HBox badgeRow = new HBox(8, roleBadge, roleSpecificBadge);
        badgeRow.getStyleClass().add("user-card-badges");

        VBox body = new VBox(8,
                createInfoRow("Telephone",  safe(user.getNumTel())),
                createInfoRow("Ville",      safe(user.getVille())),
                createInfoRow("Naissance",  user.getDateNaissance() == null ? "-" : user.getDateNaissance().toString()),
                createInfoRow("Statut",     formatStatusLabel(user.getStatut())),
                createInfoRow("Artiste".equalsIgnoreCase(managedRole()) ? "Specialite" : "Centre interet",
                        "Artiste".equalsIgnoreCase(managedRole()) ? safe(user.getSpecialite()) : safe(user.getCentreInteret())));
        body.getStyleClass().add("user-card-body");

        Button detailsButton = new Button("Voir details");
        detailsButton.getStyleClass().addAll("card-action-button", "card-soft-button");
        detailsButton.setOnAction(e -> showDetails(user));

        Button editButton = new Button("Modifier");
        editButton.getStyleClass().addAll("card-action-button", "card-soft-button");
        editButton.setOnAction(e -> onEditUser(user));

        Button deleteButton = new Button("Supprimer");
        deleteButton.getStyleClass().addAll("card-action-button", "card-danger-button");
        deleteButton.setOnAction(e -> onDeleteUser(user));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("user-card-actions");

        boolean blocked = user.getStatut() != null &&
                (user.getStatut().toLowerCase(Locale.ROOT).contains("bloqu") ||
                        user.getStatut().toLowerCase(Locale.ROOT).contains("blocked"));

        if (blocked) {
            Button activateButton = new Button("✓ Activer");
            activateButton.getStyleClass().addAll("card-action-button", "card-success-button");
            activateButton.setOnAction(e -> onActivateUser(user));
            actions.getChildren().addAll(detailsButton, spacer, activateButton, editButton, deleteButton);
        } else {
            Button blockButton = new Button("⊘ Bloquer");
            blockButton.getStyleClass().addAll("card-action-button", "card-danger-button");
            blockButton.setOnAction(e -> onBlockUser(user));
            actions.getChildren().addAll(detailsButton, spacer, blockButton, editButton, deleteButton);
        }

        card.getChildren().addAll(topRow, badgeRow, body, actions);
        return card;
    }

    private HBox createInfoRow(String label, String value) {
        Label keyLabel = new Label(label);
        keyLabel.getStyleClass().add("user-info-key");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("user-info-value");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(8, keyLabel, spacer, valueLabel);
        row.getStyleClass().add("user-info-row");
        return row;
    }

    private void showDetails(User user) {
        Alert details = new Alert(Alert.AlertType.INFORMATION);
        details.setTitle("Details " + managedRoleLabel().toLowerCase(Locale.ROOT));
        details.setHeaderText(null);
        details.getDialogPane().setPrefSize(560, 440);

        applyStylesheets(details.getDialogPane());
        details.getDialogPane().getStyleClass().addAll("users-dialog-pane", "users-details-dialog");

        Label modeChip = new Label("Consultation");
        modeChip.getStyleClass().add("users-dialog-status-chip");
        Label roleChip = new Label(managedRoleLabel());
        roleChip.getStyleClass().add("users-dialog-role-chip");
        HBox chipRow = new HBox(8, modeChip, roleChip);
        chipRow.getStyleClass().add("users-dialog-chip-row");

        Label title = new Label(safe(user.getNom()) + " " + safe(user.getPrenom()));
        title.getStyleClass().addAll("users-dialog-title", "users-details-title");
        Label subtitle = new Label("Vue complète du profil utilisateur.");
        subtitle.getStyleClass().addAll("users-dialog-subtitle", "users-details-subtitle");

        VBox hero = new VBox(6, chipRow, title, subtitle);
        hero.getStyleClass().addAll("users-dialog-hero", "users-details-hero");

        String roleSpecificLabel = "Artiste".equalsIgnoreCase(managedRole()) ? "Specialite" : "Centre interet";
        String roleSpecificValue = "Artiste".equalsIgnoreCase(managedRole())
                ? safe(user.getSpecialite()) : safe(user.getCentreInteret());

        VBox identityCard = new VBox(8,
                createDetailsRow("Email",          safe(user.getEmail())),
                createDetailsRow("Telephone",      safe(user.getNumTel())),
                createDetailsRow("Ville",          safe(user.getVille())),
                createDetailsRow("Date naissance", user.getDateNaissance() == null ? "-" : user.getDateNaissance().toString()),
                createDetailsRow("Statut",         formatStatusLabel(user.getStatut())),
                createDetailsRow(roleSpecificLabel, roleSpecificValue));
        identityCard.getStyleClass().addAll("users-dialog-section-card", "users-details-card");

        Label bioTitle = new Label("Biographie");
        bioTitle.getStyleClass().add("users-dialog-section-title");
        Label bioText = new Label(safe(user.getBiographie()));
        bioText.setWrapText(true);
        bioText.getStyleClass().add("users-details-bio-text");
        VBox bioCard = new VBox(8, bioTitle, bioText);
        bioCard.getStyleClass().addAll("users-dialog-section-card", "users-details-card");

        VBox content = new VBox(12, hero, identityCard, bioCard);
        content.getStyleClass().add("users-dialog-content");
        details.getDialogPane().setContent(content);

        Button closeButton = (Button) details.getDialogPane().lookupButton(ButtonType.OK);
        if (closeButton != null) {
            closeButton.getStyleClass().addAll("card-action-button", "card-soft-button");
            closeButton.setText("Fermer");
        }
        details.showAndWait();
    }

    private HBox createDetailsRow(String label, String value) {
        Label keyLabel = new Label(label);
        keyLabel.getStyleClass().add("users-dialog-label");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("user-info-value");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(8, keyLabel, spacer, valueLabel);
        row.getStyleClass().add("user-info-row");
        return row;
    }

    private Optional<User> showUserDialog(User existingUser) {
        boolean creation = existingUser == null;

        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle(creation
                ? "Ajouter " + managedRoleLabel().toLowerCase(Locale.ROOT)
                : "Modifier " + managedRoleLabel().toLowerCase(Locale.ROOT));
        dialog.setHeaderText(null);
        dialog.setResizable(false);
        dialog.getDialogPane().setPrefSize(560, 640);
        dialog.getDialogPane().setMinSize(560, 640);
        dialog.getDialogPane().setMaxSize(560, 640);
        dialog.getDialogPane().setGraphic(null);

        applyStylesheets(dialog.getDialogPane());
        dialog.getDialogPane().getStyleClass().add("users-dialog-pane");

        ButtonType saveButtonType = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField     nomField             = new TextField();
        TextField     prenomField          = new TextField();
        DatePicker    dateNaissancePicker  = new DatePicker();
        TextField     emailField           = new TextField();
        PasswordField passwordField        = new PasswordField();
        PasswordField confirmPasswordField = new PasswordField();
        TextField     telField             = new TextField();
        TextField     villeField           = new TextField();
        ComboBox<String> statutCombo       = new ComboBox<>(FXCollections.observableArrayList(STATUT_ACTIVE, STATUT_BLOQUE));
        ComboBox<String> roleSpecificCombo = new ComboBox<>();
        TextArea      bioArea              = new TextArea();
        Label         formErrorLabel       = new Label();

        nomField.getStyleClass().add("users-dialog-field");
        prenomField.getStyleClass().add("users-dialog-field");
        dateNaissancePicker.getStyleClass().add("users-dialog-field");
        emailField.getStyleClass().add("users-dialog-field");
        passwordField.getStyleClass().add("users-dialog-field");
        confirmPasswordField.getStyleClass().add("users-dialog-field");
        telField.getStyleClass().add("users-dialog-field");
        villeField.getStyleClass().add("users-dialog-field");
        statutCombo.getStyleClass().add("users-dialog-field");
        bioArea.getStyleClass().addAll("users-dialog-field", "users-dialog-textarea");
        roleSpecificCombo.getStyleClass().add("users-dialog-field");
        bioArea.setPrefRowCount(3);
        formErrorLabel.getStyleClass().addAll("dialog-error", "users-dialog-error");

        boolean artistRole = "Artiste".equalsIgnoreCase(managedRole());
        String roleSpecificLabel = artistRole ? "Specialite" : "Centre interet";
        String dialogModeLabel   = creation ? "Nouveau profil" : "Modification du profil";
        String dialogHint        = creation
                ? "Créez une fiche utilisateur claire et bien structurée."
                : "Mettez à jour les informations du profil.";

        roleSpecificCombo.getItems().setAll(artistRole ? SPECIALITES_ARTISTE : CENTRES_INTERET);
        roleSpecificCombo.setPromptText(artistRole ? "Choisissez une spécialité" : "Choisissez un centre d'intérêt");
        passwordField.setPromptText(creation ? "Minimum 8 caracteres" : "Laisser vide pour conserver");
        confirmPasswordField.setPromptText(creation ? "Confirmez le mot de passe" : "Laisser vide pour conserver");

        if (creation) {
            statutCombo.setValue(STATUT_ACTIVE);
        } else {
            nomField.setText(existingUser.getNom());
            prenomField.setText(existingUser.getPrenom());
            dateNaissancePicker.setValue(existingUser.getDateNaissance());
            emailField.setText(existingUser.getEmail());
            telField.setText(existingUser.getNumTel());
            villeField.setText(existingUser.getVille());
            selectComboValue(statutCombo, normalizeStatutValue(existingUser.getStatut()));
            bioArea.setText(existingUser.getBiographie());
            selectComboValue(roleSpecificCombo, artistRole
                    ? clean(existingUser.getSpecialite())
                    : clean(existingUser.getCentreInteret()));
        }

        Label modeChip   = new Label(dialogModeLabel);
        modeChip.getStyleClass().add("users-dialog-mode-chip");
        Label roleChip   = new Label(managedRoleLabel());
        roleChip.getStyleClass().add("users-dialog-role-chip");
        Label statusChip = new Label(creation ? "Création" : "Édition");
        statusChip.getStyleClass().add("users-dialog-status-chip");

        Label dialogTitle    = new Label((creation ? "Créer " : "Modifier ") + managedRoleLabel().toLowerCase(Locale.ROOT));
        dialogTitle.getStyleClass().add("users-dialog-title");
        Label dialogSubtitle = new Label(dialogHint);
        dialogSubtitle.getStyleClass().add("users-dialog-subtitle");

        HBox chipRow = new HBox(8, modeChip, roleChip, statusChip);
        chipRow.getStyleClass().add("users-dialog-chip-row");

        VBox hero = new VBox(6, chipRow, dialogTitle, dialogSubtitle);
        hero.getStyleClass().add("users-dialog-hero");

        VBox identityCard  = buildFormSection("Identité", "Les informations de base du profil.",
                new String[]{"Nom", "Prenom", "Date naissance"},
                new Node[]{nomField, prenomField, dateNaissancePicker});

        VBox contactCard   = buildFormSection("Coordonnées", "Email, téléphone et ville de contact.",
                new String[]{"Email", "Telephone", "Ville"},
                new Node[]{emailField, telField, villeField});

        VBox securityCard  = buildFormSection("Sécurité et statut", "Accès, rôle et état du compte.",
                new String[]{"Mot de passe", "Confirmation", "Statut", roleSpecificLabel},
                new Node[]{passwordField, confirmPasswordField, statutCombo, roleSpecificCombo});

        Label bioTitle = new Label("Biographie");
        bioTitle.getStyleClass().add("users-dialog-section-title");
        Label bioSubtitle = new Label("Une présentation courte et soignée du profil.");
        bioSubtitle.getStyleClass().add("users-dialog-section-subtitle");
        VBox bioCard = new VBox(10, new VBox(2, bioTitle, bioSubtitle), bioArea);
        bioCard.getStyleClass().addAll("users-dialog-section-card", "users-dialog-bio-card");

        VBox dialogContent = new VBox(14, hero, identityCard, contactCard, securityCard, bioCard, formErrorLabel);
        dialogContent.getStyleClass().add("users-dialog-content");
        dialogContent.setFillWidth(true);

        ScrollPane dialogScroll = new ScrollPane(dialogContent);
        dialogScroll.getStyleClass().add("users-dialog-scroll");
        dialogScroll.setFitToWidth(true);
        dialogScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        dialogScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        dialogScroll.setPannable(true);
        dialogScroll.setPrefViewportWidth(520);
        dialogScroll.setPrefViewportHeight(540);
        dialog.getDialogPane().setContent(dialogScroll);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.getStyleClass().add("primary-action-button");
        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.getStyleClass().addAll("card-action-button", "card-soft-button");

        final boolean[] touched = new boolean[10];

        Runnable liveValidation = () -> {
            boolean anyTouched = false;
            for (boolean t : touched) if (t) { anyTouched = true; break; }
            if (!anyTouched) { formErrorLabel.setText(""); return; }
            String err = validateFormLive(creation,
                    nomField.getText(), prenomField.getText(), dateNaissancePicker.getValue(),
                    emailField.getText(), passwordField.getText(), confirmPasswordField.getText(),
                    telField.getText(), villeField.getText(),
                    statutCombo.getValue(), roleSpecificCombo.getValue(),
                    touched[0], touched[1], touched[2], touched[3], touched[4],
                    touched[5], touched[6], touched[7], touched[8], touched[9]);
            formErrorLabel.setText(err == null ? "" : err);
        };

        nomField.textProperty().addListener((o,ov,nv)             ->{ touched[0]=true; liveValidation.run(); });
        prenomField.textProperty().addListener((o,ov,nv)          ->{ touched[1]=true; liveValidation.run(); });
        dateNaissancePicker.valueProperty().addListener((o,ov,nv) ->{ touched[2]=true; liveValidation.run(); });
        emailField.textProperty().addListener((o,ov,nv)           ->{ touched[3]=true; liveValidation.run(); });
        passwordField.textProperty().addListener((o,ov,nv)        ->{ touched[4]=true; liveValidation.run(); });
        confirmPasswordField.textProperty().addListener((o,ov,nv) ->{ touched[5]=true; liveValidation.run(); });
        telField.textProperty().addListener((o,ov,nv)             ->{ touched[6]=true; liveValidation.run(); });
        villeField.textProperty().addListener((o,ov,nv)           ->{ touched[7]=true; liveValidation.run(); });
        statutCombo.valueProperty().addListener((o,ov,nv)         ->{ touched[8]=true; liveValidation.run(); });
        roleSpecificCombo.valueProperty().addListener((o,ov,nv)   ->{ touched[9]=true; liveValidation.run(); });

        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            String err = validateForm(creation,
                    nomField.getText(), prenomField.getText(), dateNaissancePicker.getValue(),
                    emailField.getText(), passwordField.getText(), confirmPasswordField.getText(),
                    telField.getText(), villeField.getText(),
                    statutCombo.getValue(), roleSpecificCombo.getValue());
            if (err != null) { formErrorLabel.setText(err); event.consume(); }
        });

        dialog.setResultConverter(buttonType -> {
            if (buttonType != saveButtonType) return null;
            User u = new User();
            if (!creation) {
                u.setId(existingUser.getId());
                u.setDateInscription(existingUser.getDateInscription());
                u.setPhotoProfil(existingUser.getPhotoProfil());
                u.setPhotoReferencePath(existingUser.getPhotoReferencePath());
            }
            u.setNom(clean(nomField.getText()));
            u.setPrenom(clean(prenomField.getText()));
            u.setDateNaissance(dateNaissancePicker.getValue());
            u.setEmail(clean(emailField.getText()));
            String pwd = clean(passwordField.getText());
            u.setMdp(creation || !isBlank(pwd) ? pwd : null);
            u.setRole(managedRole());
            u.setStatut(clean(statutCombo.getValue()));
            u.setNumTel(clean(telField.getText()));
            u.setVille(clean(villeField.getText()));
            u.setBiographie(clean(bioArea.getText()));
            u.setDateInscription(u.getDateInscription() == null ? LocalDate.now() : u.getDateInscription());
            if (artistRole) {
                u.setSpecialite(clean(roleSpecificCombo.getValue()));
                u.setCentreInteret(null);
            } else {
                u.setCentreInteret(clean(roleSpecificCombo.getValue()));
                u.setSpecialite(null);
            }
            return u;
        });

        return dialog.showAndWait();
    }

    private VBox buildFormSection(String title, String subtitle, String[] labels, Node[] fields) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("users-dialog-section-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("users-dialog-section-subtitle");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        for (int i = 0; i < labels.length; i++) {
            Label lbl = new Label(labels[i]);
            lbl.getStyleClass().add("users-dialog-label");
            grid.add(lbl,      0, i);
            grid.add(fields[i],1, i);
        }

        VBox card = new VBox(10, new VBox(2, titleLabel, subtitleLabel), grid);
        card.getStyleClass().add("users-dialog-section-card");
        return card;
    }

    private String validateForm(boolean creation, String nom, String prenom, LocalDate dob,
                                String email, String pwd, String cpwd, String tel, String ville,
                                String statut, String roleVal) {
        if (!InputValidator.isValidName(nom))    return "Nom invalide (2-50 lettres).";
        if (!InputValidator.isValidName(prenom)) return "Prenom invalide (2-50 lettres).";
        if (dob == null)                         return "La date de naissance est obligatoire.";
        if (!InputValidator.isAdult(dob, MINIMUM_AGE)) return "Date de naissance invalide (age minimum " + MINIMUM_AGE + " ans).";
        if (!InputValidator.isValidEmail(email)) return "Email invalide.";
        if (creation && !InputValidator.isValidPassword(pwd))
            return "Mot de passe faible: 8+ caracteres avec majuscule, minuscule et chiffre.";
        if (!creation && !isBlank(pwd) && !InputValidator.isValidPassword(pwd))
            return "Mot de passe faible: 8+ caracteres avec majuscule, minuscule et chiffre.";
        if (!isBlank(pwd) && isBlank(cpwd))      return "Veuillez confirmer le mot de passe.";
        if (!isBlank(pwd) && !pwd.equals(cpwd))  return "Confirmation du mot de passe invalide.";
        if (!InputValidator.isValidPhone(tel))   return "Telephone invalide (8 a 15 chiffres, + optionnel).";
        if (!InputValidator.isValidCity(ville))  return "Ville invalide (2-60 lettres).";
        if (isBlank(statut))                     return "Le statut est obligatoire.";
        if (isBlank(roleVal)) return "Artiste".equalsIgnoreCase(managedRole())
                ? "La specialite est obligatoire."
                : "Le centre interet est obligatoire.";
        return null;
    }

    private String validateFormLive(boolean creation, String nom, String prenom, LocalDate dob,
                                    String email, String pwd, String cpwd, String tel, String ville,
                                    String statut, String roleVal,
                                    boolean tNom, boolean tPrenom, boolean tDob, boolean tEmail, boolean tPwd,
                                    boolean tCpwd, boolean tTel, boolean tVille, boolean tStatut, boolean tRole) {
        if (tNom    && !InputValidator.isValidName(nom))    return "Nom invalide (2-50 lettres).";
        if (tPrenom && !InputValidator.isValidName(prenom)) return "Prenom invalide (2-50 lettres).";
        if (tDob) {
            if (dob == null) return "La date de naissance est obligatoire.";
            if (!InputValidator.isAdult(dob, MINIMUM_AGE)) return "Date de naissance invalide (age minimum " + MINIMUM_AGE + " ans).";
        }
        if (tEmail && !InputValidator.isValidEmail(email))  return "Email invalide.";
        if (tPwd) {
            if (creation && !InputValidator.isValidPassword(pwd))
                return "Mot de passe faible: 8+ caracteres avec majuscule, minuscule et chiffre.";
            if (!creation && !isBlank(pwd) && !InputValidator.isValidPassword(pwd))
                return "Mot de passe faible: 8+ caracteres avec majuscule, minuscule et chiffre.";
        }
        if (tCpwd) {
            if (!isBlank(pwd) && isBlank(cpwd))     return "Veuillez confirmer le mot de passe.";
            if (!isBlank(pwd) && !pwd.equals(cpwd)) return "Confirmation du mot de passe invalide.";
        }
        if (tTel    && !InputValidator.isValidPhone(tel))  return "Telephone invalide (8 a 15 chiffres, + optionnel).";
        if (tVille  && !InputValidator.isValidCity(ville)) return "Ville invalide (2-60 lettres).";
        if (tStatut && isBlank(statut))                    return "Le statut est obligatoire.";
        if (tRole   && isBlank(roleVal)) return "Artiste".equalsIgnoreCase(managedRole())
                ? "La specialite est obligatoire."
                : "Le centre interet est obligatoire.";
        return null;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UTILITAIRES
    // ══════════════════════════════════════════════════════════════════════════

    private String formatStatusLabel(String status) {
        String n = safe(status).toLowerCase(Locale.ROOT);
        return switch (n) {
            case "activé", "active"  -> "Activé";
            case "bloqué", "blocked" -> "Bloqué";
            case "pending"           -> "En attente";
            default -> n.isEmpty() ? "-" : n.substring(0,1).toUpperCase(Locale.ROOT) + n.substring(1);
        };
    }

    private String statusStyleClass(String status) {
        String n = safe(status).toLowerCase(Locale.ROOT);
        return switch (n) {
            case "activé", "active"  -> "status-active";
            case "bloqué", "blocked" -> "status-blocked";
            case "pending"           -> "status-pending";
            default                  -> "status-neutral";
        };
    }

    private String normalizeStatutValue(String status) {
        String n = safe(status).trim().toLowerCase(Locale.ROOT);
        return switch (n) {
            case "activé", "active"  -> STATUT_ACTIVE;
            case "bloqué", "blocked" -> STATUT_BLOQUE;
            default -> clean(status);
        };
    }

    private void selectComboValue(ComboBox<String> cb, String value) {
        if (cb == null) return;
        String v = clean(value);
        if (isBlank(v)) { cb.setValue(null); return; }
        if (!cb.getItems().contains(v)) cb.getItems().add(0, v);
        cb.setValue(v);
    }

    private void setMessage(String message, boolean error) {
        if (messageLabel == null) return;
        messageLabel.setText(message);
        messageLabel.getStyleClass().removeAll("error", "success");
        messageLabel.getStyleClass().add(error ? "error" : "success");
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private String buildInitials(User user) {
        String f = isBlank(user.getPrenom()) ? "" : user.getPrenom().trim();
        String l = isBlank(user.getNom())    ? "" : user.getNom().trim();
        String i = (f.isEmpty() ? "" : f.substring(0,1)) + (l.isEmpty() ? "" : l.substring(0,1));
        return i.isEmpty() ? "U" : i.toUpperCase(Locale.ROOT);
    }

    private String clean(String value)    { return InputValidator.clean(value); }
    private boolean isBlank(String value) { return InputValidator.isBlank(value); }
}