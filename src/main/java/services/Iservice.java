package services;

import java.sql.SQLDataException;
import java.util.List;

public interface Iservice <T> {
    void add (T t) throws SQLDataException;
    void delete (T t) throws SQLDataException;
    void update (T t) throws SQLDataException;
    List<T> getAll () throws SQLDataException;
    T getById (int id) throws SQLDataException;
}
