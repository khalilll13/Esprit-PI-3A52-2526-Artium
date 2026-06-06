package test;

import services.SmsService;
import services.UserService;
import entities.User;

import java.time.LocalDate;
import java.sql.SQLDataException;

public class Main {
    public static void main(String[] args) {
        SmsService smsService = new SmsService();

        smsService.sendSms(
                "+21698115638",   // your phone
                "Hello from my Java app 🚀"
        );
    }


}