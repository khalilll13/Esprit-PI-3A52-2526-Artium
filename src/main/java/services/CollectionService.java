package services;

import entities.CollectionOeuvre;

import java.sql.SQLDataException;
import java.util.List;

public interface CollectionService {
    List<CollectionOeuvre> getAll() throws SQLDataException;
    List<CollectionOeuvre> getByArtist(int artistId) throws SQLDataException;
}
