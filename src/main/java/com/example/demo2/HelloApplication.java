package com.example.demo2;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.control.Alert;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("mytunes-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 1320, 840);
            var stylesheet = HelloApplication.class.getResource("app.css");
            if (stylesheet != null) {
                scene.getStylesheets().add(stylesheet.toExternalForm());
            }
            stage.setTitle("MyTunes — Music & Video");
            stage.setMinWidth(1080);
            stage.setMinHeight(700);
            stage.setScene(scene);
            stage.show();
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Startup error");
            alert.setHeaderText("Failed to load application UI");
            alert.setContentText(ex.getMessage());
            alert.showAndWait();
            throw ex instanceof IOException ? (IOException) ex : new IOException(ex);
        }
    }
}

