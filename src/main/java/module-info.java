module com.project {
    requires jdk.httpserver;
    requires com.google.gson;
    requires java.sql;
    requires java.net.http;
    requires jjwt.api;
    requires java.desktop;


    opens com.project.dao to com.google.gson;
    opens com.project.models to com.google.gson;
    opens com.project.rest to com.google.gson;
    opens com.project.adapters to com.google.gson;
    opens com.project.server to com.google.gson;
    exports com.project.utils;
    opens com.project.utils to javafx.fxml;
    opens com.project.models.message to com.google.gson;
    exports com.project.config;
    opens com.project.config to javafx.fxml;
}