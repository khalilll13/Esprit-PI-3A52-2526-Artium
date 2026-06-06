package controllers;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

import javafx.scene.shape.StrokeLineCap;
import javafx.scene.transform.Scale;
import java.util.Arrays;
import java.util.List;

public class DrawingBoardController {

    private final Stage stage;
    private Canvas canvas;
    private GraphicsContext gc;
    private Color currentColor = Color.BLACK;
    private double currentBrushSize = 5.0;
    private String savedImagePath = null;
    private double zoomLevel = 1.0;
    private Scale zoomScale = new Scale(1, 1, 0, 0);

    private enum Tool { BRUSH, ERASER, BUCKET }
    private Tool currentTool = Tool.BRUSH;
    private StrokeLineCap currentCap = StrokeLineCap.ROUND;

    private Circle brushCursor;
    private SVGPath bucketCursor;

    private static final List<Color> QUICK_COLORS = Arrays.asList(
        Color.BLACK, Color.web("#64748b"), Color.web("#ef4444"), Color.web("#f59e0b"),
        Color.web("#10b981"), Color.web("#3b82f6"), Color.web("#8b5cf6"), Color.web("#ec4899"), Color.WHITE
    );

    private final String initialImagePath;

    public DrawingBoardController(Stage owner, String initialImagePath) {
        this.stage = new Stage();
        this.initialImagePath = initialImagePath;
        this.stage.initModality(Modality.APPLICATION_MODAL); // Changed to APPLICATION_MODAL for better focus stability
        this.stage.initOwner(owner);
        this.stage.setTitle("Tableau de Dessin");

        initUI();
    }

    private SVGPath createIcon(String content, double scale) {
        SVGPath svg = new SVGPath();
        svg.setContent(content);
        svg.setScaleX(scale);
        svg.setScaleY(scale);
        svg.setFill(Color.web("#64748b"));
        svg.setStrokeWidth(0); // Ensure no border on the icon itself
        return svg;
    }

    private void updateButtonStyle(ToggleButton btn, String baseStyle, boolean selected) {
        if (selected) {
            btn.setStyle(baseStyle + "-fx-background-color: #f1f5f9; -fx-text-fill: #3f44d4;");
            if (btn.getGraphic() instanceof SVGPath) {
                ((SVGPath)btn.getGraphic()).setFill(Color.web("#3f44d4"));
            }
        } else {
            btn.setStyle(baseStyle);
            if (btn.getGraphic() instanceof SVGPath) {
                ((SVGPath)btn.getGraphic()).setFill(Color.web("#64748b"));
            }
        }
    }

    private ToggleButton createToolButton(String text, String iconPath, Tool tool, ToggleGroup group) {
        ToggleButton btn = new ToggleButton(text);
        btn.setToggleGroup(group);
        btn.setGraphic(createIcon(iconPath, 0.7));
        btn.setGraphicTextGap(5);
        
        // Reduced padding for more space
        String baseStyle = "-fx-background-color: transparent; -fx-padding: 6 10; -fx-background-radius: 8; -fx-cursor: hand; -fx-font-weight: bold; -fx-text-fill: #64748b; -fx-border-width: 0; -fx-background-insets: 0; -fx-font-size: 11px;";
        btn.setStyle(baseStyle);

        btn.selectedProperty().addListener((obs, oldVal, newVal) -> {
            updateButtonStyle(btn, baseStyle, newVal);
        });

        btn.setOnMouseEntered(e -> {
            if (!btn.isSelected()) {
                btn.setStyle(baseStyle + "-fx-background-color: #f8fafc;");
            }
        });
        btn.setOnMouseExited(e -> {
            updateButtonStyle(btn, baseStyle, btn.isSelected());
        });

        btn.setOnAction(e -> {
            currentTool = tool;
            if (tool == Tool.BUCKET) {
                brushCursor.setVisible(false);
                bucketCursor.setVisible(true);
            } else {
                brushCursor.setVisible(true);
                bucketCursor.setVisible(false);
            }
        });

        return btn;
    }

