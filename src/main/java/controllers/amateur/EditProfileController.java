package controllers.amateur;

import controllers.MainFX;
import entities.User;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import java.io.File;
import services.UserService;
import java.sql.SQLDataException;

public class EditProfileController {

    @FXML private TextField nomField;
    @FXML private TextField prenomField;
    @FXML private TextField emailField;
    @FXML private TextField villeField;
    @FXML private DatePicker dateNaissancePicker;
    @FXML private TextArea bioArea;
    @FXML private ComboBox<String> interetCombo;

    @FXML private PasswordField oldPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;

    @FXML private Button saveButton;

    @FXML private Label avatarLabel;  // présent dans le FXML actuel
    @FXML private ImageView profileImageView;

    private User connectedUser;
    private Runnable onProfileUpdated;

    public void setOnProfileUpdated(Runnable onProfileUpdated) {
        this.onProfileUpdated = onProfileUpdated;
    }

    @FXML
    public void initialize() {
        connectedUser = MainFX.getAuthenticatedUser();

        interetCombo.getItems().addAll(
                "Peinture", "Sculpture", "Photographie", "Musique", "Cinéma"
        );

        if (connectedUser != null) {
            prefillForm();
        }
    }

    private void prefillForm() {
        nomField.setText(connectedUser.getNom());
        prenomField.setText(connectedUser.getPrenom());
        emailField.setText(connectedUser.getEmail());
        villeField.setText(connectedUser.getVille());

        if (connectedUser.getDateNaissance() != null)
            dateNaissancePicker.setValue(connectedUser.getDateNaissance());

        if (connectedUser.getBiographie() != null)
            bioArea.setText(connectedUser.getBiographie());

        if (connectedUser.getCentreInteret() != null)
            interetCombo.setValue(connectedUser.getCentreInteret());

        // Initiales avatar uniquement
        if (avatarLabel != null) {
            String prenom = connectedUser.getPrenom() != null ? connectedUser.getPrenom() : "";
            String nom    = connectedUser.getNom()    != null ? connectedUser.getNom()    : "";
            String initiales = "";
            if (!prenom.isEmpty()) initiales += prenom.charAt(0);
            if (!nom.isEmpty())    initiales += nom.charAt(0);
            avatarLabel.setText(initiales.toUpperCase());
        }

        // Affichage de la photo si elle existe
        if (profileImageView != null) {
            String photoPath = connectedUser.getPhotoProfil();
            if (photoPath != null && !photoPath.trim().isEmpty()) {
                try {
                    String url = photoPath.startsWith("http") || photoPath.startsWith("file:") 
                            ? photoPath 
                            : new File(photoPath).toURI().toString();
                    Image img = new Image(url, true);
                    if (!img.isError()) {
                        profileImageView.setImage(img);
                        profileImageView.setVisible(true);
                        avatarLabel.setVisible(false);
                    }
                } catch (Exception e) {
                    System.err.println("Erreur chargement photo: " + e.getMessage());
                }
            }
        }
    }

    @FXML
    private void handleUploadPhoto() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choisir une photo de profil");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );
        
        File selectedFile = fileChooser.showOpenDialog(saveButton.getScene().getWindow());
        if (selectedFile != null) {
            String imagePath = selectedFile.getAbsolutePath();
            connectedUser.setPhotoProfil(imagePath);
            
            Image image = new Image(selectedFile.toURI().toString());
            profileImageView.setImage(image);
            profileImageView.setVisible(true);
            avatarLabel.setVisible(false);
        }
    }

    @FXML
    private void handleSave() {
        if (nomField.getText() == null || nomField.getText().trim().isEmpty() || 
            emailField.getText() == null || emailField.getText().trim().isEmpty()) {
            showAlert("Erreur", "Nom et Email sont obligatoires !");
            return;
        }

        if (dateNaissancePicker.getValue() == null) {
            showAlert("Erreur", "La date de naissance est obligatoire !");
            return;
        }

        connectedUser.setNom(nomField.getText().trim());
        connectedUser.setPrenom(prenomField.getText() != null ? prenomField.getText().trim() : "");
        connectedUser.setEmail(emailField.getText().trim());
        connectedUser.setVille(villeField.getText() != null ? villeField.getText().trim() : "");
        connectedUser.setDateNaissance(dateNaissancePicker.getValue());
        connectedUser.setBiographie(bioArea.getText());
        connectedUser.setCentreInteret(interetCombo.getValue());

        if (connectedUser.getDateInscription() == null) {
            connectedUser.setDateInscription(java.time.LocalDate.now());
        }
        if (connectedUser.getNumTel() == null || connectedUser.getNumTel().trim().isEmpty()) {
            connectedUser.setNumTel("00000000"); // Default fallback to avoid validation errors if missing
        }

        // Appel service / DB ici
        try {
            UserService userService = new UserService();
            userService.update(connectedUser);
            
            utils.SessionManager.setCurrentUser(connectedUser);
            if (onProfileUpdated != null) {
                onProfileUpdated.run();
            }
            
            showAlert("Succès", "Profil mis à jour avec succès !");
            prefillForm();
        } catch (SQLDataException e) {
            showAlert("Erreur", "Erreur lors de la mise à jour : " + e.getMessage());
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        // Appliquer le design Artium
        try {
            DialogPane dialogPane = alert.getDialogPane();
            String cssPath = getClass().getResource("/views/styles/dashboard.css").toExternalForm();
            dialogPane.getStylesheets().add(cssPath);
            dialogPane.getStyleClass().add("artium-alert");
        } catch (Exception e) {
            System.err.println("Impossible de charger le style de l'alerte : " + e.getMessage());
        }

        alert.showAndWait();
    }

    @FXML
    private void handleCancel() {
        // Annuler les modifications et remettre les champs à leur état initial
        if (connectedUser != null) {
            prefillForm();
            showAlert("Annulation", "Vos modifications ont été annulées.");
        }
    }
}