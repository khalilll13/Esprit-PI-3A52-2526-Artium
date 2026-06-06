package controllers.artist;

import entities.Evenement;
import entities.Ticket;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import services.EvenementService;
import services.NotificationService;
import services.TicketService;
import utils.SqlErrorMessageTranslator;
import utils.UserSession;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLDataException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;

public class EvenementsArtisteController {

	private Integer currentArtistId;

	@FXML
	private TextField rechercheField;

	@FXML
	private ComboBox<String> triComboBox;

	@FXML
	private VBox eventsListContainer;

	@FXML
	private Label emptyStateLabel;

	@FXML
	private Button addEvenementButton;

	private final EvenementService evenementService = new EvenementService();
	private final TicketService ticketService = new TicketService();
	private final NotificationService notificationService = new NotificationService();
	private final List<Evenement> allEvenements = new ArrayList<>();

	@FXML
	public void initialize() {
		currentArtistId = UserSession.getCurrentUserId();
		if (currentArtistId == null) {
			handleMissingSession();
			return;
		}

		triComboBox.getItems().addAll(
				"Date (plus recente)",
				"Date (plus ancienne)",
				"Titre (A-Z)",
				"Titre (Z-A)",
				"Type (A-Z)"
		);
		triComboBox.setValue("Date (plus recente)");

		rechercheField.textProperty().addListener((observable, oldValue, newValue) -> applySearchAndSort());
		triComboBox.valueProperty().addListener((observable, oldValue, newValue) -> applySearchAndSort());

		refreshEvenements();
	}

	private void handleMissingSession() {
		eventsListContainer.getChildren().clear();
		emptyStateLabel.setText("Session utilisateur introuvable. Veuillez vous reconnecter.");
		emptyStateLabel.setVisible(true);
		emptyStateLabel.setManaged(true);
		if (addEvenementButton != null) {
			addEvenementButton.setDisable(true);
		}
	}

	@FXML
	private void onAddEvenementClick() {
		openFormDialog(null);
	}

	private void refreshEvenements() {
		try {
			allEvenements.clear();
			allEvenements.addAll(evenementService.getByArtisteId(currentArtistId));
			applySearchAndSort();
		} catch (SQLDataException e) {
			showError("Chargement impossible", e.getMessage());
		}
	}

	private void applySearchAndSort() {
		String search = rechercheField.getText() == null ? "" : rechercheField.getText().trim().toLowerCase();

		List<Evenement> filtered = allEvenements.stream()
				.filter(event -> matchesSearch(event, search))
				.sorted(buildComparator(triComboBox.getValue()))
				.collect(Collectors.toList());

		renderCards(filtered);
	}

	private boolean matchesSearch(Evenement event, String search) {
		if (search.isEmpty()) {
			return true;
		}

		return contains(event.getTitre(), search)
				|| contains(event.getType(), search)
				|| contains(event.getDescription(), search)
				|| contains(event.getStatut(), search);
	}

	private Comparator<Evenement> buildComparator(String selectedSort) {
		if (selectedSort == null) {
			return Comparator.comparing(EvenementsArtisteController::safeDateTime, Comparator.reverseOrder());
		}

		return switch (selectedSort) {
			case "Date (plus ancienne)" -> Comparator.comparing(EvenementsArtisteController::safeDateTime);
			case "Titre (A-Z)" -> Comparator.comparing(event -> safe(event.getTitre()));
			case "Titre (Z-A)" -> Comparator.comparing((Evenement event) -> safe(event.getTitre())).reversed();
			case "Type (A-Z)" -> Comparator.comparing(event -> safe(event.getType()));
			case "Date (plus recente)" -> Comparator.comparing(EvenementsArtisteController::safeDateTime, Comparator.reverseOrder());
			default -> Comparator.comparing(EvenementsArtisteController::safeDateTime, Comparator.reverseOrder());
		};
	}

