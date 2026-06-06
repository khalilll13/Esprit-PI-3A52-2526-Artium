package controllers.amateur;

import entities.Ticket;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import services.EvenementService;
import services.TicketPdfService;
import entities.Evenement;

import java.io.File;
import java.sql.SQLDataException;
import java.time.format.DateTimeFormatter;
import java.awt.image.BufferedImage;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;

public class PaymentController {

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy");

	@FXML
	private Label ticketReferenceLabel;

	@FXML
	private Label eventIdLabel;

	@FXML
	private Label userIdLabel;

	@FXML
	private Label purchaseDateLabel;

	@FXML
	private Label qrPayloadLabel;

	private final TicketPdfService ticketPdfService = new TicketPdfService();
	private final EvenementService evenementService = new EvenementService();
	private Ticket ticket;
	private Runnable backToEventHandler;
	private Runnable backToEventsHandler;

	public void setTicket(Ticket ticket) {
		this.ticket = ticket;
		if (ticket == null) {
			clearView();
			return;
		}

		ticketReferenceLabel.setText(resolveReference(ticket));
		purchaseDateLabel.setText(ticket.getDateAchat() == null ? "-" : DATE_FORMATTER.format(ticket.getDateAchat()));

		try {
			Evenement evenement = evenementService.getById(ticket.getEvenementId());
			if (evenement != null) {
				eventIdLabel.setText(evenement.getTitre() != null ? evenement.getTitre() : "Evénement inconnu");
				qrPayloadLabel.setText(evenement.getPrixTicket() != null ? String.format(java.util.Locale.ROOT, "%.2f TND", evenement.getPrixTicket()) : "Gratuit");
			} else {
				eventIdLabel.setText(formatInteger(ticket.getEvenementId()));
				qrPayloadLabel.setText("-");
			}
		} catch (Exception e) {
			eventIdLabel.setText(formatInteger(ticket.getEvenementId()));
			qrPayloadLabel.setText("-");
		}

		entities.User user = controllers.MainFX.getAuthenticatedUser();
		if (user != null) {
			String prenom = user.getPrenom() != null ? user.getPrenom() : "";
			String nom = user.getNom() != null ? user.getNom() : "";
			userIdLabel.setText((prenom + " " + nom).trim());
		} else {
			userIdLabel.setText(formatInteger(ticket.getUserId()));
		}
	}

	public void setBackToEventHandler(Runnable backToEventHandler) {
		this.backToEventHandler = backToEventHandler;
	}

	public void setBackToEventsHandler(Runnable backToEventsHandler) {
		this.backToEventsHandler = backToEventsHandler;
	}

	@FXML
	private void onShowQrCodeClick() {
		if (ticket == null) {
			return;
		}

		try {
			BufferedImage qrImage = ticketPdfService.createQrImage(resolveQrPayload(ticket));
			if (qrImage != null) {
				Image fxImage = SwingFXUtils.toFXImage(qrImage, null);
				ImageView imageView = new ImageView(fxImage);
				imageView.setFitWidth(250);
				imageView.setFitHeight(250);
				imageView.setPreserveRatio(true);

				StackPane pane = new StackPane(imageView);
				pane.setStyle("-fx-padding: 20px; -fx-background-color: white;");

				Dialog<Void> dialog = new Dialog<>();
				dialog.setTitle("QR Code du Ticket");
				dialog.setHeaderText("Scannez ce QR Code à l'entrée");
				dialog.getDialogPane().setContent(pane);
				dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
				dialog.showAndWait();
			} else {
				throw new Exception("L'image QR n'a pas pu être générée.");
			}
		} catch (Exception e) {
			Alert alert = new Alert(Alert.AlertType.ERROR);
			alert.setTitle("Erreur");
			alert.setHeaderText("Impossible d'afficher le QR Code");
			alert.setContentText(e.getMessage());
			alert.showAndWait();
		}
	}

