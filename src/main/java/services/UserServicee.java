package services;

import entities.User;
import java.sql.SQLDataException;

public interface UserServicee {
    User login(String email, String password) throws SQLDataException;
    User getById(int id) throws SQLDataException;
}