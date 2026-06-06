package controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import utils.ArtisticBackground;
import utils.CardAnimator;

public class AuthLandingController {

	@FXML private StackPane landingShell;
	@FXML private HBox      landingCard;
	@FXML private HBox      topbar;
	@FXML private VBox      landingHero;
	@FXML private VBox      landingPanel;

	@FXML
	public void initialize() {
        if (landingShell != null) {
            ArtisticBackground.attach(landingShell, 20); // 20 touches pour l'accueil
        }
        
		// Attendre que la vue soit bien attachée à la SceneGraph
		Platform.runLater(() -> {
			// Topbar (petite entrée par le haut)
			if (topbar != null) {
				topbar.setOpacity(0);
				topbar.setTranslateY(-14);
				// On réutilise animateFadeSlideUp, puis on restaure une entrée "vers le bas"
				// en inversant la position initiale.
				CardAnimator.animateFadeSlideUp(topbar, 20);
			}

			// Animation globale
			if (landingShell != null) CardAnimator.animatePageTransition(landingShell);

			// Animation du contenu, en deux temps (gauche puis droite)
			if (landingHero != null)  CardAnimator.animateFadeSlideUp(landingHero, 120);
			if (landingPanel != null) CardAnimator.animateFadeSlideUp(landingPanel, 200);
		});
	}

	@FXML
	private void onCreateAccount() {
		MainFX.switchToRegistrationView();
	}

	@FXML
	private void onLoginClick() {
		MainFX.switchToLoginView();
	}
}

