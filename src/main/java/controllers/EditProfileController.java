package controllers;

import entities.User;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import services.UserService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;

public class EditProfileController {

    @FXML private TextField prenomField;
    @FXML private TextField nomField;
    @FXML private TextArea  bioField;
    @FXML private TextField interessField;

    @FXML private Label interessLabel;
    @FXML private VBox  interessSection;

    @FXML private ImageView photoPreview;
    @FXML private Button    changePhotoBtn;
    @FXML private Button    cancelBtn;
    @FXML private Button    saveBtn;

    private User        currentUser;
    private File        selectedPhotoFile;
    private UserService userService;

    private static final String PHOTOS_DIR = "src/main/resources/images/avatars/";

    @FXML
    public void initialize() {
        userService = new UserService();
        currentUser = MainFX.getAuthenticatedUser();

        if (currentUser != null) {
            setupFormByRole(currentUser);
            populateForm(currentUser);
            setupPhotoSection(currentUser);
        }

        cancelBtn.setOnAction(e -> onCancel());
        saveBtn.setOnAction(e -> onSave());
        if (changePhotoBtn != null) {
            changePhotoBtn.setOnAction(e -> onChangePhoto());
        }
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void setupFormByRole(User user) {
        boolean isAmateur = "amateur".equalsIgnoreCase(user.getRole());
        if (interessSection != null) {
            interessSection.setVisible(isAmateur);
            interessSection.setManaged(isAmateur);
        }
    }

    private void populateForm(User user) {
        if (prenomField   != null && user.getPrenom()        != null) prenomField  .setText(user.getPrenom());
        if (nomField      != null && user.getNom()           != null) nomField     .setText(user.getNom());
        if (bioField      != null && user.getBiographie()    != null) bioField     .setText(user.getBiographie());
        if (interessField != null && user.getCentreInteret() != null) interessField.setText(user.getCentreInteret());
    }

    private void setupPhotoSection(User user) {
        if (photoPreview == null) return;

        Circle clip = new Circle(50);
        clip.setCenterX(50);
        clip.setCenterY(50);
        photoPreview.setClip(clip);

        String photoPath = user.getPhotoProfil();
        if (photoPath != null && !photoPath.isEmpty()) {
            try {
                Image photo = new Image(toUrl(photoPath), 100, 100, true, true);
                if (!photo.isError()) photoPreview.setImage(photo);
            } catch (Exception ignored) {}
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    @FXML
    private void onChangePhoto() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choisir une photo de profil");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"),
                new FileChooser.ExtensionFilter("Tous les fichiers", "*.*")
        );
        try {
            File chosen = fc.showOpenDialog(changePhotoBtn.getScene().getWindow());
            if (chosen != null) {
                selectedPhotoFile = chosen;
                Image preview = new Image(chosen.toURI().toString(), 100, 100, true, true);
                if (!preview.isError()) photoPreview.setImage(preview);
                System.out.println("Photo sélectionnée : " + chosen.getAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Erreur sélection photo : " + e.getMessage());
        }
    }

    @FXML
    private void onSave() {
        if (currentUser == null) return;

        try {
            // ── 1. Champs texte ────────────────────────────────────────────
            String prenom = prenomField.getText();
            String nom    = nomField.getText();
            String bio    = bioField.getText();

            if (prenom != null && !prenom.isBlank()) currentUser.setPrenom(prenom);
            if (nom    != null && !nom.isBlank())    currentUser.setNom(nom);
            // Bio peut être vide, on la met à jour quoi qu'il arrive
            currentUser.setBiographie(bio != null ? bio : "");

            if ("amateur".equalsIgnoreCase(currentUser.getRole())) {
                String interet = interessField.getText();
                if (interet != null && !interet.isBlank())
                    currentUser.setCentreInteret(interet);
            }

            // ── 2. Photo : copier dans ressources pour persistance ─────────
            if (selectedPhotoFile != null) {
                String savedPath = copyPhotoToResources(selectedPhotoFile, currentUser.getId());
                String finalPath = savedPath != null ? savedPath : selectedPhotoFile.getAbsolutePath();
                currentUser.setPhotoProfil(finalPath);
                System.out.println("Photo path sauvegardé : " + finalPath);
            }

            // ── 3. Dates obligatoires par défaut si null ───────────────────
            if (currentUser.getDateNaissance() == null) {
                currentUser.setDateNaissance(LocalDate.of(2000, 1, 1));
            }
            if (currentUser.getDateInscription() == null) {
                currentUser.setDateInscription(LocalDate.now());
            }

            // ── 4. Champs obligatoires manquants pour l'admin par défaut ───
            if (currentUser.getNumTel() == null || currentUser.getNumTel().isBlank()) {
                currentUser.setNumTel("00000000");
            }
            if (currentUser.getVille() == null || currentUser.getVille().isBlank()) {
                currentUser.setVille("Tunis");
            }
            if (currentUser.getStatut() == null || currentUser.getStatut().isBlank()) {
                currentUser.setStatut("Activé");
            }

            // ── 5. CRITIQUE : vider le mdp pour éviter le double hashage ───
            // Le UserService re-hashe le mdp si getMdp() n'est pas blank.
            // Le hash stocké en base ne doit PAS être re-hashé.
            // On vide le mdp → buildUpdateSql(false) → pas de SET mdp dans le SQL.
            currentUser.setMdp("");

            // ── 6. Debug avant update ──────────────────────────────────────
            System.out.println("=== AVANT UPDATE ===");
            System.out.println("userId       : " + currentUser.getId());
            System.out.println("photo_profil : " + currentUser.getPhotoProfil());
            System.out.println("prenom       : " + currentUser.getPrenom());
            System.out.println("dateNaiss    : " + currentUser.getDateNaissance());
            System.out.println("dateInscript : " + currentUser.getDateInscription());
            System.out.println("statut       : " + currentUser.getStatut());

            // ── 7. Persister en base ───────────────────────────────────────
            userService.update(currentUser);
            System.out.println("✅ Profil sauvegardé pour userId=" + currentUser.getId());

            // ── 8. Naviguer vers le profil ─────────────────────────────────
            MainController mc = MainController.getInstance();
            if (mc != null) mc.navigateTo("profile");

        } catch (Exception e) {
            System.err.println("Erreur sauvegarde : " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Copie la photo dans src/main/resources/images/avatars/avatar_<id>.<ext>
     * pour qu'elle survive aux redémarrages de l'app.
     */
    private String copyPhotoToResources(File sourceFile, int userId) {
        try {
            Path destDir = Paths.get(PHOTOS_DIR);
            if (!Files.exists(destDir)) {
                Files.createDirectories(destDir);
            }

            String originalName = sourceFile.getName();
            String ext = originalName.contains(".")
                    ? originalName.substring(originalName.lastIndexOf("."))
                    : ".png";
            String destFileName = "avatar_" + userId + ext;
            Path destPath = destDir.resolve(destFileName);

            Files.copy(sourceFile.toPath(), destPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Photo copiée vers : " + destPath.toAbsolutePath());

            return destPath.toAbsolutePath().toString();

        } catch (IOException e) {
            System.err.println("Erreur copie photo : " + e.getMessage());
            return null;
        }
    }

    @FXML
    private void onCancel() {
        try {
            MainController mc = MainController.getInstance();
            if (mc != null) mc.navigateTo("profile");
        } catch (Exception e) {
            System.err.println("Erreur annulation : " + e.getMessage());
        }
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private String toUrl(String p) {
        if (p.startsWith("file:") || p.startsWith("http://") || p.startsWith("https://"))
            return p;
        return new File(p).toURI().toString();
    }
}