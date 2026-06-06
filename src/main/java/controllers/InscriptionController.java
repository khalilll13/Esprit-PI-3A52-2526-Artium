package controllers;

import services.UserService;
import entities.User;
import utils.CardAnimator;
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import utils.ArtisticBackground;
import utils.InputValidator;

import java.io.File;
import java.sql.SQLDataException;
import java.time.LocalDate;

public class InscriptionController {

	private static final int MINIMUM_AGE = 13;
	private static final String ROLE_AMATEUR = "Amateur";
	private static final String ROLE_ARTISTE = "Artiste";
	private static final String ROLE_ADMIN = "Admin";
	private static final String[] CENTRES_INTERET = {
			"Peinture",
			"Sculpture",
			"Photographie",
			"Musique",
			"Lecture"
	};
	private static final String[] SPECIALITES_ARTISTE = {
			"Peintre",
			"Sculpteur",
			"Photographe",
			"Musicien",
			"Auteur"
	};

	@FXML private VBox stepOnePane;
	@FXML private VBox stepTwoPane;
	@FXML private VBox stepThreePane;
	@FXML private StackPane landingShell;
	@FXML private VBox landingHero;
	@FXML private VBox signupCard;
	@FXML private Label stepOneBadge;
	@FXML private Label stepTwoBadge;
	@FXML private Label stepThreeBadge;
	@FXML private VBox specialiteContainer;
	@FXML private VBox centreInteretContainer;
	@FXML private Label messageLabel;
	@FXML private Label photoPathLabel;
	@FXML private Label photoPlaceholderLabel;
	@FXML private TextField nomField;
	@FXML private TextField prenomField;
	@FXML private DatePicker dateNaissancePicker;
	@FXML private TextField phoneField;
	@FXML private TextField villeField;
	@FXML private TextField emailField;
	@FXML private PasswordField passwordField;
	@FXML private PasswordField confirmPasswordField;
	@FXML private ComboBox<String> specialiteComboBox;
	@FXML private ComboBox<String> centreInteretComboBox;
	@FXML private TextArea biographieArea;
	@FXML private ToggleGroup roleToggleGroup;
	@FXML private Button previousButton;
	@FXML private Button nextButton;
	@FXML private Button submitButton;

	private final User draftUser = new User();
	private final UserService userService = new UserService();
	private int currentStep = 1;
	private String selectedPhotoPath;
	private boolean nomTouched;
	private boolean prenomTouched;
	private boolean dateNaissanceTouched;
	private boolean phoneTouched;
	private boolean villeTouched;
	private boolean emailTouched;
	private boolean passwordTouched;
	private boolean confirmPasswordTouched;
	private boolean biographieTouched;
	private boolean specialiteTouched;
	private boolean centreInteretTouched;
	private boolean roleTouched;

	@FXML
	public void initialize() {
		specialiteComboBox.getItems().setAll(SPECIALITES_ARTISTE);
		centreInteretComboBox.getItems().setAll(CENTRES_INTERET);
		if (roleToggleGroup.getSelectedToggle() == null && !roleToggleGroup.getToggles().isEmpty()) {
			roleToggleGroup.selectToggle(roleToggleGroup.getToggles().get(0));
		}
		roleToggleGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> {
			roleTouched = true;
			updateRoleHints();
			updateLiveValidationMessage();
		});
		updateRoleHints();
		showStep(1, false);

        if (landingShell != null) {
            ArtisticBackground.attach(landingShell, 20); // 20 touches pour l'inscription
        }

