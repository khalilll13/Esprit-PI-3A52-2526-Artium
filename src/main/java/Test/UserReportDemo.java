package Test;

import services.UserReportService;
import entities.User;
import entities.UserReport;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class UserReportDemo {

    public static void main(String[] args) {
        UserReportService reportService = new UserReportService(null, null);
        UserReport report = reportService.generateActiveUsersMonthlyReportFromSnapshot(mockUsers());
        System.out.println(report.getReportText());
    }

    private static List<User> mockUsers() {
        List<User> users = new ArrayList<>();
        LocalDate now = LocalDate.now();

        users.add(buildUser("Admin", "Active", now.minusMonths(8)));
        users.add(buildUser("Artiste", "Active", now.minusDays(3)));
        users.add(buildUser("Artiste", "Bloque", now.minusDays(2)));
        users.add(buildUser("Amateur", "Activé", now.minusDays(10)));
        users.add(buildUser("Amateur", "Bloqué", now.minusMonths(1)));

        return users;
    }

    private static User buildUser(String role, String status, LocalDate inscriptionDate) {
        User user = new User();
        user.setRole(role);
        user.setStatut(status);
        user.setDateInscription(inscriptionDate);
        return user;
    }
}