	private void renderCards(List<Evenement> events) {
		eventsListContainer.getChildren().clear();

		for (Evenement event : events) {
			try {
				FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/views/artist/components/evenement-artiste-card.fxml")));
				Parent card = loader.load();
				EvenementArtisteCardController cardController = loader.getController();
				cardController.setData(event, new EvenementArtisteCardController.CardActionHandler() {
					@Override
					public void onEdit(Evenement evenement) {
						handleEdit(evenement);
					}

					@Override
					public void onDelete(Evenement evenement) {
						handleDelete(evenement);
					}

					@Override
					public void onCancel(Evenement evenement) {
						handleCancel(evenement);
					}
				});
				eventsListContainer.getChildren().add(card);
			} catch (IOException e) {
				showError("Affichage impossible", "Erreur pendant le rendu d'un evenement.");
				return;
			}
		}

		boolean empty = events.isEmpty();
		emptyStateLabel.setVisible(empty);
		emptyStateLabel.setManaged(empty);
	}

	private void handleEdit(Evenement evenementToEdit) {
		openFormDialog(evenementToEdit);
	}

	private void handleCancel(Evenement evenementToCancel) {
		if (!canCancelEvent(evenementToCancel)) {
			showError("Annulation impossible", "Un evenement ne peut etre annule que 10 heures au moins avant son heure de debut.");
			return;
		}

		ButtonType confirmButton = new ButtonType("Annuler l'evenement", ButtonBar.ButtonData.OK_DONE);
		ButtonType backButton = new ButtonType("Retour", ButtonBar.ButtonData.CANCEL_CLOSE);

		Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION, "", confirmButton, backButton);
		confirmAlert.setTitle("Confirmer l'annulation");
		confirmAlert.setHeaderText("Annuler l'evenement: " + safe(evenementToCancel.getTitre()) + " ?");
		confirmAlert.setContentText("Les tickets achetes seront rembourses et les amateurs seront notifies.");

		applyAlertTheme(confirmAlert);
		styleAlertButton(confirmAlert, backButton, "artist-secondary-button");
		styleAlertButton(confirmAlert, confirmButton, "artist-danger-button");

		Optional<ButtonType> result = confirmAlert.showAndWait();
		if (result.isEmpty() || result.get() != confirmButton) {
			return;
		}

