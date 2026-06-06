package controllers;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class ImageEditorController {

    private final String initialImagePath;
    private String editedImagePath;
    private final Stage stage;
    private ImageView imageView;
    private ColorAdjust colorAdjust;
    
    private static final double POST_ASPECT_RATIO = 700.0 / 360.0;
    
    // Crop variables
    private Rectangle selectionRect;
    private double startX, startY;
    private boolean isCropping = false;
    private boolean isMoving = false;
    private double lastMouseX, lastMouseY;
    private Button validateCropBtn;
    private Button undoCropBtn;
    private Button cancelCropBtn;
    private Button cropBtn;
    private Image baseImage;
    private Image previousImage;

    public ImageEditorController(Stage owner, String imagePath) {
        this.initialImagePath = imagePath;
        this.editedImagePath = imagePath;
        this.stage = new Stage();
        this.stage.initModality(Modality.WINDOW_MODAL);
        this.stage.initOwner(owner);
        this.stage.setTitle("Editeur d'image");
        
        initUI();
    }

    private void initUI() {
        VBox root = new VBox(0);
        root.getStyleClass().add("editor-root");
        root.setStyle("-fx-background-color: white;");

        // --- Main Content (Image + Controls) ---
        HBox body = new HBox(40);
        body.setPadding(new Insets(30));
        body.setAlignment(Pos.CENTER);
        body.getStyleClass().add("editor-body");

        // Left: Image Container
        StackPane imageContainer = new StackPane();
        imageContainer.setPrefSize(560, 400);
        imageContainer.setMinSize(560, 400);
        imageContainer.getStyleClass().add("editor-image-container");
        imageContainer.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 20; -fx-background-radius: 20; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 20, 0, 0, 10);");
        
        File file = new File(initialImagePath);
        baseImage = new Image(file.toURI().toString());
        imageView = new ImageView(baseImage);
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(520);
        imageView.setFitHeight(360);

        colorAdjust = new ColorAdjust();
        imageView.setEffect(colorAdjust);

        // We use a Pane for the selection to avoid StackPane's automatic centering
        javafx.scene.layout.Pane selectionLayer = new javafx.scene.layout.Pane();
        selectionLayer.setMouseTransparent(true); // Let events pass to the container
        
        selectionRect = new Rectangle();
        selectionRect.setFill(Color.rgb(63, 68, 212, 0.15));
        selectionRect.setStroke(Color.valueOf("#3f44d4"));
        selectionRect.setStrokeWidth(2);
        selectionRect.getStrokeDashArray().addAll(6.0, 4.0);
        selectionRect.setVisible(false);
        selectionRect.setManaged(false); // Important: manual positioning
        selectionRect.setCursor(javafx.scene.Cursor.MOVE);
        
        selectionLayer.getChildren().add(selectionRect);

        imageContainer.getChildren().addAll(imageView, selectionLayer);
        StackPane.setAlignment(selectionLayer, Pos.TOP_LEFT);

        // Right: Controls
        VBox controlsSide = new VBox(30);
        controlsSide.setPrefWidth(340);
        controlsSide.setAlignment(Pos.TOP_LEFT);
        controlsSide.getStyleClass().add("editor-controls-side");

        // 1. AJUSTEMENTS
        VBox ajustementsSection = new VBox(15);
        Label ajustementsHeader = new Label("AJUSTEMENTS");
        ajustementsHeader.getStyleClass().add("editor-section-header");
        ajustementsHeader.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #94a3b8; -fx-letter-spacing: 1.5px;");
        
        VBox ajustementsBox = new VBox(20);
        VBox brightnessCtrl = createSliderControl("Luminosité", -1.0, 1.0, 0, (obs, oldVal, newVal) -> colorAdjust.setBrightness(newVal.doubleValue()));
        VBox contrastCtrl = createSliderControl("Contraste", -1.0, 1.0, 0, (obs, oldVal, newVal) -> colorAdjust.setContrast(newVal.doubleValue()));
        VBox saturationCtrl = createSliderControl("Saturation", -1.0, 1.0, 0, (obs, oldVal, newVal) -> colorAdjust.setSaturation(newVal.doubleValue()));
        ajustementsBox.getChildren().addAll(brightnessCtrl, contrastCtrl, saturationCtrl);
        ajustementsSection.getChildren().addAll(ajustementsHeader, ajustementsBox);

        // 2. FILTRES
        VBox filtresSection = new VBox(15);
        Label filtresHeader = new Label("FILTRES");
        filtresHeader.getStyleClass().add("editor-section-header");
        filtresHeader.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #94a3b8; -fx-letter-spacing: 1.5px;");
        
        HBox filterButtons = new HBox(12);
        Button normalBtn = createFilterButton("Normal", 0, 0);
        Button grayscaleBtn = createFilterButton("N&B", 0, -1);
        
        Button sepiaBtn = new Button("Sepia");
        sepiaBtn.getStyleClass().add("filter-pill");
        sepiaBtn.setStyle("-fx-background-radius: 12; -fx-padding: 10 22; -fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-font-weight: bold; -fx-cursor: hand;");
        sepiaBtn.setOnAction(e -> {
            colorAdjust.setSaturation(-0.2);
            colorAdjust.setHue(0.05);
            colorAdjust.setBrightness(0.1);
        });
        filterButtons.getChildren().addAll(normalBtn, grayscaleBtn, sepiaBtn);
        filtresSection.getChildren().addAll(filtresHeader, filterButtons);

        // 3. OUTILS
        VBox outilsSection = new VBox(15);
        Label outilsHeader = new Label("OUTILS");
        outilsHeader.getStyleClass().add("editor-section-header");
        outilsHeader.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #94a3b8; -fx-letter-spacing: 1.5px;");
        
        cropBtn = new Button("Démarrer le rognage");
        cropBtn.setMaxWidth(Double.MAX_VALUE);
        cropBtn.getStyleClass().add("tool-button");
        cropBtn.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-font-weight: bold; -fx-padding: 12; -fx-background-radius: 12; -fx-cursor: hand;");
        
        validateCropBtn = new Button("Valider le rognage");
        validateCropBtn.setMaxWidth(Double.MAX_VALUE);
        validateCropBtn.getStyleClass().add("btn-apply");
        validateCropBtn.setDisable(true);
        validateCropBtn.setVisible(false);
        validateCropBtn.setOnAction(e -> applyImmediateCrop());

        cancelCropBtn = new Button("Annuler le rognage");
        cancelCropBtn.setMaxWidth(Double.MAX_VALUE);
        cancelCropBtn.getStyleClass().add("btn-cancel");
        cancelCropBtn.setStyle("-fx-text-fill: #f87171; -fx-font-weight: bold; -fx-padding: 12; -fx-background-radius: 12; -fx-cursor: hand; -fx-border-color: #f87171; -fx-border-radius: 12;");
        cancelCropBtn.setVisible(false);
        cancelCropBtn.setOnAction(e -> {
            toggleCrop();
            cropBtn.setVisible(true);
            validateCropBtn.setVisible(false);
            cancelCropBtn.setVisible(false);
            if (previousImage != null) undoCropBtn.setVisible(true);
        });

        undoCropBtn = new Button("Annuler le dernier recadrage");
        undoCropBtn.setMaxWidth(Double.MAX_VALUE);
        undoCropBtn.getStyleClass().add("btn-cancel");
        undoCropBtn.setStyle("-fx-text-fill: #f87171; -fx-font-weight: bold; -fx-padding: 12; -fx-background-radius: 12; -fx-cursor: hand; -fx-border-color: #f87171; -fx-border-radius: 12;");
        undoCropBtn.setVisible(false);
        undoCropBtn.setOnAction(e -> undoLastCrop());

        cropBtn.setOnAction(e -> {
            toggleCrop();
            if (isCropping) {
                cropBtn.setVisible(false);
                validateCropBtn.setVisible(true);
                cancelCropBtn.setVisible(true);
                undoCropBtn.setVisible(false);
            }
        });
        outilsSection.getChildren().addAll(outilsHeader, cropBtn, validateCropBtn, cancelCropBtn, undoCropBtn);

        controlsSide.getChildren().addAll(ajustementsSection, filtresSection, outilsSection);

        body.getChildren().addAll(imageContainer, controlsSide);

        // --- Footer (Buttons) ---
        HBox footer = new HBox(15);
        footer.setPadding(new Insets(25, 40, 30, 40));
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.getStyleClass().add("editor-footer");
        footer.setStyle("-fx-background-color: white; -fx-border-color: #f1f5f9; -fx-border-width: 1 0 0 0;");
        
        Button cancelBtn = new Button("Annuler");
        cancelBtn.getStyleClass().add("btn-cancel");
        cancelBtn.setStyle("-fx-text-fill: #f87171; -fx-font-weight: bold; -fx-padding: 12 30; -fx-cursor: hand; -fx-font-size: 14px;");
        cancelBtn.setOnAction(e -> stage.close());

        Button saveBtn = new Button("Terminer et Sauvegarder");
        saveBtn.getStyleClass().add("btn-apply");
        saveBtn.setStyle("-fx-background-color: #3f44d4; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 12 35; -fx-background-radius: 12; -fx-cursor: hand; -fx-font-size: 14px;");
        saveBtn.setOnAction(e -> saveEditedImage());

        footer.getChildren().addAll(cancelBtn, saveBtn);

        root.getChildren().addAll(body, footer);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/views/styles/amateur-theme.css").toExternalForm());
        stage.setScene(scene);

        // Mouse events for cropping
        imageContainer.setOnMousePressed(e -> {
            if (isCropping) {
                double mouseX = e.getX();
                double mouseY = e.getY();
                
                // Double click inside selection applies crop
                if (e.getClickCount() == 2 && selectionRect.isVisible() && selectionRect.getBoundsInParent().contains(mouseX, mouseY)) {
                    applyImmediateCrop();
                    return;
                }

                // If clicking inside existing selection, start moving
                if (selectionRect.isVisible() && selectionRect.getBoundsInParent().contains(mouseX, mouseY)) {
                    isMoving = true;
                    lastMouseX = mouseX;
                    lastMouseY = mouseY;
                } else {
                    // Start new selection
                    javafx.geometry.Bounds imageBounds = imageView.getBoundsInParent();
                    if (imageBounds.contains(mouseX, mouseY)) {
                        isMoving = false;
                        startX = mouseX;
                        startY = mouseY;
                        selectionRect.setX(startX);
                        selectionRect.setY(startY);
                        selectionRect.setWidth(0);
                        selectionRect.setHeight(0);
                        selectionRect.setVisible(true);
                        validateCropBtn.setDisable(true);
                    }
                }
            }
        });

        imageContainer.setOnMouseDragged(e -> {
            if (isCropping) {
                double mouseX = e.getX();
                double mouseY = e.getY();
                javafx.geometry.Bounds imageBounds = imageView.getBoundsInParent();

                if (isMoving) {
                    double deltaX = mouseX - lastMouseX;
                    double deltaY = mouseY - lastMouseY;
                    
                    double newX = selectionRect.getX() + deltaX;
                    double newY = selectionRect.getY() + deltaY;
                    
                    // Keep within image bounds
                    newX = Math.max(imageBounds.getMinX(), Math.min(imageBounds.getMaxX() - selectionRect.getWidth(), newX));
                    newY = Math.max(imageBounds.getMinY(), Math.min(imageBounds.getMaxY() - selectionRect.getHeight(), newY));
                    
                    selectionRect.setX(newX);
                    selectionRect.setY(newY);
                    
                    lastMouseX = mouseX;
                    lastMouseY = mouseY;
                } else if (selectionRect.isVisible()) {
                    // Keep within image bounds
                    double currentX = Math.max(imageBounds.getMinX(), Math.min(imageBounds.getMaxX(), mouseX));
                    double currentY = Math.max(imageBounds.getMinY(), Math.min(imageBounds.getMaxY(), mouseY));

                    double diffX = currentX - startX;
                    double diffY = currentY - startY;

                    double w = Math.abs(diffX);
                    double h = Math.abs(diffY);

                    double finalX = diffX > 0 ? startX : startX - w;
                    double finalY = diffY > 0 ? startY : startY - h;

                    selectionRect.setX(finalX);
                    selectionRect.setY(finalY);
                    selectionRect.setWidth(w);
                    selectionRect.setHeight(h);
                    
                    if (w > 5 && h > 5) validateCropBtn.setDisable(false);
                }
            }
        });

        imageContainer.setOnMouseReleased(e -> {
            isMoving = false;
        });
    }

    private void applyImmediateCrop() {
        if (selectionRect.getWidth() < 10 || selectionRect.getHeight() < 10) return;

        try {
            javafx.geometry.Bounds imageBounds = imageView.getBoundsInParent();
            WritableImage snapshot = imageView.snapshot(null, null);
            
            double scaleX = snapshot.getWidth() / imageBounds.getWidth();
            double scaleY = snapshot.getHeight() / imageBounds.getHeight();
            
            double x = (selectionRect.getX() - imageBounds.getMinX()) * scaleX;
            double y = (selectionRect.getY() - imageBounds.getMinY()) * scaleY;
            double w = selectionRect.getWidth() * scaleX;
            double h = selectionRect.getHeight() * scaleY;

            x = Math.max(0, Math.min(snapshot.getWidth() - 1, x));
            y = Math.max(0, Math.min(snapshot.getHeight() - 1, y));
            w = Math.min(snapshot.getWidth() - x, w);
            h = Math.min(snapshot.getHeight() - y, h);

            if (w >= 1 && h >= 1) {
                javafx.scene.image.PixelReader reader = snapshot.getPixelReader();
                Image croppedImage = new WritableImage(reader, (int)x, (int)y, (int)w, (int)h);
                
                // Store current image for undo
                previousImage = imageView.getImage();
                
                // Update base image and reset effects for the new image
                imageView.setImage(croppedImage);
                colorAdjust.setBrightness(0);
                colorAdjust.setContrast(0);
                colorAdjust.setSaturation(0);
                colorAdjust.setHue(0);
                
                // Exit crop mode
                toggleCrop();
                selectionRect.setVisible(false);
                validateCropBtn.setVisible(false);
                cancelCropBtn.setVisible(false);
                cropBtn.setVisible(true);
                undoCropBtn.setVisible(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void undoLastCrop() {
        if (previousImage != null) {
            imageView.setImage(previousImage);
            previousImage = null;
            undoCropBtn.setVisible(false);
            
            // Reset effects just in case
            colorAdjust.setBrightness(0);
            colorAdjust.setContrast(0);
            colorAdjust.setSaturation(0);
            colorAdjust.setHue(0);
        }
    }

    private VBox createSliderControl(String labelText, double min, double max, double initial, javafx.beans.value.ChangeListener<Number> listener) {
        VBox box = new VBox(8);
        box.getStyleClass().add("slider-control");
        
        HBox labelRow = new HBox();
        labelRow.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label(labelText);
        label.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569; -fx-font-size: 13px;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label percentLabel = new Label("0%");
        percentLabel.setStyle("-fx-font-weight: 800; -fx-text-fill: #3f44d4; -fx-font-size: 12px;");
        
        labelRow.getChildren().addAll(label, spacer, percentLabel);
        
        Slider slider = new Slider(min, max, initial);
        slider.setMaxWidth(Double.MAX_VALUE);
        slider.getStyleClass().add("modern-slider");
        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            listener.changed(obs, oldVal, newVal);
            int percent = (int) (newVal.doubleValue() * 100);
            percentLabel.setText((percent > 0 ? "+" : "") + percent + "%");
        });
        
        // Simple modern slider styling
        slider.setStyle("-fx-control-inner-background: #f1f5f9; -fx-cursor: hand;");
        
        box.getChildren().addAll(labelRow, slider);
        return box;
    }

    private Button createFilterButton(String text, double hue, double saturation) {
        Button btn = new Button(text);
        btn.getStyleClass().add("filter-pill");
        btn.setStyle("-fx-background-radius: 12; -fx-padding: 10 22; -fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-font-weight: bold; -fx-cursor: hand;");
        btn.setOnAction(e -> {
            colorAdjust.setHue(hue);
            colorAdjust.setSaturation(saturation);
        });
        return btn;
    }

    private void toggleCrop() {
        isCropping = !isCropping;
        if (!isCropping) {
            selectionRect.setVisible(false);
        }
    }

    private void saveEditedImage() {
        try {
            Image imageToSave;
            if (isCropping && selectionRect.getWidth() > 10 && selectionRect.getHeight() > 10) {
                // Get the coordinates relative to the ImageView
                javafx.geometry.Bounds imageBounds = imageView.getBoundsInParent();
                
                // Take a snapshot of the ImageView with its current effects
                WritableImage snapshot = imageView.snapshot(null, null);
                
                // The selection is relative to the StackPane. 
                // We need to translate it to be relative to the ImageView's snapshot.
                double scaleX = snapshot.getWidth() / imageBounds.getWidth();
                double scaleY = snapshot.getHeight() / imageBounds.getHeight();
                
                double x = (selectionRect.getX() - imageBounds.getMinX()) * scaleX;
                double y = (selectionRect.getY() - imageBounds.getMinY()) * scaleY;
                double w = selectionRect.getWidth() * scaleX;
                double h = selectionRect.getHeight() * scaleY;

                // Ensure within bounds of the snapshot
                x = Math.max(0, Math.min(snapshot.getWidth() - 1, x));
                y = Math.max(0, Math.min(snapshot.getHeight() - 1, y));
                w = Math.min(snapshot.getWidth() - x, w);
                h = Math.min(snapshot.getHeight() - y, h);

                if (w < 1 || h < 1) {
                    imageToSave = snapshot;
                } else {
                    javafx.scene.image.PixelReader reader = snapshot.getPixelReader();
                    imageToSave = new WritableImage(reader, (int)x, (int)y, (int)w, (int)h);
                }
            } else {
                imageToSave = imageView.snapshot(null, null);
            }

            File tempFile = File.createTempFile("edited_oeuvre_" + UUID.randomUUID(), ".png");
            BufferedImage bImage = SwingFXUtils.fromFXImage(imageToSave, null);
            ImageIO.write(bImage, "png", tempFile);
            
            this.editedImagePath = tempFile.getAbsolutePath();
            stage.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String showAndWait() {
        stage.showAndWait();
        return editedImagePath;
    }
}
