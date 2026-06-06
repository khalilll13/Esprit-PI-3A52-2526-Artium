package controllers;

import entities.User;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProfileController {

    // ── IDENTITY ──
    @FXML private ImageView profileAvatar;
    @FXML private Label avatarInitial;
    @FXML private Circle avatarPlaceholder;
    @FXML private Label fullNameLabel;
    @FXML private Label roleLabel;
    @FXML private Label villeLabel;
    @FXML private Label emailLabel;
    @FXML private Label telLabel;
    @FXML private Button editProfileBtn;

    // ── KANBAN TO-DO ──
    @FXML private TextField newTaskInput;
    @FXML private VBox todoBox;
    @FXML private VBox doingBox;
    @FXML private VBox doneBox;

    // Persistance locale simple (sans BDD)
    private static final String TASKS_FILE = System.getProperty("user.home") + File.separator + ".artium_admin_tasks.txt";
    private List<AdminTask> tasks = new ArrayList<>();

    @FXML
    public void initialize() {
        User user = MainFX.getAuthenticatedUser();
        if (user != null) {
            populateAvatar(user);
            populateIdentity(user);
        }
        
        loadTasks();
        renderKanban();
    }

    // ───────── AVATAR ─────────
    private void populateAvatar(User user) {
        String path = pickPhoto(user);
        Image img = null;
        if (path != null) {
            try {
                img = new Image(toUrl(path), 140, 140, false, true);
                if (img.isError()) img = null;
            } catch (Exception ignored) {}
        }

        if (img != null) {
            profileAvatar.setImage(img);
            profileAvatar.setVisible(true);
            if (avatarInitial != null) avatarInitial.setVisible(false);
            if (avatarPlaceholder != null) avatarPlaceholder.setVisible(false);
        } else {
            profileAvatar.setVisible(false);
            String initial = "?";
            if (user.getNom() != null && !user.getNom().isBlank()) {
                initial = user.getNom().substring(0, 1).toUpperCase();
            } else if (user.getPrenom() != null && !user.getPrenom().isBlank()) {
                initial = user.getPrenom().substring(0, 1).toUpperCase();
            }
            if (avatarInitial != null) {
                avatarInitial.setText(initial);
                avatarInitial.setVisible(true);
            }
            if (avatarPlaceholder != null) {
                avatarPlaceholder.setVisible(true);
            }
        }
    }

    private String pickPhoto(User user) {
        if (user.getPhotoProfil() != null && !user.getPhotoProfil().isBlank()) return user.getPhotoProfil().trim();
        if (user.getPhotoReferencePath() != null && !user.getPhotoReferencePath().isBlank()) return user.getPhotoReferencePath().trim();
        return null;
    }

    private String toUrl(String p) {
        if (p.startsWith("file:") || p.startsWith("http")) return p;
        return new File(p).toURI().toString();
    }

    // ───────── IDENTITY ─────────
    private void populateIdentity(User user) {
        fullNameLabel.setText(orDash(user.getPrenom()) + " " + orDash(user.getNom()));
        String role = user.getRole();
        roleLabel.setText(role != null ? role.toUpperCase() : "UTILISATEUR");
        villeLabel.setText(orDash(user.getVille()));
        emailLabel.setText(orDash(user.getEmail()));
        telLabel.setText(orDash(user.getNumTel()));
    }

    private String orDash(String s) {
        return (s != null && !s.isBlank()) ? s : "—";
    }

    @FXML
    private void onEditProfile() {
        MainController.getInstance().navigateTo("editProfile");
    }

    // ───────── KANBAN LOGIC (LOCAL PERSISTENCE) ─────────

    private static class AdminTask {
        String id;
        String desc;
        String status; // TODO, DOING, DONE
        public AdminTask(String i, String d, String s) { id = i; desc = d; status = s; }
    }

    private void loadTasks() {
        tasks.clear();
        try {
            File f = new File(TASKS_FILE);
            if (!f.exists()) return;
            List<String> lines = Files.readAllLines(Paths.get(TASKS_FILE));
            for (String line : lines) {
                String[] parts = line.split(";;;");
                if (parts.length >= 3) {
                    tasks.add(new AdminTask(parts[0], parts[1], parts[2]));
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur de chargement des tâches locales : " + e.getMessage());
        }
    }

    private void saveTasks() {
        try {
            List<String> lines = new ArrayList<>();
            for (AdminTask t : tasks) {
                lines.add(t.id + ";;;" + t.desc.replace(";;;", " ") + ";;;" + t.status);
            }
            Files.write(Paths.get(TASKS_FILE), lines);
        } catch (Exception e) {
            System.err.println("Erreur de sauvegarde des tâches locales : " + e.getMessage());
        }
    }

    @FXML
    private void onAddTask() {
        String txt = newTaskInput.getText();
        if (txt == null || txt.isBlank()) return;
        
        AdminTask t = new AdminTask(UUID.randomUUID().toString(), txt.trim(), "TODO");
        tasks.add(t);
        saveTasks();
        newTaskInput.clear();
        renderKanban();
    }

    private void renderKanban() {
        todoBox.getChildren().clear();
        doingBox.getChildren().clear();
        doneBox.getChildren().clear();

        for (AdminTask t : tasks) {
            VBox card = createTaskCard(t);
            switch (t.status) {
                case "TODO": todoBox.getChildren().add(card); break;
                case "DOING": doingBox.getChildren().add(card); break;
                case "DONE": doneBox.getChildren().add(card); break;
            }
        }
    }

    private VBox createTaskCard(AdminTask t) {
        VBox card = new VBox(12);
        card.getStyleClass().add("task-card");

        Label desc = new Label(t.desc);
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 13.5px; -fx-text-fill: #1e293b; -fx-font-weight: 500;");

        HBox actions = new HBox(5);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button btnLeft = new Button("←");
        btnLeft.getStyleClass().add("task-btn");
        btnLeft.setOnAction(e -> moveTask(t, false));

        Button btnRight = new Button("→");
        btnRight.getStyleClass().add("task-btn");
        btnRight.setOnAction(e -> moveTask(t, true));

        Button btnDel = new Button("Supprimer");
        btnDel.getStyleClass().addAll("task-btn", "task-btn-delete");
        btnDel.setStyle("-fx-font-size: 11px;");
        btnDel.setOnAction(e -> {
            tasks.remove(t);
            saveTasks();
            renderKanban();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        if (t.status.equals("TODO")) actions.getChildren().addAll(btnDel, spacer, btnRight);
        else if (t.status.equals("DOING")) actions.getChildren().addAll(btnLeft, btnDel, spacer, btnRight);
        else if (t.status.equals("DONE")) actions.getChildren().addAll(btnLeft, spacer, btnDel);

        card.getChildren().addAll(desc, actions);
        return card;
    }

    private void moveTask(AdminTask t, boolean forward) {
        if (forward) {
            if (t.status.equals("TODO")) t.status = "DOING";
            else if (t.status.equals("DOING")) t.status = "DONE";
        } else {
            if (t.status.equals("DONE")) t.status = "DOING";
            else if (t.status.equals("DOING")) t.status = "TODO";
        }
        saveTasks();
        renderKanban();
    }
}