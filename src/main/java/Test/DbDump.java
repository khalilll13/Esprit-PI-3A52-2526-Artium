package test;

import services.JdbcLivreService;
import entities.Livre;
import java.util.List;

public class DbDump {
    public static void main(String[] args) {
        try {
            JdbcLivreService service = new JdbcLivreService();
            List<Livre> all = service.getAll();
            System.out.println("Total books found: " + all.size());
            for (Livre l : all) {
                System.out.println("ID: " + l.getId() + ", Title: " + l.getTitre() + ", CollectionID: " + l.getCollectionId() + ", Artist: " + l.getAuteur());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
