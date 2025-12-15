package com.aureate.client;

import com.aureate.core.updater.Downloader;
import com.aureate.core.model.BuildManifest;
import com.aureate.core.http.HttpClientHelper;
import com.google.gson.Gson;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.ArrayList;
import java.util.List;

public class MainController {

    private Gson gson = new Gson();

    @FXML
    private TabPane mainTabPane;

    @FXML
    private ListView<BuildManifest> buildsListView;

    @FXML
    private Button updateBuildButton;

    @FXML
    private Button launchGameButton;

    @FXML
    private ProgressBar downloadProgressBar;

    @FXML
    private Label statusLabel;

    private ObservableList<BuildManifest> builds = FXCollections.observableArrayList();
    private BuildManifest selectedManifest;

    @FXML
    public void initialize() {
        statusLabel.setText("Готово");

        // Загрузка доступных сборок
        builds.addAll(loadBuilds());
        buildsListView.setItems(builds);

        // Настройка отображения сборок как карточек
        buildsListView.setCellFactory(listView -> new ListCell<BuildManifest>() {
            @Override
            protected void updateItem(BuildManifest item, boolean empty) {
                super.updateItem(item, empty);
                System.out.println("Update item for " + item + " empty: " + empty + " selected: " + isSelected());
                if (empty || item == null || item.id.isEmpty()) {
                    setText(null);
                    setGraphic(null);
                } else {
                    VBox card = new VBox();
                    card.setSpacing(5);
                    String baseStyle = "-fx-padding: 10; -fx-border-width: 1; -fx-background-radius: 5; -fx-border-radius: 5;";
                    if (isSelected()) {
                        card.setStyle(baseStyle + "-fx-background-color: #3498db; -fx-border-color: #2980b9;");
                    } else {
                        card.setStyle(baseStyle + "-fx-background-color: #ecf0f1; -fx-border-color: #bdc3c7;");
                    }
                    Label title = new Label(item.toString());
                    title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: " + (isSelected() ? "white" : "black") + ";");
                    card.getChildren().add(title);
                    card.setOnMouseClicked(event -> {
                        System.out.println("Clicked on card for index " + getIndex());
                        getListView().getSelectionModel().select(getIndex());
                        getListView().refresh();
                        System.out.println("Selected index: " + getListView().getSelectionModel().getSelectedIndex());
                    });
                    setGraphic(card);
                }
            }
        });

        // Выбрать первую по умолчанию
        buildsListView.getSelectionModel().selectFirst();

        // Обработчик выбора сборки
        buildsListView.getSelectionModel().selectedIndexProperty().addListener((obs, oldIndex, newIndex) -> {
            System.out.println("Selection index changed from " + oldIndex + " to " + newIndex);
            if (newIndex.intValue() > 0) {
                BuildManifest selected = buildsListView.getItems().get(newIndex.intValue());
                loadManifestForBuild(selected);
            }
        });
        System.out.println("Listener added");

        // Кнопка обновления выбранной сборки
        updateBuildButton.setOnAction(e -> {
            if (selectedManifest != null) {
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("Скачивание...");
                    downloadProgressBar.setVisible(true);
                    downloadProgressBar.setProgress(0);
                });

                new Thread(() -> {
                    Downloader downloader = new Downloader(4);
                    try {
                        downloader.installManifest(selectedManifest, new Downloader.ProgressListener() {
                            @Override
                            public void onFileProgress(com.aureate.core.model.FileEntry entry, long downloaded, long total) {
                                javafx.application.Platform.runLater(() -> {
                                    if (total > 0) downloadProgressBar.setProgress((double) downloaded / total);
                                });
                            }

                            @Override
                            public void onFileCompleted(com.aureate.core.model.FileEntry entry) {
                                // no-op
                            }

                            @Override
                            public void onFileFailed(com.aureate.core.model.FileEntry entry, Exception ex) {
                                javafx.application.Platform.runLater(() -> {
                                    statusLabel.setText("Ошибка при скачивании: " + ex.getMessage());
                                });
                            }

                            @Override
                            public void onOverallProgress(long downloaded, long total) {
                                javafx.application.Platform.runLater(() -> {
                                    double p = total > 0 ? ((double) downloaded) / total : 0.0;
                                    downloadProgressBar.setProgress(Math.max(0.0, Math.min(1.0, p)));
                                });
                            }
                        });

                        javafx.application.Platform.runLater(() -> {
                            downloadProgressBar.setVisible(false);
                            statusLabel.setText("Сборка обновлена!");
                        });
                    } catch (Exception ex) {
                        javafx.application.Platform.runLater(() -> {
                            downloadProgressBar.setVisible(false);
                            statusLabel.setText("Ошибка: " + ex.getMessage());
                            Alert a = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
                            a.setHeaderText("Ошибка при обновлении сборки");
                            a.showAndWait();
                        });
                        ex.printStackTrace();
                    } finally {
                        downloader.shutdown();
                    }
                }, "download-thread").start();
            } else {
                statusLabel.setText("Выберите сборку!");
            }
        });

        // Кнопка запуска игры
        launchGameButton.setOnAction(e -> {
            if (selectedManifest != null) {
                java.nio.file.Path clientJar = com.aureate.core.LauncherPaths.getInstanceDir(selectedManifest.getBuildId()).resolve("client.jar");
                if (java.nio.file.Files.exists(clientJar)) {
                    statusLabel.setText("Запуск игры...");
                    try {
                        LauncherUtils.launchMinecraft(selectedManifest);
                        statusLabel.setText("Игра запущена!");
                    } catch (Exception ex) {
                        statusLabel.setText("Ошибка запуска: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                } else {
                    statusLabel.setText("Сборка не установлена! Сначала обновите.");
                }
            } else {
                statusLabel.setText("Выберите сборку!");
            }
        });
    }

    private List<BuildManifest> loadBuilds() {
        List<BuildManifest> builds = new ArrayList<>();
        BuildManifest dummy = new BuildManifest();
        dummy.id = "";
        builds.add(dummy);
        try {
            String manifestUrl = getVersionUrl("1.20.1");
            BuildManifest bm = new BuildManifest();
            bm.id = "1.20.1";
            bm.url = manifestUrl;
            builds.add(bm);
        } catch (Exception e) {
            System.out.println("Error loading version manifest: " + e.getMessage());
            // Fallback to old URL
            BuildManifest bm = new BuildManifest();
            bm.id = "1.20.1";
            bm.url = "https://piston-meta.mojang.com/v1/packages/8da8881815e3d53da86adbdc051f836d0c4e2b93/1.20.1.json";
            builds.add(bm);
        }
        return builds;
    }

    private String getVersionUrl(String versionId) throws Exception {
        String manifestJson = HttpClientHelper.getString("https://piston-meta.mojang.com/mc/game/version_manifest.json");
        // Parse JSON to find version
        com.google.gson.JsonObject manifest = gson.fromJson(manifestJson, com.google.gson.JsonObject.class);
        com.google.gson.JsonArray versions = manifest.getAsJsonArray("versions");
        for (com.google.gson.JsonElement el : versions) {
            com.google.gson.JsonObject ver = el.getAsJsonObject();
            if (versionId.equals(ver.get("id").getAsString())) {
                return ver.get("url").getAsString();
            }
        }
        throw new Exception("Version not found");
    }

    private void loadManifestForBuild(BuildManifest bm) {
        System.out.println("Loading manifest for: " + bm.id + ", url: " + bm.url);
        try {
            String json = HttpClientHelper.getString(bm.url);
            selectedManifest = gson.fromJson(json, BuildManifest.class);
            // Ensure buildId is set (some manifests use public `id` field)
            if (selectedManifest.getBuildId() == null || selectedManifest.getBuildId().isEmpty()) {
                if (selectedManifest.id != null && !selectedManifest.id.isEmpty()) selectedManifest.setBuildId(selectedManifest.id);
                else if (selectedManifest.getMcVersion() != null) selectedManifest.setBuildId(selectedManifest.getMcVersion());
            }
            System.out.println("Manifest loaded, id: " + selectedManifest.id + ", buildId: " + selectedManifest.getBuildId() + ", mainClass: " + selectedManifest.getMainClass());
            statusLabel.setText("Манифест загружен для " + bm.id);
        } catch (Exception e) {
            System.out.println("Error loading manifest: " + e.getMessage());
            statusLabel.setText("Ошибка загрузки манифеста");
            e.printStackTrace();
        }
    }
}
