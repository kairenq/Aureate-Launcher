package com.aureate.client;

import com.aureate.core.updater.Downloader;
import com.aureate.core.model.BuildManifest;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public class MainController {

    @FXML
    private Button updateButton;

    @FXML
    private Button playButton;

    @FXML
    private Label statusLabel;

    private BuildManifest manifest;

    // Вызывается после инициализации FXML
    @FXML
    public void initialize() {
        statusLabel.setText("Готово");

        // Кнопка обновления сборки
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

        // Кнопка запуска Minecraft
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
    }

    // Метод для установки манифеста сборки из LauncherApp
    public void setManifest(BuildManifest manifest) {
        this.manifest = manifest;
    }
}
