package controllers.amateur;

import entities.Evenement;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.scene.layout.GridPane;
import services.EvenementService;
import services.EventAiSearchService;

import java.io.IOException;
import java.sql.SQLDataException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class EventsfrontController {

	@FXML
	private TextField searchField;

	@FXML
	private ToggleButton allTabButton;

	@FXML
	private ToggleButton expositionTabButton;

	@FXML
	private ToggleButton concertTabButton;

	@FXML
	private ToggleButton spectacleTabButton;

	@FXML
	private ToggleButton conferenceTabButton;

	@FXML
	private GridPane eventsGridPane;

	@FXML
	private Label emptyStateLabel;

	private final EvenementService evenementService = new EvenementService();
	private final EventAiSearchService aiSearchService = new EventAiSearchService();
	private final List<Evenement> allEvents = new ArrayList<>();
	private final AtomicLong searchSequence = new AtomicLong(0L);
	private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "event-ai-search");
		thread.setDaemon(true);
		return thread;
	});
	private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(280));
	private String selectedCategory = "Tous";
	private Consumer<Evenement> detailNavigationHandler;

	@FXML
	public void initialize() {
		searchDebounce.setOnFinished(event -> triggerSearch(searchField.getText()));
		searchField.textProperty().addListener((observable, oldValue, newValue) -> {
			searchDebounce.stop();
			searchDebounce.playFromStart();
		});
		searchField.setOnAction(event -> triggerSearch(searchField.getText()));

		refreshEvents();
	}

	public void setDetailNavigationHandler(Consumer<Evenement> detailNavigationHandler) {
		this.detailNavigationHandler = detailNavigationHandler;
		// Cards are first rendered during initialize(), before parent injects navigation handler.
		// Re-run the search so every visible card receives the click callback.
		if (!allEvents.isEmpty()) {
			triggerSearch(searchField.getText());
		}
	}

	@FXML
	private void onSearchClick() {
		triggerSearch(searchField.getText());
	}

	@FXML
	private void onAllTabClick() {
		selectedCategory = "Tous";
		setActiveTab(allTabButton);
		triggerSearch(searchField.getText());
	}

	@FXML
	private void onExpositionTabClick() {
		selectedCategory = "Exposition";
		setActiveTab(expositionTabButton);
		triggerSearch(searchField.getText());
	}

	@FXML
	private void onConcertTabClick() {
		selectedCategory = "Concert";
		setActiveTab(concertTabButton);
		triggerSearch(searchField.getText());
	}

	@FXML
	private void onSpectacleTabClick() {
		selectedCategory = "Spectacle";
		setActiveTab(spectacleTabButton);
		triggerSearch(searchField.getText());
	}

	@FXML
	private void onConferenceTabClick() {
		selectedCategory = "Conférence";
		setActiveTab(conferenceTabButton);
		triggerSearch(searchField.getText());
	}

	private void refreshEvents() {
		try {
			allEvents.clear();
			allEvents.addAll(evenementService.getAll());
			allEvents.sort(Comparator.comparing(Evenement::getDateDebut, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
			triggerSearch(searchField.getText());
		} catch (SQLDataException e) {
			emptyStateLabel.setText("Impossible de charger les evenements.");
			emptyStateLabel.setVisible(true);
			emptyStateLabel.setManaged(true);
		}
	}

	private void triggerSearch(String rawQuery) {
		String query = rawQuery == null ? "" : rawQuery.trim();
		long sequence = searchSequence.incrementAndGet();

		CompletableFuture
				.supplyAsync(() -> searchEvents(query), searchExecutor)
				.thenAccept(results -> Platform.runLater(() -> {
					if (sequence != searchSequence.get()) {
						return;
					}
					renderCards(results);
				}))
				.exceptionally(ex -> {
					Platform.runLater(() -> {
						if (sequence != searchSequence.get()) {
							return;
						}
						emptyStateLabel.setText("La recherche IA est temporairement indisponible.");
						emptyStateLabel.setVisible(true);
						emptyStateLabel.setManaged(true);
					});
					return null;
				});
	}

	private List<EventAiSearchService.RankedEvent> searchEvents(String query) {
		List<Evenement> scopedEvents = allEvents.stream()
				.filter(event -> matchesCategory(event, selectedCategory))
				.collect(Collectors.toList());

		if (query.isBlank()) {
			return scopedEvents.stream()
					.map(event -> new EventAiSearchService.RankedEvent(event, Double.NaN))
					.collect(Collectors.toList());
		}

		return aiSearchService.rankEvents(scopedEvents, query);
	}

	private boolean matchesCategory(Evenement event, String category) {
		if (category == null || "Tous".equalsIgnoreCase(category)) {
			return true;
		}
		return category.equalsIgnoreCase(safe(event.getType()));
	}

	private void renderCards(List<EventAiSearchService.RankedEvent> rankedEvents) {
		eventsGridPane.getChildren().clear();

        int row = 0;
        int col = 0;
		for (EventAiSearchService.RankedEvent rankedEvent : rankedEvents) {
			Evenement event = rankedEvent.event();
			try {
				FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/views/amateur/components/event-card.fxml")));
				Parent card = loader.load();
				EventCardController controller = loader.getController();
				controller.setData(event);
				controller.setScore(rankedEvent.scoreOutOf10());
				controller.setDetailHandler(detailNavigationHandler);
				
                eventsGridPane.add(card, col, row);
                col++;
                if (col == 3) {
                    col = 0;
                    row++;
                }
			} catch (IOException e) {
				emptyStateLabel.setText("Erreur lors de l'affichage des evenements.");
				emptyStateLabel.setVisible(true);
				emptyStateLabel.setManaged(true);
				return;
			}
		}

		boolean empty = rankedEvents.isEmpty();
		emptyStateLabel.setVisible(empty);
		emptyStateLabel.setManaged(empty);
	}

	private void setActiveTab(ToggleButton activeButton) {
		ToggleButton[] buttons = {allTabButton, expositionTabButton, concertTabButton, spectacleTabButton, conferenceTabButton};
		for (ToggleButton button : buttons) {
			if (button != null) {
				button.getStyleClass().remove("active");
				button.setSelected(false);
			}
		}
		if (activeButton != null) {
			activeButton.getStyleClass().add("active");
			activeButton.setSelected(true);
		}
	}

	private boolean contains(String value, String search) {
		return value != null && value.toLowerCase().contains(search);
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}
}



