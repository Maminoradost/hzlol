package com.valkyrie.launcher;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.CacheHint;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public final class ValkyrieLauncherApp extends Application {
    private static final Interpolator EASE_OUT = Interpolator.SPLINE(0.2, 0.8, 0.2, 1.0);

    private final LauncherConfig config = LauncherConfig.load();
    private final UpdateService updates = new UpdateService();
    private final GameInstallationService installation = new GameInstallationService();
    private final MinecraftLauncher minecraft = new MinecraftLauncher();
    private final VersionCatalogService versions = new VersionCatalogService();
    private UpdateService.Manifest manifest;
    private LauncherSounds sounds;
    private Stage stage;
    private Scene scene;
    private StackPane root;
    private SakuraBackground background;
    private Region backgroundTint;
    private VBox hero;
    private VBox drawer;
    private VBox toastLayer;
    private Region scrim;
    private ImageView heroLogo;
    private TextField nickname;
    private Button versionTrigger;
    private Label versionTriggerTitle;
    private Label versionTriggerMeta;
    private Label versionTriggerBadge;
    private HBox installBadge;
    private Label installBadgeText;
    private StackPane versionBrowserLayer;
    private VBox versionBrowserCard;
    private ListView<GameVersion> versionList;
    private TextField versionSearch;
    private ToggleButton releasesFilter;
    private ToggleButton snapshotsFilter;
    private ParallelTransition versionBrowserAnimation;
    private boolean versionBrowserOpen;
    private List<GameVersion> availableVersions = List.of(GameVersion.VALKYRIE);
    private GameVersion selectedVersion = GameVersion.VALKYRIE;
    private Label version;
    private Label footerPlatform;
    private Label footerJava;
    private Label status;
    private Label validation;
    private VBox changes;
    private ProgressBar progress;
    private Button play;
    private Button changelogButton;
    private VBox settingsPane;
    private Region settingsScrim;
    private ParallelTransition drawerAnimation;
    private ParallelTransition settingsAnimation;
    private boolean drawerOpen;
    private boolean settingsOpen;
    private LaunchState launchState = LaunchState.CHECKING;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        LauncherLog.info("Launcher started: java=" + System.getProperty("java.version") + ", home=" + System.getProperty("java.home")
            + ", game=" + config.instanceDirectory);
        sounds = new LauncherSounds(config);
        Motion.setReduced(config.reducedMotion);
        loadFonts();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle("Valkyrie Launcher");
        stage.getIcons().add(new Image(resource("/assets/launcher-icon.png")));
        stage.setMinWidth(820);
        stage.setMinHeight(540);

        root = new StackPane();
        root.getStyleClass().addAll("root", "theme-" + config.theme);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(root.widthProperty());
        clip.heightProperty().bind(root.heightProperty());
        clip.setArcWidth(36);
        clip.setArcHeight(36);
        root.setClip(clip);

        backgroundTint = new Region();
        backgroundTint.getStyleClass().add("background-tint");
        backgroundTint.setMouseTransparent(true);
        background = new SakuraBackground();
        background.widthProperty().bind(root.widthProperty());
        background.heightProperty().bind(root.heightProperty());
        background.setReducedMotion(config.reducedMotion);
        background.setDark(darkTheme());
        stage.iconifiedProperty().addListener((ignored, old, iconified) -> background.setPaused(iconified));
        Region glowLeft = new Region();
        glowLeft.getStyleClass().addAll("ambient-glow", "ambient-glow-left");
        glowLeft.setMouseTransparent(true);
        Region glowRight = new Region();
        glowRight.getStyleClass().addAll("ambient-glow", "ambient-glow-right");
        glowRight.setMouseTransparent(true);
        VBox shell = new VBox();
        shell.getStyleClass().add("shell");
        HBox titleBar = titleBar();
        StackPane content = new StackPane(buildHero());
        VBox.setVgrow(content, Priority.ALWAYS);
        shell.getChildren().addAll(titleBar, content, footer());

        scrim = new Region();
        scrim.getStyleClass().add("drawer-scrim");
        scrim.setVisible(false);
        scrim.setOpacity(0);
        scrim.setOnMouseClicked(event -> setDrawer(false));
        drawer = buildDrawer();
        StackPane.setAlignment(drawer, Pos.CENTER_RIGHT);
        drawer.setVisible(false);
        settingsScrim = new Region();
        settingsScrim.getStyleClass().add("settings-scrim");
        settingsScrim.setVisible(false);
        settingsScrim.setOnMouseClicked(event -> setSettings(false));
        settingsPane = buildSettings();
        settingsPane.setVisible(false);
        StackPane.setAlignment(settingsPane, Pos.TOP_RIGHT);
        StackPane.setMargin(settingsPane, new Insets(62, 20, 0, 0));
        toastLayer = new VBox(8);
        toastLayer.setMouseTransparent(true);
        toastLayer.setMaxWidth(340);
        StackPane.setAlignment(toastLayer, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(toastLayer, new Insets(0, 22, 24, 0));
        versionBrowserLayer = buildVersionBrowser();
        root.getChildren().addAll(backgroundTint, glowLeft, glowRight, background, shell,
            versionBrowserLayer, scrim, drawer, settingsScrim, settingsPane, toastLayer);

        scene = new Scene(root, 1040, 650);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        scene.getStylesheets().add(resource("/ui/launcher.css"));
        installWindowDrag();
        root.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            Button button = findButton(event.getTarget());
            if (event.getButton() == MouseButton.PRIMARY && button != null && !button.getStyleClass().contains("sound-preview")) sounds.press();
        });
        nickname.setOnKeyTyped(event -> {
            if (!event.getCharacter().isEmpty() && !Character.isISOControl(event.getCharacter().charAt(0))) sounds.type();
        });
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE && versionBrowserOpen) setVersionBrowser(false);
            else if (event.getCode() == KeyCode.ESCAPE && drawerOpen) setDrawer(false);
            else if (event.getCode() == KeyCode.ESCAPE && settingsOpen) setSettings(false);
            if (event.getCode() == KeyCode.ENTER && nickname.isFocused() && !versionBrowserOpen) installAndLaunch();
        });
        stage.setScene(scene);
        stage.show();
        revealHero();
        sounds.open();
        if (config.needsMigration) {
            config.needsMigration = false;
            saveQuietly();
        }
        loadVersions();
        if (config.changelogOpen) Platform.runLater(() -> setDrawer(true));
    }

    @Override
    public void stop() {
        if (background != null) background.stop();
        if (sounds != null) sounds.close();
    }

    private HBox titleBar() {
        Label brandName = new Label("Valkyrie");
        brandName.getStyleClass().add("brand-name");
        Label brandVersion = new Label("·  " + LauncherVersion.VALUE);
        brandVersion.getStyleClass().add("brand-version");
        HBox brand = new HBox(6, brandName, brandVersion);
        brand.setAlignment(Pos.CENTER_LEFT);

        changelogButton = iconButton("Updates", "nav-button");
        changelogButton.setGraphic(LauncherIcons.updates());
        changelogButton.setOnAction(event -> {
            setDrawer(!drawerOpen);
        });
        Button settings = iconButton("Settings", "nav-button");
        settings.setGraphic(LauncherIcons.settings());
        settings.setOnAction(event -> {
            setSettings(!settingsOpen);
        });
        Button minimize = iconButton("", "window-button");
        minimize.setGraphic(LauncherIcons.minimize());
        minimize.setOnAction(event -> {
            stage.setIconified(true);
        });
        Button close = iconButton("", "window-button", "close-button");
        close.setGraphic(LauncherIcons.close());
        close.setOnAction(event -> {
            Platform.exit();
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(5, brand, spacer, changelogButton, settings, minimize, close);
        bar.getStyleClass().add("title-bar");
        bar.setAlignment(Pos.CENTER);
        return bar;
    }

    private void installWindowDrag() {
        root.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY || isInteractive(event.getTarget())) return;
            background.setPaused(true);
            root.setCache(true);
            root.setCacheHint(CacheHint.SPEED);
            try {
                if (NativeWindowDrag.begin(stage.getTitle())) event.consume();
            } finally {
                root.setCache(false);
                background.setPaused(stage.isIconified());
            }
        });
    }

    private static boolean isInteractive(Object target) {
        Node node = target instanceof Node value ? value : null;
        while (node != null) {
            if (node instanceof Control) return true;
            node = node.getParent();
        }
        return false;
    }

    private static Button findButton(Object target) {
        Node node = target instanceof Node value ? value : null;
        while (node != null) {
            if (node instanceof Button button) return button;
            node = node.getParent();
        }
        return null;
    }

    private VBox buildHero() {
        heroLogo = image("/assets/launcher-logo.png", 218);
        heroLogo.getStyleClass().add("hero-logo");
        Label subtitle = new Label("Твоя игра. Твой ритм. Твоя Valkyrie.");
        subtitle.getStyleClass().add("hero-subtitle");
        subtitle.setMaxWidth(Double.MAX_VALUE);
        subtitle.setAlignment(Pos.CENTER);

        nickname = new TextField(config.nickname);
        nickname.getStyleClass().add("nickname-field");
        nickname.setPromptText("Никнейм");
        nickname.textProperty().addListener((ignored, old, value) -> validateNickname(false));
        validation = new Label();
        validation.getStyleClass().add("validation");
        validation.setManaged(false);
        validation.setVisible(false);
        nickname.setAlignment(Pos.CENTER);
        VBox accountField = new VBox(5, nickname, validation);
        accountField.getStyleClass().add("field-group");

        versionTrigger = new Button();
        versionTrigger.getStyleClass().add("version-trigger");
        versionTrigger.setMaxWidth(Double.MAX_VALUE);
        versionTriggerTitle = new Label("Valkyrie Client");
        versionTriggerTitle.getStyleClass().add("version-trigger-title");
        versionTriggerMeta = new Label("Minecraft 1.21.10 · Forge 60.1.0");
        versionTriggerMeta.getStyleClass().add("version-trigger-meta");
        VBox versionText = new VBox(2, versionTriggerTitle, versionTriggerMeta);
        versionText.setAlignment(Pos.CENTER_LEFT);
        versionTriggerBadge = new Label("RECOMMENDED");
        versionTriggerBadge.getStyleClass().add("recommended-badge");
        Label chevron = new Label("›");
        chevron.getStyleClass().add("version-chevron");
        Region triggerSpacer = new Region();
        HBox.setHgrow(triggerSpacer, Priority.ALWAYS);
        HBox triggerContent = new HBox(12, new Label("✦"), versionText, triggerSpacer, versionTriggerBadge, chevron);
        triggerContent.getStyleClass().add("version-trigger-content");
        triggerContent.setAlignment(Pos.CENTER_LEFT);
        versionTrigger.setGraphic(triggerContent);
        versionTrigger.setOnAction(event -> setVersionBrowser(true));

        Label installDot = new Label("●");
        installDot.getStyleClass().add("install-badge-dot");
        installBadgeText = new Label();
        installBadgeText.getStyleClass().add("install-badge-text");
        installBadge = new HBox(6, installDot, installBadgeText);
        installBadge.getStyleClass().add("install-badge");
        installBadge.setAlignment(Pos.CENTER_LEFT);
        installBadge.setVisible(false);
        installBadge.setManaged(false);
        VBox versionGroup = new VBox(6, versionTrigger, installBadge);

        play = new Button("Запустить");
        actionIconBox = new StackPane();
        actionIconBox.setMinSize(18, 18);
        actionIconBox.setPrefSize(18, 18);
        actionIconBox.setMaxSize(18, 18);
        actionIconBox.setMouseTransparent(true);
        setActionIcon(ActionIcon.PLAY, false);
        play.setGraphic(actionIconBox);
        play.getStyleClass().add("play-button");
        play.setMaxWidth(Double.MAX_VALUE);
        play.setCursor(Cursor.HAND);
        play.setDisable(true);
        play.setOnAction(event -> {
            animatePress(play);
            background.gust(1.0);
            installAndLaunch();
        });
        // Лёгкое оживление фона при наведении — лепестки чуть подхватывает
        // ветром, ощущение отклика без единого лишнего виджета.
        play.hoverProperty().addListener((ignored, old, hover) -> {
            if (hover && !play.isDisabled()) background.gust(0.35);
        });
        // Подъём при наведении живёт на самой кнопке, пульсация занятости —
        // на иконке внутри неё. Разные узлы, поэтому за масштаб они не спорят.
        installHoverLift(play, 1.025);

        version = new Label("Проверяем");
        version.getStyleClass().add("version-label");
        status = new Label("●");
        status.getStyleClass().add("status-label");
        VBox statusText = new VBox(1, version);
        statusText.setAlignment(Pos.CENTER_LEFT);
        HBox state = new HBox(10, status, statusText);
        state.getStyleClass().add("launch-status");
        state.setAlignment(Pos.CENTER);
        progress = new ProgressBar();
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.getStyleClass().add("launch-progress");

        VBox card = new VBox(13, subtitle, versionGroup, accountField, play, progress, state);
        card.getStyleClass().add("hero-card");
        card.setMaxWidth(440);
        card.setPrefWidth(440);
        hero = new VBox(-18, heroLogo, card);
        hero.getStyleClass().add("hero");
        hero.setAlignment(Pos.CENTER);
        hero.setMaxWidth(470);
        return hero;
    }

    private HBox footer() {
        footerPlatform = new Label("Minecraft 1.21.10 · Forge 60.1.0");
        footerPlatform.getStyleClass().add("footer-platform");
        footerJava = new Label("JAVA 21");
        footerJava.getStyleClass().add("footer-chip");
        Button logs = new Button("Logs");
        logs.getStyleClass().add("footer-link");
        logs.setOnAction(event -> openPath(LauncherConfig.DIRECTORY.resolve("logs")));
        Button gameFolder = new Button("Game folder");
        gameFolder.getStyleClass().add("footer-link");
        gameFolder.setOnAction(event -> openPath(config.instanceDirectory));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(9, footerPlatform, footerJava, spacer, gameFolder, logs);
        footer.getStyleClass().add("footer");
        footer.setAlignment(Pos.CENTER_LEFT);
        return footer;
    }

    private VBox buildSettings() {
        Label title = new Label("Settings");
        title.getStyleClass().add("settings-title");
        Button close = iconButton("×", "icon-button");
        close.setOnAction(event -> setSettings(false));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox heading = new HBox(title, spacer, close);
        heading.setAlignment(Pos.CENTER);

        Label appearance = settingLabel("APPEARANCE");
        ToggleButton theme = switchControl(darkTheme());
        theme.setOnAction(event -> {
            applyTheme(theme.isSelected());
            saveQuietly();
        });
        HBox themeRow = settingControlRow(LauncherIcons.theme(), "Sakura Night", "Dark interface theme", theme);
        ToggleButton motion = switchControl(config.reducedMotion);
        motion.setOnAction(event -> {
            config.reducedMotion = motion.isSelected();
            Motion.setReduced(config.reducedMotion);
            background.setReducedMotion(config.reducedMotion);
            saveQuietly();
        });
        HBox motionRow = settingControlRow(LauncherIcons.motion(), "Reduced motion", "Calmer spatial animation", motion);

        Label soundLabel = settingLabel("SOUND");
        ToggleButton sound = switchControl(config.uiSounds);
        sound.setOnAction(event -> {
            config.uiSounds = sound.isSelected();
            saveQuietly();
            if (config.uiSounds) sounds.press();
        });
        Button preview = new Button("Preview");
        preview.getStyleClass().addAll("settings-action-small", "sound-preview");
        preview.setOnAction(event -> sounds.press());
        HBox soundRow = settingControlRow(LauncherIcons.volume(), "Interface sounds", "Clicks, panels and feedback", sound);

        Label minecraftLabel = settingLabel("MINECRAFT");
        ToggleButton autoMemory = switchControl(config.automaticMemory);
        Label memoryValue = new Label(memoryText());
        memoryValue.getStyleClass().add("settings-value");
        Slider memory = new Slider(2048, 8192, config.automaticMemory ? config.effectiveMemoryMb() : config.memoryMb);
        memory.setBlockIncrement(1024);
        memory.setMajorTickUnit(2048);
        memory.setSnapToTicks(true);
        memory.getStyleClass().add("memory-slider");
        memory.setDisable(config.automaticMemory);
        memory.valueProperty().addListener((ignored, old, value) -> {
            if (!config.automaticMemory) {
                config.memoryMb = (int) (Math.round(value.doubleValue() / 1024.0) * 1024);
                memoryValue.setText(config.memoryMb + " MB");
            }
        });
        memory.setOnMouseReleased(event -> saveQuietly());
        autoMemory.setOnAction(event -> {
            config.automaticMemory = autoMemory.isSelected();
            memory.setDisable(config.automaticMemory);
            memoryValue.setText(memoryText());
            if (config.automaticMemory) memory.setValue(config.effectiveMemoryMb());
            saveQuietly();
        });
        HBox memoryHeader = settingControlRow(null, "Memory", "Automatic allocation", autoMemory);
        VBox memoryCard = new VBox(7, memoryHeader, memoryValue, memory);
        memoryCard.getStyleClass().add("settings-card");

        Label javaValue = new Label("automatic".equals(config.javaMode) ? "Automatic · downloaded if missing" : "Custom executable");
        javaValue.getStyleClass().add("settings-value");
        Button java = new Button("Change");
        java.getStyleClass().add("settings-action-small");
        java.setOnAction(event -> chooseJava(javaValue));
        HBox javaRow = settingControlRow(null, "Java", "Automatic is recommended", java);
        VBox javaCard = new VBox(5, javaRow, javaValue);
        javaCard.getStyleClass().add("settings-card");

        Button folder = settingActionRow(LauncherIcons.folder(), "Game installation", "Verify and repair", "Repair");
        folder.setOnAction(event -> repairGameInstallation());
        Label aboutLabel = settingLabel("ABOUT");
        Label about = new Label("Valkyrie Launcher " + LauncherVersion.VALUE + "\nJava 21 · GPL-3.0");
        about.getStyleClass().add("settings-about");

        VBox content = new VBox(9, appearance, themeRow, motionRow, soundLabel, soundRow, preview,
            minecraftLabel, memoryCard, javaCard, folder, aboutLabel, about);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("settings-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox panel = new VBox(10, heading, scroll);
        panel.getStyleClass().add("settings-pane");
        panel.setPrefWidth(340);
        panel.setMaxWidth(340);
        panel.setMaxHeight(540);
        return panel;
    }

    private void setSettings(boolean open) {
        settingsOpen = open;
        if (open && drawerOpen) setDrawer(false);
        Duration duration = config.reducedMotion ? Duration.millis(100) : Duration.millis(open ? 210 : 160);
        if (open) {
            settingsPane.setVisible(true);
            settingsScrim.setVisible(true);
            settingsPane.setOpacity(0);
            settingsPane.setTranslateY(config.reducedMotion ? 0 : -8);
            settingsScrim.setOpacity(0);
        }
        FadeTransition paneFade = new FadeTransition(duration, settingsPane);
        paneFade.setToValue(open ? 1 : 0);
        FadeTransition scrimFade = new FadeTransition(duration, settingsScrim);
        scrimFade.setToValue(open ? 1 : 0);
        TranslateTransition move = new TranslateTransition(duration, settingsPane);
        move.setToY(open ? 0 : (config.reducedMotion ? 0 : -6));
        move.setInterpolator(open ? Motion.STANDARD : Motion.IN_CUBIC);
        if (settingsAnimation != null) settingsAnimation.stop();
        settingsAnimation = new ParallelTransition(paneFade, scrimFade, move);
        settingsAnimation.setOnFinished(event -> {
            if (!open) {
                settingsPane.setVisible(false);
                settingsScrim.setVisible(false);
            }
        });
        settingsAnimation.play();
    }

    private boolean darkTheme() {
        return "dark".equals(config.theme);
    }

    /**
     * Единственная точка переключения темы: класс на root переопределяет все
     * токены-цвета в launcher.css, а лепестки Canvas стилями не достаются и
     * переключаются отдельно.
     */
    private void applyTheme(boolean dark) {
        config.theme = dark ? "dark" : "light";
        root.getStyleClass().removeAll("theme-light", "theme-dark");
        root.getStyleClass().add("theme-" + config.theme);
        background.setDark(dark);
    }

    private static Label settingLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("settings-section");
        return label;
    }

    private static ToggleButton switchControl(boolean selected) {
        ToggleButton toggle = new ToggleButton("●");
        toggle.setSelected(selected);
        toggle.getStyleClass().add("settings-switch");
        return toggle;
    }

    private static HBox settingControlRow(Node icon, String name, String description, Node control) {
        Label title = new Label(name);
        title.getStyleClass().add("settings-row-title");
        Label detail = new Label(description);
        detail.getStyleClass().add("settings-row-description");
        VBox text = new VBox(1, title, detail);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = icon == null ? new HBox(9, text, spacer, control) : new HBox(10, icon, text, spacer, control);
        row.getStyleClass().add("settings-control-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Button settingActionRow(Node icon, String name, String description, String action) {
        Button button = new Button();
        button.getStyleClass().add("settings-action-row");
        button.setMaxWidth(Double.MAX_VALUE);
        Label value = new Label(action);
        value.getStyleClass().add("settings-action-value");
        button.setGraphic(settingControlRow(icon, name, description, value));
        return button;
    }

    private VBox buildDrawer() {
        Label eyebrow = new Label("CHANGELOG");
        eyebrow.getStyleClass().add("drawer-eyebrow");
        Label title = new Label("Что нового");
        title.getStyleClass().add("drawer-title");
        Button close = iconButton("×", "icon-button");
        close.setOnAction(event -> setDrawer(false));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox heading = new HBox(eyebrow, spacer, close);
        heading.setAlignment(Pos.CENTER);

        changes = new VBox(13);
        changes.getStyleClass().add("changes");
        setChanges(List.of("Valkyrie Flow с единым Combat pipeline", "Sakura Bloom, Threat Constellation и Memory Echo",
            "ClickGUI с анимированным Book Fold", "Светлая и тёмная Sakura-темы", "HUD Editor, Target HUD и Visual Profiles"));
        ScrollPane scroll = new ScrollPane(changes);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("changes-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox panel = new VBox(12, heading, title, new Label("Valkyrie 0.9.0 Beta"), scroll);
        panel.getStyleClass().add("drawer");
        panel.setPrefWidth(380);
        panel.setMaxWidth(420);
        return panel;
    }

    private StackPane buildVersionBrowser() {
        Region browserScrim = new Region();
        browserScrim.getStyleClass().add("version-browser-scrim");
        browserScrim.setOnMouseClicked(event -> setVersionBrowser(false));

        Label eyebrow = new Label("VERSION LIBRARY");
        eyebrow.getStyleClass().add("version-browser-eyebrow");
        Label title = new Label("Выбери свою игру");
        title.getStyleClass().add("version-browser-title");
        Label description = new Label("Valkyrie рекомендована, но выбор всегда остаётся за тобой.");
        description.getStyleClass().add("version-browser-description");
        eyebrow.setMaxWidth(Double.MAX_VALUE);
        eyebrow.setAlignment(Pos.CENTER);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setAlignment(Pos.CENTER);
        description.setMaxWidth(Double.MAX_VALUE);
        description.setAlignment(Pos.CENTER);
        VBox headingText = new VBox(2, eyebrow, title, description);
        headingText.setAlignment(Pos.CENTER);
        Button close = iconButton("×", "icon-button");
        close.setOnAction(event -> setVersionBrowser(false));
        Region headingSpacer = new Region();
        HBox.setHgrow(headingSpacer, Priority.ALWAYS);
        HBox.setHgrow(headingText, Priority.ALWAYS);
        HBox heading = new HBox(headingText, close);
        heading.setAlignment(Pos.TOP_CENTER);

        versionSearch = new TextField();
        versionSearch.setPromptText("Поиск версии, например 1.20.1");
        versionSearch.getStyleClass().add("version-search");
        versionSearch.textProperty().addListener((ignored, old, value) -> filterVersions());

        ToggleGroup filters = new ToggleGroup();
        releasesFilter = filterButton("Releases", filters, true);
        snapshotsFilter = filterButton("Snapshots", filters, false);
        releasesFilter.setOnAction(event -> filterVersions());
        snapshotsFilter.setOnAction(event -> filterVersions());
        HBox filterBar = new HBox(7, releasesFilter, snapshotsFilter);
        filterBar.getStyleClass().add("version-filter-bar");

        versionList = new ListView<>();
        versionList.getStyleClass().add("version-list");
        versionList.setFixedCellSize(64);
        versionList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(GameVersion item, boolean empty) {
                super.updateItem(item, empty);
                pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("recommended"), !empty && item != null && item.valkyrie());
                pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("snapshot"), !empty && item != null && "snapshot".equals(item.type()));
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label rowTitle = new Label(item.valkyrie() ? "Valkyrie Client" : "Minecraft " + item.id());
                rowTitle.getStyleClass().add("version-row-title");
                Label rowMeta = new Label(item.valkyrie() ? "Minecraft 1.21.10 · Forge 60.1.0 · Optimized" :
                    ("snapshot".equals(item.type()) ? "Snapshot · experimental" : "Vanilla release"));
                rowMeta.getStyleClass().add("version-row-meta");
                VBox text = new VBox(2, rowTitle, rowMeta);
                Label badge = new Label(item.valkyrie() ? "RECOMMENDED" : item.type().toUpperCase(Locale.ROOT));
                badge.getStyleClass().add(item.valkyrie() ? "recommended-badge" : "version-type-badge");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox row = new HBox(11, new Label(item.valkyrie() ? "✦" : "◇"), text, spacer, badge);
                row.getStyleClass().add("version-row");
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
                setText(null);
            }
        });
        versionList.setOnMouseClicked(event -> {
            if (event.getClickCount() >= 1 && versionList.getSelectionModel().getSelectedItem() != null) {
                selectVersion(versionList.getSelectionModel().getSelectedItem());
                setVersionBrowser(false);
            }
        });
        versionList.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && versionList.getSelectionModel().getSelectedItem() != null) {
                selectVersion(versionList.getSelectionModel().getSelectedItem());
                setVersionBrowser(false);
            }
        });
        VBox.setVgrow(versionList, Priority.ALWAYS);

        versionBrowserCard = new VBox(14, heading, versionSearch, filterBar, versionList);
        versionBrowserCard.getStyleClass().add("version-browser-card");
        versionBrowserCard.setMaxWidth(610);
        versionBrowserCard.setMaxHeight(520);
        versionBrowserCard.setPrefWidth(610);
        versionBrowserCard.setPrefHeight(520);
        StackPane layer = new StackPane(browserScrim, versionBrowserCard);
        layer.getStyleClass().add("version-browser-layer");
        layer.setVisible(false);
        layer.setManaged(false);
        return layer;
    }

    private static ToggleButton filterButton(String text, ToggleGroup group, boolean selected) {
        ToggleButton button = new ToggleButton(text);
        button.setToggleGroup(group);
        button.setSelected(selected);
        button.getStyleClass().add("version-filter");
        return button;
    }

    private void setVersionBrowser(boolean open) {
        if (open == versionBrowserOpen) return;
        versionBrowserOpen = open;
        if (open) {
            if (drawerOpen) setDrawer(false);
            if (settingsOpen) setSettings(false);
            versionBrowserLayer.setManaged(true);
            versionBrowserLayer.setVisible(true);
            versionBrowserLayer.toFront();
            toastLayer.toFront();
            versionBrowserLayer.setOpacity(0);
            versionBrowserCard.setOpacity(0);
            versionBrowserCard.setTranslateY(config.reducedMotion ? 0 : 16);
            versionBrowserCard.setScaleX(config.reducedMotion ? 1 : 0.975);
            versionBrowserCard.setScaleY(config.reducedMotion ? 1 : 0.975);
            filterVersions();
            versionList.getSelectionModel().select(selectedVersion);
        }
        Duration duration = config.reducedMotion ? Duration.millis(90) : Duration.millis(open ? 260 : 180);
        FadeTransition layerFade = new FadeTransition(duration, versionBrowserLayer);
        layerFade.setToValue(open ? 1 : 0);
        FadeTransition cardFade = new FadeTransition(duration, versionBrowserCard);
        cardFade.setToValue(open ? 1 : 0);
        TranslateTransition move = new TranslateTransition(duration, versionBrowserCard);
        move.setToY(open ? 0 : (config.reducedMotion ? 0 : 8));
        move.setInterpolator(open ? Motion.STANDARD : Motion.IN_CUBIC);
        ScaleTransition scale = new ScaleTransition(duration, versionBrowserCard);
        scale.setToX(open ? 1 : (config.reducedMotion ? 1 : 0.985));
        scale.setToY(open ? 1 : (config.reducedMotion ? 1 : 0.985));
        scale.setInterpolator(EASE_OUT);
        if (versionBrowserAnimation != null) versionBrowserAnimation.stop();
        versionBrowserAnimation = new ParallelTransition(layerFade, cardFade, move, scale);
        versionBrowserAnimation.setOnFinished(event -> {
            if (!open) {
                versionBrowserLayer.setVisible(false);
                versionBrowserLayer.setManaged(false);
                versionTrigger.requestFocus();
            } else {
                versionSearch.requestFocus();
            }
        });
        versionBrowserAnimation.play();
    }

    private void filterVersions() {
        if (versionList == null) return;
        String query = versionSearch == null ? "" : versionSearch.getText().trim().toLowerCase(Locale.ROOT);
        boolean snapshots = snapshotsFilter != null && snapshotsFilter.isSelected();
        List<GameVersion> filtered = availableVersions.stream().filter(item -> item.valkyrie() ||
                (snapshots ? "snapshot".equals(item.type()) : "release".equals(item.type())))
            .filter(item -> query.isBlank() || item.displayName().toLowerCase(Locale.ROOT).contains(query))
            .toList();
        versionList.getItems().setAll(filtered);
    }

    private void setDrawer(boolean open) {
        drawerOpen = open;
        config.changelogOpen = open;
        saveQuietly();
        double width = Math.min(420, Math.max(340, stage.getWidth() * 0.38));
        drawer.setPrefWidth(width);
        Duration duration = config.reducedMotion ? Duration.millis(120) : Duration.millis(open ? 250 : 190);
        if (open) {
            drawer.setVisible(true);
            scrim.setVisible(true);
            drawer.setTranslateX(config.reducedMotion ? 0 : 22);
        }
        TranslateTransition slide = new TranslateTransition(duration, drawer);
        slide.setToX(open ? 0 : 22);
        slide.setInterpolator(open ? Motion.STANDARD : Motion.IN_CUBIC);
        FadeTransition drawerFade = new FadeTransition(duration, drawer);
        drawerFade.setToValue(open ? 1 : 0);
        FadeTransition scrimFade = new FadeTransition(duration, scrim);
        scrimFade.setToValue(open ? 1 : 0);
        if (drawerAnimation != null) drawerAnimation.stop();
        drawerAnimation = new ParallelTransition(slide, drawerFade, scrimFade);
        drawerAnimation.setOnFinished(event -> {
            if (!open) {
                drawer.setVisible(false);
                scrim.setVisible(false);
            } else {
                revealChanges();
            }
        });
        drawerAnimation.play();
    }

    private void checkUpdates() {
        if (!selectedVersion.valkyrie()) {
            manifest = null;
            setLaunchState(installation.installed(config, selectedVersion) ? LaunchState.READY : LaunchState.INSTALL_REQUIRED,
                selectedVersion.displayName());
            return;
        }
        runTask(() -> updates.loadManifest(config), loaded -> {
            manifest = loaded;
            if (loaded == null) {
                setLaunchState(isReady() ? LaunchState.READY : LaunchState.INSTALL_REQUIRED, installedVersion());
            } else {
                setLaunchState(isInstalled(loaded) && installation.installed(config) ? LaunchState.READY :
                        (installation.installed(config) ? LaunchState.UPDATE_AVAILABLE : LaunchState.INSTALL_REQUIRED),
                    "Valkyrie " + loaded.version());
                setChanges(loaded.changelog());
            }
        }, error -> {
            setLaunchState(isReady() ? LaunchState.READY : LaunchState.INSTALL_REQUIRED, installedVersion());
            showValidation("Сервер обновлений недоступен, используется локальная сборка", false);
            showToast("Сервер обновлений недоступен", false);
        });
    }

    private void installAndLaunch() {
        if (!validateNickname(true)) return;
        GameVersion launchVersion = selectedVersion;
        Path launchDirectory = launchVersion.valkyrie() ? LauncherConfig.DIRECTORY.resolve("game")
            : LauncherConfig.DIRECTORY.resolve("instances").resolve(launchVersion.instanceId());
        config.nickname = nickname.getText().trim();
        setLaunchState(manifest != null && !isInstalled(manifest) ? LaunchState.UPDATING : LaunchState.STARTING, version.getText());
        runTask(() -> {
            config.save();
            config.select(launchVersion);
            installation.ensureInstalled(config, launchVersion,
                message -> Platform.runLater(() -> setLaunchState(LaunchState.INSTALLING, message)),
                fraction -> Platform.runLater(() -> setProgress(fraction)));
            if (launchVersion.valkyrie() && manifest != null) updates.install(manifest, config);
            else if (launchVersion.valkyrie() && !hasInstalledClient()) updates.installBundled(config);
            return minecraft.launch(config,
                message -> Platform.runLater(() -> {
                    // Загрузка Java — отдельная фаза после установки, полоска
                    // начинает её с нуля вместо застывших 100%.
                    resetProgress();
                    setLaunchState(LaunchState.INSTALLING, message);
                }),
                fraction -> Platform.runLater(() -> setProgress(fraction)));
        }, process -> {
            setLaunchState(LaunchState.RUNNING, version.getText());
            showToast("Minecraft успешно запущен", false);
            process.onExit().thenAccept(exited -> Platform.runLater(() -> {
                if (exited.exitValue() == 0) {
                    setLaunchState(LaunchState.READY, installedVersion());
                } else {
                    setLaunchState(LaunchState.ERROR, "Minecraft завершился с кодом " + exited.exitValue());
                    showValidation("Minecraft завершился с ошибкой. Лог: " + launchDirectory.resolve("logs/minecraft-process.log"), true);
                    showToast("Minecraft завершился с ошибкой " + exited.exitValue(), true);
                }
            }));
        }, error -> {
            LauncherLog.error("Ошибка установки или запуска Minecraft", error);
            setLaunchState(LaunchState.ERROR, "Ошибка запуска");
            showValidation(error.getMessage(), true);
            showToast(error.getMessage(), true);
        });
    }

    private void setLaunchState(LaunchState state, String detail) {
        String previous = version.getText();
        launchState = state;
        play.setText(state.buttonText);
        play.setDisable(!state.actionable);
        // Кнопка меняет "характер" по состоянию: установка — спокойный
        // нейтральный тон, обновление — акцентный, запуск — пульс.
        // Пользователь понимает, что произойдёт, не читая текст.
        play.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("install"), state == LaunchState.INSTALL_REQUIRED);
        play.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("update"), state == LaunchState.UPDATE_AVAILABLE);
        play.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("busy"), state.busy);
        setActionIcon(state.icon, true);
        animatePlayPulse(state.busy);
        if (versionTrigger != null) versionTrigger.setDisable(state.busy || state == LaunchState.RUNNING);
        status.setText("●");
        status.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("error"), state == LaunchState.ERROR);
        status.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("accent"), state == LaunchState.UPDATE_AVAILABLE || state == LaunchState.UPDATING);
        version.setText(state.statusText + (detail == null || detail.isBlank() ? "" : " · " + detail));
        if (!config.reducedMotion && !previous.equals(version.getText())) {
            FadeTransition fade = new FadeTransition(Duration.millis(220), version);
            fade.setFromValue(0.25);
            fade.setToValue(1);
            fade.play();
        }
        progress.setVisible(state.busy);
        progress.setManaged(state.busy);
        if (!state.busy) {
            stopProgressCreep();
            displayedProgress = 0;
            targetProgress = 0;
            progress.setProgress(0);
        }
        updateInstallBadge();
    }

    /**
     * Доля установки 0..1 из фонового потока установки; -1 — неопределённая фаза.
     * Значение только растёт в пределах одного запуска: фазы установки идут
     * последовательно, а откат полоски назад читается как сбой.
     */
    private void setProgress(double fraction) {
        if (fraction < 0) {
            // Неопределённая фаза (Forge): полоска не прыгает в sweep, а
            // медленно ползёт от текущей доли к 1.0, чтобы движение не
            // останавливалось на многоминутном шаге.
            startProgressCreep();
            return;
        }
        stopProgressCreep();
        animateProgressTo(Math.min(1.0, fraction));
    }

    /** Начинает новую фазу: снимает монотонный потолок предыдущей. */
    private void resetProgress() {
        stopProgressCreep();
        if (progressAnimation != null) progressAnimation.stop();
        displayedProgress = 0;
        targetProgress = 0;
        progress.setProgress(0);
    }

    /** Плавно подтягивает полоску к цели, чтобы пачки файлов не дёргали её рывками. */
    private void animateProgressTo(double fraction) {
        if (fraction <= targetProgress) return;
        targetProgress = fraction;
        if (progressAnimation != null) progressAnimation.stop();
        if (config.reducedMotion) {
            displayedProgress = fraction;
            progress.setProgress(fraction);
            return;
        }
        progressAnimation = new Timeline(new KeyFrame(Duration.millis(260),
            new KeyValue(progress.progressProperty(), fraction, Motion.OUT_CUBIC)));
        progressAnimation.setOnFinished(event -> displayedProgress = fraction);
        progressAnimation.play();
    }

    /**
     * Forge не отдаёт разбираемый прогресс, поэтому последние 10% дорисовываем
     * синтетически: шаг замедляется по мере приближения к 1.0 и никогда его
     * не достигает — финальный 1.0 приходит только из реального завершения.
     */
    private void startProgressCreep() {
        if (progressCreep != null) return;
        if (progressAnimation != null) progressAnimation.stop();
        // Анимация могла быть прервана на середине — берём реальное значение
        // полоски, иначе ползание стартует с устаревшей доли и бар прыгнет назад.
        displayedProgress = Math.max(0, progress.getProgress());
        targetProgress = displayedProgress;
        progressCreep = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            double remaining = 0.995 - displayedProgress;
            if (remaining <= 0.001) return;
            displayedProgress += Math.max(0.0004, remaining * 0.012);
            targetProgress = displayedProgress;
            progress.setProgress(displayedProgress);
        }));
        progressCreep.setCycleCount(javafx.animation.Animation.INDEFINITE);
        progressCreep.play();
    }

    private void stopProgressCreep() {
        if (progressCreep == null) return;
        progressCreep.stop();
        progressCreep = null;
    }

    /** Отрисованная доля полоски; растёт монотонно до смены состояния. */
    private double displayedProgress;
    private double targetProgress;
    private Timeline progressAnimation;
    private Timeline progressCreep;

    private ScaleTransition playPulse;
    private FadeTransition playPulseReduced;

    /**
     * Мягкая пульсация, пока идёт установка/запуск.
     *
     * <p>Пульс живёт на подписи кнопки, а не на самой кнопке: масштаб кнопки
     * занят наведением, и две бесконечные анимации одного свойства дают рывки.
     * В режиме пониженной анимации масштабирование заменяется изменением
     * прозрачности — бесконечное движение является вестибулярным раздражителем.
     */
    private void animatePlayPulse(boolean busy) {
        if (busy) {
            if (config.reducedMotion) {
                if (playPulseReduced != null) return;
                playPulseReduced = new FadeTransition(Duration.millis(1100), play);
                playPulseReduced.setFromValue(1.0);
                playPulseReduced.setToValue(0.85);
                playPulseReduced.setAutoReverse(true);
                playPulseReduced.setCycleCount(javafx.animation.Animation.INDEFINITE);
                playPulseReduced.play();
            } else {
                if (playPulse != null) return;
                playPulse = new ScaleTransition(Duration.millis(900), actionIconBox);
                playPulse.setFromX(1.0);
                playPulse.setFromY(1.0);
                playPulse.setToX(1.08);
                playPulse.setToY(1.08);
                playPulse.setAutoReverse(true);
                playPulse.setCycleCount(javafx.animation.Animation.INDEFINITE);
                playPulse.play();
            }
        } else {
            // stop() не возвращает свойству исходное значение — сбрасываем вручную.
            if (playPulse != null) {
                playPulse.stop();
                actionIconBox.setScaleX(1.0);
                actionIconBox.setScaleY(1.0);
                playPulse = null;
            }
            if (playPulseReduced != null) {
                playPulseReduced.stop();
                play.setOpacity(1.0);
                playPulseReduced = null;
            }
        }
    }

    private StackPane actionIconBox;
    private ActionIcon currentActionIcon;
    private Node currentActionIconNode;
    private ParallelTransition iconSwap;
    private RotateTransition spinnerSpin;

    /**
     * Меняет иконку кнопки действия с перекрёстным растворением.
     * Уходящая иконка сжимается и гаснет, входящая приходит с лёгким перелётом.
     */
    private void setActionIcon(ActionIcon icon, boolean animated) {
        if (icon == currentActionIcon) return;
        currentActionIcon = icon;

        stopSpinner();
        if (iconSwap != null) {
            iconSwap.stop();
            iconSwap = null;
        }

        Node incoming = icon.create();
        Node outgoing = currentActionIconNode;
        currentActionIconNode = incoming;

        if (icon == ActionIcon.SPINNER) {
            startSpinner(incoming);
        }

        if (!animated || config.reducedMotion || outgoing == null) {
            actionIconBox.getChildren().setAll(incoming);
            return;
        }

        incoming.setOpacity(0);
        incoming.setScaleX(0.6);
        incoming.setScaleY(0.6);
        actionIconBox.getChildren().setAll(outgoing, incoming);

        Duration duration = Duration.millis(180);
        FadeTransition fadeOut = new FadeTransition(duration, outgoing);
        fadeOut.setToValue(0);
        fadeOut.setInterpolator(Motion.IN_CUBIC);
        ScaleTransition scaleOut = new ScaleTransition(duration, outgoing);
        scaleOut.setToX(0.6);
        scaleOut.setToY(0.6);
        scaleOut.setInterpolator(Motion.IN_CUBIC);
        FadeTransition fadeIn = new FadeTransition(duration, incoming);
        fadeIn.setToValue(1);
        fadeIn.setInterpolator(Motion.OUT_CUBIC);
        ScaleTransition scaleIn = new ScaleTransition(duration, incoming);
        scaleIn.setToX(1);
        scaleIn.setToY(1);
        scaleIn.setInterpolator(Motion.OUT_BACK_SOFT);

        iconSwap = new ParallelTransition(fadeOut, scaleOut, fadeIn, scaleIn);
        iconSwap.setOnFinished(event -> {
            actionIconBox.getChildren().remove(outgoing);
            iconSwap = null;
        });
        iconSwap.play();
    }

    /** Непрерывное вращение дуги-спиннера вокруг геометрического центра сетки. */
    private void startSpinner(Node node) {
        if (config.reducedMotion) {
            // Вращение сохраняем и здесь: это индикатор работы, а не украшение,
            // но замедляем, чтобы движение не раздражало.
            spinnerSpin = new RotateTransition(Duration.millis(1600), node);
        } else {
            spinnerSpin = new RotateTransition(Duration.millis(900), node);
        }
        spinnerSpin.setNode(node);
        spinnerSpin.setByAngle(360);
        spinnerSpin.setInterpolator(Interpolator.LINEAR);
        spinnerSpin.setCycleCount(javafx.animation.Animation.INDEFINITE);
        spinnerSpin.play();
    }

    private void stopSpinner() {
        if (spinnerSpin != null) {
            spinnerSpin.stop();
            Node node = spinnerSpin.getNode();
            if (node != null) node.setRotate(0);
            spinnerSpin = null;
        }
    }

    /**
     * Индикатор "что установлено" на карточке версии: точка + текст живут
     * постоянно, в отличие от исчезающих тостов.
     */
    private void updateInstallBadge() {
        if (installBadge == null) return;
        String kind;
        String text;
        switch (launchState) {
            case READY, RUNNING -> { kind = "ok"; text = selectedVersion.valkyrie() ? "Установлен · " + installedVersion() : "Установлена"; }
            case UPDATE_AVAILABLE -> { kind = "update"; text = "Доступно обновление" + (manifest != null ? " · " + manifest.version() : ""); }
            case INSTALL_REQUIRED -> { kind = "none"; text = "Не установлена"; }
            case ERROR -> { kind = "error"; text = "Ошибка"; }
            default -> { kind = "none"; text = ""; }
        }
        installBadge.setVisible(!text.isBlank());
        installBadge.setManaged(!text.isBlank());
        installBadgeText.setText(text);
        for (String state : new String[]{"ok", "update", "none", "error"}) {
            installBadge.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass(state), state.equals(kind));
        }
    }

    private boolean validateNickname(boolean showError) {
        boolean valid = nickname.getText().trim().matches("[A-Za-z0-9_]{3,16}");
        nickname.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("invalid"), !valid);
        if (showError && !valid) showValidation("3–16 латинских букв, цифр или _", true);
        else if (valid) hideValidation();
        return valid;
    }

    private void showValidation(String text, boolean error) {
        validation.setText(text == null || text.isBlank() ? "Неизвестная ошибка" : text);
        validation.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("error"), error);
        validation.setManaged(true);
        validation.setVisible(true);
    }

    private void hideValidation() {
        validation.setManaged(false);
        validation.setVisible(false);
    }

    private void repairGameInstallation() {
        setSettings(false);
        setLaunchState(LaunchState.INSTALLING, "Проверяем игровые файлы");
        runTask(() -> {
            installation.repair(config, selectedVersion,
                message -> Platform.runLater(() -> setLaunchState(LaunchState.INSTALLING, message)),
                fraction -> Platform.runLater(() -> setProgress(fraction)));
            return null;
        }, ignored -> {
            setLaunchState(isReady() ? LaunchState.READY : LaunchState.INSTALL_REQUIRED, installedVersion());
            showToast(selectedVersion.valkyrie() ? "Minecraft и Forge готовы" : "Minecraft готов", false);
        }, error -> {
            setLaunchState(LaunchState.ERROR, "Ошибка установки");
            showValidation(error.getMessage(), true);
            showToast(error.getMessage(), true);
        });
    }

    private void showToast(String message, boolean error) {
        if (toastLayer == null) return;
        Label icon = new Label(error ? "!" : "✓");
        icon.getStyleClass().add(error ? "toast-error-icon" : "toast-success-icon");
        Label text = new Label(message == null || message.isBlank() ? "Неизвестная ошибка" : message);
        text.setWrapText(true);
        text.getStyleClass().add("toast-text");
        HBox toast = new HBox(11, icon, text);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.getStyleClass().addAll("toast", error ? "toast-error" : "toast-success");
        toast.setOpacity(0);
        toast.setTranslateY(8);
        toastLayer.getChildren().add(0, toast);
        while (toastLayer.getChildren().size() > 3) {
            toastLayer.getChildren().remove(toastLayer.getChildren().size() - 1);
        }
        Duration enterDuration = config.reducedMotion ? Duration.millis(120) : Duration.millis(240);
        FadeTransition fade = new FadeTransition(enterDuration, toast);
        fade.setToValue(1);
        TranslateTransition move = new TranslateTransition(enterDuration, toast);
        move.setToY(0);
        move.setInterpolator(EASE_OUT);
        ParallelTransition enter = new ParallelTransition(fade, move);
        enter.setOnFinished(event -> {
            Timeline wait = new Timeline(new KeyFrame(Duration.seconds(error ? 5 : 3.4), ignored -> dismissToast(toast)));
            wait.play();
        });
        enter.play();
    }

    private void dismissToast(Node toast) {
        FadeTransition fade = new FadeTransition(Duration.millis(180), toast);
        fade.setToValue(0);
        fade.setOnFinished(event -> toastLayer.getChildren().remove(toast));
        fade.play();
    }

    private void setChanges(List<String> items) {
        changes.getChildren().clear();
        for (String item : items) {
            Label text = new Label(item);
            text.setWrapText(true);
            text.getStyleClass().add("change-text");
            Label dot = new Label("✦");
            dot.getStyleClass().add("change-dot");
            HBox row = new HBox(11, dot, text);
            row.getStyleClass().add("change-row");
            changes.getChildren().add(row);
        }
    }

    /** Предельное число ступеней каскада: дальше задержка читается как подвисание. */
    private static final int STAGGER_LIMIT = 8;

    private void revealChanges() {
        if (config.reducedMotion) return;
        int index = 0;
        for (Node row : changes.getChildren()) {
            row.setOpacity(0);
            row.setTranslateY(7);
            // Ступени за пределом лимита стартуют одновременно с последней,
            // иначе хвост длинного списка проявляется с заметным опозданием.
            long delay = Math.min(index, STAGGER_LIMIT) * 32L;
            FadeTransition fade = new FadeTransition(Duration.millis(210), row);
            fade.setDelay(Duration.millis(delay));
            fade.setToValue(1);
            TranslateTransition move = new TranslateTransition(Duration.millis(230), row);
            move.setDelay(Duration.millis(delay));
            move.setToY(0);
            move.setInterpolator(Motion.OUT_CUBIC);
            new ParallelTransition(fade, move).play();
            index++;
        }
    }

    private boolean isInstalled(UpdateService.Manifest current) {
        Path jar = config.instanceDirectory.resolve("mods").resolve("valkyrieclient-" + current.version() + ".jar");
        if (!Files.isRegularFile(jar)) return false;
        try {
            return UpdateService.sha256(jar).equals(current.sha256());
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean hasInstalledClient() {
        Path mods = config.instanceDirectory.resolve("mods");
        if (!Files.isDirectory(mods)) return false;
        try (var files = Files.list(mods)) {
            return files.anyMatch(path -> path.getFileName().toString().matches("valkyrieclient-.*\\.jar"));
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean isReady() {
        return installation.installed(config, selectedVersion) && (!selectedVersion.valkyrie() || hasInstalledClient());
    }

    private String installedVersion() {
        if (!selectedVersion.valkyrie()) return selectedVersion.displayName();
        Path mods = config.instanceDirectory.resolve("mods");
        if (Files.isDirectory(mods)) {
            try (var files = Files.list(mods)) {
                String name = files.map(path -> path.getFileName().toString()).filter(file -> file.matches("valkyrieclient-.*\\.jar")).findFirst().orElse(null);
                if (name != null) return "Valkyrie " + name.substring("valkyrieclient-".length(), name.length() - 4);
            } catch (IOException ignored) {
            }
        }
        return "Valkyrie не установлен";
    }

    private void loadVersions() {
        runTask(versions::load, loaded -> {
            availableVersions = loaded;
            filterVersions();
            GameVersion restored = loaded.stream().filter(item -> item.id().equals(config.selectedVersion)
                && item.valkyrie() == config.selectedValkyrie).findFirst().orElse(GameVersion.VALKYRIE);
            selectVersion(restored);
        }, error -> {
            availableVersions = List.of(GameVersion.VALKYRIE);
            filterVersions();
            selectVersion(GameVersion.VALKYRIE);
            showToast("Каталог версий недоступен", true);
        });
    }

    private void selectVersion(GameVersion selected) {
        if (selected == null) return;
        selectedVersion = selected;
        config.select(selected);
        updateSelectedVersion(selected);
        saveQuietly();
        checkUpdates();
    }

    private String memoryText() {
        return config.automaticMemory ? "Auto · " + config.effectiveMemoryMb() + " MB" : config.memoryMb + " MB";
    }

    private void updateSelectedVersion(GameVersion selected) {
        if (versionTriggerTitle == null) return;
        versionTriggerTitle.setText(selected.valkyrie() ? "Valkyrie Client" : "Minecraft " + selected.id());
        versionTriggerMeta.setText(selected.valkyrie() ? "Minecraft 1.21.10 · Forge 60.1.0" :
            ("snapshot".equals(selected.type()) ? "Snapshot · Java selected automatically" : "Vanilla release · Isolated instance"));
        versionTriggerBadge.setVisible(selected.valkyrie());
        versionTriggerBadge.setManaged(selected.valkyrie());
        footerPlatform.setText(selected.valkyrie() ? "Minecraft 1.21.10 · Forge 60.1.0" : "Minecraft " + selected.id() + " · Vanilla");
        footerJava.setText(selected.valkyrie() ? "JAVA 21" : "JAVA AUTO");
        if (versionList != null) versionList.getSelectionModel().select(selected);
        if (!config.reducedMotion) {
            FadeTransition fade = new FadeTransition(Duration.millis(220), versionTrigger);
            fade.setFromValue(0.45);
            fade.setToValue(1);
            fade.play();
        }
    }

    private void chooseJava(Label javaValue) {
        if ("custom".equals(config.javaMode)) {
            config.javaMode = "automatic";
            config.customJava = "";
            javaValue.setText("Automatic · downloaded if missing");
            saveQuietly();
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose javaw.exe or java.exe");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java executable", "javaw.exe", "java.exe"));
        java.io.File selected = chooser.showOpenDialog(stage);
        if (selected != null) {
            config.javaMode = "custom";
            config.customJava = selected.getAbsolutePath();
            javaValue.setText("Custom · " + selected.getName());
            saveQuietly();
        }
    }

    private void openPath(Path path) {
        try {
            Files.createDirectories(path);
            java.awt.Desktop.getDesktop().open(path.toFile());
        } catch (IOException | UnsupportedOperationException error) {
            showToast("Не удалось открыть папку", true);
        }
    }

    private void revealHero() {
        if (config.reducedMotion) return;
        hero.setOpacity(0);
        hero.setTranslateY(14);
        FadeTransition fade = new FadeTransition(Duration.millis(520), hero);
        fade.setToValue(1);
        TranslateTransition move = new TranslateTransition(Duration.millis(560), hero);
        move.setToY(0);
        move.setInterpolator(EASE_OUT);
        new ParallelTransition(fade, move).play();
    }

    private void animatePress(Node node) {
        if (config.reducedMotion) {
            // Отклик на нажатие сохраняем даже здесь: он короткий, без перемещения,
            // и является основной обратной связью о том, что клик принят.
            ScaleTransition quiet = new ScaleTransition(Duration.millis(70), node);
            quiet.setToX(0.99);
            quiet.setToY(0.99);
            quiet.setAutoReverse(true);
            quiet.setCycleCount(2);
            quiet.play();
            return;
        }
        ScaleTransition down = new ScaleTransition(Duration.millis(90), node);
        down.setToX(0.97);
        down.setToY(0.97);
        down.setInterpolator(Motion.OUT_QUAD);
        ScaleTransition up = new ScaleTransition(Duration.millis(160), node);
        up.setToX(1.0);
        up.setToY(1.0);
        // Мягкий перелёт на возврате — ощущение упругости вместо плоского отката.
        up.setInterpolator(Motion.OUT_BACK_SOFT);
        new SequentialTransition(down, up).play();
    }

    /**
     * Приподнимает кнопку при наведении.
     *
     * <p>Анимация создаётся один раз и проигрывается в обе стороны сменой знака
     * скорости: так прерванное наведение продолжается с текущей точки, а не
     * прыгает к началу, и не порождает объектов на каждое движение мыши.
     */
    private void installHoverLift(Region target, double scale) {
        Timeline lift = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(target.scaleXProperty(), 1.0, Motion.OUT_CUBIC),
                new KeyValue(target.scaleYProperty(), 1.0, Motion.OUT_CUBIC)),
            new KeyFrame(Duration.millis(140),
                new KeyValue(target.scaleXProperty(), scale, Motion.OUT_CUBIC),
                new KeyValue(target.scaleYProperty(), scale, Motion.OUT_CUBIC)));
        target.hoverProperty().addListener((ignored, old, hover) -> {
            if (config.reducedMotion || target.isDisabled()) return;
            lift.setRate(hover ? 1 : -1);
            lift.play();
        });
        // При блокировке кнопки масштаб может остаться увеличенным — сбрасываем.
        target.disabledProperty().addListener((ignored, old, disabled) -> {
            if (disabled) {
                lift.stop();
                target.setScaleX(1.0);
                target.setScaleY(1.0);
            }
        });
    }

    private void saveQuietly() {
        try {
            config.save();
        } catch (IOException error) {
            showValidation("Не удалось сохранить настройки", true);
        }
    }

    private <T> void runTask(Callable<T> work, Consumer<T> success, Consumer<Exception> failure) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> success.accept(task.getValue()));
        task.setOnFailed(event -> {
            Throwable cause = task.getException();
            LauncherLog.error("Фоновая операция launcher завершилась ошибкой", cause);
            failure.accept(cause instanceof Exception exception ? exception : new Exception(cause));
        });
        Thread.startVirtualThread(task);
    }

    private static Button iconButton(String text, String... classes) {
        Button button = new Button(text);
        button.getStyleClass().addAll(classes);
        button.setCursor(Cursor.HAND);
        return button;
    }

    private static ImageView image(String path, double width) {
        ImageView view = new ImageView(new Image(resource(path), true));
        view.setFitWidth(width);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }

    private static String resource(String path) {
        var resource = ValkyrieLauncherApp.class.getResource(path);
        if (resource == null) throw new IllegalStateException("Missing launcher resource: " + path);
        return resource.toExternalForm();
    }

    private static void loadFonts() {
        javafx.scene.text.Font.loadFont(resource("/assets/valkyrieclient/font/inter-regular.ttf"), 14);
        javafx.scene.text.Font.loadFont(resource("/assets/valkyrieclient/font/inter-bold.ttf"), 14);
        javafx.scene.text.Font.loadFont(resource("/assets/valkyrieclient/font/nunito.ttf"), 14);
        javafx.scene.text.Font.loadFont(resource("/assets/valkyrieclient/font/jetbrains-mono.ttf"), 11);
        LauncherLog.info("Доступные семейства шрифтов: " + javafx.scene.text.Font.getFamilies().stream()
            .filter(family -> family.startsWith("Inter")).toList());
    }

    /** Обозначение иконки кнопки действия. Узел создаётся по требованию: один
     *  экземпляр Node не может присутствовать в графе сцены дважды. */
    private enum ActionIcon {
        PLAY, DOWNLOAD, UPDATE, SPINNER, STOP, ALERT;

        Node create() {
            return switch (this) {
                case PLAY -> LauncherIcons.play();
                case DOWNLOAD -> LauncherIcons.download();
                case UPDATE -> LauncherIcons.update();
                case SPINNER -> LauncherIcons.spinner();
                case STOP -> LauncherIcons.stop();
                case ALERT -> LauncherIcons.alert();
            };
        }
    }

    private enum LaunchState {
        CHECKING("Проверяем", "Проверка", false, true, ActionIcon.SPINNER),
        INSTALL_REQUIRED("Установить и запустить", "Требуется установка", true, false, ActionIcon.DOWNLOAD),
        INSTALLING("Устанавливаем", "Подготовка игры", false, true, ActionIcon.SPINNER),
        READY("Запустить", "Готово", true, false, ActionIcon.PLAY),
        UPDATE_AVAILABLE("Обновить и запустить", "Доступно обновление", true, false, ActionIcon.UPDATE),
        UPDATING("Обновляем", "Обновление", false, true, ActionIcon.SPINNER),
        STARTING("Запускаем", "Запуск", false, true, ActionIcon.SPINNER),
        RUNNING("Запущено", "Игра запущена", false, false, ActionIcon.STOP),
        UNAVAILABLE("Не установлен", "Клиент недоступен", false, false, ActionIcon.ALERT),
        ERROR("Повторить", "Ошибка", true, false, ActionIcon.ALERT);

        private final String buttonText;
        private final String statusText;
        private final boolean actionable;
        private final boolean busy;
        private final ActionIcon icon;

        LaunchState(String buttonText, String statusText, boolean actionable, boolean busy, ActionIcon icon) {
            this.buttonText = buttonText;
            this.statusText = statusText;
            this.actionable = actionable;
            this.busy = busy;
            this.icon = icon;
        }
    }
}