		try {
			Evenement evenementPersisted = evenementService.getById(evenementToCancel.getId());
			Evenement target = evenementPersisted == null ? evenementToCancel : evenementPersisted;
			target.setStatut("Annulé");
			evenementService.updateForArtiste(target, currentArtistId);

			List<Ticket> tickets = ticketService.getTicketsByEvent(target.getId());
			Map<Integer, Long> ticketsPerUser = tickets.stream()
					.filter(ticket -> ticket.getUserId() != null)
					.collect(Collectors.groupingBy(Ticket::getUserId, LinkedHashMap::new, Collectors.counting()));

			int refundedTickets = ticketService.deleteTicketsByEvent(target.getId());
			ticketsPerUser.forEach((userId, count) ->
					notificationService.sendCancellationNotice(userId, target, count.intValue()));

			refreshEvenements();
			showInfo("L'evenement a ete annule. " + refundedTickets + " ticket(s) ont ete rembourses.");
		} catch (SQLDataException e) {
			showError("Annulation impossible", e.getMessage());
		}
	}

	private void handleDelete(Evenement evenementToDelete) {
		ButtonType deleteButton = new ButtonType("Supprimer", ButtonBar.ButtonData.OK_DONE);
		ButtonType backButton = new ButtonType("Retour", ButtonBar.ButtonData.CANCEL_CLOSE);

		Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION, "", deleteButton, backButton);
		confirmAlert.setTitle("Confirmer la suppression");
		confirmAlert.setHeaderText("Supprimer l'evenement: " + safe(evenementToDelete.getTitre()) + " ?");
		confirmAlert.setContentText("Cette action est irreversible.");

		applyAlertTheme(confirmAlert);
		styleAlertButton(confirmAlert, backButton, "artist-secondary-button");
		styleAlertButton(confirmAlert, deleteButton, "artist-danger-button");

		Optional<ButtonType> result = confirmAlert.showAndWait();
		if (result.isEmpty() || result.get() != deleteButton) {
			return;
		}

		try {
			evenementService.deleteForArtiste(evenementToDelete, currentArtistId);
			refreshEvenements();
		} catch (SQLDataException e) {
			showError("Suppression impossible", e.getMessage());
		}
	}

	private boolean canCancelEvent(Evenement evenement) {
		if (evenement == null || evenement.getDateDebut() == null) {
			return false;
		}

		Duration untilStart = Duration.between(LocalDateTime.now(), evenement.getDateDebut());
		return !untilStart.isNegative() && untilStart.compareTo(Duration.ofHours(10)) >= 0;
	}

	private void openFormDialog(Evenement evenementToEdit) {
		try {
			FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/views/artist/EvenementForm.fxml")));
			Parent formRoot = loader.load();
			EvenementArtisteFormController controller = loader.getController();

			Stage stage = new Stage();
			stage.initModality(Modality.APPLICATION_MODAL);
			if (addEvenementButton != null && addEvenementButton.getScene() != null) {
				stage.initOwner(addEvenementButton.getScene().getWindow());
			}
			stage.setTitle(evenementToEdit == null ? "Ajouter un evenement" : "Modifier un evenement");

			Scene scene = new Scene(formRoot);
			URL stylesheet = getClass().getResource("/views/styles/artist-theme.css");
			if (stylesheet != null) {
				scene.getStylesheets().add(stylesheet.toExternalForm());
			}
			stage.setScene(scene);

			controller.setDialogStage(stage);
			controller.setEvenement(evenementToEdit);
			controller.setSaveHandler(event -> persistFromPopup(controller, event, evenementToEdit != null));

			stage.showAndWait();
			if (controller.getResultEvenement() != null) {
				refreshEvenements();
			}
		} catch (IOException e) {
			showError("Ouverture impossible", "Le formulaire evenement artiste est introuvable.");
		}
	}

	private Boolean persistFromPopup(EvenementArtisteFormController formController, Evenement savedEvent, boolean isEdit) {
		try {
			if (!isEdit) {
				evenementService.addForArtiste(savedEvent, currentArtistId);
			} else {
				evenementService.updateForArtiste(savedEvent, currentArtistId);
			}
			return true;
		} catch (SQLDataException e) {
			String errorMessage = SqlErrorMessageTranslator.translate(e.getMessage());
			formController.setExternalValidationError(errorMessage);
			return false;
		}
	}

	private void showError(String title, String message) {
		Alert alert = new Alert(Alert.AlertType.ERROR);
		alert.setTitle("Erreur");
		alert.setHeaderText(title);
		alert.setContentText(message);
		applyAlertTheme(alert);
		alert.showAndWait();
	}

	private void showInfo(String message) {
		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.setTitle("Information");
		alert.setHeaderText(null);
		alert.setContentText(message);
		applyAlertTheme(alert);
		alert.showAndWait();
	}

	private void applyAlertTheme(Alert alert) {
		URL stylesheet = getClass().getResource("/views/styles/artist-theme.css");
		if (stylesheet != null) {
			alert.getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
		}
		alert.getDialogPane().getStyleClass().add("artist-alert");
	}

	private void styleAlertButton(Alert alert, ButtonType buttonType, String styleClass) {
		Node node = alert.getDialogPane().lookupButton(buttonType);
		if (node instanceof Button button) {
			button.getStyleClass().add(styleClass);
			button.setMinWidth(110);
		}
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}

	private boolean contains(String value, String search) {
		return value != null && value.toLowerCase().contains(search);
	}

	private static LocalDateTime safeDateTime(Evenement event) {
		return event.getDateDebut() == null ? LocalDateTime.MIN : event.getDateDebut();
	}
}

