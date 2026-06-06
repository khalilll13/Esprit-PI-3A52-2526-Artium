package controllers.artist;

import entities.Evenement;
import entities.Galerie;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import services.GalerieService;

import java.io.File;
import java.sql.SQLDataException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class EvenementArtisteFormController {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @FXML
    private Label formTitleLabel;

    @FXML
    private TextField titreField;

    @FXML
    private ComboBox<String> typeComboBox;

    @FXML
    private DatePicker dateDebutPicker;

    @FXML
    private TextField heureDebutField;

    @FXML
    private DatePicker dateFinPicker;

    @FXML
    private TextField heureFinField;

    @FXML
    private TextField capaciteField;

    @FXML
    private TextField prixField;

    @FXML
    private ComboBox<GalerieOption> galerieComboBox;

    @FXML
    private Label imageFileLabel;

    @FXML
    private TextArea descriptionArea;

    @FXML
    private Label validationErrorLabel;

    private final GalerieService galerieService = new GalerieService();
    private final List<GalerieOption> galerieOptions = new ArrayList<>();

    private Stage dialogStage;
    private Evenement originalEvenement;
    private Evenement resultEvenement;
    private String selectedImagePath;
    private Function<Evenement, Boolean> saveHandler;
    private Runnable cancelHandler;

    @FXML
    public void initialize() {
        typeComboBox.getItems().addAll("Exposition", "Concert", "Spectacle", "Conférence");
        typeComboBox.setValue("Exposition");

        clearValidationError();
        wireFieldListeners();
        loadGaleries();
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setSaveHandler(Function<Evenement, Boolean> saveHandler) {
        this.saveHandler = saveHandler;
    }

    public void setCancelHandler(Runnable cancelHandler) {
        this.cancelHandler = cancelHandler;
    }

    public void setEvenement(Evenement evenement) {
        this.originalEvenement = evenement;
        clearValidationError();

        if (evenement == null) {
            formTitleLabel.setText("Ajouter un evenement");
            titreField.clear();
            typeComboBox.setValue("Exposition");
            dateDebutPicker.setValue(null);
            heureDebutField.clear();
            dateFinPicker.setValue(null);
            heureFinField.clear();
            capaciteField.clear();
            prixField.clear();
            if (!galerieOptions.isEmpty()) {
                galerieComboBox.setValue(galerieOptions.get(0));
            }
            descriptionArea.clear();
            imageFileLabel.setText("Aucun fichier choisi");
            selectedImagePath = null;
            return;
        }

        formTitleLabel.setText("Modifier un evenement");
        titreField.setText(evenement.getTitre());
        typeComboBox.setValue(evenement.getType() == null || evenement.getType().isBlank() ? "Exposition" : evenement.getType());
        dateDebutPicker.setValue(evenement.getDateDebut() == null ? null : evenement.getDateDebut().toLocalDate());
        heureDebutField.setText(formatTime(evenement.getDateDebut()));
        dateFinPicker.setValue(evenement.getDateFin() == null ? null : evenement.getDateFin().toLocalDate());
        heureFinField.setText(formatTime(evenement.getDateFin()));
        capaciteField.setText(evenement.getCapaciteMax() == null ? "" : String.valueOf(evenement.getCapaciteMax()));
        prixField.setText(evenement.getPrixTicket() == null ? "" : String.valueOf(evenement.getPrixTicket()));
        descriptionArea.setText(evenement.getDescription());
        imageFileLabel.setText(evenement.getImageCouverture() == null || evenement.getImageCouverture().isBlank()
                ? "Aucun fichier choisi"
                : "Image existante" );
        selectedImagePath = evenement.getImageCouverture();

        GalerieOption selected = findGalerieOption(evenement.getGalerieId());
        if (selected != null) {
            galerieComboBox.setValue(selected);
        }
    }

    public Evenement getResultEvenement() {
        return resultEvenement;
    }

    public void setExternalValidationError(String message) {
        showValidationError(message);
    }

    @FXML
    private void onChooseImageClick() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choisir image de couverture");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.webp")
        );

        File selectedFile = fileChooser.showOpenDialog(dialogStage);
        if (selectedFile == null) {
            return;
        }

        selectedImagePath = selectedFile.getAbsolutePath();
        imageFileLabel.setText(selectedFile.getName());
        clearValidationError();
    }

    @FXML
    private void onSaveClick() {
        clearValidationError();

        String titre = safeTrim(titreField.getText());
        String type = safeTrim(typeComboBox.getValue());
        LocalDate dateDebut = dateDebutPicker.getValue();
        String heureDebutText = safeTrim(heureDebutField.getText());
        LocalDate dateFin = dateFinPicker.getValue();
        String heureFinText = safeTrim(heureFinField.getText());
        String capaciteText = safeTrim(capaciteField.getText());
        String prixText = safeTrim(prixField.getText());
        String description = safeTrim(descriptionArea.getText());
        GalerieOption selectedGalerie = galerieComboBox.getValue();

        if (titre.isEmpty() || type.isEmpty() || dateDebut == null || heureDebutText.isEmpty() || dateFin == null || heureFinText.isEmpty() || selectedGalerie == null) {
            showValidationError("Veuillez remplir les champs obligatoires.");
            return;
        }

        LocalTime heureDebut = parseTime(heureDebutText, "Heure debut invalide. Format attendu: HH:mm");
        if (heureDebut == null) {
            return;
        }

        LocalTime heureFin = parseTime(heureFinText, "Heure fin invalide. Format attendu: HH:mm");
        if (heureFin == null) {
            return;
        }

        LocalDateTime dateDebutDateTime = LocalDateTime.of(dateDebut, heureDebut);
        LocalDateTime dateFinDateTime = LocalDateTime.of(dateFin, heureFin);

        if (originalEvenement == null && dateDebut.isBefore(LocalDate.now().plusDays(3))) {
            showValidationError("La date de debut doit etre au moins 3 jours apres aujourd'hui.");
            return;
        }

        if (dateFinDateTime.isBefore(dateDebutDateTime)) {
            showValidationError("La date fin doit etre apres la date debut.");
            return;
        }

        Integer capacite = null;
        if (!capaciteText.isEmpty()) {
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
        }

        Double prixTicket = null;
        if (!prixText.isEmpty()) {
            try {
                prixTicket = Double.parseDouble(prixText.replace(',', '.'));
                if (prixTicket < 0) {
                    showValidationError("Le prix ticket doit etre positif.");
                    return;
                }
            } catch (NumberFormatException e) {
                showValidationError("Le prix ticket doit etre un nombre valide.");
                return;
            }
        }

        Evenement evenement = new Evenement();
        if (originalEvenement != null) {
            evenement.setId(originalEvenement.getId());
            evenement.setArtisteId(originalEvenement.getArtisteId());
        }

        evenement.setTitre(titre);
        evenement.setType(type);
        evenement.setDateDebut(dateDebutDateTime);
        evenement.setDateFin(dateFinDateTime);
        evenement.setDateCreation(originalEvenement != null && originalEvenement.getDateCreation() != null
                ? originalEvenement.getDateCreation()
                : LocalDate.now());
        evenement.setDescription(description);
        evenement.setCapaciteMax(capacite);
        evenement.setPrixTicket(prixTicket);
        evenement.setGalerieId(selectedGalerie.id());
        evenement.setImageCouverture(selectedImagePath);
        evenement.setStatut(computeStatut(dateDebutDateTime, dateFinDateTime));

        resultEvenement = evenement;
        boolean saved = saveHandler == null || Boolean.TRUE.equals(saveHandler.apply(evenement));
        if (saved) {
            closeDialog();
        }
    }

    @FXML
    private void onCancelClick() {
        resultEvenement = null;
        if (cancelHandler != null) {
            cancelHandler.run();
        }
        closeDialog();
    }

    private void loadGaleries() {
        try {
            galerieOptions.clear();
            for (Galerie galerie : galerieService.getAll()) {
                galerieOptions.add(new GalerieOption(galerie.getId(), galerie.getNom()));
            }
            galerieComboBox.getItems().setAll(galerieOptions);
            if (!galerieOptions.isEmpty()) {
                galerieComboBox.setValue(galerieOptions.get(0));
            }
        } catch (SQLDataException e) {
            showValidationError("Impossible de charger la liste des galeries.");
        }
    }

    private GalerieOption findGalerieOption(Integer galerieId) {
        if (galerieId == null) {
            return null;
        }
        for (GalerieOption option : galerieOptions) {
            if (galerieId.equals(option.id())) {
                return option;
            }
        }
        return null;
    }

    private void wireFieldListeners() {
        titreField.textProperty().addListener((obs, oldValue, newValue) -> clearValidationError());
        dateDebutPicker.valueProperty().addListener((obs, oldValue, newValue) -> clearValidationError());
        heureDebutField.textProperty().addListener((obs, oldValue, newValue) -> clearValidationError());
        dateFinPicker.valueProperty().addListener((obs, oldValue, newValue) -> clearValidationError());
        heureFinField.textProperty().addListener((obs, oldValue, newValue) -> clearValidationError());
        capaciteField.textProperty().addListener((obs, oldValue, newValue) -> clearValidationError());
        prixField.textProperty().addListener((obs, oldValue, newValue) -> clearValidationError());
        descriptionArea.textProperty().addListener((obs, oldValue, newValue) -> clearValidationError());
        typeComboBox.valueProperty().addListener((obs, oldValue, newValue) -> clearValidationError());
        galerieComboBox.valueProperty().addListener((obs, oldValue, newValue) -> clearValidationError());
    }

    private LocalTime parseTime(String text, String errorMessage) {
        try {
            return LocalTime.parse(text, TIME_FORMATTER);
        } catch (Exception e) {
            showValidationError(errorMessage);
            return null;
        }
    }

    private String computeStatut(LocalDateTime debut, LocalDateTime fin) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(debut)) {
            return "À venir";
        }
        if (now.isAfter(fin)) {
            return "Termine";
        }
        return "À venir";
    }

    private String formatTime(LocalDateTime dateTime) {
        return dateTime == null ? "" : TIME_FORMATTER.format(dateTime.toLocalTime());
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
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

    private record GalerieOption(Integer id, String nom) {
        @Override
        public String toString() {
            return nom == null || nom.isBlank() ? "Galerie #" + id : nom;
        }
    }
}

