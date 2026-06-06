package services;

import entities.Livre;

import java.sql.SQLDataException;
import java.util.List;

public interface LivreService {
    void add(Livre livre) throws SQLDataException;

    void update(Livre livre) throws SQLDataException;

    void delete(int livreId) throws SQLDataException;

    List<Livre> getAll() throws SQLDataException;

    List<Livre> getByArtist(int artistId) throws SQLDataException;

    List<Livre> search(String searchText, String categorie) throws SQLDataException;
}

