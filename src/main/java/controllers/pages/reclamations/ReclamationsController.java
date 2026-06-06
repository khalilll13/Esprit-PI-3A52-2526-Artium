package controllers.pages.reclamations;

import services.ReclamationService;
import services.UserService;
import entities.Reclamation;
import entities.User;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;

import java.sql.SQLDataException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.text.Normalizer;


public class ReclamationsController {

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statutFilter;
    @FXML private ComboBox<String> dateFilter;

    @FXML private ToggleGroup typeTabsGroup;
    @FXML private ToggleButton tabAll;
    @FXML private ToggleButton tabPaiement;
    @FXML private ToggleButton tabOeuvre;
    @FXML private ToggleButton tabEvenement;
    @FXML private ToggleButton tabCompte;

    @FXML private GridPane cardsContainer;

    // Layout grid: nombre de colonnes dépend de la largeur disponible
    private static final double CARD_MIN_WIDTH = 340; // approx. largeur d'une card
    private static final double GRID_GAP = 10;
    private int currentColumns = 1;

    private final ReclamationService reclamationService = new ReclamationService();
    private final services.ReponseService reponseService = new services.ReponseService();
    private final services.UserService userService = new services.UserService();

    private final List<Reclamation> all = new ArrayList<>();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    public void initialize() {
        statutFilter.getItems().setAll("Tous", "Traitée", "Non traitée", "Archivée");
        statutFilter.getSelectionModel().selectFirst();
        
        if (dateFilter != null) {
            dateFilter.getItems().setAll("Toutes les dates", "Aujourd'hui", "Ce mois", "Cette année");
            dateFilter.getSelectionModel().selectFirst();
            dateFilter.valueProperty().addListener((obs, o, n) -> applySearchAndFilter());
        }

        searchField.textProperty().addListener((obs, o, n) -> applySearchAndFilter());
        statutFilter.valueProperty().addListener((obs, o, n) -> applySearchAndFilter());
        typeTabsGroup.selectedToggleProperty().addListener((obs, o, n) -> applySearchAndFilter());

        // Grille responsive: 2 ou 3 cartes par ligne selon l'espace
        if (cardsContainer != null) {
            cardsContainer.setHgap(GRID_GAP);
            cardsContainer.setVgap(GRID_GAP);
            cardsContainer.widthProperty().addListener((obs, o, n) -> updateGridColumns());
            updateGridColumns();
        }

        refresh();
    }

    private void updateGridColumns() {
        if (cardsContainer == null) return;
        double w = cardsContainer.getWidth();
        if (w <= 0) return;

        // Calcule le max de colonnes en fonction d'une largeur minimale de card
        int cols = (int) Math.floor((w + GRID_GAP) / (CARD_MIN_WIDTH + GRID_GAP));
        cols = Math.max(1, Math.min(3, cols)); // on limite à 3 (comme demandé)

        if (cols != currentColumns) {
            currentColumns = cols;
            applySearchAndFilter();
        }
    }

    private void refresh() {
        try {
            all.clear();
            List<Reclamation> reclamations = reclamationService.getAll();
            autoArchiveReclamations(reclamations);
            all.addAll(reclamations);
            applySearchAndFilter();
        } catch (SQLDataException e) {
            showError("Chargement impossible", e.getMessage());
        }
    }

