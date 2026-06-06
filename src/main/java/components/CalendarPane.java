package components;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.sql.SQLDataException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

import entities.Evenement;
import services.EvenementService;
import javafx.scene.control.Tooltip;

public class CalendarPane extends VBox {
    private EvenementService evenementService = new EvenementService();

    public CalendarPane() {
        getStyleClass().add("list-card");
        setSpacing(10);
        setPadding(new Insets(12));

        LocalDate today = LocalDate.now();
        YearMonth month = YearMonth.of(today.getYear(), today.getMonth());

        Label title = new Label("Calendrier des evenements");
        title.getStyleClass().add("section-title");

        Label monthLabel = new Label(month.getMonth().name() + " " + month.getYear());
        monthLabel.getStyleClass().add("section-subtitle");

        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);

        String[] days = {"L", "M", "M", "J", "V", "S", "D"};
        for (int i = 0; i < days.length; i++) {
            Label day = new Label(days[i]);
            day.getStyleClass().add("calendar-day-header");
            grid.add(day, i, 0);
        }

        int firstDay = month.atDay(1).getDayOfWeek().getValue();
        int monthLength = month.lengthOfMonth();
        int row = 1;
        int col = firstDay - 1;

        // Fetch events for current month
        List<Evenement> eventsThisMonth = null;
        try {
            eventsThisMonth = evenementService.getAll().stream()
                .filter(e -> e.getDateDebut() != null && e.getDateDebut().getYear() == month.getYear() && e.getDateDebut().getMonth() == month.getMonth())
                .collect(Collectors.toList());
        } catch (SQLDataException e) {
            e.printStackTrace();
        }

        for (int value = 1; value <= monthLength; value++) {
            Label dayLabel = new Label(String.valueOf(value));
            dayLabel.getStyleClass().add("calendar-day");
            
            if (value == today.getDayOfMonth() && month.getYear() == today.getYear() && month.getMonthValue() == today.getMonthValue()) {
                dayLabel.getStyleClass().add("today");
            }

            // Check if there's an event on this day
            if (eventsThisMonth != null) {
                int currentDay = value;
                List<Evenement> dayEvents = eventsThisMonth.stream()
                    .filter(e -> e.getDateDebut().getDayOfMonth() == currentDay)
                    .collect(Collectors.toList());
                
                if (!dayEvents.isEmpty()) {
                    dayLabel.setStyle("-fx-background-color: #38bdf8; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 50%;");
                    String tooltipText = dayEvents.stream().map(Evenement::getTitre).collect(Collectors.joining("\n"));
                    Tooltip.install(dayLabel, new Tooltip(tooltipText));
                }
            }

            grid.add(dayLabel, col, row);
            col++;
            if (col > 6) {
                col = 0;
                row++;
            }
        }

        HBox footer = new HBox();
        Label filter = new Label("Filtre: Tous / A venir / Aujourd'hui");
        filter.getStyleClass().add("section-subtitle");
        HBox.setHgrow(filter, Priority.ALWAYS);
        footer.getChildren().add(filter);

        getChildren().addAll(title, monthLabel, grid, footer);
    }
}

