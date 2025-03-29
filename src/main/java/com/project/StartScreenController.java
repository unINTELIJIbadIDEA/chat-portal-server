package com.project;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.project.apiServer.Post;
import com.project.apiServer.User;
import com.project.apiServer.UserAdapter;
import javafx.animation.ScaleTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.util.Duration;


import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;

public class StartScreenController {

    @FXML
    private Button ButtonLogin;

    @FXML
    private Button ButtonSignUp;

    @FXML
    private void initialize() {
        // Dodaj akcje dla przycisków
        ButtonLogin.setOnAction(event -> {handleSignIn();

        });
        ButtonSignUp.setOnAction(event -> handleSignUp());

        // Animacja najechania
        ButtonLogin.setOnMouseEntered(this::onMouseEntered);
        ButtonLogin.setOnMouseExited(this::onMouseExited);
        ButtonLogin.setOnMousePressed(this::onMousePressed);
        ButtonLogin.setOnMouseReleased(this::onMouseReleased);

        ButtonSignUp.setOnMouseEntered(this::onMouseEntered);
        ButtonSignUp.setOnMouseExited(this::onMouseExited);
        ButtonSignUp.setOnMousePressed(this::onMousePressed);
        ButtonSignUp.setOnMouseReleased(this::onMouseReleased);
    }

    // Akcja dla przycisku "SIGN IN"
    private void handleSignIn() {
        System.out.println("Przycisk SIGN IN kliknięty");
        try {
            URL resource = HelloApplication.class.getResource("loginscreen.fxml");
            FXMLLoader fxmlLoader = new FXMLLoader(resource);
            AnchorPane loginScreen = fxmlLoader.load();
            Stage stage = (Stage) ButtonLogin.getScene().getWindow();
            stage.setScene(new Scene(loginScreen));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Akcja dla przycisku "SIGN UP"
    private void handleSignUp() {
        System.out.println("Przycisk SIGN UP kliknięty");
        try {
            URL resource = HelloApplication.class.getResource("registerscreen.fxml");
            FXMLLoader fxmlLoader = new FXMLLoader(resource);
            AnchorPane loginScreen = fxmlLoader.load();
            Stage stage = (Stage) ButtonSignUp.getScene().getWindow();
            stage.setScene(new Scene(loginScreen));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Zmiana koloru tła po najechaniu
    private void onMouseEntered(MouseEvent event) {
        Button button = (Button) event.getSource();
        button.setStyle("-fx-background-color: #ffe0b2; -fx-border-color: grey; -fx-border-radius: 10px; -fx-background-radius: 10px;");
    }

    // Przywrócenie oryginalnego koloru tła
    private void onMouseExited(MouseEvent event) {
        Button button = (Button) event.getSource();
        button.setStyle("-fx-background-color: #fff8e1; -fx-border-color: grey; -fx-border-radius: 10px; -fx-background-radius: 10px;");
    }

    // Efekt kliknięcia (zmiana skali)
    private void onMousePressed(MouseEvent event) {
        Button button = (Button) event.getSource();
        ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(150), button);
        scaleTransition.setToX(0.9);
        scaleTransition.setToY(0.9);
        scaleTransition.play();
    }

    // Powrót do normalnej skali po kliknięciu
    private void onMouseReleased(MouseEvent event) {
        Button button = (Button) event.getSource();
        ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(150), button);
        scaleTransition.setToX(1.0);
        scaleTransition.setToY(1.0);
        scaleTransition.play();
    }

    public void test(ActionEvent actionEvent) throws IOException, InterruptedException {
//        Gson gson = new GsonBuilder()
//                .registerTypeAdapter(Post.class, new UserAdapter())
//                .create();
//        System.out.println("test worked!");
//        User user1 = new User(5, "Jan", "Rapowanie", "Janek123", "janek@gmail.com", LocalDate.now().toString(), "jr123!");
//        String jsonUser = gson.toJson(user1, User.class);
//        HttpClient client = HttpClient.newHttpClient();
////        HttpRequest request = HttpRequest.newBuilder()
////                .uri(URI.create("http://localhost:8080/api/users"))
////                .header("Content-Type", "application/json")
////                .POST(HttpRequest.BodyPublishers.ofString(jsonUser))
////                .build();
//
//        HttpRequest request = HttpRequest.newBuilder()
//                .uri(URI.create("http://localhost:8080/api/users?userId=" + 2))
//                .header("Content-Type", "application/json")
//                .GET()
//                .build();
//        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
//        System.out.println(response);
//        System.out.println(response.body());
    }
}