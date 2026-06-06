package controllers.amateur;

import controllers.MainFX;
import entities.Evenement;
import entities.Galerie;
import entities.Ticket;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import services.GalerieService;
import services.TicketPdfService;
import services.TicketService;
import services.WeatherService;
import utils.CoordinateUtils;
import javafx.application.Platform;
import java.util.concurrent.CompletableFuture;

import java.io.File;
import java.sql.SQLDataException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class EventDetailController {

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm");
	private static final DateTimeFormatter PURCHASE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");
	private static final int COLLAPSED_TICKET_LIMIT = 3;

	@FXML
	private ImageView coverImageView;

	@FXML
	private Label titleLabel;

	@FXML
	private Label typeLabel;

	@FXML
	private Label statusLabel;

	@FXML
	private Label dateDebutLabel;

	@FXML
	private Label dateFinLabel;

	@FXML
	private Label priceLabel;

	@FXML
	private Label capacityLabel;

	@FXML
	private Label galleryLabel;

	@FXML
	private Label descriptionLabel;

	@FXML
	private Label metaLabel;

	@FXML
	private Label galleryNameLabel;

	@FXML
	private Label galleryAddressLabel;

	@FXML
	private Label galleryCoordinatesLabel;

	@FXML
	private Label galleryCapacityLabel;

	@FXML
	private WebView mapWebView;

	@FXML
	private Pane mapContainer;

	@FXML
	private Button buyTicketButton;

	@FXML
	private Button backButton;

	@FXML
	private Button ticketsToggleButton;

	@FXML
	private VBox ticketsContainer;

	@FXML
	private Label ticketsEmptyLabel;

	@FXML
	private Label ticketsCountLabel;

	@FXML
	private HBox weatherContainer;

	@FXML
	private Label weatherTempLabel;

	@FXML
	private Label weatherWindLabel;

	@FXML
	private Label weatherRainLabel;

	private final TicketService ticketService = new TicketService();
	private final TicketPdfService ticketPdfService = new TicketPdfService();
	private final GalerieService galerieService = new GalerieService();
	private final WeatherService weatherService = new WeatherService();
	private Evenement event;
	private Galerie currentGalerie;
	private List<Ticket> currentPurchasedTickets = List.of();
	private boolean ticketsExpanded;
	private Consumer<Ticket> purchaseHandler;
	private Consumer<Evenement> payHandler;
	private Runnable backHandler;

	@FXML
	private void initialize() {
		if (mapWebView != null) {
			mapWebView.setContextMenuEnabled(false);
			if (mapContainer != null) {
				mapWebView.prefWidthProperty().bind(mapContainer.widthProperty());
				mapWebView.prefHeightProperty().bind(mapContainer.heightProperty());
				mapWebView.maxWidthProperty().bind(mapContainer.widthProperty());
				
				mapContainer.widthProperty().addListener((obs, oldVal, newVal) -> forceMapResize());
				mapContainer.heightProperty().addListener((obs, oldVal, newVal) -> forceMapResize());
			}
			renderMapPlaceholder();
		}
		updateTicketsToggleButton(false);
	}

	public void setEvent(Evenement event) {
		this.event = event;
		this.currentGalerie = null;
		this.currentPurchasedTickets = List.of();
		ticketsExpanded = false;

		if (event == null) {
			buyTicketButton.setDisable(true);
			buyTicketButton.setText("Acheter ticket");
			titleLabel.setText("Evenement");
			typeLabel.setText("Type non precise");
			statusLabel.setText("A venir");
			dateDebutLabel.setText("-");
			dateFinLabel.setText("-");
			priceLabel.setText("-");
			capacityLabel.setText("-");
			galleryLabel.setText("Galerie");
			descriptionLabel.setText("Aucune description disponible.");
			metaLabel.setText("Date de creation: -");
			galleryNameLabel.setText("Galerie");
			galleryAddressLabel.setText("-");
			galleryCoordinatesLabel.setText("-");
			galleryCapacityLabel.setText("-");
			renderPurchasedTickets(List.of());
			renderMapPlaceholder();
			if (backButton != null) {
				backButton.setDisable(false);
			}
			return;
		}

		boolean purchasable = event.getStatut() == null || !"Annulé".equalsIgnoreCase(event.getStatut().trim());
		Integer currentUserId = resolveCurrentUserId();
		if (currentUserId == null) {
			buyTicketButton.setDisable(true);
			buyTicketButton.setText("Connexion requise");
		} else {
			if (purchasable && event.getDateDebut() != null) {
				purchasable = LocalDateTime.now().isBefore(event.getDateDebut());
			}
			buyTicketButton.setDisable(!purchasable);
			buyTicketButton.setText(purchasable ? "Acheter ticket" : "Ticket indisponible");
		}
		if (backButton != null) {
			backButton.setDisable(false);
		}

		titleLabel.setText(textOrDefault(event.getTitre(), "Evenement"));
		typeLabel.setText(textOrDefault(event.getType(), "Type non precise"));
		statusLabel.setText(textOrDefault(event.getStatut(), "A venir"));
		dateDebutLabel.setText(formatDate(event.getDateDebut()));
		dateFinLabel.setText(formatDate(event.getDateFin()));
		priceLabel.setText(formatPrice(event.getPrixTicket()));
		capacityLabel.setText(formatCapacity(event.getCapaciteMax()));
		descriptionLabel.setText(textOrDefault(event.getDescription(), "Aucune description disponible."));
		metaLabel.setText("Date de creation: " + (event.getDateCreation() == null ? "-" : event.getDateCreation().toString()));
		applyImage(event.getImageCouverture());
		loadGalleryDetails();
		loadPurchasedTickets();
	}

	public void setPurchaseHandler(Consumer<Ticket> purchaseHandler) {
		this.purchaseHandler = purchaseHandler;
	}

	public void setPayHandler(Consumer<Evenement> payHandler) {
		this.payHandler = payHandler;
	}

	public void setBackHandler(Runnable backHandler) {
		this.backHandler = backHandler;
	}

	@FXML
	private void onBuyTicketClick() {
		if (event == null) {
			return;
		}

		Integer currentUserId = resolveCurrentUserId();
		if (currentUserId == null) {
			Alert alert = new Alert(Alert.AlertType.WARNING);
			alert.setTitle("Connexion requise");
			alert.setHeaderText("Vous devez être connecté pour acheter un ticket");
			alert.setContentText("Veuillez vous reconnecter avant de continuer.");
			alert.showAndWait();
			return;
		}

		if (payHandler != null) {
			payHandler.accept(event);
		} else {
			// Fallback: original behaviour if payHandler is not set
			try {
				Ticket ticket = ticketService.purchaseTicket(event, currentUserId);
				loadPurchasedTickets();
				if (purchaseHandler != null) {
					purchaseHandler.accept(ticket);
				} else {
					Alert alert = new Alert(Alert.AlertType.INFORMATION);
					alert.setTitle("Ticket généré");
					alert.setHeaderText("Votre ticket a été créé avec succès");
					alert.setContentText("Le ticket a été enregistré.");
					alert.showAndWait();
				}
			} catch (SQLDataException e) {
				Alert alert = new Alert(Alert.AlertType.ERROR);
				alert.setTitle("Achat impossible");
				alert.setHeaderText("Le ticket n'a pas pu etre achete");
				alert.setContentText(e.getMessage());
				alert.showAndWait();
			}
		}
	}

	@FXML
	private void onToggleTicketsClick() {
		if (currentPurchasedTickets.isEmpty()) {
			return;
		}
		ticketsExpanded = !ticketsExpanded;
		renderPurchasedTickets(currentPurchasedTickets);
	}

	private void loadGalleryDetails() {
		if (event == null || event.getGalerieId() == null) {
			renderGalleryDetails(null);
			return;
		}

		try {
			currentGalerie = galerieService.getById(event.getGalerieId());
		} catch (SQLDataException e) {
			currentGalerie = null;
		}

		renderGalleryDetails(currentGalerie);
	}

	private void renderGalleryDetails(Galerie galerie) {
		String fallbackGalleryLabel = event != null && event.getGalerieId() != null ? "Galerie #" + event.getGalerieId() : "Galerie";

		if (galerie == null) {
			galleryLabel.setText(fallbackGalleryLabel);
			galleryNameLabel.setText(fallbackGalleryLabel);
			galleryAddressLabel.setText("Adresse non disponible");
			galleryCoordinatesLabel.setText("Localisation non disponible");
			galleryCapacityLabel.setText("Capacite non definie");
			renderMapPlaceholder();
			return;
		}

		String galleryName = textOrDefault(galerie.getNom(), fallbackGalleryLabel);
		galleryLabel.setText(galleryName);
		galleryNameLabel.setText(galleryName);
		galleryAddressLabel.setText(textOrDefault(galerie.getAdresse(), "Adresse non disponible"));
		galleryCoordinatesLabel.setText(textOrDefault(galerie.getLocalisation(), "Localisation non disponible"));
		galleryCapacityLabel.setText(formatCapacity(galerie.getCapaciteMax()));

		CoordinateUtils.parseCoordinates(galerie.getLocalisation())
				.ifPresentOrElse(
						coordinates -> {
							renderMap(coordinates.latitude(), coordinates.longitude(), galleryName, galerie.getAdresse(), galerie.getLocalisation());
							fetchWeather(coordinates);
						},
						() -> {
							renderMapPlaceholder();
							hideWeatherPanel();
						}
				);
	}

	private void fetchWeather(CoordinateUtils.Coordinates coordinates) {
		if (event == null || event.getDateDebut() == null || weatherContainer == null) {
			hideWeatherPanel();
			return;
		}

		weatherTempLabel.setText("Chargement...");
		weatherWindLabel.setText("");
		weatherRainLabel.setText("");
		weatherContainer.setVisible(true);
		weatherContainer.setManaged(true);

		CompletableFuture.supplyAsync(() -> weatherService.getForecastForEvent(coordinates, event.getDateDebut()))
			.thenAccept(weatherOpt -> {
				Platform.runLater(() -> {
					if (weatherOpt.isPresent()) {
						WeatherService.WeatherData w = weatherOpt.get();
						weatherTempLabel.setText(String.format(Locale.ROOT, "%.1f °C", w.temperature()));
						weatherWindLabel.setText(String.format(Locale.ROOT, "Vent: %.1f km/h", w.windSpeed()));
						weatherRainLabel.setText(String.format(Locale.ROOT, "Pluie: %.1f mm", w.rain()));
					} else {
						hideWeatherPanel();
					}
				});
			});
	}

	private void hideWeatherPanel() {
		if (weatherContainer != null) {
			weatherContainer.setVisible(false);
			weatherContainer.setManaged(false);
		}
	}

	private void loadPurchasedTickets() {
		Integer currentUserId = resolveCurrentUserId();
		if (event == null || event.getId() == null || currentUserId == null) {
			renderPurchasedTickets(List.of());
			return;
		}

		try {
			List<Ticket> tickets = ticketService.getTicketsByEventAndUser(event.getId(), currentUserId);
			renderPurchasedTickets(tickets);
		} catch (SQLDataException e) {
			renderPurchasedTickets(List.of());
			ticketsEmptyLabel.setText("Impossible de charger vos tickets.");
			ticketsEmptyLabel.setVisible(true);
			ticketsEmptyLabel.setManaged(true);
		}
	}

	private void renderPurchasedTickets(List<Ticket> tickets) {
		List<Ticket> sortedTickets = tickets == null ? new ArrayList<>() : new ArrayList<>(tickets);
		sortedTickets.sort(Comparator.comparing(Ticket::getDateAchat, Comparator.nullsLast(Comparator.reverseOrder())));
		currentPurchasedTickets = sortedTickets;

		if (currentPurchasedTickets.isEmpty()) {
			ticketsExpanded = false;
			ticketsContainer.getChildren().clear();
			ticketsCountLabel.setText("0 ticket(s)");
			ticketsEmptyLabel.setText("Aucun ticket achete pour cet evenement.");
			ticketsEmptyLabel.setVisible(true);
			ticketsEmptyLabel.setManaged(true);
			updateTicketsToggleButton(false);
			return;
		}

		ticketsEmptyLabel.setVisible(false);
		ticketsEmptyLabel.setManaged(false);
		ticketsCountLabel.setText(currentPurchasedTickets.size() + " ticket(s)");

		ticketsContainer.getChildren().clear();
		int visibleLimit = ticketsExpanded ? currentPurchasedTickets.size() : Math.min(COLLAPSED_TICKET_LIMIT, currentPurchasedTickets.size());
		for (int i = 0; i < currentPurchasedTickets.size(); i++) {
			Ticket ticket = currentPurchasedTickets.get(i);
			boolean visible = i < visibleLimit;

			HBox row = new HBox(10);
			row.getStyleClass().add("ticket-row");
			row.setVisible(visible);
			row.setManaged(visible);

			VBox info = new VBox(3);
			info.getStyleClass().add("ticket-info");
			Label title = new Label("Ticket #" + (i + 1));
			title.getStyleClass().add("ticket-title");
			Label subtitle = new Label("Achat le " + formatPurchaseDate(ticket) + " | Ref: " + resolveReference(ticket));
			subtitle.getStyleClass().add("ticket-subtitle");
			info.getChildren().addAll(title, subtitle);

			HBox actions = new HBox(8);
			actions.getStyleClass().add("ticket-actions");
			Button pdfButton = new Button("Aperçu PDF");
			pdfButton.getStyleClass().add("ticket-action-secondary");
			pdfButton.setOnAction(event -> showTicketPreview(ticket));
			Button downloadButton = new Button("Télécharger PDF");
			downloadButton.getStyleClass().add("ticket-action-primary");
			downloadButton.setOnAction(event -> downloadTicketPdf(ticket));
			actions.getChildren().addAll(pdfButton, downloadButton);

			HBox.setHgrow(info, Priority.ALWAYS);
			row.getChildren().addAll(info, actions);
			ticketsContainer.getChildren().add(row);
		}

		updateTicketsToggleButton(currentPurchasedTickets.size() > COLLAPSED_TICKET_LIMIT);
	}

	private void updateTicketsToggleButton(boolean shouldShow) {
		if (ticketsToggleButton == null) {
			return;
		}
		ticketsToggleButton.setVisible(shouldShow);
		ticketsToggleButton.setManaged(shouldShow);
		ticketsToggleButton.setText(ticketsExpanded ? "Voir moins" : "Voir plus");
	}

	private void forceMapResize() {
		if (mapWebView != null && mapWebView.getEngine().getDocument() != null) {
			try {
				mapWebView.getEngine().executeScript("if (typeof fixSize === 'function') fixSize();");
			} catch (Exception ignored) { }
		}
	}

	private void renderMap(double latitude, double longitude, String galleryName, String address, String coordinatesText) {
		if (mapWebView == null) {
			return;
		}
		mapWebView.getEngine().loadContent(buildLeafletMapHtml(latitude, longitude, galleryName, address, coordinatesText));
	}

	private void renderMapPlaceholder() {
		if (mapWebView == null) {
			return;
		}
		mapWebView.getEngine().loadContent(buildPlaceholderHtml());
	}

	private String buildLeafletMapHtml(double latitude, double longitude, String galleryName, String address, String coordinatesText) {
		return String.format(Locale.ROOT, """
			<!DOCTYPE html>
			<html lang="fr">
			<head>
			    <meta charset="UTF-8" />
			    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
			    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
			    <script>var L_DISABLE_3D = true;</script>
			    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
			    <style>
			        html, body {
			            margin: 0;
			            padding: 0;
			            width: 100%%;
			            height: 100%%;
			            overflow: hidden;
			            background: #edf0f5;
			            font-family: 'Segoe UI', Arial, sans-serif;
			        }
			        #map {
			            position: absolute;
			            top: 0;
			            left: 0;
			            right: 0;
			            bottom: 0;
			        }
			        .leaflet-container {
			            background: #edf0f5;
			        }
			    </style>
			</head>
			<body>
			    <div id="map"></div>
			    <script>
			        const map = L.map('map', { 
			            zoomControl: true, 
			            attributionControl: true,
			            zoomAnimation: false,
			            markerZoomAnimation: false,
			            fadeAnimation: false
			        }).setView([%s, %s], 16);
			        
			        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
			            maxZoom: 19,
			            attribution: '&copy; OpenStreetMap contributors'
			        }).addTo(map);
			        
			        const marker = L.marker([%s, %s]).addTo(map);
			        marker.bindPopup('<strong>%s</strong><br/>%s<br/>%s');
			        
			        // Aggressive fix for grey tiles and partial rendering in JavaFX WebView
			        const fixSize = function() {
			            map.invalidateSize();
			        };
			        
			        let checks = 0;
			        const interval = setInterval(function() {
			            fixSize();
			            if (++checks > 10) clearInterval(interval);
			        }, 200);
			        
			        window.addEventListener('resize', fixSize);
			        
			        if (typeof ResizeObserver !== 'undefined') {
			            new ResizeObserver(fixSize).observe(document.body);
			            new ResizeObserver(fixSize).observe(document.getElementById('map'));
			        }
			    </script>
			</body>
			</html>
			""", latitude, longitude, latitude, longitude, jsString(textOrDefault(galleryName, "Galerie")), jsString(textOrDefault(address, "Adresse non disponible")), jsString(textOrDefault(coordinatesText, "Localisation non disponible")));
	}

	private String buildPlaceholderHtml() {
		return """
			<!DOCTYPE html>
			<html lang="fr">
			<head>
			    <meta charset="UTF-8" />
			    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
			    <style>
			        html, body {
			            margin: 0;
			            width: 100%;
			            height: 100%;
			            display: flex;
			            align-items: center;
			            justify-content: center;
			            background: #edf0f5;
			            font-family: 'Segoe UI', Arial, sans-serif;
			            color: #8ea0b8;
			        }
			        .message {
			            display: flex;
			            align-items: center;
			            gap: 10px;
			            font-size: 15px;
			            font-weight: 600;
			        }
			    </style>
			</head>
			<body>
			    <div class="message">&#x1f4cd; Localisation non disponible</div>
			</body>
			</html>
			""";
	}

	private void showTicketPreview(Ticket ticket) {
		if (event == null) {
			return;
		}

		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.setTitle("Aperçu du ticket");
		alert.setHeaderText("Ticket PDF prêt à être ouvert");
		alert.setContentText("Le PDF va être généré dans un fichier temporaire et ouvert dans votre lecteur PDF par défaut.");
		alert.showAndWait();

		try {
			File previewPdf = ticketPdfService.createPreviewPdf(event, ticket);
			ticketPdfService.openPdf(previewPdf);
		} catch (Exception e) {
			Alert error = new Alert(Alert.AlertType.ERROR, "Impossible d'ouvrir le PDF: " + e.getMessage(), ButtonType.OK);
			error.setTitle("Aperçu PDF indisponible");
			error.showAndWait();
		}
	}

	private void downloadTicketPdf(Ticket ticket) {
		if (event == null) {
			return;
		}

		FileChooser chooser = new FileChooser();
		chooser.setTitle("Télécharger le ticket PDF");
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
		chooser.setInitialFileName(ticketPdfService.buildSuggestedFileName(event, ticket));

		Window owner = buyTicketButton == null ? null : buyTicketButton.getScene().getWindow();
		File target = chooser.showSaveDialog(owner);
		if (target == null) {
			return;
		}

		File pdfFile = ensurePdfExtension(target);
		try {
			ticketPdfService.exportTicketPdf(event, ticket, pdfFile);
			Alert info = new Alert(Alert.AlertType.INFORMATION);
			info.setTitle("Ticket téléchargé");
			info.setHeaderText("PDF généré avec succès");
			info.setContentText("Le ticket a été enregistré dans:\n" + pdfFile.getAbsolutePath());
			info.showAndWait();
		} catch (Exception e) {
			Alert error = new Alert(Alert.AlertType.ERROR);
			error.setTitle("Téléchargement impossible");
			error.setHeaderText("Le PDF n'a pas pu être créé");
			error.setContentText(e.getMessage());
			error.showAndWait();
		}
	}

	private File ensurePdfExtension(File target) {
		String name = target.getName().toLowerCase();
		if (name.endsWith(".pdf")) {
			return target;
		}
		File parent = target.getParentFile();
		String baseName = target.getName() + ".pdf";
		return parent == null ? new File(baseName) : new File(parent, baseName);
	}

	@FXML
	private void onBackClick() {
		if (backHandler != null) {
			backHandler.run();
		}
	}

	private void applyImage(String imageSource) {
		if (imageSource == null || imageSource.isBlank()) {
			coverImageView.setImage(null);
			return;
		}
		try {
			Image image;
			if (imageSource.startsWith("http://") || imageSource.startsWith("https://") || imageSource.startsWith("file:")) {
				image = new Image(imageSource, true);
			} else {
				image = new Image(new File(imageSource).toURI().toString(), true);
			}
			coverImageView.setImage(image.isError() ? null : image);
		} catch (Exception e) {
			coverImageView.setImage(null);
		}
	}

	private String formatDate(LocalDateTime dateTime) {
		return dateTime == null ? "Date non definie" : DATE_FORMATTER.format(dateTime);
	}

	private String formatPrice(Double price) {
		return price == null ? "Prix non defini" : String.format(Locale.ROOT, "%.0f TND", price);
	}

	private String formatCapacity(Integer value) {
		return value == null ? "Capacite non definie" : value + " personnes";
	}

	private String textOrDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private String resolveReference(Ticket ticket) {
		if (ticket.getCodeQr() == null || ticket.getCodeQr().isBlank()) {
			return "-";
		}

		String payload = ticket.getCodeQr();
		int refIndex = payload.indexOf("|ref=");
		if (refIndex >= 0 && refIndex + 5 < payload.length()) {
			return payload.substring(refIndex + 5);
		}
		return payload;
	}

	private String formatPurchaseDate(Ticket ticket) {
		return ticket.getDateAchat() == null ? "-" : PURCHASE_DATE_FORMATTER.format(ticket.getDateAchat());
	}

	private Integer resolveCurrentUserId() {
		if (MainFX.getAuthenticatedUser() == null || MainFX.getAuthenticatedUser().getId() == null) {
			return null;
		}
		return MainFX.getAuthenticatedUser().getId();
	}

	private String jsString(String value) {
		if (value == null) {
			return "";
		}
		return value
				.replace("\\", "\\\\")
				.replace("'", "\\'")
				.replace("\r", " ")
				.replace("\n", " ");
	}
}


