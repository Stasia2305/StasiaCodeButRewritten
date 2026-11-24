package com.example.demo2.gui;

import com.example.demo2.bll.PlaylistService;
import com.example.demo2.bll.SongService;
import com.example.demo2.entities.Playlist;
import com.example.demo2.entities.Song;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.function.IntConsumer;

/**
 * Main controller for the MyTunes UI.
 * Handles songs, playlists and playback of both audio files and music videos.
 */
public class MyTunesController {
    // Header
    @FXML private Label statusLabel;
    @FXML private TextField filterField;
    @FXML private Button filterButton;

    // Left: playlists
    @FXML private ListView<Playlist> playlistList;

    // Center: songs in selected playlist
    @FXML private Label playlistNameLabel;
    @FXML private ListView<Song> playlistSongsList;

    // Center: all tracks
    @FXML private Label libraryCountLabel;
    @FXML private TableView<Song> songTable;
    @FXML private TableColumn<Song, String> titleCol;
    @FXML private TableColumn<Song, String> artistCol;
    @FXML private TableColumn<Song, String> typeCol;
    @FXML private TableColumn<Song, String> durationCol;

    // Right: now playing / video stage
    @FXML private StackPane mediaStage;
    @FXML private MediaView mediaView;
    @FXML private Label mediaPlaceholder;
    @FXML private Label mediaBadge;
    @FXML private Label nowTitle;
    @FXML private Label nowMeta;

    // Bottom: playback bar
    @FXML private Button playPauseButton;
    @FXML private Slider progressSlider;
    @FXML private Slider volumeSlider;
    @FXML private Label currentTimeLabel;
    @FXML private Label totalTimeLabel;

    private final SongService songService = new SongService();
    private final PlaylistService playlistService = new PlaylistService();

    private final ObservableList<Song> songs = FXCollections.observableArrayList();
    private final ObservableList<Playlist> playlists = FXCollections.observableArrayList();
    private final ObservableList<Song> songsInSelectedPlaylist = FXCollections.observableArrayList();

    // Playback state
    private MediaPlayer mediaPlayer;
    private ObservableList<Song> currentQueue = FXCollections.observableArrayList();
    private int currentIndex = -1;
    private boolean seeking = false;
    private boolean mediaErrorShown = false;
    private Stage theaterStage;

