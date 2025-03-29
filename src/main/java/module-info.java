module com.project {
    requires javafx.controls;
    requires javafx.fxml;
    requires jdk.httpserver;
    requires com.google.gson;
    requires java.sql;
    requires java.net.http;


    opens com.project to javafx.fxml;
    exports com.project;
}