    private void autoArchiveReclamations(List<Reclamation> list) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        for (Reclamation r : list) {
            if (Boolean.TRUE.equals(r.getIsArchived())) continue;
            
            String s = normalize(r.getStatut());
            if ((s.contains("traite") || s.contains("resolu") || s.contains("done")) && !s.contains("non")) {
                if (r.getUpdatedAt() != null) {
                    long days = java.time.temporal.ChronoUnit.DAYS.between(r.getUpdatedAt(), now);
                    if (days >= 7) {
                        try {
                            reclamationService.updateArchiveStatusById(r.getId(), true);
                            r.setIsArchived(true);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
    }

    private void applySearchAndFilter() {
        String q = safe(searchField.getText()).trim().toLowerCase(Locale.ROOT);
        String selectedStatut = statutFilter.getValue();
        String selectedType = getSelectedType();
        String selectedDate = dateFilter != null ? dateFilter.getValue() : "Toutes les dates";

        List<Reclamation> filtered = all.stream()
                .filter(r -> matchesQuery(r, q))
                .filter(r -> matchesStatut(r, selectedStatut))
                .filter(r -> matchesType(r, selectedType))
                .filter(r -> matchesDate(r, selectedDate))
                .sorted(Comparator.comparing((Reclamation r) -> r.getId() == null ? 0 : r.getId()).reversed())
                .collect(Collectors.toList());

        renderCards(filtered);
    }

    private void renderCards(List<Reclamation> list) {
        cardsContainer.getChildren().clear();

        int cols = Math.max(1, currentColumns);
        for (int index = 0; index < list.size(); index++) {
            Reclamation r = list.get(index);
            try {
                FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/views/pages/reclamations/reclamation_card.fxml")));
                Parent card = loader.load();

                controllers.pages.reclamations.ReclamationCardController cardController = loader.getController();
                cardController.setData(r, buildUserText(r), buildDateText(r), new controllers.pages.reclamations.ReclamationCardController.CardActionHandler() {
                    @Override
                    public void onReply(Reclamation reclamation) {
                        cardController.openReplyDialog(reclamation);
                        refresh();
                    }

                    @Override
                    public void onArchive(Reclamation reclamation) {
                        handleArchive(reclamation);
                    }

                    @Override
                    public void onDelete(Reclamation reclamation) {
                        handleDelete(reclamation);
                    }
                });

                if (card instanceof Region region) {
                    region.setMaxWidth(Double.MAX_VALUE);
                }

                int col = index % cols;
                int row = index / cols;
                cardsContainer.add(card, col, row);
            } catch (Exception e) {
                showError("Affichage impossible", "Erreur pendant le rendu d'une carte reclamation: " + e.getMessage());
                return;
            }
        }
    }

    private void handleDelete(Reclamation rec) {
        ButtonType deleteButton = new ButtonType("Supprimer", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "", deleteButton, cancelButton);
        confirm.setTitle("Confirmer la suppression");
        confirm.setHeaderText("Supprimer la reclamation #" + (rec.getId() == null ? "" : rec.getId()) + " ?");
        confirm.setContentText("Cette action est irreversible.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != deleteButton) return;

        try {
            try { reponseService.deleteByReclamationId(rec.getId()); } catch (Exception ignored) {}
            reclamationService.delete(rec);
            refresh();
        } catch (Exception e) {
            showError("Suppression impossible", e.getMessage());
        }
    }

    private void handleArchive(Reclamation rec) {
        try {
            reclamationService.updateArchiveStatusById(rec.getId(), true);
            refresh();
        } catch (Exception e) {
            showError("Archivage impossible", e.getMessage());
        }
    }

    private String getSelectedType() {
        Toggle t = typeTabsGroup.getSelectedToggle();
        if (t == tabPaiement) return "Paiement";
        if (t == tabOeuvre) return "Oeuvre";
                if (t == tabEvenement) return "Evènement";
        if (t == tabCompte) return "Compte";
        return "Tous les types";
    }

    private boolean matchesQuery(Reclamation r, String q) {
        if (q == null || q.isEmpty()) return true;
        String userText = buildUserText(r).toLowerCase(Locale.ROOT);
        return contains(r.getTexte(), q)
                || contains(r.getType(), q)
                || contains(r.getStatut(), q)
                || userText.contains(q);
    }

    private boolean matchesStatut(Reclamation r, String selected) {
        String s = normalize(r.getStatut());
        String sel = normalize(selected);
        
        boolean isArchived = Boolean.TRUE.equals(r.getIsArchived());
        if (sel.contains("archive")) return isArchived;
        
        if (selected == null || selected.equals("Tous")) {
            return !isArchived; // Hide archived ones from "Tous"
        }
        
        if (isArchived) return false;

        // normalize status values from DB: "non traitée", "NON_TRAITEE", "traitee", "en_cours"...
        boolean isNon = s.contains("nontra") || s.contains("non tra") || s.contains("non-tr") || s.contains("en cours") || s.contains("encours") || s.contains("pending");
        boolean isTraite = s.contains("traite") || s.contains("trait") || s.contains("resolu") || s.contains("resolved") || s.contains("done");

        if (sel.contains("traite") && !sel.contains("non")) {
            return isTraite && !isNon;
        }
        if (sel.contains("non")) {
            return isNon;
        }
        return true;
    }

    private boolean matchesType(Reclamation r, String selected) {
        if (selected == null || selected.equals("Tous les types")) return true;
        return normalize(r.getType()).contains(normalize(selected));
    }

    private boolean matchesDate(Reclamation r, String selected) {
        if (selected == null || selected.equals("Toutes les dates")) return true;
        if (r.getDateCreation() == null) return false;

        java.time.LocalDate dateCreation = r.getDateCreation().toLocalDate();
        java.time.LocalDate now = java.time.LocalDate.now();

        switch (selected) {
            case "Aujourd'hui":
                return dateCreation.isEqual(now);
            case "Ce mois":
                return dateCreation.getYear() == now.getYear() && dateCreation.getMonthValue() == now.getMonthValue();
            case "Cette année":
                return dateCreation.getYear() == now.getYear();
            default:
                return true;
        }
    }

    private String buildDateText(Reclamation r) {
        return r.getDateCreation() != null ? r.getDateCreation().format(DATE_FMT) : "";
    }

    private String buildUserText(Reclamation r) {
        UserService userService = new UserService();

        User user = null;

        try {
            user = userService.getById(r.getUserId());
        } catch (SQLDataException e) {
            e.printStackTrace();
        }

        return (user != null && user.getNom() != null)
                ? user.getNom() + " " + user.getPrenom()
                : "-";
            }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search);
    }

    private String normalize(String s) {
        String v = safe(s).toLowerCase(Locale.ROOT).replace("_", " ").replace("-", " ");
        v = Normalizer.normalize(v, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        while (v.contains("  ")) v = v.replace("  ", " ");
        return v.trim();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private void showError(String title, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Erreur");
        a.setHeaderText(title);
        a.setContentText(message);
        a.showAndWait();
    }
}


