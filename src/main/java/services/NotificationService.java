package services;

import entities.Evenement;
import entities.Notification;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class NotificationService {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(1);
    private static final List<Notification> STORE = Collections.synchronizedList(new ArrayList<>());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public Notification sendNotification(int userId, String title, String message) {
        Notification notification = new Notification();
        notification.setId(SEQUENCE.getAndIncrement());
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setRead(false);
        STORE.add(notification);
        return notification;
    }

    public void sendCancellationNotice(int userId, Evenement event, int refundedTickets) {
        String eventTitle = event == null || event.getTitre() == null || event.getTitre().isBlank()
                ? "un evenement"
                : event.getTitre();
        String when = event == null || event.getDateDebut() == null ? "date inconnue" : DATE_FORMATTER.format(event.getDateDebut());
        String message = "L'evenement '" + eventTitle + "' prevu le " + when
                + " a ete annule. Vos " + refundedTickets + " ticket(s) seront rembourses.";
        sendNotification(userId, "Evenement annule", message);
    }

    public void sendReclamationReplyNotice(int userId, Integer reclamationId) {
        String message = "Un administrateur a répondu à votre réclamation"  + ".";
        sendNotification(userId, "Réponse à votre réclamation", message);
    }

    public List<Notification> getUnreadNotifications(int userId) {
        synchronized (STORE) {
            return STORE.stream()
                    .filter(notification -> notification.getUserId() != null && notification.getUserId() == userId)
                    .filter(notification -> !notification.isRead())
                    .map(this::copy)
                    .collect(Collectors.toList());
        }
    }

    public int countUnreadNotifications(int userId) {
        synchronized (STORE) {
            return (int) STORE.stream()
                    .filter(notification -> notification.getUserId() != null && notification.getUserId() == userId)
                    .filter(notification -> !notification.isRead())
                    .count();
        }
    }

    public void markAllAsRead(int userId) {
        synchronized (STORE) {
            STORE.stream()
                    .filter(notification -> notification.getUserId() != null && notification.getUserId() == userId)
                    .forEach(notification -> notification.setRead(true));
        }
    }

    private Notification copy(Notification source) {
        Notification notification = new Notification();
        notification.setId(source.getId());
        notification.setUserId(source.getUserId());
        notification.setTitle(source.getTitle());
        notification.setMessage(source.getMessage());
        notification.setCreatedAt(source.getCreatedAt());
        notification.setRead(source.isRead());
        return notification;
    }
}