		javafx.application.Platform.runLater(() -> {
			if (landingHero != null) CardAnimator.animateFadeSlideUp(landingHero, 40);
			if (signupCard != null) CardAnimator.animateFadeSlideUp(signupCard, 120);
		});
	}

	@FXML
	private void onNextStep() {
		if (!validateCurrentStep()) {
			return;
		}
		showStep(Math.min(3, currentStep + 1), true);
	}

	@FXML
	private void onPreviousStep() {
		if (currentStep == 1) {
			MainFX.switchToAuthLandingView();
			return;
		}
		showStep(currentStep - 1, true);
	}

	@FXML
	private void onBackToLanding() {
		MainFX.switchToLoginView();
	}

	@FXML
	private void onBrowsePhoto() {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Choisir une photo de référence");
		chooser.getExtensionFilters().addAll(
				new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
				new FileChooser.ExtensionFilter("Tous les fichiers", "*.*")
		);
		File file = chooser.showOpenDialog(previousButton.getScene().getWindow());
		if (file == null) {
			return;
		}
		selectedPhotoPath = file.getAbsolutePath();
		photoPathLabel.setText(file.getName());
		photoPlaceholderLabel.setVisible(false);
		photoPlaceholderLabel.setManaged(false);
		setMessage("Photo chargée avec succès.", false);
	}

	@FXML
	private void onSubmit() {
		if (!validateCurrentStep()) {
			return;
		}
		User user = buildUser();
		try {
			userService.add(user);
			showSuccessAlert();
			clearForm();
			MainFX.switchToLoginView();
		} catch (SQLDataException e) {
			setMessage("Erreur de sauvegarde: " + e.getMessage(), true);
		}
	}

	private void showSuccessAlert() {
		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.setTitle("Inscription confirmée");
		alert.setHeaderText(null);

		// Créer le contenu personnalisé avec le style du thème
		VBox contentBox = new VBox();
		contentBox.setSpacing(12);
		contentBox.setPadding(new Insets(20, 10, 20, 10));
		contentBox.setStyle("-fx-background-color: rgba(255, 255, 255, 0.94); -fx-background-radius: 12;");

		Label titleLabel = new Label("✓ Inscription réussie !");
		titleLabel.setStyle("-fx-font-size: 18; -fx-font-weight: 700; -fx-text-fill: #0f172a;");

		Label descriptionLabel = new Label("Votre compte a été créé avec succès.");
		descriptionLabel.setStyle("-fx-font-size: 14; -fx-text-fill: #475569; -fx-font-weight: 600; -fx-wrap-text: true;");

		Label messageLabel = new Label("Votre compte est en attente d'activation par l'admin avant la première connexion.");
		messageLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #667085; -fx-wrap-text: true;");

		VBox successIndicator = new VBox();
		successIndicator.setStyle("-fx-background-color: linear-gradient(to right, #22c55e, #16a34a); -fx-background-radius: 8; -fx-padding: 10;");
		successIndicator.setPrefHeight(4);
		successIndicator.setMaxHeight(4);

		contentBox.getChildren().addAll(titleLabel, descriptionLabel, messageLabel, successIndicator);

		alert.getDialogPane().setContent(contentBox);
		alert.getDialogPane().setStyle(
			"-fx-background-color: linear-gradient(to bottom right, rgba(255, 255, 255, 0.96), rgba(241, 247, 255, 0.96)); " +
			"-fx-border-color: rgba(148, 163, 184, 0.2); " +
			"-fx-border-width: 1; " +
			"-fx-padding: 0; " +
			"-fx-background-radius: 20;"
		);

		Button okButton = (Button) alert.getDialogPane().lookupButton(javafx.scene.control.ButtonType.OK);
		okButton.setStyle(
			"-fx-background-color: linear-gradient(to right, #1fc4d7, #4f7cff); " +
			"-fx-text-fill: white; " +
			"-fx-font-weight: 700; " +
			"-fx-padding: 10 20; " +
			"-fx-background-radius: 12; " +
			"-fx-cursor: hand; " +
			"-fx-font-size: 13;"
		);
		okButton.setText("Continuer");

		alert.showAndWait();
	}

	private boolean validateCurrentStep() {
		switch (currentStep) {
			case 1:
				return validateStepOne();
			case 2:
				return validateStepTwo();
			case 3:
				return validateStepThree();
			default:
				return true;
		}
	}

	private boolean validateStepOne() {
		if (!InputValidator.isValidName(nomField.getText())) {
			return failValidation("Nom invalide (2-50 lettres).", nomField);
		}
		if (!InputValidator.isValidName(prenomField.getText())) {
			return failValidation("Prénom invalide (2-50 lettres).", prenomField);
		}
		if (dateNaissancePicker.getValue() == null) {
			return failValidation("Veuillez sélectionner une date de naissance.", dateNaissancePicker);
		}
		if (!InputValidator.isAdult(dateNaissancePicker.getValue(), MINIMUM_AGE)) {
			return failValidation("Date de naissance invalide (âge minimum " + MINIMUM_AGE + " ans).", dateNaissancePicker);
		}
		if (!InputValidator.isValidPhone(phoneField.getText())) {
			return failValidation("Téléphone invalide (8 à 15 chiffres, + optionnel).", phoneField);
		}
		if (!InputValidator.isValidCity(villeField.getText())) {
			return failValidation("Ville invalide (2-60 lettres).", villeField);
		}
		return true;
	}

	private boolean validateStepTwo() {
		if (!InputValidator.isValidEmail(emailField.getText())) {
			return failValidation("Veuillez saisir une adresse e-mail valide.", emailField);
		}
		if (!InputValidator.isValidPassword(passwordField.getText())) {
			return failValidation("Mot de passe faible: 8+ caractères avec majuscule, minuscule et chiffre.", passwordField);
		}
		if (InputValidator.isBlank(confirmPasswordField.getText())) {
			return failValidation("Veuillez confirmer le mot de passe.", confirmPasswordField);
		}
		if (!passwordField.getText().equals(confirmPasswordField.getText())) {
			return failValidation("La confirmation du mot de passe est incorrecte.", confirmPasswordField);
		}
		return true;
	}

	private boolean validateStepThree() {
		if (roleToggleGroup.getSelectedToggle() == null) return failValidation("Veuillez sélectionner un rôle.", null);
		if (InputValidator.isBlank(biographieArea.getText()) || biographieArea.getText().trim().length() < 10) {
			return failValidation("Veuillez ajouter une biographie (minimum 10 caractères).", biographieArea);
		}

		String role = getSelectedRole();
		if (ROLE_AMATEUR.equalsIgnoreCase(role) && centreInteretComboBox.getValue() == null) {
			return failValidation("Veuillez choisir un centre d'intérêt.", centreInteretComboBox);
		}
		if (ROLE_ARTISTE.equalsIgnoreCase(role) && specialiteComboBox.getValue() == null) {
			return failValidation("Veuillez choisir une spécialité.", specialiteComboBox);
		}
		return true;
	}

	private boolean failValidation(String message, Node focus) {
		setMessage(message, true);
		if (focus != null) focus.requestFocus();
		return false;
	}

	private void showStep(int step, boolean animate) {
		currentStep = step;
		stepOnePane.setVisible(step == 1); stepOnePane.setManaged(step == 1);
		stepTwoPane.setVisible(step == 2); stepTwoPane.setManaged(step == 2);
		stepThreePane.setVisible(step == 3); stepThreePane.setManaged(step == 3);
		if (animate) {
			Node activePane = step == 1 ? stepOnePane : step == 2 ? stepTwoPane : stepThreePane;
			CardAnimator.animateFadeSlideUp(activePane, 0);
		}
		previousButton.setText(step == 1 ? "Retour à l'accueil" : "Précédent");
		nextButton.setVisible(step < 3); nextButton.setManaged(step < 3);
		submitButton.setVisible(step == 3); submitButton.setManaged(step == 3);
		updateStepper();
		updateRoleHints();
		updateLiveValidationMessage();
	}

	private void setupLiveValidation() {
		nomField.textProperty().addListener((obs, oldV, newV) -> { nomTouched = true; updateLiveValidationMessage(); });
		prenomField.textProperty().addListener((obs, oldV, newV) -> { prenomTouched = true; updateLiveValidationMessage(); });
		dateNaissancePicker.valueProperty().addListener((obs, oldV, newV) -> { dateNaissanceTouched = true; updateLiveValidationMessage(); });
		phoneField.textProperty().addListener((obs, oldV, newV) -> { phoneTouched = true; updateLiveValidationMessage(); });
		villeField.textProperty().addListener((obs, oldV, newV) -> { villeTouched = true; updateLiveValidationMessage(); });
		emailField.textProperty().addListener((obs, oldV, newV) -> { emailTouched = true; updateLiveValidationMessage(); });
		passwordField.textProperty().addListener((obs, oldV, newV) -> { passwordTouched = true; updateLiveValidationMessage(); });
		confirmPasswordField.textProperty().addListener((obs, oldV, newV) -> { confirmPasswordTouched = true; updateLiveValidationMessage(); });
		biographieArea.textProperty().addListener((obs, oldV, newV) -> { biographieTouched = true; updateLiveValidationMessage(); });
		specialiteComboBox.valueProperty().addListener((obs, oldV, newV) -> { specialiteTouched = true; updateLiveValidationMessage(); });
		centreInteretComboBox.valueProperty().addListener((obs, oldV, newV) -> { centreInteretTouched = true; updateLiveValidationMessage(); });
	}

	private void updateLiveValidationMessage() {
		if (!hasTouchedInCurrentStep()) {
			clearMessage();
			return;
		}

		String validationError = getCurrentStepLiveValidationError();
		if (validationError == null) {
			clearMessage();
			return;
		}
		setMessage(validationError, true);
	}

	private boolean hasTouchedInCurrentStep() {
		switch (currentStep) {
			case 1:
				return nomTouched || prenomTouched || dateNaissanceTouched || phoneTouched || villeTouched;
			case 2:
				return emailTouched || passwordTouched || confirmPasswordTouched;
			case 3:
				return biographieTouched || specialiteTouched || centreInteretTouched || roleTouched;
			default:
				return false;
		}
	}

	private String getCurrentStepLiveValidationError() {
		switch (currentStep) {
			case 1:
				return getStepOneLiveValidationError();
			case 2:
				return getStepTwoLiveValidationError();
			case 3:
				return getStepThreeLiveValidationError();
			default:
				return null;
		}
	}

	private String getStepOneLiveValidationError() {
		if (nomTouched && !InputValidator.isValidName(nomField.getText())) return "Nom invalide (2-50 lettres).";
		if (prenomTouched && !InputValidator.isValidName(prenomField.getText())) return "Prénom invalide (2-50 lettres).";
		if (dateNaissanceTouched) {
			if (dateNaissancePicker.getValue() == null) return "Veuillez sélectionner une date de naissance.";
			if (!InputValidator.isAdult(dateNaissancePicker.getValue(), MINIMUM_AGE)) return "Date de naissance invalide (âge minimum " + MINIMUM_AGE + " ans).";
		}
		if (phoneTouched && !InputValidator.isValidPhone(phoneField.getText())) return "Téléphone invalide (8 à 15 chiffres, + optionnel).";
		if (villeTouched && !InputValidator.isValidCity(villeField.getText())) return "Ville invalide (2-60 lettres).";
		return null;
	}

	private String getStepTwoLiveValidationError() {
		if (emailTouched && !InputValidator.isValidEmail(emailField.getText())) return "Veuillez saisir une adresse e-mail valide.";
		if (passwordTouched && !InputValidator.isValidPassword(passwordField.getText())) return "Mot de passe faible: 8+ caractères avec majuscule, minuscule et chiffre.";
		if (confirmPasswordTouched) {
			if (InputValidator.isBlank(confirmPasswordField.getText())) return "Veuillez confirmer le mot de passe.";
			if (!InputValidator.isBlank(passwordField.getText()) && !passwordField.getText().equals(confirmPasswordField.getText())) {
				return "La confirmation du mot de passe est incorrecte.";
			}
		}
		return null;
	}

	private String getStepThreeLiveValidationError() {
		if (roleTouched && roleToggleGroup.getSelectedToggle() == null) return "Veuillez sélectionner un rôle.";
		if (biographieTouched && (InputValidator.isBlank(biographieArea.getText()) || biographieArea.getText().trim().length() < 10)) {
			return "Veuillez ajouter une biographie (minimum 10 caractères).";
		}
		String role = getSelectedRole();
		if (ROLE_AMATEUR.equalsIgnoreCase(role) && centreInteretTouched && centreInteretComboBox.getValue() == null) return "Veuillez choisir un centre d'intérêt.";
		if (ROLE_ARTISTE.equalsIgnoreCase(role) && specialiteTouched && specialiteComboBox.getValue() == null) return "Veuillez choisir une spécialité.";
		return null;
	}

	private void clearMessage() {
		messageLabel.setText("");
		messageLabel.getStyleClass().removeAll("error", "success");
	}

	private void updateStepper() {
		setBadgeState(stepOneBadge, currentStep == 1, currentStep > 1);
		setBadgeState(stepTwoBadge, currentStep == 2, currentStep > 2);
		setBadgeState(stepThreeBadge, currentStep == 3, false);
	}

	private void setBadgeState(Label badge, boolean active, boolean done) {
		badge.getStyleClass().removeAll("active", "done");
		if (active) badge.getStyleClass().add("active");
		else if (done) badge.getStyleClass().add("done");
	}

	private void updateRoleHints() {
		String role = getSelectedRole();
		boolean isAmateur = ROLE_AMATEUR.equalsIgnoreCase(role);
		boolean isArtiste = ROLE_ARTISTE.equalsIgnoreCase(role);
		boolean isAdmin = ROLE_ADMIN.equalsIgnoreCase(role);

		specialiteContainer.setVisible(isArtiste);
		specialiteContainer.setManaged(isArtiste);
		centreInteretContainer.setVisible(isAmateur);
		centreInteretContainer.setManaged(isAmateur);

		if (!isArtiste) {
			specialiteComboBox.setValue(null);
		}
		if (!isAmateur) {
			centreInteretComboBox.setValue(null);
		}
		if (isAdmin) {
			specialiteComboBox.setValue(null);
			centreInteretComboBox.setValue(null);
		}
	}

	private String getSelectedRole() {
		Toggle selected = roleToggleGroup.getSelectedToggle();
		return selected instanceof ToggleButton ? ((ToggleButton) selected).getText() : "";
	}

	private User buildUser() {
		draftUser.setNom(InputValidator.clean(nomField.getText()));
		draftUser.setPrenom(InputValidator.clean(prenomField.getText()));
		draftUser.setDateNaissance(dateNaissancePicker.getValue());
		draftUser.setNumTel(InputValidator.clean(phoneField.getText()));
		draftUser.setVille(InputValidator.clean(villeField.getText()));
		draftUser.setEmail(InputValidator.clean(emailField.getText()));
		draftUser.setMdp(passwordField.getText());
		draftUser.setRole(getSelectedRole());
		draftUser.setSpecialite(ROLE_ARTISTE.equalsIgnoreCase(getSelectedRole()) ? InputValidator.clean(specialiteComboBox.getValue()) : null);
		draftUser.setCentreInteret(ROLE_AMATEUR.equalsIgnoreCase(getSelectedRole()) ? InputValidator.clean(centreInteretComboBox.getValue()) : null);
		draftUser.setBiographie(InputValidator.clean(biographieArea.getText()));
		draftUser.setPhotoReferencePath(selectedPhotoPath);
		draftUser.setPhotoProfil(selectedPhotoPath);
        draftUser.setStatut("Activé");
		draftUser.setDateInscription(LocalDate.now());
		draftUser.setStatut(ROLE_ADMIN.equalsIgnoreCase(getSelectedRole()) ? "Activé" : "Bloqué");
		return draftUser;
	}

	private void clearForm() {
		nomField.clear(); prenomField.clear(); dateNaissancePicker.setValue(null); phoneField.clear(); villeField.clear();
		emailField.clear(); passwordField.clear(); confirmPasswordField.clear(); specialiteComboBox.setValue(null); centreInteretComboBox.setValue(null);
		biographieArea.clear(); selectedPhotoPath = null; photoPathLabel.setText("Sélectionnez une image pour votre profil.");
		nomTouched = false; prenomTouched = false; dateNaissanceTouched = false; phoneTouched = false; villeTouched = false;
		emailTouched = false; passwordTouched = false; confirmPasswordTouched = false;
		biographieTouched = false; specialiteTouched = false; centreInteretTouched = false; roleTouched = false;
		photoPlaceholderLabel.setVisible(true); photoPlaceholderLabel.setManaged(true);
		if (!roleToggleGroup.getToggles().isEmpty()) roleToggleGroup.selectToggle(roleToggleGroup.getToggles().get(0));
		showStep(1, false);
	}

	private void fadeIn(Node node) {
		// Not used anymore, replaced by CardAnimator
	}

	private void setMessage(String message, boolean error) {
		messageLabel.setText(message);
		messageLabel.getStyleClass().removeAll("error", "success");
		messageLabel.getStyleClass().add(error ? "error" : "success");
	}

}



