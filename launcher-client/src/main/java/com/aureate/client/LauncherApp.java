package com.aureate.client;

import com.aureate.core.updater.Downloader;
import com.aureate.core.model.BuildManifest;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.InputStreamReader;
import com.google.gson.Gson;

public class LauncherApp extends Application {

    private Label statusLabel = new Label("Готово");
    private Button updateButton = new Button("Обновить сборку");
    private Button playButton = new Button("Запустить игру");

    @Override
    public void start(Stage stage) throws Exception {
        // Загружаем манифест (пример)
        BuildManifest manifest = new Gson().fromJson(
            new InputStreamReader(getClass().getResourceAsStream("/com/aureate/client/ui/sample-manifest.json")),
            BuildManifest.class
        );

        VBox root = new VBox(12, updateButton, playButton, statusLabel);
        root.setStyle("-fx-padding: 20; -fx-alignment: center;");

        updateButton.setOnAction(e -> {
            statusLabel.setText("Скачивание...");
            try {
                Downloader downloader = new Downloader(4); // число потоков
                downloader.installManifest(manifest, null);
                statusLabel.setText("Сборка обновлена!");
            } catch (Exception ex) {
                statusLabel.setText("Ошибка: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        playButton.setOnAction(e -> {
            statusLabel.setText("Запуск игры...");
            try {
                LauncherUtils.launchMinecraft(manifest);
                statusLabel.setText("Игра запущена!");
            } catch (Exception ex) {
                statusLabel.setText("Ошибка запуска: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        stage.setScene(new Scene(root, 400, 200));
        stage.setTitle("Aureate Launcher");
        stage.setResizable(false);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
