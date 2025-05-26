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

    public static Integer getLOCAL_API_PORT() {
        return Integer.parseInt(properties.getProperty("LOCAL_API_PORT"));
    }

    public static Integer getLOCAL_SERVER_PORT() {
        return Integer.parseInt(properties.getProperty("LOCAL_TCP_PORT"));
    }

    public static Integer getREMOTE_API_PORT() {
        return Integer.parseInt(properties.getProperty("REMOTE_API_PORT"));
    }

    public static Integer getREMOTE_SERVER_PORT() {
        return Integer.parseInt(properties.getProperty("REMOTE_TCP_PORT"));
    }

    public static String getHOST_SERVER() {
        return properties.getProperty("SERVER_HOST");
    }

    public static Integer getBATTLESHIP_SERVER_PORT() {
        return Integer.parseInt(properties.getProperty("BATTLESHIP_SERVER_PORT", "12350"));
    }

    public static Integer getREMOTE_BATTLESHIP_PORT() {
        return Integer.parseInt(properties.getProperty("REMOTE_BATTLESHIP_PORT", "21374"));
    }
}
