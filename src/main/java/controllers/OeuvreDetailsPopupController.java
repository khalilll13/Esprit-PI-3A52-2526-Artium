package controllers;

import entities.Commentaire;
import entities.Oeuvre;
import entities.User;
import services.CommentaireService;
import services.LikeService;
import services.OeuvreService;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class OeuvreDetailsPopupController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.FRANCE);
    private static final DateTimeFormatter COMMENT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.FRANCE);
    private static final double POST_IMAGE_MAX_WIDTH = 760;
    private static final double POST_IMAGE_MAX_HEIGHT = 360;

    private final OeuvreService oeuvreService = new OeuvreService();
    private final CommentaireService commentaireService = new CommentaireService();
    private final LikeService likeService = new LikeService();
    private Oeuvre currentOeuvre;
    private List<Commentaire> currentComments = new ArrayList<>();

    @FXML
    private Label titleLabel;

    @FXML
    private Label authorLabel;

    @FXML
    private Label metaLabel;

    @FXML
    private Label dateLabel;

    @FXML
    private Label descriptionLabel;

    @FXML
    private Label tagsLabel;

    @FXML
    private StackPane avatarPane;

    @FXML
    private Label avatarLetterLabel;

    @FXML
    private StackPane imageWrapper;

    @FXML
    private ImageView imageView;

    @FXML
    private Label noImageLabel;

    @FXML
    private HBox statsRow;

    @FXML
    private Label commentsTitleLabel;

    @FXML
    private VBox commentsListBox;

    @FXML
    private Label moreCommentsLabel;

    public void setData(Oeuvre oeuvre, String artistName, String collectionTitle) {
        if (oeuvre == null) {
            return;
        }
        currentOeuvre = oeuvre;

        titleLabel.setText(safe(oeuvre.getTitre(), "Sans titre"));
        authorLabel.setText(safe(artistName, "Artiste"));
        metaLabel.setText(safe(collectionTitle, "Collection inconnue"));
        dateLabel.setText(formatDate(oeuvre.getDateCreation()));
        descriptionLabel.setText(safe(oeuvre.getDescription(), "Aucune description."));
        avatarLetterLabel.setText(getInitialLetter(artistName));
        tagsLabel.setText((toHashtag(oeuvre.getType()) + " " + toHashtag(collectionTitle)).trim());

        ImageView postImage = createPostImageView(oeuvre.getImage());
        boolean hasImage = postImage != null;
        imageView.setVisible(hasImage);
        imageView.setManaged(hasImage);
        noImageLabel.setVisible(!hasImage);
        noImageLabel.setManaged(!hasImage);
        if (hasImage) {
            imageView.setImage(postImage.getImage());
        }

        currentComments = loadCommentsForOeuvre(oeuvre);

        int commentCount = currentComments.size();
        int likeCount = getLikeCount(oeuvre);
        int favoriCount = getFavoriCount(oeuvre);
        commentsTitleLabel.setText("Commentaires (" + commentCount + ")");

        buildStatsRow(likeCount, commentCount, favoriCount);
        buildCommentsPreview(currentComments);
    }

    private void buildStatsRow(int likeCount, int commentCount, int favoriCount) {
        statsRow.getChildren().setAll(
                buildStatChip("M12.1 18.55 10.55 17.14C5.4 12.47 2 9.39 2 5.6 2 2.52 4.42 0 7.5 0c1.74 0 3.41.81 4.5 2.09C13.09.81 14.76 0 16.5 0 19.58 0 22 2.52 22 5.6c0 3.79-3.4 6.87-8.55 11.55z", likeCount),
                buildStatChip("M20 2H4c-1.1 0-1.99.9-1.99 2L2 22l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z", commentCount),
                buildStatChip("M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z", favoriCount)
        );
    }

    private HBox buildStatChip(String iconPath, int count) {
        HBox statChip = new HBox(6);
        statChip.getStyleClass().add("oeuvre-post-stat-chip");

        SVGPath icon = new SVGPath();
        icon.setContent(iconPath);
        icon.setScaleX(0.55);
        icon.setScaleY(0.55);
        icon.getStyleClass().add("oeuvre-post-stat-icon");

        Label countLabel = new Label(String.valueOf(Math.max(0, count)));
        countLabel.getStyleClass().add("oeuvre-post-stat-count");
        statChip.getChildren().addAll(icon, countLabel);
        return statChip;
    }

    private void buildCommentsPreview(List<Commentaire> comments) {
        commentsListBox.getChildren().clear();
        // Cacher l'ancien label statique s'il existe dans le FXML
        if (moreCommentsLabel != null) {
            moreCommentsLabel.setVisible(false);
            moreCommentsLabel.setManaged(false);
        }

        if (comments == null || comments.isEmpty()) {
            Label emptyLabel = new Label("Aucun commentaire pour le moment.");
            emptyLabel.getStyleClass().add("oeuvre-post-comments-empty");
            commentsListBox.getChildren().add(emptyLabel);
            return;
        }

        int displayCount = Math.min(3, comments.size());
        for (int i = 0; i < displayCount; i++) {
            commentsListBox.getChildren().add(buildCommentRow(comments.get(i)));
        }

        if (comments.size() > 3) {
            StackPane btnStack = new StackPane();
            Button showMoreBtn = new Button("Afficher plus (" + (comments.size() - 3) + ")");
            showMoreBtn.getStyleClass().add("oeuvre-post-comments-more-btn");
            showMoreBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #3f44d4; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8 0; -fx-font-size: 12px;");

            HBox loadingDots = buildLoadingDots();
            loadingDots.setVisible(false);
            loadingDots.setManaged(false);

            showMoreBtn.setOnAction(event -> {
                showMoreBtn.setVisible(false);
                showMoreBtn.setManaged(false);
                loadingDots.setVisible(true);
                loadingDots.setManaged(true);

                PauseTransition pause = new PauseTransition(Duration.seconds(1.2));
                pause.setOnFinished(e -> {
                    int subDelay = 0;
                    for (int i = 3; i < comments.size(); i++) {
                        Node row = buildCommentRow(comments.get(i));
                        row.setOpacity(0);
                        commentsListBox.getChildren().add(row);

                        FadeTransition ft = new FadeTransition(Duration.seconds(0.4), row);
                        ft.setFromValue(0);
                        ft.setToValue(1);
                        ft.setDelay(Duration.millis(subDelay));
                        ft.play();

                        subDelay += 80;
                    }
                    VBox parent = (VBox) btnStack.getParent();
                    if (parent != null) {
                        parent.getChildren().remove(btnStack);
                    }
                });
                pause.play();
            });

            btnStack.getChildren().addAll(showMoreBtn, loadingDots);
            // On ajoute le bouton dans la box parente de commentsListBox si possible, 
            // ou directement à la fin de commentsListBox
            commentsListBox.getChildren().add(btnStack);
        }
    }

    private HBox buildLoadingDots() {
        HBox dots = new HBox(4);
        dots.setAlignment(Pos.CENTER);
        for (int i = 0; i < 3; i++) {
            Circle dot = new Circle(3, i == 0 ? javafx.scene.paint.Color.valueOf("#3f44d4") : javafx.scene.paint.Color.valueOf("#94a3b8"));
            FadeTransition ft = new FadeTransition(Duration.seconds(0.5), dot);
            ft.setFromValue(1.0);
            ft.setToValue(0.3);
            ft.setCycleCount(javafx.animation.Animation.INDEFINITE);
            ft.setAutoReverse(true);
            ft.setDelay(Duration.seconds(i * 0.15));
            dots.getChildren().add(dot);
            ft.play();
        }
        return dots;
    }

    private HBox buildCommentRow(Commentaire comment) {
        HBox row = new HBox(8);
        row.getStyleClass().add("oeuvre-post-comment-row");

        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("oeuvre-post-comment-avatar");

        User user = null;
        if (comment.getUserId() != null) {
            user = oeuvreService.getUserById(comment.getUserId());
        }

        String authorName = "Utilisateur";
        String authorPhoto = null;
        if (user != null) {
            String prenom = safe(user.getPrenom(), "");
            String nom = safe(user.getNom(), "");
            String full = (prenom + " " + nom).trim();
            if (!full.isEmpty()) {
                authorName = full;
            }
            authorPhoto = user.getPhotoProfil();
        }

        ImageView profileImage = createImageFromSource(authorPhoto);
        if (profileImage != null) {
            avatar.getChildren().add(profileImage);
        } else {
            Label initial = new Label(getInitialLetter(authorName));
            initial.getStyleClass().add("oeuvre-post-comment-avatar-text");
            avatar.getChildren().add(initial);
        }

        VBox body = new VBox(2);
        HBox.setHgrow(body, Priority.ALWAYS);

        HBox header = new HBox(8);
        Label authorLabel = new Label(authorName);
        authorLabel.getStyleClass().add("oeuvre-post-comment-author");

        Label dateLabel = new Label(formatCommentDate(comment.getDateCommentaire()));
        dateLabel.getStyleClass().add("oeuvre-post-comment-date");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button deleteButton = new Button("Supprimer");
        deleteButton.getStyleClass().add("oeuvre-post-comment-delete-button");
        deleteButton.setOnAction(event -> onDeleteComment(comment));

        Label textLabel = new Label(safe(comment.getTexte(), "..."));
        textLabel.getStyleClass().add("oeuvre-post-comment-text");
        textLabel.setWrapText(true);

        header.getChildren().addAll(authorLabel, dateLabel, spacer, deleteButton);
        body.getChildren().addAll(header, textLabel);

        row.getChildren().addAll(avatar, body);
        return row;
    }

    private void onDeleteComment(Commentaire comment) {
        if (comment == null || comment.getId() == null) {
            showError("Suppression impossible", "Ce commentaire ne peut pas etre supprime.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Supprimer le commentaire");
        confirmation.setHeaderText(null);
        confirmation.setContentText("Voulez-vous supprimer ce commentaire ?");

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        try {
            commentaireService.delete(comment);
            currentComments.removeIf(c -> c != null && c.getId() != null && c.getId().equals(comment.getId()));
            if (currentOeuvre != null) {
                currentOeuvre.setComments(new ArrayList<>(currentComments));
            }
            commentsTitleLabel.setText("Commentaires (" + currentComments.size() + ")");
            int likeCount = getLikeCount(currentOeuvre);
            int favoriCount = getFavoriCount(currentOeuvre);
            buildStatsRow(likeCount, currentComments.size(), favoriCount);
            buildCommentsPreview(currentComments);
        } catch (Exception e) {
            showError("Erreur", e.getMessage() == null ? "Une erreur est survenue lors de la suppression." : e.getMessage());
        }
    }

    private List<Commentaire> loadCommentsForOeuvre(Oeuvre oeuvre) {
        if (oeuvre == null || oeuvre.getId() == null) {
            return new ArrayList<>();
        }

        try {
            List<Commentaire> comments = commentaireService.getCommentsByOeuvreId(oeuvre.getId());
            List<Commentaire> safeComments = comments == null ? new ArrayList<>() : new ArrayList<>(comments);
            oeuvre.setComments(safeComments);
            return safeComments;
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private int getLikeCount(Oeuvre oeuvre) {
        if (oeuvre == null || oeuvre.getId() == null) {
            return 0;
        }
        return likeService.countLikesByOeuvre(oeuvre.getId());
    }

    private int getFavoriCount(Oeuvre oeuvre) {
        if (oeuvre == null || oeuvre.getId() == null) {
            return 0;
        }
        return likeService.countFavorisByOeuvre(oeuvre.getId());
    }

    private void showError(String header, String message) {
        Alert error = new Alert(Alert.AlertType.ERROR);
        error.setTitle("Erreur");
        error.setHeaderText(header);
        error.setContentText(message);
        error.showAndWait();
    }

    private String formatCommentDate(LocalDate dateCommentaire) {
        if (dateCommentaire == null) {
            return "Date inconnue";
        }
        return COMMENT_DATE_FORMATTER.format(dateCommentaire);
    }

    private ImageView createPostImageView(String imageSource) {
        if (imageSource == null || imageSource.isBlank()) {
            return null;
        }

        try {
            Image image;
            if (imageSource.startsWith("http://") || imageSource.startsWith("https://") || imageSource.startsWith("file:")) {
                image = new Image(imageSource, true);
            } else {
                image = new Image(new File(imageSource).toURI().toString(), true);
            }
            if (image.isError()) {
                return null;
            }

            ImageView view = new ImageView(image);
            view.setFitWidth(POST_IMAGE_MAX_WIDTH);
            view.setFitHeight(POST_IMAGE_MAX_HEIGHT);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            return view;
        } catch (Exception ignored) {
            return null;
        }
    }

    private ImageView createImageFromSource(String source) {
        String safeSource = safe(source, "");
        if (safeSource.isEmpty()) {
            return null;
        }

        try {
            Image image;
            if (safeSource.startsWith("http://") || safeSource.startsWith("https://") || safeSource.startsWith("file:")) {
                image = new Image(safeSource, true);
            } else {
                image = new Image(new File(safeSource).toURI().toString(), true);
            }

            if (image.isError()) {
                return null;
            }

            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(30);
            imageView.setFitHeight(30);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            return imageView;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String formatDate(LocalDate date) {
        if (date == null) {
            return "-";
        }
        return DATE_FORMATTER.format(date);
    }

    private String toHashtag(String value) {
        String cleaned = safe(value, "");
        if (cleaned.isEmpty()) {
            return "";
        }
        return "#" + cleaned.replaceAll("\\s+", "_");
    }

    private String getInitialLetter(String value) {
        String safe = safe(value, "");
        if (safe.isEmpty()) {
            return "A";
        }
        return safe.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}

