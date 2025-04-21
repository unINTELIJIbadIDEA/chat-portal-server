package com.project.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {
    private static final Properties properties = new Properties();

    static {
        try (InputStream input = Config.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.out.println("Sorry, unable to find config.properties");
            }
            properties.load(input);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }


    public static String getDbUrl() {
        return properties.getProperty("db.URL");
    }

    public static String getDbUsername() {
        return properties.getProperty("db.Username");
    }

    public static String getDbPassword() {
        return properties.getProperty("db.Password");
    }


    public static String getSecretKey() {
        return properties.getProperty("SECRET_KEY");
    }

    public static Integer getExpirationTime() {
        return Integer.parseInt(properties.getProperty("EXPIRATION_TIME"));
    }

    public static Integer getPORT_API() {
        return Integer.parseInt(properties.getProperty("API_PORT"));
    }

    public static Integer getPORT_SERVER() {
        return Integer.parseInt(properties.getProperty("SERVER_PORT"));
    }

    public static String getHOST_SERVER() {
        return properties.getProperty("SERVER_HOST");
    }
}
