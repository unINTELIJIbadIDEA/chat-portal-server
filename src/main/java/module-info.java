module com.project {
    requires javafx.controls;
    requires javafx.fxml;
    requires jdk.httpserver;
    requires com.google.gson;
    requires java.sql;
    requires java.net.http;
    requires jjwt.api;
    requires java.desktop;


    opens com.project to javafx.fxml;
    opens com.project.dao to com.google.gson;
    exports com.project;
    opens com.project.models to com.google.gson;
    opens com.project.rest to com.google.gson;
    opens com.project.adapters to com.google.gson;
    opens com.project.server to com.google.gson;
    exports com.project.controllers;
    opens com.project.controllers to javafx.fxml;
    exports com.project.utils;
    opens com.project.utils to javafx.fxml;
    opens com.project.models.message to com.google.gson;
}