    private void initUI() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: #f1f5f9;");

        // Top Toolbar
        HBox topBar = new HBox(8); // Reduced spacing
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(6, 10, 6, 10)); // Reduced padding
        topBar.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 10, 0, 0, 2);");

        // Tools Section
        ToggleGroup toolGroup = new ToggleGroup();
        ToggleButton brushBtn = createToolButton("Pinceau", "M7,14.94L13.06,8.88L15.12,10.94L9.06,17H7V14.94M17.66,8.4L15.6,6.34L16.74,5.2C17.13,4.81 17.77,4.81 18.16,5.2L19.8,6.84C20.19,7.23 20.19,7.87 19.8,8.26L18.66,9.4L17.66,8.4M3,17.25V21H6.75L17.81,9.94L14.06,6.19L3,17.25Z", Tool.BRUSH, toolGroup);
        brushBtn.setSelected(true);
        ToggleButton eraserBtn = createToolButton("Gomme", "M16.24,3.56L21.19,8.51C21.97,9.29 21.97,10.55 21.19,11.33L14.6,17.92L12,15.33L16.24,3.56M13.56,18.95L10.17,22.34C9.39,23.12 8.13,23.12 7.35,22.34L2.4,17.39C1.62,16.61 1.62,15.35 2.4,14.57L5.79,11.18L13.56,18.95Z", Tool.ERASER, toolGroup);
        ToggleButton bucketBtn = createToolButton("Pot", "M19,3H5C3.89,3 3,3.9 3,5V19C3,20.1 3.89,21 5,21H19C20.1,21 21,20.1 21,19V5C21,3.9 20.1,3 19,3M19,19H5V5H19V19M17,11H13V7H11V11H7V13H11V17H13V13H17V11Z", Tool.BUCKET, toolGroup);

        Separator s1 = new Separator(javafx.geometry.Orientation.VERTICAL);
        
        // Brush Types
        ComboBox<StrokeLineCap> capCombo = new ComboBox<>();
        capCombo.getItems().addAll(StrokeLineCap.ROUND, StrokeLineCap.SQUARE, StrokeLineCap.BUTT);
        capCombo.setValue(StrokeLineCap.ROUND);
        capCombo.setCellFactory(lv -> new ListCell<StrokeLineCap>() {
            @Override
            protected void updateItem(StrokeLineCap item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.toString());
                    SVGPath icon = new SVGPath();
                    if (item == StrokeLineCap.ROUND) icon.setContent("M12,2A10,10 0 0,0 2,12A10,10 0 0,0 12,22A10,10 0 0,0 22,12A10,10 0 0,0 12,2Z");
                    else if (item == StrokeLineCap.SQUARE) icon.setContent("M3,3H21V21H3V3Z");
                    else icon.setContent("M3,10H21V14H3V10Z");
                    icon.setScaleX(0.5);
                    icon.setScaleY(0.5);
                    icon.setFill(Color.web("#64748b"));
                    setGraphic(icon);
                }
            }
        });
        capCombo.setButtonCell(capCombo.getCellFactory().call(null));
        capCombo.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 1; -fx-font-size: 10px;");
        capCombo.setPrefWidth(90); // Narrower
        capCombo.setOnAction(e -> currentCap = capCombo.getValue());
        
        Label styleLabel = new Label("Style");
        styleLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #94a3b8;");
        VBox capBox = new VBox(1, styleLabel, capCombo);
        capBox.setAlignment(Pos.CENTER_LEFT);

        // Colors Palette
        HBox palette = new HBox(3); // Reduced spacing
        palette.setAlignment(Pos.CENTER_LEFT);
        for (Color c : QUICK_COLORS) {
            StackPane colorBtn = new StackPane();
            Circle colorDot = new Circle(8, c); // Smaller dots
            colorDot.setStroke(Color.web("#e2e8f0"));
            colorDot.setStrokeWidth(1);
            
            colorBtn.getChildren().add(colorDot);
            colorBtn.setCursor(javafx.scene.Cursor.HAND);
            colorBtn.setPadding(new Insets(1));
            colorBtn.setStyle("-fx-background-radius: 50;");
            
            colorBtn.setOnMouseEntered(e -> colorBtn.setStyle("-fx-background-color: #e2e8f0; -fx-background-radius: 50;"));
            colorBtn.setOnMouseExited(e -> colorBtn.setStyle("-fx-background-color: transparent;"));
            
            colorBtn.setOnMouseClicked(e -> {
                currentColor = c;
                palette.getChildren().forEach(n -> n.setScaleX(1.0));
                palette.getChildren().forEach(n -> n.setScaleY(1.0));
                colorBtn.setScaleX(1.2);
                colorBtn.setScaleY(1.2);
            });
            palette.getChildren().add(colorBtn);
        }

        ColorPicker customColor = new ColorPicker(Color.BLACK);
        customColor.getStyleClass().add("button");
        customColor.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 0;");
        customColor.setPrefWidth(25);
        customColor.setOnAction(e -> {
            currentColor = customColor.getValue();
            palette.getChildren().forEach(n -> n.setScaleX(1.0));
            palette.getChildren().forEach(n -> n.setScaleY(1.0));
            stage.requestFocus();
        });

        Label colorLabel = new Label("Couleur");
        colorLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #94a3b8;");
        VBox colorSection = new VBox(1, colorLabel, new HBox(5, palette, customColor));

        Separator s2 = new Separator(javafx.geometry.Orientation.VERTICAL);

        // Size Slider
        Slider sizeSlider = new Slider(1, 80, 5);
        sizeSlider.setPrefWidth(80); // Narrower
        sizeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            currentBrushSize = newVal.doubleValue();
            brushCursor.setRadius(currentBrushSize / 2);
        });
        Label sizeLabel = new Label("Taille");
        sizeLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #94a3b8;");
        VBox sizeBox = new VBox(1, sizeLabel, sizeSlider);

        // Zoom Section
        HBox zoomControls = new HBox(3);
        zoomControls.setAlignment(Pos.CENTER_LEFT);
        
        Button zoomOutBtn = new Button("-");
        zoomOutBtn.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 4; -fx-cursor: hand; -fx-font-size: 9px; -fx-padding: 1 5;");
        
        Button zoomInBtn = new Button("+");
        zoomInBtn.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 4; -fx-cursor: hand; -fx-font-size: 9px; -fx-padding: 1 5;");

        Slider zoomSlider = new Slider(0.5, 3.0, 1.0);
        zoomSlider.setPrefWidth(50); // Narrower
        
        zoomOutBtn.setOnAction(e -> zoomSlider.setValue(zoomSlider.getValue() - 0.2));
        zoomInBtn.setOnAction(e -> zoomSlider.setValue(zoomSlider.getValue() + 0.2));
        
        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            zoomLevel = newVal.doubleValue();
            zoomScale.setX(zoomLevel);
            zoomScale.setY(zoomLevel);
        });
        
        zoomControls.getChildren().addAll(zoomOutBtn, zoomSlider, zoomInBtn);
        Label zoomLabel = new Label("Zoom");
        zoomLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #94a3b8;");
        VBox zoomBox = new VBox(1, zoomLabel, zoomControls);

        Button clearBtn = new Button("Vider");
        clearBtn.setGraphic(createIcon("M19,4H15.5L14.5,3H9.5L8.5,4H5V6H19V4M6,19A2,2 0 0,0 8,21H16A2,2 0 0,0 18,19V7H6V19Z", 0.6));
        clearBtn.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #ef4444; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 5 10; -fx-border-width: 0; -fx-font-size: 10px;");
        clearBtn.setOnAction(e -> {
            gc.setFill(Color.WHITE);
            gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        });

        topBar.getChildren().addAll(brushBtn, eraserBtn, bucketBtn, s1, capBox, s2, colorSection, sizeBox, zoomBox, new Region(), clearBtn);
        HBox.setHgrow(topBar.getChildren().get(topBar.getChildren().size()-2), Priority.ALWAYS);

        // Drawing Area
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setStyle("-fx-background: #cbd5e1; -fx-background-color: #cbd5e1; -fx-border-color: #e2e8f0;");
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);

        canvas = new Canvas(2000, 1600); // Increased canvas size for more space
        gc = canvas.getGraphicsContext2D();
        
        // Initial state: white background
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Load existing drawing if provided
        if (initialImagePath != null) {
            try {
                File file = new File(initialImagePath);
                if (file.exists()) {
                    javafx.scene.image.Image image = new javafx.scene.image.Image(file.toURI().toString());
                    // Draw the image onto the canvas. 
                    // We keep it at its original size or center it if it's smaller.
                    // For simplicity, we draw it at (0,0)
                    gc.drawImage(image, 0, 0);
                }
            } catch (Exception e) {
                System.err.println("Could not load initial drawing: " + e.getMessage());
            }
        }
        
        // Brush Cursor
        brushCursor = new Circle(currentBrushSize / 2);
        brushCursor.setFill(Color.TRANSPARENT);
        brushCursor.setStroke(Color.GRAY);
        brushCursor.setStrokeWidth(1);
        brushCursor.setMouseTransparent(true);
        
        // Bucket cursor - set a fixed visual size that we'll adjust in updateCursorPosition
        bucketCursor = createIcon("M19,3H5C3.89,3 3,3.9 3,5V19C3,20.1 3.89,21 5,21H19C20.1,21 21,20.1 21,19V5C21,3.9 20.1,3 19,3M19,19H5V5H19V19M17,11H13V7H11V11H7V13H11V17H13V13H17V11Z", 1.0);
        bucketCursor.setMouseTransparent(true);
        bucketCursor.setVisible(false);

        // Create a Group that scales everything together
        javafx.scene.Group drawingGroup = new javafx.scene.Group(canvas, brushCursor, bucketCursor);
        drawingGroup.getTransforms().add(zoomScale);

        StackPane canvasContainer = new StackPane(drawingGroup);
        canvasContainer.setStyle("-fx-background-color: #cbd5e1;"); // Match scrollpane background
        canvasContainer.setPadding(new Insets(50)); // More breathing room around canvas

        canvas.setOnMouseMoved(e -> {
            updateCursorPosition(e.getX(), e.getY());
        });

        canvas.setOnMouseEntered(e -> {
            if (currentTool == Tool.BUCKET) bucketCursor.setVisible(true);
            else brushCursor.setVisible(true);
        });

        canvas.setOnMouseExited(e -> {
            brushCursor.setVisible(false);
            bucketCursor.setVisible(false);
        });

        canvas.setOnMousePressed(e -> {
            if (currentTool == Tool.BUCKET) {
                floodFill(e.getX(), e.getY(), currentColor);
            } else {
                gc.setStroke(currentTool == Tool.ERASER ? Color.WHITE : currentColor);
                gc.setLineWidth(currentBrushSize);
                gc.setLineCap(currentCap);
                gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
                gc.beginPath();
                gc.moveTo(e.getX(), e.getY());
                gc.stroke();
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (currentTool != Tool.BUCKET) {
                gc.lineTo(e.getX(), e.getY());
                gc.stroke();
            }
            updateCursorPosition(e.getX(), e.getY());
        });

        scrollPane.setContent(canvasContainer);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Footer
        HBox footer = new HBox(15);
        footer.setAlignment(Pos.CENTER_RIGHT);
        
        Button cancelBtn = new Button("Annuler");
        cancelBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748b; -fx-font-weight: bold; -fx-cursor: hand;");
        cancelBtn.setOnAction(e -> stage.close());

        Button saveBtn = new Button("Valider le dessin");
        saveBtn.setStyle("-fx-background-color: #3f44d4; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-background-radius: 10; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(63, 68, 212, 0.3), 10, 0, 0, 4);");
        saveBtn.setOnAction(e -> saveDrawing());

        footer.getChildren().addAll(cancelBtn, saveBtn);

        root.getChildren().addAll(topBar, scrollPane, footer);

        Scene scene = new Scene(root, 1150, 800);
        stage.setScene(scene);
    }

    private void updateCursorPosition(double x, double y) {
        // Since cursors are in the same scaled group as the canvas, 
        // we use raw coordinates for the base position.
        // We divide the offset by zoomLevel so the visual distance 
        // from the mouse tip stays constant on screen.
        if (currentTool == Tool.BUCKET) {
            double offset = 12 / zoomLevel;
            bucketCursor.setTranslateX(x + offset);
            bucketCursor.setTranslateY(y - offset);
            // Also keep the bucket icon size consistent on screen
            bucketCursor.setScaleX(1.0 / zoomLevel);
            bucketCursor.setScaleY(1.0 / zoomLevel);
        } else {
            brushCursor.setTranslateX(x);
            brushCursor.setTranslateY(y);
            // Brush cursor radius already scales with the group, 
            // which is correct as it shows the actual area being painted.
        }
    }

    private void floodFill(double startX, double startY, Color fillBoxColor) {
        WritableImage image = canvas.snapshot(null, null);
        PixelReader reader = image.getPixelReader();
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        
        int x = (int) startX;
        int y = (int) startY;
        
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        
        Color targetColor = reader.getColor(x, y);
        if (targetColor.equals(fillBoxColor)) return;

        PixelWriter writer = gc.getPixelWriter();
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{x, y});

        boolean[][] visited = new boolean[width][height];

        while (!queue.isEmpty()) {
            int[] point = queue.poll();
            int px = point[0];
            int py = point[1];

            if (px < 0 || px >= width || py < 0 || py >= height || visited[px][py]) continue;
            
            Color currentColor = reader.getColor(px, py);
            if (isSameColor(currentColor, targetColor)) {
                writer.setColor(px, py, fillBoxColor);
                visited[px][py] = true;
                
                queue.add(new int[]{px + 1, py});
                queue.add(new int[]{px - 1, py});
                queue.add(new int[]{px, py + 1});
                queue.add(new int[]{px, py - 1});
            }
        }
    }

    private boolean isSameColor(Color c1, Color c2) {
        double threshold = 0.05;
        return Math.abs(c1.getRed() - c2.getRed()) < threshold &&
               Math.abs(c1.getGreen() - c2.getGreen()) < threshold &&
               Math.abs(c1.getBlue() - c2.getBlue()) < threshold &&
               Math.abs(c1.getOpacity() - c2.getOpacity()) < threshold;
    }

    private void saveDrawing() {
        try {
            int width = (int) canvas.getWidth();
            int height = (int) canvas.getHeight();
            
            BufferedImage bImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2d = bImage.createGraphics();
            g2d.setColor(java.awt.Color.WHITE);
            g2d.fillRect(0, 0, width, height);
            
            BufferedImage canvasImage = SwingFXUtils.fromFXImage(canvas.snapshot(null, null), null);
            g2d.drawImage(canvasImage, 0, 0, null);
            g2d.dispose();

            File tempFile = File.createTempFile("drawing_" + UUID.randomUUID(), ".png");
            ImageIO.write(bImage, "png", tempFile);
            
            this.savedImagePath = tempFile.getAbsolutePath();
            stage.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String showAndWait() {
        stage.showAndWait();
        return savedImagePath;
    }
}
