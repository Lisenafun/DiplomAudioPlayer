package sample;

import javafx.application.*;
import javafx.beans.InvalidationListener;
import javafx.beans.value.*;
import javafx.collections.*;
import javafx.event.*;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.MapValueFactory;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.media.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Callback;
import javafx.util.Duration;

import java.io.File;
import java.util.*;

/** Пример проигрывания файлов из заданного каталога */
public class AudioPlaylist extends Application {
    private static final String MUSIC_DIR = "D:\\Music\\MusicNew";
    private static final String COLUMN_NAME = "Name";
    private static final String COLUMN_VALUE = "Value";
    private static final List<String> SUPPORTED_FILE_EXTENSIONS = Arrays.asList(".mp3", ".m4a");
    private static final int FILE_EXTENSION_LEN = 4;

    private final Label currentlyPlaying = new Label();
    private final ProgressBar progress = new ProgressBar();
    private final TableView<Map> metadataTable = new TableView<>();
    private ChangeListener<Duration> progressChangeListener;
    private MapChangeListener<String, Object> metadataChangeListener;

    public static void main(String[] args) throws Exception { launch(args); }

    public void start(final Stage stage) throws Exception {
        stage.setTitle("My Audio Player");

        // определить исходный каталог для списка воспроизведения (либо первый аргумент программы, либо значение по умолчанию).
        final List<String> params = getParameters().getRaw();
        final File dir = (params.size() > 0) ? new File(params.get(0)) : new File(MUSIC_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            System.out.println("Не удается найти аудиофайл в: " + dir);
            Platform.exit();
            return;
        }

        // создаем несколько медиаплееров
        final List<MediaPlayer> players = new ArrayList<>();
        //FilenameFilter(accept)
        for (String pathName : dir.list((dir1, name) -> {
            for (String ext: SUPPORTED_FILE_EXTENSIONS) {
                if (name.endsWith(ext)) {
                    return true;
                }
            }
            return false;
        })) players.add(createPlayer("file:///" + (dir + "\\" + pathName).replace("\\", "/").replaceAll(" ", "%20")));
        if (players.isEmpty()) {
            System.out.println("Аудиофайлы не найдены " + dir);
            Platform.exit();
            return;
        }

        // создаем визуализацию для медиаплееров
        final MediaView mediaView = new MediaView(players.get(0));
        final Button skip = new Button("Skip");
        final Button play = new Button("Pause");
        final Button stop = new Button ("Stop");

        Label volumeLabel = new Label("Volume:");
        final Slider volumeSlider = new Slider (0,100,50);
        volumeSlider.setMaxWidth(Double.MAX_VALUE);
        volumeSlider.valueProperty().addListener(ov -> {
            final MediaPlayer mp = mediaView.getMediaPlayer ();
            if (volumeSlider.isPressed ()) {
                mp.setVolume(volumeSlider.getValue() / 100.0);
            }
        });

        // воспроизводим каждый аудиофайл по очереди.
        for (int i = 0; i < players.size(); i++) {
            final MediaPlayer player = players.get(i);
            final MediaPlayer nextPlayer = players.get((i + 1) % players.size());
            player.setOnEndOfMedia(new Runnable() {
                @Override public void run() {
                    player.currentTimeProperty().removeListener(progressChangeListener);//удаляет слушателя, когда длиельность файла заканчивается
                    player.getMedia().getMetadata().removeListener(metadataChangeListener);
                    player.stop();
                    mediaView.setMediaPlayer(nextPlayer);
                    nextPlayer.play();
                }
            });
        }

        // пропустить трек
        skip.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent actionEvent) {
                final MediaPlayer curPlayer = mediaView.getMediaPlayer();
                curPlayer.currentTimeProperty().removeListener(progressChangeListener);
                curPlayer.getMedia().getMetadata().removeListener(metadataChangeListener);
                curPlayer.stop();
                MediaPlayer nextPlayer = players.get((players.indexOf(curPlayer) + 1) % players.size());
                mediaView.setMediaPlayer(nextPlayer);
                if ("Pause".equals(play.getText()) & skip.isArmed ()) {
                    nextPlayer.play ();
                    nextPlayer.setVolume (volumeSlider.getValue ()/100.0);
                } else if("Play".equals(play.getText()) & skip.isArmed ()){
                    nextPlayer.pause ();
                    nextPlayer.setVolume (volumeSlider.getValue ()/100.0);
                }
            }
        });

        // проигрываем или ставим трек на паузу
        play.setOnAction(new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent actionEvent) {
                if ("Pause".equals(play.getText())) {
                    mediaView.getMediaPlayer().pause();
                    play.setText("Play");
                } else {
                    mediaView.getMediaPlayer().play();
                    play.setText("Pause");
                }
            }
        });
        stop.setOnAction (new EventHandler<ActionEvent> () {
            @Override public void handle(ActionEvent event) {
                final MediaPlayer curPlayer = mediaView.getMediaPlayer();
                curPlayer.currentTimeProperty().removeListener(progressChangeListener);
                curPlayer.getMedia().getMetadata().removeListener(metadataChangeListener);
                curPlayer.stop();
                if ("Pause".equals(play.getText()) & stop.isArmed ()) {
                    play.setText("Play");
                }
            }
        });
        // отображаем название текущего трека
        mediaView.mediaPlayerProperty().addListener(new ChangeListener<MediaPlayer>() {
            @Override public void changed(ObservableValue<? extends MediaPlayer> observableValue, MediaPlayer oldPlayer, MediaPlayer newPlayer) {
                setCurrentlyPlaying(newPlayer);
            }
        });

        // начинаем проигрывание с первого трека
        mediaView.setMediaPlayer(players.get(0));
        mediaView.getMediaPlayer().play();
        setCurrentlyPlaying(mediaView.getMediaPlayer());

        // делаем так, чтобы кнопка play/pause была одного размера.
        Button invisiblePause = new Button("Pause");
        invisiblePause.setVisible(false);
        play.prefHeightProperty().bind(invisiblePause.heightProperty());
        play.prefWidthProperty().bind(invisiblePause.widthProperty());

        // добавляем таблицу с метаданными для дисплея
        metadataTable.setStyle("-fx-font-size: 13px;");

        //столбцы
        TableColumn<Map, String> nameColumn = new TableColumn<>(COLUMN_NAME);
        nameColumn.setPrefWidth(150); //предпочтительная ширина
        TableColumn<Map, Object> valueColumn = new TableColumn<>(COLUMN_VALUE);
        valueColumn.setPrefWidth(400);

        //привязка столбца к определенному св-ву данных
        nameColumn.setCellValueFactory(new MapValueFactory<>(COLUMN_NAME));
        valueColumn.setCellValueFactory(new MapValueFactory<>(COLUMN_VALUE));

        metadataTable.setEditable(true);//делает таблицу доступной для редактирования
        metadataTable.getSelectionModel().setCellSelectionEnabled(true);//дает возможность выбрать ячейку, по умолчанию стоит выбор строки
        metadataTable.getColumns().setAll(nameColumn, valueColumn); //добавляем столбцы в таблицу
        valueColumn.setCellFactory(new Callback<TableColumn<Map, Object>, TableCell<Map, Object>>() {
            @Override
            public TableCell<Map, Object> call(TableColumn<Map, Object> column) {
                return new TableCell<Map, Object>() {
                    @Override
                    protected void updateItem(Object item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null) {
                            if (item instanceof String) {
                                setText((String) item);
                                setGraphic(null);
                            } else if (item instanceof Integer) {
                                setText(Integer.toString((Integer) item));
                                setGraphic(null);
                            } else if (item instanceof Image) {
                                setText(null);
                                ImageView imageView = new ImageView((Image) item);
                                imageView.setFitWidth(200);
                                imageView.setPreserveRatio(true);
                                setGraphic(imageView);
                            } else {
                                setText("N/A");
                                setGraphic(null);
                            }
                        } else {
                            setText(null);
                            setGraphic(null);
                        }
                    }
                };
            }
        });

        //позиционирование элементов
        //стек
        final StackPane layout = new StackPane();
        layout.setStyle("-fx-background-color: paleturquoise; -fx-font-size: 20; -fx-padding: 20; -fx-alignment: center;");
        //горизонтальный столбец - прогресс бар
        final HBox progressBar = new HBox(10);
        progressBar.setAlignment(Pos.CENTER);
        progressBar.getChildren().setAll(play, stop, skip, progress);

        final HBox volumeBar = new HBox (10);
        volumeBar.setAlignment (Pos.CENTER);
        volumeBar.setPrefWidth (Double.MAX_VALUE);
        volumeBar.getChildren ().setAll (volumeLabel, volumeSlider);

        //вертикальный столбец
        final VBox content = new VBox(10);
        content.getChildren().setAll(
                currentlyPlaying,
                progressBar,
                volumeBar,
                metadataTable
        );

        layout.getChildren().addAll(
                invisiblePause,
                content
        );
        progress.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow (progress, Priority.ALWAYS);
        HBox.setHgrow(volumeSlider, Priority.ALWAYS);
        VBox.setVgrow(metadataTable, Priority.ALWAYS);

        Scene scene = new Scene(layout, 600, 400);
        stage.setScene(scene);
        stage.show();
    }

    /** устанавливаем текущую воспроизводящуюся метку на метку нового медиаплеера и обновляем монитор прогресса. */
    private void setCurrentlyPlaying(final MediaPlayer newPlayer) {
        newPlayer.seek(Duration.ZERO);

        progress.setProgress(0);
        progressChangeListener = new ChangeListener<Duration>() {
            @Override public void changed(ObservableValue<? extends Duration> observableValue, Duration oldValue, Duration newValue) {
                progress.setProgress(1.0 * newPlayer.getCurrentTime().toMillis() / newPlayer.getTotalDuration().toMillis());
            }
        };
        newPlayer.currentTimeProperty().addListener(progressChangeListener);

        //вывод имени трека в прогресс бар
        String source = newPlayer.getMedia().getSource();
        source = source.substring(0, source.length() - FILE_EXTENSION_LEN);//обрезаем формат файла
        source = source.substring(source.lastIndexOf("/") + 1).replaceAll("%20", " ");
        currentlyPlaying.setText("Now Playing: " + source);

        setMetaDataDisplay(newPlayer.getMedia().getMetadata());
    }
    //передаем метаданные в дисплей
    private void setMetaDataDisplay(ObservableMap<String, Object> metadata) {
        //для ObservableList таблицы мы присваиваем значения ObservableList полученных метаданных
        metadataTable.getItems().setAll(convertMetadataToTableData (metadata));
//        Вызывается после внесения изменений в ObservableMap.
        metadataChangeListener = new MapChangeListener<String, Object>() {
            @Override
            public void onChanged(Change<? extends String, ?> change) {
                metadataTable.getItems().setAll(convertMetadataToTableData (metadata));
            }
        };
        metadata.addListener(metadataChangeListener);
    }
    //получаем из ObservableMap - ObservableList
    private ObservableList<Map> convertMetadataToTableData(ObservableMap<String, Object> metadata) {
        ObservableList<Map> allData = FXCollections.observableArrayList();

        for (String key: metadata.keySet()) {
            Map<String, Object> dataRow = new HashMap<>();

            dataRow.put(COLUMN_NAME, key);
            dataRow.put(COLUMN_VALUE, metadata.get(key));

            allData.add(dataRow);
        }

        return allData;
    }

    //возвращает MediaPlayer для данного источника, который будет сообщать о любых ошибках, с которыми он сталкивается
    private MediaPlayer createPlayer(String mediaSource) {
        final Media media = new Media(mediaSource);
        final MediaPlayer player = new MediaPlayer(media);
        player.setOnError(new Runnable() {
            @Override public void run() {
                System.out.println("Произошла ошибка носителя: " + player.getError());
            }
        });
        return player;
    }
}