    @FXML
    private void initialize() {
        setupTableColumns();
        setupListViews();
        setupEventHandlers();
        setupPlayerBindings();

        playlistList.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            refreshPlaylistSongs();
            playlistNameLabel.setText(sel == null ? "No playlist selected" : sel.getName());
        });

        songTable.setPlaceholder(new Label("Your library is empty — add a track to get started."));
        playlistSongsList.setPlaceholder(new Label("Select a playlist to see its tracks."));
        playlistList.setPlaceholder(new Label("No playlists yet."));

        installShortcuts();

        // Initial load
        reloadAll();
    }

    // ===== Setup =====

    private void setupTableColumns() {
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        artistCol.setCellValueFactory(new PropertyValueFactory<>("artist"));
        typeCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getMediaTypeDisplay()));
        durationCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDurationDisplay()));
        durationCol.setCellFactory(column -> new TableCell<>() {
            {
                setAlignment(Pos.CENTER_RIGHT);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        });
        songTable.setItems(songs);
    }

    private void setupListViews() {
        playlistList.setItems(playlists);
        playlistSongsList.setItems(songsInSelectedPlaylist);

        playlistList.setCellFactory(lv -> new ListCell<>() {
            private final Label icon = new Label("♫");
            private final Label name = new Label();
            private final HBox box = new HBox(9, icon, name);

            {
                box.setAlignment(Pos.CENTER_LEFT);
                icon.getStyleClass().add("playlist-icon");
                name.getStyleClass().add("playlist-name");
            }

            @Override
            protected void updateItem(Playlist item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    name.setText(item.getName());
                    setGraphic(box);
                }
            }
        });

        playlistSongsList.setCellFactory(lv -> new ListCell<>() {
            private final Label title = new Label();
            private final Label meta = new Label();
            private final VBox box = new VBox(2, title, meta);

            {
                title.getStyleClass().add("track-title");
                meta.getStyleClass().add("track-meta");
            }

            @Override
            protected void updateItem(Song item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    title.setText(item.getTitle());
                    meta.setText(item.getArtist() + "  ·  " + item.getDurationDisplay()
                            + "  ·  " + item.getMediaTypeDisplay());
                    setGraphic(box);
                }
            }
        });
    }

    private void setupEventHandlers() {
        // Double-click track in library to play
        songTable.setRowFactory(tv -> {
            TableRow<Song> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    playFromTable(row.getItem());
                }
            });
            return row;
        });

        // Double-click track in playlist to play
        playlistSongsList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Song s = playlistSongsList.getSelectionModel().getSelectedItem();
                if (s != null) playFromPlaylist(s);
            }
        });
    }

    private void setupPlayerBindings() {
        mediaView.setVisible(false);
        // The video is only revealed once the media reports real picture dimensions
        bindVideoSize(mediaStage, 20);
        progressSlider.setDisable(true);

        progressSlider.setOnMousePressed(e -> seeking = true);
        progressSlider.setOnMouseReleased(e -> {
            seeking = false;
            if (mediaPlayer != null) {
                mediaPlayer.seek(Duration.seconds(progressSlider.getValue()));
            }
        });
        progressSlider.valueProperty().addListener((obs, old, val) -> {
            if (seeking) currentTimeLabel.setText(formatDuration(val.intValue()));
        });

        volumeSlider.valueProperty().addListener((obs, old, val) -> applyVolume());
    }

    private void installShortcuts() {
        Platform.runLater(() -> {
            Scene scene = statusLabel.getScene();
            if (scene == null) return;
            scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                Node focus = scene.getFocusOwner();
                if (focus instanceof TextInputControl) return;

                switch (event.getCode()) {
                    case SPACE -> {
                        onPlayPause();
                        event.consume();
                    }
                    case RIGHT -> {
                        seekBy(5);
                        event.consume();
                    }
                    case LEFT -> {
                        seekBy(-5);
                        event.consume();
                    }
                    default -> { }
                }
            });
        });
    }

    private void reloadAll() {
        try {
            songs.setAll(songService.getAll());
            playlists.setAll(playlistService.getAll());
            refreshPlaylistSongs();
            updateCounts();
            // Try to auto-fill durations for any songs that have unknown duration (0)
            autofillUnknownDurations();
        } catch (Exception e) {
            showError("Failed to load data", e);
        }
    }

    private void refreshPlaylistSongs() {
        try {
            Playlist selected = playlistList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                songsInSelectedPlaylist.clear();
            } else {
                songsInSelectedPlaylist.setAll(playlistService.getSongs(selected.getId()));
            }
        } catch (Exception e) {
            showError("Failed to refresh playlist", e);
        }
    }

    private void updateCounts() {
        libraryCountLabel.setText(songs.size() + (songs.size() == 1 ? " track" : " tracks"));
    }

    // ===== Playback =====

    @FXML
    private void onPlayPause() {
        if (mediaPlayer == null) {
            onPlay();
            return;
        }
        switch (mediaPlayer.getStatus()) {
            case PLAYING -> {
                mediaPlayer.pause();
                setStatus("Paused");
            }
            case PAUSED, READY, STOPPED -> {
                mediaPlayer.play();
                setStatus("Playing");
            }
            default -> { }
        }
    }

    @FXML
    private void onPlay() {
        if (mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.PAUSED) {
            mediaPlayer.play();
            setStatus("Playing");
            return;
        }

        // Determine current context: playlist if selected and has songs, else song table
        Playlist selPl = playlistList.getSelectionModel().getSelectedItem();
        if (selPl != null && !songsInSelectedPlaylist.isEmpty()) {
            Song song = playlistSongsList.getSelectionModel().getSelectedItem();
            if (song == null) {
                playlistSongsList.getSelectionModel().select(0);
                song = playlistSongsList.getSelectionModel().getSelectedItem();
            }
            if (song != null) playQueue(songsInSelectedPlaylist, playlistSongsList.getSelectionModel().getSelectedIndex());
            return;
        }

        Song song = songTable.getSelectionModel().getSelectedItem();
        if (song == null && !songs.isEmpty()) {
            songTable.getSelectionModel().select(0);
            song = songTable.getSelectionModel().getSelectedItem();
        }
        if (song != null) playQueue(songs, songTable.getSelectionModel().getSelectedIndex());
        else setStatus("No track to play");
    }

    @FXML
    private void onStop() {
        if (mediaPlayer == null) return;
        mediaPlayer.stop();
        progressSlider.setValue(0);
        currentTimeLabel.setText("0:00");
        setStatus("Stopped");
    }

    @FXML
    private void onNext() {
        if (currentQueue.isEmpty()) return;
        currentIndex = (currentIndex + 1) % currentQueue.size();
        playCurrent();
    }

    @FXML
    private void onPrevious() {
        if (currentQueue.isEmpty()) return;
        // Restart the current track first when we are well into it
        if (mediaPlayer != null && mediaPlayer.getCurrentTime().toSeconds() > 3) {
            mediaPlayer.seek(Duration.ZERO);
            return;
        }
        currentIndex = (currentIndex - 1 + currentQueue.size()) % currentQueue.size();
        playCurrent();
    }

    private void seekBy(double seconds) {
        if (mediaPlayer == null) return;
        Duration target = mediaPlayer.getCurrentTime().add(Duration.seconds(seconds));
        if (target.lessThan(Duration.ZERO)) target = Duration.ZERO;
        Duration total = mediaPlayer.getTotalDuration();
        if (total != null && !total.isUnknown() && target.greaterThan(total)) target = total;
        mediaPlayer.seek(target);
    }

    private void applyVolume() {
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(volumeSlider.getValue() / 100.0);
        }
    }

    private void playFromTable(Song s) {
        playQueue(songs, songs.indexOf(s));
    }

    private void playFromPlaylist(Song s) {
        playQueue(songsInSelectedPlaylist, songsInSelectedPlaylist.indexOf(s));
    }

    private void playQueue(ObservableList<Song> queue, int startIndex) {
        if (startIndex < 0 || startIndex >= queue.size()) return;
        currentQueue = FXCollections.observableArrayList(queue);
        currentIndex = startIndex;
        playCurrent();
    }

    private void playCurrent() {
        if (currentQueue.isEmpty()) {
            setStatus("Nothing in the queue");
            return;
        }
        if (currentIndex < 0) currentIndex = 0;
        if (currentIndex >= currentQueue.size()) currentIndex = 0;

        Song s = currentQueue.get(currentIndex);
        String source = s.getFilePath();
        if (!s.isUrl()) {
            if (!new File(source).exists()) {
                setStatus("File not found: " + s.getTitle());
                return;
            }
            source = new File(s.getFilePath()).toURI().toString();
        }

        disposePlayer();
        showVideoStage(false);
        mediaPlaceholder.setText("…");
        mediaErrorShown = false;

        nowTitle.setText(s.getTitle());
        nowMeta.setText(s.getArtist() + (s.isUrl() ? "  ·  streaming" : ""));
        currentTimeLabel.setText("0:00");
        totalTimeLabel.setText("0:00");
        progressSlider.setValue(0);
        progressSlider.setDisable(false);
        setStatus("Loading: " + s.getTitle());

        try {
            Media media = new Media(source);
            MediaPlayer player = new MediaPlayer(media);
            mediaPlayer = player;
            mediaView.setMediaPlayer(player);

            player.setOnReady(() -> {
                // Real picture size: 0 means the media has no decodable video track
                double width = media.getWidth();
                double height = media.getHeight();
                boolean hasVideo = width > 0 && height > 0;
                showVideoStage(hasVideo);

                if (hasVideo) {
                    mediaBadge.setText(s.isUrl() ? "VIDEO STREAM" : "MUSIC VIDEO");
                    nowMeta.setText(s.getArtist() + "  ·  " + media.getWidth() + "×" + media.getHeight());
                } else if (s.isVideo()) {
                    mediaBadge.setText("AUDIO ONLY");
                    nowMeta.setText(s.getArtist() + "  ·  video track not decodable");
                    setStatus("No decodable video track — playing audio only (try H.264 in MP4/MOV)");
                } else {
                    mediaBadge.setText(s.isUrl() ? "AUDIO STREAM" : "AUDIO");
                    nowMeta.setText(s.getArtist() + (s.isUrl() ? "  ·  stream" : "  ·  audio"));
                }

                Duration total = media.getDuration();
                double seconds = total.isUnknown() ? 0 : total.toSeconds();
                progressSlider.setMax(Math.max(1, seconds));
                totalTimeLabel.setText(seconds > 0 ? formatDuration((int) Math.round(seconds)) : "—");

                applyVolume();
                player.play();
                if (!hasVideo || s.isVideo()) {
                    setStatus("Playing: " + s.getTitle());
                }
                cacheDuration(s, seconds);
            });

            player.currentTimeProperty().addListener((obs, old, now) -> {
                if (!seeking) {
                    progressSlider.setValue(now.toSeconds());
                    currentTimeLabel.setText(formatDuration((int) Math.floor(now.toSeconds())));
                }
            });

            player.statusProperty().addListener((obs, old, status) -> {
                switch (status) {
                    case PLAYING -> playPauseButton.setText("❚❚");
                    case PAUSED, STOPPED -> playPauseButton.setText("▶");
                    default -> { }
                }
            });

            player.setOnEndOfMedia(() -> {
                if (currentIndex < currentQueue.size() - 1) {
                    currentIndex++;
                    playCurrent();
                } else {
                    progressSlider.setValue(0);
                    currentTimeLabel.setText("0:00");
                    setStatus("Queue finished");
                }
            });

            player.setOnError(() -> {
                setStatus("Playback error: " + player.getError());
                showMediaError(s, player.getError());
            });
        } catch (MediaException ex) {
            showMediaError(s, ex);
        } catch (Exception ex) {
            setStatus("Could not play: " + s.getTitle());
        }
    }

    /** Persist the duration once it is known so the library shows it later. */
    private void cacheDuration(Song song, double seconds) {
        if (seconds <= 0 || Math.abs(song.getDurationSeconds() - (int) Math.round(seconds)) < 2) return;
        int duration = (int) Math.round(seconds);
        Platform.runLater(() -> {
            try {
                song.setDurationSeconds(duration);
                songService.update(song);
                songTable.refresh();
                if (songsInSelectedPlaylist.contains(song)) playlistSongsList.refresh();
            } catch (Exception ignored) { }
        });
    }

    private void showVideoStage(boolean video) {
        mediaView.setVisible(video);
        mediaPlaceholder.setVisible(!video);
        if (!video) mediaPlaceholder.setText("♪");
    }

    private void bindVideoSize(Region holder, double inset) {
        mediaView.fitWidthProperty().unbind();
        mediaView.fitHeightProperty().unbind();
        mediaView.fitWidthProperty().bind(holder.widthProperty().subtract(inset));
        mediaView.fitHeightProperty().bind(holder.heightProperty().subtract(inset));
    }

    // ===== Video theater =====

    @FXML
    private void onMediaStageClick(MouseEvent event) {
        if (event.getClickCount() == 2) onToggleTheater();
    }

    @FXML
    private void onToggleTheater() {
        if (mediaPlayer == null || !mediaView.isVisible()) return;
        if (theaterStage != null) {
            theaterStage.close();
            return;
        }
        VBox holder = new VBox(mediaView);
        holder.setAlignment(Pos.CENTER);
        holder.setStyle("-fx-background-color: black;");

        Scene scene = new Scene(holder, 1280, 720);
        scene.setFill(Color.BLACK);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE && theaterStage != null) theaterStage.close();
        });

        theaterStage = new Stage();
        theaterStage.setTitle(nowTitle.getText() + " — MyTunes");
        theaterStage.setScene(scene);
        theaterStage.setOnHidden(e -> {
            theaterStage = null;
            if (!mediaStage.getChildren().contains(mediaView)) {
                mediaStage.getChildren().setAll(mediaPlaceholder, mediaView);
            }
            bindVideoSize(mediaStage, 20);
        });

        Window owner = getWindow();
        if (owner != null) theaterStage.initOwner(owner);

        mediaPlaceholder.setText("⛶");
        mediaPlaceholder.setVisible(true);
        bindVideoSize(holder, 0);
        theaterStage.show();
        theaterStage.centerOnScreen();
    }

    private void disposePlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.dispose();
            } catch (Exception ignored) { }
            mediaPlayer = null;
        }
        mediaView.setMediaPlayer(null);
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    // ===== Playlists =====

    @FXML
    private void onNewPlaylist() {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("New Playlist");
        dlg.setHeaderText("Create new playlist");
        dlg.setContentText("Name:");
        styleDialog(dlg);
        Optional<String> res = dlg.showAndWait();
        res.map(String::trim).filter(n -> !n.isBlank()).ifPresent(name -> {
            try {
                playlistService.create(new Playlist(name));
                playlists.setAll(playlistService.getAll());
            } catch (Exception e) {
                showError("Failed to create playlist", e);
            }
        });
    }

    @FXML
    private void onEditPlaylist() {
        Playlist sel = playlistList.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        TextInputDialog dlg = new TextInputDialog(sel.getName());
        dlg.setTitle("Edit Playlist");
        dlg.setHeaderText("Rename playlist");
        dlg.setContentText("Name:");
        styleDialog(dlg);
        dlg.showAndWait().map(String::trim).filter(n -> !n.isBlank()).ifPresent(name -> {
            try {
                sel.setName(name);
                playlistService.rename(sel);
                playlists.setAll(playlistService.getAll());
                playlistList.getSelectionModel().select(sel);
                playlistNameLabel.setText(name);
            } catch (Exception e) {
                showError("Failed to rename playlist", e);
            }
        });
    }

    @FXML
    private void onDeletePlaylist() {
        Playlist sel = playlistList.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Playlist");
        confirm.setHeaderText("Delete playlist '" + sel.getName() + "'?");
        confirm.setContentText("This will not delete the songs.");
        styleDialog(confirm);
        if (confirm.showAndWait().filter(b -> b == ButtonType.OK).isPresent()) {
            try {
                playlistService.delete(sel.getId());
                playlists.setAll(playlistService.getAll());
                songsInSelectedPlaylist.clear();
                playlistNameLabel.setText("No playlist selected");
            } catch (Exception e) {
                showError("Failed to delete playlist", e);
            }
        }
    }

    @FXML
    private void onMoveUp() {
        Playlist sel = playlistList.getSelectionModel().getSelectedItem();
        int idx = playlistSongsList.getSelectionModel().getSelectedIndex();
        if (sel == null || idx <= 0) return;
        try {
            playlistService.move(sel.getId(), idx, idx - 1);
            refreshPlaylistSongs();
            playlistSongsList.getSelectionModel().select(idx - 1);
        } catch (Exception e) {
            showError("Failed to move", e);
        }
    }

    @FXML
    private void onMoveDown() {
        Playlist sel = playlistList.getSelectionModel().getSelectedItem();
        int idx = playlistSongsList.getSelectionModel().getSelectedIndex();
        if (sel == null || idx < 0 || idx >= songsInSelectedPlaylist.size() - 1) return;
        try {
            playlistService.move(sel.getId(), idx, idx + 1);
            refreshPlaylistSongs();
            playlistSongsList.getSelectionModel().select(idx + 1);
        } catch (Exception e) {
            showError("Failed to move", e);
        }
    }

    @FXML
    private void onRemoveFromPlaylist() {
        Playlist sel = playlistList.getSelectionModel().getSelectedItem();
        int idx = playlistSongsList.getSelectionModel().getSelectedIndex();
        if (sel == null || idx < 0) return;
        try {
            playlistService.removeAtPosition(sel.getId(), idx);
            refreshPlaylistSongs();
        } catch (Exception e) {
            showError("Failed to remove from playlist", e);
        }
    }

    // ===== Songs =====

    @FXML
    private void onNewSong() {
        showSongDialog(null);
    }

    @FXML
    private void onEditSong() {
        showSongDialog(songTable.getSelectionModel().getSelectedItem());
    }

    /**
     * Add a track that is streamed from a direct media URL.
     */
    @FXML
    private void onAddFromLink() {
        Dialog<Song> dialog = new Dialog<>();
        dialog.setTitle("Add from link");
        dialog.setHeaderText("Stream a media file hosted on the web");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField url = new TextField();
        url.setPromptText("https://example.com/music-video.mp4");
        url.setPrefWidth(380);
        TextField title = new TextField();
        title.setPromptText("Title");
        TextField artist = new TextField();
        artist.setPromptText("Artist");

        Button fetch = new Button("Check link");
        Label info = new Label("Paste a direct link to a media file (mp4, m4v, mov, m3u8, mp3, m4a, wav).\n"
                + "Web pages such as YouTube cannot be played — the link must be the file itself.");
        info.getStyleClass().add("hint");
        info.setWrapText(true);
        info.setMaxWidth(400);

        final int[] duration = { 0 };
        fetch.setOnAction(e -> {
            String link = url.getText().trim();
            if (!Song.isUrlString(link)) {
                info.setText("Enter a link starting with http:// or https://");
                return;
            }
            info.setText("Reading…");
            try {
                Media media = new Media(link);
                MediaPlayer probe = new MediaPlayer(media);
                probe.setOnReady(() -> {
                    duration[0] = media.getDuration().isUnknown()
                            ? 0 : (int) Math.round(media.getDuration().toSeconds());
                    boolean video = media.getWidth() > 0 && media.getHeight() > 0;
                    info.setText((video
                                    ? "Video " + media.getWidth() + "×" + media.getHeight()
                                    : "Audio only")
                            + (duration[0] > 0 ? " · " + formatDuration(duration[0]) : " · live stream"));
                    if (title.getText().isBlank()) title.setText(prettyNameFromFile(link));
                    if (artist.getText().isBlank()) artist.setText("Unknown artist");
                    try { probe.dispose(); } catch (Exception ignored) { }
                });
                probe.setOnError(() -> {
                    info.setText("Can't decode this link — is it a direct media file?");
                    try { probe.dispose(); } catch (Exception ignored) { }
                });
            } catch (Exception ex) {
                info.setText("Invalid link — it must point directly at a media file.");
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.add(new Label("Link"), 0, 0);
        grid.add(url, 1, 0);
        grid.add(fetch, 2, 0);
        grid.addRow(1, new Label("Title"), title);
        grid.addRow(2, new Label("Artist"), artist);
        grid.add(info, 0, 3, 3, 1);
        dialog.getDialogPane().setContent(grid);
        styleDialog(dialog);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            if (!Song.isUrlString(url.getText()) || title.getText().isBlank() || artist.getText().isBlank()) {
                showWarning("Enter a direct media link, a title and an artist");
                evt.consume();
            }
        });

        dialog.setResultConverter(btn -> btn == ButtonType.OK
                ? new Song(title.getText().trim(), artist.getText().trim(), duration[0], url.getText().trim())
                : null);

        dialog.showAndWait().ifPresent(song -> {
            try {
                songService.create(song);
                songs.setAll(songService.getAll());
                updateCounts();
                refreshPlaylistSongs();
                setStatus("Added link: " + song.getTitle());
            } catch (Exception e) {
                showError("Failed to add link", e);
            }
        });
    }

    @FXML
    private void onDeleteSong() {
        Song sel = songTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Track");
        confirm.setHeaderText("Delete track '" + sel.getTitle() + "'?");
        CheckBox deleteFile = new CheckBox("Also delete the file from disk");
        deleteFile.setDisable(sel.isUrl());
        confirm.getDialogPane().setContent(deleteFile);
        styleDialog(confirm);
        if (confirm.showAndWait().filter(b -> b == ButtonType.OK).isPresent()) {
            try {
                songService.delete(sel.getId());
                if (deleteFile.isSelected()) {
                    try { new File(sel.getFilePath()).delete(); } catch (Exception ignored) { }
                }
                songs.setAll(songService.getAll());
                updateCounts();
                refreshPlaylistSongs();
            } catch (Exception e) {
                showError("Failed to delete song", e);
            }
        }
    }

    @FXML
    private void onAddToPlaylist() {
        Playlist pl = playlistList.getSelectionModel().getSelectedItem();
        Song song = songTable.getSelectionModel().getSelectedItem();
        if (pl == null) {
            showWarning("Select a playlist first");
            return;
        }
        if (song == null) {
            showWarning("Select a track to add");
            return;
        }
        try {
            playlistService.addSongToEnd(pl.getId(), song.getId());
            refreshPlaylistSongs();
        } catch (Exception e) {
            showError("Failed to add to playlist", e);
        }
    }

    @FXML
    private void onFilterToggle() {
        try {
            if ("Filter".equals(filterButton.getText())) {
                List<Song> filtered = songService.search(filterField.getText().trim());
                songs.setAll(filtered);
                filterButton.setText("Clear");
            } else {
                songs.setAll(songService.getAll());
                filterField.clear();
                filterButton.setText("Filter");
            }
            updateCounts();
        } catch (Exception e) {
            showError("Filter failed", e);
        }
    }

    private void showSongDialog(Song editing) {
        Dialog<Song> dialog = new Dialog<>();
        dialog.setTitle(editing == null ? "New Track" : "Edit Track");
        dialog.setHeaderText(editing == null
                ? "Add an audio file or a music video to your library"
                : "Update the details of this track");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField title = new TextField(editing != null ? editing.getTitle() : "");
        title.setPromptText("Title");
        TextField artist = new TextField(editing != null ? editing.getArtist() : "");
        artist.setPromptText("Artist");
        TextField path = new TextField(editing != null ? editing.getFilePath() : "");
        path.setPromptText("File path, or a direct media link (https://…/video.mp4)");
        Button choose = new Button("Browse…");
        choose.setOnAction(e -> {
            FileChooser ch = new FileChooser();
            ch.setTitle("Choose audio or video file");
            ch.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("All supported media",
                            "*.mp3", "*.wav", "*.m4a", "*.aac", "*.mp4", "*.m4v", "*.mov"),
                    new FileChooser.ExtensionFilter("Audio", "*.mp3", "*.wav", "*.m4a", "*.aac"),
                    new FileChooser.ExtensionFilter("Music videos", "*.mp4", "*.m4v", "*.mov"));
            File f = ch.showOpenDialog(getWindow());
            if (f != null) {
                path.setText(f.getAbsolutePath());
                if (title.getText().isBlank()) {
                    title.setText(prettyNameFromFile(f.getName()));
                }
                if (artist.getText().isBlank()) {
                    artist.setText("Unknown artist");
                }
            }
        });

        // Duration UI and state
        Label durationLbl = new Label("Unknown");
        durationLbl.getStyleClass().add("hint");
        final int[] computedDuration = { editing != null ? editing.getDurationSeconds() : 0 };
        if (computedDuration[0] > 0) durationLbl.setText(formatDuration(computedDuration[0]));

        Label kindLbl = new Label("—");
        kindLbl.getStyleClass().add("hint");

        // Helper to (re)probe duration when file path changes
        final MediaPlayer[] probeRef = { null };
        Runnable doProbe = () -> {
            String p = path.getText().trim();
            if (p.isEmpty()) {
                durationLbl.setText("No file");
                kindLbl.setText("—");
                computedDuration[0] = 0;
                return;
            }
            if (Song.isUrlString(p)) {
                // Never hit the network while the user is typing a link
                kindLbl.setText(Song.isVideoFile(p) ? "Video link" : "Media link");
                durationLbl.setText("Set when played");
                computedDuration[0] = 0;
                if (title.getText().isBlank()) title.setText(prettyNameFromFile(p));
                if (artist.getText().isBlank()) artist.setText("Unknown artist");
                return;
            }
            File f = new File(p);
            if (!f.exists()) {
                durationLbl.setText("File not found");
                kindLbl.setText("—");
                computedDuration[0] = 0;
                return;
            }
            kindLbl.setText(Song.isVideoFile(p) ? "Music video" : "Audio");
            // Cancel previous probe
            if (probeRef[0] != null) {
                try { probeRef[0].dispose(); } catch (Exception ignored) { }
                probeRef[0] = null;
            }
            durationLbl.setText("Reading…");
            try {
                Media media = new Media(f.toURI().toString());
                MediaPlayer mp = new MediaPlayer(media);
                probeRef[0] = mp;
                mp.setOnReady(() -> {
                    int secs = (int) Math.round(media.getDuration().toSeconds());
                    computedDuration[0] = Math.max(0, secs);
                    durationLbl.setText(computedDuration[0] > 0 ? formatDuration(computedDuration[0]) : "Unknown");
                    try { mp.dispose(); } catch (Exception ignored) { }
                    probeRef[0] = null;
                });
                mp.setOnError(() -> {
                    computedDuration[0] = 0;
                    durationLbl.setText("Unknown");
                    try { mp.dispose(); } catch (Exception ignored) { }
                    probeRef[0] = null;
                });
            } catch (Exception ex) {
                computedDuration[0] = 0;
                durationLbl.setText("Unknown");
            }
        };
        // Trigger probe when the path changes (typing or choosing)
        path.textProperty().addListener((obs, ov, nv) -> doProbe.run());
        // If editing and path already present, try to probe at dialog open (in case duration was 0)
        if (editing != null && path.getText() != null && !path.getText().isBlank() && editing.getDurationSeconds() == 0) {
            Platform.runLater(doProbe);
        }

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.addRow(0, new Label("Title"), title);
        grid.addRow(1, new Label("Artist"), artist);
        grid.add(new Label("File / link"), 0, 2);
        grid.add(path, 1, 2);
        grid.add(choose, 2, 2);
        grid.addRow(3, new Label("Type"), kindLbl);
        grid.addRow(4, new Label("Duration"), durationLbl);
        dialog.getDialogPane().setContent(grid);
        styleDialog(dialog);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            if (title.getText().isBlank() || artist.getText().isBlank() || path.getText().isBlank()) {
                showWarning("Please fill title, artist and choose a file or link");
                evt.consume();
            }
        });

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                if (editing == null) {
                    // Use computed duration if available
                    return new Song(title.getText().trim(), artist.getText().trim(), computedDuration[0], path.getText().trim());
                } else {
                    editing.setTitle(title.getText().trim());
                    editing.setArtist(artist.getText().trim());
                    editing.setFilePath(path.getText().trim());
                    // If a new duration was successfully computed (and > 0), update it
                    if (computedDuration[0] > 0) {
                        editing.setDurationSeconds(computedDuration[0]);
                    }
                    return editing;
                }
            }
            return null;
        });

        Optional<Song> res = dialog.showAndWait();
        if (res.isPresent()) {
            try {
                Song s = res.get();
                if (s.getId() == null) songService.create(s); else songService.update(s);
                songs.setAll(songService.getAll());
                songTable.refresh();
                updateCounts();
                refreshPlaylistSongs();
            } catch (Exception e) {
                showError("Failed to save song", e);
            }
        }
    }

    private static String prettyNameFromFile(String nameOrUrl) {
        String value = nameOrUrl == null ? "" : nameOrUrl.trim();
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        int slash = value.lastIndexOf('/');
        if (slash >= 0) value = value.substring(slash + 1);
        int dot = value.lastIndexOf('.');
        String base = dot > 0 ? value.substring(0, dot) : value;
        String cleaned = base.replace('_', ' ').trim();
        if (cleaned.isEmpty()) return "Untitled";
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    // ===== Dialogs / feedback =====

    private Window getWindow() {
        return statusLabel.getScene() != null ? statusLabel.getScene().getWindow() : null;
    }

    private void styleDialog(Dialog<?> dialog) {
        URL css = MyTunesController.class.getResource("/com/example/demo2/app.css");
        if (css != null) {
            dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
        }
        dialog.getDialogPane().getStyleClass().add("app-dialog");
        Window owner = getWindow();
        if (owner != null) {
            dialog.initOwner(owner);
        }
    }

    /** Explains why a track could not be decoded, with actionable advice. */
    private void showMediaError(Song song, Throwable error) {
        if (mediaErrorShown) return;
        mediaErrorShown = true;

        String detail = error == null || error.getMessage() == null
                ? "The media could not be decoded."
                : error.getMessage();

        if (song.hasUnsupportedVideoFormat()) {
            showInfo("Unsupported video format",
                    "'" + song.getTitle() + "' is a ." + Song.extensionOf(song.getFilePath())
                            + " file, which the JavaFX player cannot decode.\n\n"
                            + "Use MP4, M4V or MOV with H.264 video and AAC audio. Converting is easy:\n\n"
                            + "ffmpeg -i input.mkv -c:v libx264 -c:a aac output.mp4");
        } else if (song.isUrl()) {
            showInfo("Can't play this link",
                    "The link must point directly at a media file, not at a web page.\n\n"
                            + "Working examples:\nhttps://host/video.mp4\nhttps://host/stream.m3u8\n\n"
                            + "Details: " + detail);
        } else {
            showInfo("Can't play this track", "The media could not be decoded.\n\nDetails: " + detail);
        }
    }

    private void showError(String msg, Throwable e) {
        setStatus(msg);
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Error");
        a.setHeaderText(msg);
        a.setContentText(e.getMessage());
        styleDialog(a);
        a.showAndWait();
    }

    private void showWarning(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        a.setHeaderText(null);
        styleDialog(a);
        a.showAndWait();
    }

    private void showInfo(String header, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("MyTunes");
        a.setHeaderText(header);
        a.setContentText(message);
        styleDialog(a);
        a.showAndWait();
    }

    // ===== Helpers for duration probing and backfilling =====

    private static String formatDuration(int durationSeconds) {
        int s = durationSeconds % 60;
        int m = (durationSeconds / 60) % 60;
        int h = durationSeconds / 3600;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%d:%02d", m, s);
    }

    private void probeDuration(String filePath, IntConsumer onDone) {
        File f = new File(filePath);
        if (!f.exists()) { onDone.accept(0); return; }
        try {
            Media media = new Media(f.toURI().toString());
            MediaPlayer mp = new MediaPlayer(media);
            mp.setOnReady(() -> {
                int secs = (int) Math.round(media.getDuration().toSeconds());
                try { mp.dispose(); } catch (Exception ignored) { }
                onDone.accept(Math.max(0, secs));
            });
            mp.setOnError(() -> {
                try { mp.dispose(); } catch (Exception ignored) { }
                onDone.accept(0);
            });
        } catch (Exception ex) {
            onDone.accept(0);
        }
    }

    private void autofillUnknownDurations() {
        // Iterate over a copy to avoid concurrent modification
        for (Song s : List.copyOf(songs)) {
            // Never probe links at startup - that would hit the network for every track
            if (s.getDurationSeconds() == 0 && !s.isUrl()) {
                File f = new File(s.getFilePath());
                if (f.exists()) {
                    probeDuration(s.getFilePath(), secs -> {
                        if (secs > 0) {
                            // Persist and update UI
                            Platform.runLater(() -> {
                                try {
                                    s.setDurationSeconds(secs);
                                    songService.update(s);
                                    songTable.refresh();
                                } catch (Exception ignored) { }
                            });
                        }
                    });
                }
            }
        }
    }
}
