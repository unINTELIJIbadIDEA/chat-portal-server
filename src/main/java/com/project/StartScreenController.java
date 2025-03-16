package com.project;
import javafx.animation.ScaleTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.util.Duration;


import java.io.IOException;

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
                FXMLLoader fxmlLoader = new FXMLLoader(StartScreenController.class.getResource("C:/Users/lap_h/IdeaProjects/chat-portal/src/main/resources/com/project/loginscreen.fxml"));
                Scene scene = new Scene(fxmlLoader.load());

                Stage loginStage = new Stage();
                loginStage.setTitle("Logowanie");
                loginStage.setScene(scene);
                loginStage.show();
            } catch (IOException e) {
                e.printStackTrace();
            }

    }

    // Akcja dla przycisku "SIGN UP"
    private void handleSignUp() {
        System.out.println("Przycisk SIGN UP kliknięty");
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
}