	@FXML
	private void onShowTicketClick() {
		if (ticket == null) {
			return;
		}

		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.setTitle("Aperçu du ticket PDF");
		alert.setHeaderText("Le ticket sera ouvert en PDF");
		alert.setContentText("Un document PDF professionnel va être généré puis ouvert dans votre lecteur PDF par défaut.");
		alert.showAndWait();

		try {
			Evenement evenement = evenementService.getById(ticket.getEvenementId());
			File preview = ticketPdfService.createPreviewPdf(evenement, ticket);
			ticketPdfService.openPdf(preview);
		} catch (SQLDataException e) {
			Alert error = new Alert(Alert.AlertType.ERROR);
			error.setTitle("Aperçu impossible");
			error.setHeaderText("Impossible de charger l'évènement");
			error.setContentText(e.getMessage());
			error.showAndWait();
		} catch (Exception e) {
			Alert error = new Alert(Alert.AlertType.ERROR);
			error.setTitle("Aperçu PDF indisponible");
			error.setHeaderText("Le PDF n'a pas pu être ouvert");
			error.setContentText(e.getMessage());
			error.showAndWait();
		}
	}

	@FXML
	private void onDownloadTicketPdfClick() {
		if (ticket == null) {
			return;
		}

		try {
			Evenement evenement = evenementService.getById(ticket.getEvenementId());
			FileChooser chooser = new FileChooser();
			chooser.setTitle("Télécharger le ticket PDF");
			chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
			chooser.setInitialFileName(ticketPdfService.buildSuggestedFileName(evenement, ticket));

			Window owner = qrPayloadLabel == null ? null : qrPayloadLabel.getScene().getWindow();
			File target = chooser.showSaveDialog(owner);
			if (target == null) {
				return;
			}

			File pdf = ticketPdfService.exportTicketPdf(evenement, ticket, ensurePdfExtension(target));
			Alert info = new Alert(Alert.AlertType.INFORMATION);
			info.setTitle("Téléchargement réussi");
			info.setHeaderText("Votre ticket PDF est prêt");
			info.setContentText("Le fichier a été enregistré dans:\n" + pdf.getAbsolutePath());
			info.showAndWait();
		} catch (SQLDataException e) {
			Alert error = new Alert(Alert.AlertType.ERROR);
			error.setTitle("Téléchargement impossible");
			error.setHeaderText("Impossible de charger l'évènement");
			error.setContentText(e.getMessage());
			error.showAndWait();
		} catch (Exception e) {
			Alert error = new Alert(Alert.AlertType.ERROR);
			error.setTitle("Téléchargement impossible");
			error.setHeaderText("Le PDF n'a pas pu être créé");
			error.setContentText(e.getMessage());
			error.showAndWait();
		}
	}

	private File ensurePdfExtension(File target) {
		String lowerName = target.getName().toLowerCase();
		if (lowerName.endsWith(".pdf")) {
			return target;
		}
		File parent = target.getParentFile();
		String fileName = target.getName() + ".pdf";
		return parent == null ? new File(target.getAbsolutePath() + ".pdf") : new File(parent, fileName);
	}

	@FXML
	private void onBackToEventClick() {
		if (backToEventHandler != null) {
			backToEventHandler.run();
		}
	}

	@FXML
	private void onBackToEventsClick() {
		if (backToEventsHandler != null) {
			backToEventsHandler.run();
		}
	}

	private void clearView() {
		ticketReferenceLabel.setText("-");
		eventIdLabel.setText("-");
		userIdLabel.setText("-");
		purchaseDateLabel.setText("-");
		qrPayloadLabel.setText("Aucun ticket disponible");
	}

	private String resolveReference(Ticket ticket) {
		if (ticket.getCodeQr() != null && !ticket.getCodeQr().isBlank()) {
			String payload = ticket.getCodeQr();
			int refIndex = payload.indexOf("|ref=");
			if (refIndex >= 0 && refIndex + 5 < payload.length()) {
				return payload.substring(refIndex + 5);
			}
			return payload;
		}
		return ticket.getEvenementId() + "-" + ticket.getUserId();
	}

	private String resolveQrPayload(Ticket ticket) {
		if (ticket.getCodeQr() == null || ticket.getCodeQr().isBlank()) {
			return "QR non disponible";
		}
		return ticket.getCodeQr();
	}

	private String formatInteger(Integer value) {
		return value == null ? "-" : String.valueOf(value);
	}
}

