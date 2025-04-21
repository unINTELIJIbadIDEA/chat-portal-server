package com.project;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;

public class TextWelcomeController {

    @FXML
    private Label welcomeLabel;

    public void initialize() {
        ScaleTransition scaleTransition = new ScaleTransition(Duration.seconds(1.3), welcomeLabel);
        scaleTransition.setFromX(1.0);
        scaleTransition.setFromY(1.0);
        scaleTransition.setToX(1.1);
        scaleTransition.setToY(1.1);

        scaleTransition.setOnFinished(event -> {
            FadeTransition fadeOut = new FadeTransition(Duration.seconds(1.3), welcomeLabel);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setCycleCount(1);
            fadeOut.setAutoReverse(false);

            fadeOut.setOnFinished(e -> {
                try {
                    URL resource = HelloApplication.class.getResource("chatscreen.fxml");
                    FXMLLoader loader = new FXMLLoader(resource);
                    AnchorPane chatLayout = loader.load();

                    Scene chatScene = new Scene(chatLayout);
                    FadeTransition fadeIn = new FadeTransition(Duration.seconds(1), chatLayout);
                    fadeIn.setFromValue(0);
                    fadeIn.setToValue(1);
                    fadeIn.play();

                    Stage stage = (Stage) welcomeLabel.getScene().getWindow();
                    stage.setScene(chatScene);
                    stage.setMaximized(true);
                    stage.show();

                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });

            fadeOut.play();
        });

        scaleTransition.play();
    }
}
