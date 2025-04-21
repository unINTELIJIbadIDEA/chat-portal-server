package com.project;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

public class ChatController {

    @FXML
    private ListView<String> messageList;

    @FXML
    private TextField messageField;

    @FXML
    private Button sendButton;

    @FXML
    public void sendMessage() {
        String text = messageField.getText().trim();
        if (!text.isEmpty()) {
            messageList.getItems().add("Ty: " + text);
            messageField.clear();

            messageList.scrollTo(messageList.getItems().size() - 1);
        }
    }
}