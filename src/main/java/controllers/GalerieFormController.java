package controllers;

import entities.Galerie;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class GalerieFormController {

    @FXML
    private Label formTitleLabel;

    @FXML
    private TextField nomField;

    @FXML
    private TextField adresseField;

    @FXML
    private TextField localisationField;

    @FXML
    private TextField capaciteField;

    @FXML
    private TextArea descriptionArea;

    @FXML
    private Label validationErrorLabel;

    private Stage dialogStage;
    private Galerie originalGalerie;
    private Galerie resultGalerie;

    @FXML
    public void initialize() {
        clearValidationError();
        nomField.textProperty().addListener((obs, oldValue, newValue) -> clearValidationError());
        adresseField.textProperty().addListener((obs, oldValue, newValue) -> clearValidationError());
        localisationField.textProperty().addListener((obs, oldValue, newValue) -> clearValidationError());
        capaciteField.textProperty().addListener((obs, oldValue, newValue) -> clearValidationError());
        descriptionArea.textProperty().addListener((obs, oldValue, newValue) -> clearValidationError());
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setGalerie(Galerie galerie) {
        this.originalGalerie = galerie;
        clearValidationError();
        if (galerie == null) {
            formTitleLabel.setText("Ajouter une galerie");
            nomField.clear();
            adresseField.clear();
            localisationField.clear();
            capaciteField.clear();
            descriptionArea.clear();
            return;
        }

        formTitleLabel.setText("Modifier la galerie");
        nomField.setText(galerie.getNom());
        adresseField.setText(galerie.getAdresse());
        localisationField.setText(galerie.getLocalisation());
        capaciteField.setText(galerie.getCapaciteMax() == null ? "" : String.valueOf(galerie.getCapaciteMax()));
        descriptionArea.setText(galerie.getDescription());
    }

    public Galerie getResultGalerie() {
        return resultGalerie;
    }

    @FXML
    private void onSaveClick() {
        clearValidationError();
        String nom = nomField.getText() == null ? "" : nomField.getText().trim();
        String adresse = adresseField.getText() == null ? "" : adresseField.getText().trim();
        String localisation = localisationField.getText() == null ? "" : localisationField.getText().trim();
        String capaciteText = capaciteField.getText() == null ? "" : capaciteField.getText().trim();
        String description = descriptionArea.getText() == null ? "" : descriptionArea.getText().trim();

        if (nom.isEmpty() || adresse.isEmpty() || localisation.isEmpty() || capaciteText.isEmpty()) {
            showValidationError("Veuillez remplir les champs obligatoires.");
            return;
        }

        int capacite;
        try {
            capacite = Integer.parseInt(capaciteText);
            if (capacite <= 0) {
                showValidationError("La capacite doit etre superieure a 0.");
                return;
            }
        } catch (NumberFormatException e) {
            showValidationError("La capacite max doit etre un nombre entier.");
            return;
        }

        Galerie galerie = new Galerie();
        if (originalGalerie != null) {
            galerie.setId(originalGalerie.getId());
        }
        galerie.setNom(nom);
        galerie.setAdresse(adresse);
        galerie.setLocalisation(localisation);
        galerie.setDescription(description);
        galerie.setCapaciteMax(capacite);

        resultGalerie = galerie;
        closeDialog();
    }

    @FXML
    private void onCancelClick() {
        resultGalerie = null;
        closeDialog();
    }

    private void closeDialog() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    private void showValidationError(String message) {
        validationErrorLabel.setText(message);
        validationErrorLabel.setVisible(true);
        validationErrorLabel.setManaged(true);
    }

    private void clearValidationError() {
        if (validationErrorLabel == null) {
            return;
        }
        validationErrorLabel.setText("");
        validationErrorLabel.setVisible(false);
        validationErrorLabel.setManaged(false);
    }
}

