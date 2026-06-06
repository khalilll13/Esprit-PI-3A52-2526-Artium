package utils;

import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.Random;

public class ArtisticBackground {
    private static final Random random = new Random();
    
    // Palette de couleurs inspirée de l'image (Touches de coloration abstraites)
    private static final Color[] COLORS = {
            Color.web("#273c95", 0.12), // Bleu Royal logo
            Color.web("#5164d9", 0.12), // Bleu Vibrant
            Color.web("#38bdf8", 0.12), // Cyan
            Color.web("#D946EF", 0.12), // Fuchsia / Magenta
            Color.web("#F59E0B", 0.12), // Ambre / Jaune peinture
            Color.web("#10B981", 0.12)  // Vert émeraude peinture
    };

    /**
     * Attache un fond animé avec des bulles/taches de couleurs flottantes.
     * @param root Le panneau racine (idéalement un StackPane).
     * @param elementCount Le nombre de taches de peinture/bulles.
     */
    public static void attach(Pane root, int elementCount) {
        Pane backgroundPane = new Pane();
        backgroundPane.setMouseTransparent(true); // Empêche le fond de bloquer les clics
        
        // S'assurer que le pane prend toute la taille disponible
        backgroundPane.prefWidthProperty().bind(root.widthProperty());
        backgroundPane.prefHeightProperty().bind(root.heightProperty());

        for (int i = 0; i < elementCount; i++) {
            // Créer une "tache" ou "bulle" de peinture (Cercles de différentes tailles)
            Circle drop = new Circle(random.nextInt(40, 180));
            drop.setFill(COLORS[random.nextInt(COLORS.length)]);
            
            // Position de départ aléatoire (sur une zone large pour couvrir l'écran)
            drop.setTranslateX(random.nextDouble() * 1400); 
            drop.setTranslateY(random.nextDouble() * 900);
            
            backgroundPane.getChildren().add(drop);
            
            animateDrop(drop);
        }
        
        // Ajouter en arrière-plan (index 0)
        if (!root.getChildren().isEmpty()) {
            root.getChildren().add(0, backgroundPane);
        } else {
            root.getChildren().add(backgroundPane);
        }
    }

    private static void animateDrop(Circle drop) {
        // Animation de flottement lent (Déplacement)
        TranslateTransition tt = new TranslateTransition(Duration.seconds(random.nextInt(15, 35)), drop);
        tt.setByX(random.nextInt(-150, 150));
        tt.setByY(random.nextInt(-150, 150));
        tt.setAutoReverse(true);
        tt.setCycleCount(TranslateTransition.INDEFINITE);

        // Animation de respiration (Agrandissement/Rétrécissement)
        ScaleTransition st = new ScaleTransition(Duration.seconds(random.nextInt(12, 25)), drop);
        st.setToX(random.nextDouble() * 0.4 + 1.1); // Scale entre 1.1 et 1.5
        st.setToY(random.nextDouble() * 0.4 + 1.1);
        st.setAutoReverse(true);
        st.setCycleCount(ScaleTransition.INDEFINITE);

        ParallelTransition pt = new ParallelTransition(tt, st);
        pt.play();
    }
}
