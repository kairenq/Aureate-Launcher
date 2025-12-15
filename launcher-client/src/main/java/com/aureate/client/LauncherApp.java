package com.aureate.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class LauncherApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        // Загрузочный экран
        showLoadingScreen(stage);

        // Имитация загрузки (в реальности проверка Java, загрузка ресурсов)
        Thread.sleep(2000); // Симуляция

        // Загрузка основного UI
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/aureate/client/ui/main.fxml"));
        Scene scene = new Scene(loader.load(), 800, 600);
        stage.setTitle("Aureate Launcher");
        stage.setScene(scene);
        stage.show();
    }

    private void showLoadingScreen(Stage stage) {
        VBox loadingRoot = new VBox(20);
        loadingRoot.setStyle("-fx-alignment: center; -fx-padding: 50;");
        Text loadingText = new Text("Загрузка Aureate Launcher...");
        ProgressBar progressBar = new ProgressBar();
        progressBar.setPrefWidth(300);
        loadingRoot.getChildren().addAll(loadingText, progressBar);

        Scene loadingScene = new Scene(loadingRoot, 400, 200);
        stage.setScene(loadingScene);
        stage.setTitle("Загрузка...");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
