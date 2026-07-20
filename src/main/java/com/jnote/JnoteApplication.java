package com.jnote;

import com.jnote.io.NoteIO;
import com.jnote.io.NotePathPolicy;
import com.jnote.markdown.SimpleMarkdownRenderer;
import com.jnote.model.NoteFormat;
import com.jnote.model.OpenDocument;
import com.jnote.state.AppState;
import com.jnote.state.StateStore;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import netscape.javascript.JSObject;

import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class JnoteApplication extends Application {
    private static final int RECENT_ROOT_LIMIT = 10;
    private static final double DEFAULT_WINDOW_WIDTH = 1280;
    private static final double DEFAULT_WINDOW_HEIGHT = 800;
    private static final double MIN_WINDOW_WIDTH = 980;
    private static final double MIN_WINDOW_HEIGHT = 640;
    private static final double SIDEBAR_MIN_WIDTH = 220;
    private static final double SIDEBAR_MAX_WIDTH = 520;
    private static final double TREE_INDENT_WIDTH = 18.0;
    private static final long DIRECTORY_LONG_PRESS_NANOS = 350_000_000L;
    private static final String ICON_DRAG_HANDLE =
            "M5.5 7 L9 3.5 L12.5 7 M5.5 11 L9 14.5 L12.5 11";
    private static final DateTimeFormatter PASTE_IMAGE_NAME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final List<String> APPLICATION_ICONS = List.of(
            "/com/jnote/images/jnote-app-icon-16.png",
            "/com/jnote/images/jnote-app-icon-24.png",
            "/com/jnote/images/jnote-app-icon-32.png",
            "/com/jnote/images/jnote-app-icon-48.png",
            "/com/jnote/images/jnote-app-icon-64.png",
            "/com/jnote/images/jnote-app-icon-128.png",
            "/com/jnote/images/jnote-app-icon-256.png");
    private static final String TITLE_BAR_ICON = "/com/jnote/images/jnote-app-icon-28.png";
    private static final String ICON_FOLDER_PLUS = "M3 7 H9 L11 9 H21 V19 C21 20 20 21 19 21 H5 C4 21 3 20 3 19 Z M12 12 V18 M9 15 H15";
    private static final String ICON_FOLDER_MINUS = "M3 7 H9 L11 9 H21 V19 C21 20 20 21 19 21 H5 C4 21 3 20 3 19 Z M9 15 H15";
    private static final String ICON_RENAME = "M4 20 L8 19 L19 8 L16 5 L5 16 Z M14 7 L17 10";
    private static final String ICON_TRASH = "M4 7 H20 M10 11 V17 M14 11 V17 M6 7 L7 21 H17 L18 7 M9 7 V4 H15 V7";
    private static final String ICON_DIALOG_FILE = "M6 3 H14 L19 8 V21 H6 Z M14 3 V8 H19";
    private static final String ICON_DIALOG_FOLDER = "M3 6 H9 L11 8 H21 V20 H3 Z";
    private static final String ICON_DIALOG_WARNING = "M12 3 L22 21 H2 Z M12 9 V14 M12 18 L12 18";
    private static final String ICON_WINDOW_MINIMIZE = "M4 10 H16";
    private static final String ICON_WINDOW_MAXIMIZE = "M4 4 H16 V16 H4 Z";
    private static final String ICON_WINDOW_RESTORE = "M6 4 H16 V14 M4 6 H14 V16 H4 Z";
    private static final String ICON_WINDOW_CLOSE = "M5 5 L15 15 M15 5 L5 15";
    private final StateStore stateStore = new StateStore();
    private final SimpleMarkdownRenderer renderer = new SimpleMarkdownRenderer();
    private final EditorBridge editorBridge = new EditorBridge();
    private final Map<Path, OpenDocument> openDocuments = new LinkedHashMap<>();
    private final Set<Path> expandedDirectories = new LinkedHashSet<>();

    private AppState appState;
    private OpenDocument activeDocument;
    private List<Image> applicationIcons = List.of();
    private Image titleBarIcon;
    private Stage primaryStage;
    private BorderPane root;
    private BorderPane mainArea;
    private VBox sidebar;
    private HBox sidebarShell;
    private VBox treeBody;
    private ScrollPane treeScroll;
    private HBox tabsBox;
    private WebView webView;
    private WebEngine webEngine;
    private TextField searchField;
    private Label saveStatus;
    private Label treeStatus;
    private Label editorStatus;
    private Path selectedDirectory;
    private Path selectedTreePath;
    private Path selectedTreeRoot;
    private boolean sidebarVisible = true;
    private boolean loadingEditor;
    private double sidebarResizeStartX;
    private double sidebarResizeStartWidth;
    private double titleBarDragOffsetX;
    private double titleBarDragOffsetY;
    private boolean resizingWindow;
    private Cursor windowResizeCursor = Cursor.DEFAULT;
    private double windowResizeStartScreenX;
    private double windowResizeStartScreenY;
    private double windowResizeStartX;
    private double windowResizeStartY;
    private double windowResizeStartWidth;
    private double windowResizeStartHeight;
    private boolean restoringState;
    private long treeRenderGeneration;
    private Path draggedTreeRoot;

    private record RestoreRequest(
            List<Path> recentRoots,
            List<Path> openFiles,
            Path activeFile,
            Map<Path, Set<String>> collapsedHeadings) {
    }

    private record LoadedDocument(OpenDocument document, String renderedHtml) {
    }

    private record PrimaryRestore(List<Path> recentRoots, LoadedDocument primary, List<Path> remainingFiles) {
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        applicationIcons = APPLICATION_ICONS.stream()
                .map(path -> new Image(getClass().getResource(path).toExternalForm(), false))
                .toList();
        titleBarIcon = new Image(getClass().getResource(TITLE_BAR_ICON).toExternalForm(), false);
        stage.getIcons().setAll(applicationIcons);
        stage.initStyle(StageStyle.UNDECORATED);
        appState = stateStore.load();
        root = buildShell();
        Scene scene = new Scene(root, initialWindowWidth(), initialWindowHeight());
        scene.getStylesheets().add(getClass().getResource("/com/jnote/styles/app.css").toExternalForm());
        installAccelerators(scene);
        installSceneFilters(scene);
        installWindowResize(scene);

        stage.setTitle("Jnote");
        stage.setScene(scene);
        stage.setMinWidth(MIN_WINDOW_WIDTH);
        stage.setMinHeight(MIN_WINDOW_HEIGHT);
        stage.setOnCloseRequest(event -> {
            syncActiveDocument();
            if (!confirmAllSaved()) {
                event.consume();
                return;
            }
            persistState();
        });

        CompletableFuture<PrimaryRestore> restoreFuture = beginRestoreState();
        stage.show();
        Platform.runLater(() -> {
            initializeEditor();
            restoreFuture.whenComplete((restored, error) ->
                    Platform.runLater(() -> applyPrimaryRestore(restored, error)));
        });
    }

    private double initialWindowWidth() {
        return appState.windowWidth() > 0
                ? Math.max(MIN_WINDOW_WIDTH, appState.windowWidth())
                : DEFAULT_WINDOW_WIDTH;
    }

    private double initialWindowHeight() {
        return appState.windowHeight() > 0
                ? Math.max(MIN_WINDOW_HEIGHT, appState.windowHeight())
                : DEFAULT_WINDOW_HEIGHT;
    }

    private BorderPane buildShell() {
        BorderPane shell = new BorderPane();
        shell.getStyleClass().add("app-shell");
        shell.setTop(buildTop());
        mainArea = new BorderPane();
        mainArea.getStyleClass().add("main-area");
        Label editorLoading = new Label("正在准备编辑器…");
        editorLoading.getStyleClass().add("editor-loading");
        mainArea.setCenter(new StackPane(editorLoading));
        sidebar = buildSidebar();
        sidebarShell = buildResizableSidebar(sidebar);
        mainArea.setRight(sidebarShell);
        shell.setCenter(mainArea);
        shell.setBottom(buildStatusbar());
        return shell;
    }

    private void initializeEditor() {
        if (webView != null) {
            return;
        }
        webView = new WebView();
        webView.getStyleClass().add("web-view");
        webEngine = webView.getEngine();
        webEngine.locationProperty().addListener((observable, oldLocation, newLocation) -> {
            if (isEditorPageLocation(newLocation)) {
                return;
            }
            webEngine.getLoadWorker().cancel();
            if (isExternalWebLocation(newLocation)) {
                Platform.runLater(() -> openWithDesktop(newLocation));
            }
        });
        webEngine.getLoadWorker().stateProperty().addListener(installBridgeWhenReady());
        mainArea.setCenter(webView);
    }

    private VBox buildTop() {
        return new VBox(buildTitleBar(), buildTabsBar());
    }

    private HBox buildTitleBar() {
        ImageView brand = new ImageView(titleBarIcon);
        brand.setFitWidth(28);
        brand.setFitHeight(28);
        brand.setPreserveRatio(true);
        brand.setSmooth(false);
        brand.setMouseTransparent(true);
        brand.getStyleClass().add("title-brand");
        Label title = new Label("Jnote");
        title.getStyleClass().add("window-title");
        HBox dragArea = new HBox(9, brand, title);
        dragArea.getStyleClass().add("title-drag-area");
        dragArea.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(dragArea, Priority.ALWAYS);
        dragArea.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY || primaryStage.isMaximized()) {
                return;
            }
            titleBarDragOffsetX = event.getScreenX() - primaryStage.getX();
            titleBarDragOffsetY = event.getScreenY() - primaryStage.getY();
        });
        dragArea.setOnMouseDragged(event -> {
            if (event.getButton() != MouseButton.PRIMARY || primaryStage.isMaximized()) {
                return;
            }
            primaryStage.setX(event.getScreenX() - titleBarDragOffsetX);
            primaryStage.setY(event.getScreenY() - titleBarDragOffsetY);
        });
        dragArea.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                toggleMaximized();
            }
        });

        SVGPath minimizeIcon = windowIcon(ICON_WINDOW_MINIMIZE);
        SVGPath maximizeIcon = windowIcon(ICON_WINDOW_MAXIMIZE);
        SVGPath closeIcon = windowIcon(ICON_WINDOW_CLOSE);
        Button minimize = windowButton(minimizeIcon, "最小化", () -> primaryStage.setIconified(true));
        Button maximize = windowButton(maximizeIcon, "最大化", this::toggleMaximized);
        primaryStage.maximizedProperty().addListener((observable, oldValue, maximized) -> {
            maximizeIcon.setContent(maximized ? ICON_WINDOW_RESTORE : ICON_WINDOW_MAXIMIZE);
            maximize.setTooltip(new Tooltip(maximized ? "还原" : "最大化"));
        });
        Button close = windowButton(closeIcon, "关闭", this::requestClose);
        close.getStyleClass().add("window-close-button");

        HBox controls = new HBox(minimize, maximize, close);
        controls.getStyleClass().add("window-controls");
        HBox bar = new HBox(dragArea, controls);
        bar.getStyleClass().add("title-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private SVGPath windowIcon(String path) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.getStyleClass().add("window-icon-shape");
        return icon;
    }

    private Button windowButton(SVGPath icon, String tooltip, Runnable action) {
        StackPane iconBox = new StackPane(icon);
        iconBox.getStyleClass().add("window-icon-box");
        Button button = new Button();
        button.setGraphic(iconBox);
        button.getStyleClass().add("window-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(event -> action.run());
        return button;
    }

    private void toggleMaximized() {
        primaryStage.setMaximized(!primaryStage.isMaximized());
    }

    private void requestClose() {
        WindowEvent closeRequest = new WindowEvent(primaryStage, WindowEvent.WINDOW_CLOSE_REQUEST);
        Event.fireEvent(primaryStage, closeRequest);
        if (!closeRequest.isConsumed()) {
            primaryStage.hide();
        }
    }

    private Button iconButton(String text, String tooltip, Runnable action) {
        Button button = new Button();
        button.getStyleClass().add("icon-button");
        button.setGraphic(svgIcon(text));
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(event -> action.run());
        return button;
    }

    private StackPane svgIcon(String path) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.setFill(Color.TRANSPARENT);
        icon.setStroke(Color.web("#3f444a"));
        icon.setStrokeWidth(2.0);
        icon.setScaleX(0.72);
        icon.setScaleY(0.72);
        icon.getStyleClass().add("svg-icon");

        StackPane wrapper = new StackPane(icon);
        wrapper.setMinSize(18, 18);
        wrapper.setPrefSize(18, 18);
        wrapper.setMaxSize(18, 18);
        return wrapper;
    }

    private HBox buildTabsBar() {
        tabsBox = new HBox();
        tabsBox.getStyleClass().add("tabs-box");
        ScrollPane scroll = new ScrollPane(tabsBox);
        scroll.getStyleClass().add("tabs-scroll");
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        installSmoothScroll(scroll, true);

        searchField = new TextField();
        searchField.getStyleClass().add("search-field");
        searchField.setPromptText("查找 Ctrl+F");
        searchField.setOnAction(event -> findInDocument());
        searchField.setMinWidth(180);
        searchField.setPrefWidth(206);
        searchField.setMaxWidth(240);

        HBox bar = new HBox(8, scroll, searchField);
        bar.getStyleClass().add("tabs-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(scroll, Priority.ALWAYS);
        HBox.setHgrow(searchField, Priority.NEVER);
        return bar;
    }

    private VBox buildSidebar() {
        VBox panel = new VBox();
        panel.getStyleClass().add("sidebar");

        Label title = new Label("文件树");
        title.getStyleClass().add("section-title");
        Button openRoot = iconButton(ICON_FOLDER_PLUS, "添加顶级目录", this::openRootDirectory);
        Button removeRoot = iconButton(ICON_FOLDER_MINUS, "移除选中顶级目录（不删除文件）", this::removeSelectedRoot);
        Button renameItem = iconButton(ICON_RENAME, "重命名选中文件或文件夹（F2）", this::renameSelectedTreePath);
        Button deleteItem = iconButton(ICON_TRASH, "删除选中文件或文件夹", this::deleteSelectedTreePath);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(6, title, spacer, openRoot, removeRoot, renameItem, deleteItem);
        head.getStyleClass().add("sidebar-head");

        treeBody = new VBox();
        treeBody.getStyleClass().add("tree-body");
        treeScroll = new ScrollPane(treeBody);
        treeScroll.getStyleClass().add("tree-scroll");
        treeScroll.setFitToWidth(true);
        installSmoothScroll(treeScroll, false);
        VBox.setVgrow(treeScroll, Priority.ALWAYS);
        panel.getChildren().addAll(head, treeScroll);
        return panel;
    }

    private HBox buildResizableSidebar(VBox sidebarPanel) {
        Region handle = new Region();
        handle.getStyleClass().add("sidebar-resize-handle");
        handle.setOnMousePressed(event -> {
            sidebarResizeStartX = event.getScreenX();
            sidebarResizeStartWidth = sidebarPanel.getWidth();
        });
        handle.setOnMouseDragged(event -> {
            double delta = event.getScreenX() - sidebarResizeStartX;
            double nextWidth = clamp(sidebarResizeStartWidth - delta, SIDEBAR_MIN_WIDTH, SIDEBAR_MAX_WIDTH);
            sidebarPanel.setMinWidth(nextWidth);
            sidebarPanel.setPrefWidth(nextWidth);
        });

        HBox shell = new HBox(handle, sidebarPanel);
        shell.getStyleClass().add("sidebar-shell");
        return shell;
    }

    private void installSmoothScroll(ScrollPane scrollPane, boolean horizontal) {
        SmoothScrollAnimator animator = new SmoothScrollAnimator(scrollPane, horizontal);
        scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                return;
            }
            double delta = horizontal
                    ? (Math.abs(event.getDeltaY()) >= Math.abs(event.getDeltaX())
                            ? event.getDeltaY()
                            : event.getDeltaX())
                    : event.getDeltaY();
            if (!horizontal && Math.abs(event.getDeltaX()) > Math.abs(delta)) {
                return;
            }
            if (animator.scrollBy(delta)) {
                event.consume();
            }
        });
        scrollPane.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> animator.cancel());
    }

    private HBox buildStatusbar() {
        HBox statusbar = new HBox();
        statusbar.getStyleClass().add("statusbar");
        treeStatus = new Label("0 个顶级目录 · 最近目录上限 10");
        editorStatus = new Label("准备就绪");
        saveStatus = new Label("● 已自动保存");
        saveStatus.getStyleClass().add("status-pill");
        Label target = new Label("JavaFX 目标实现");
        Label encoding = new Label("UTF-8");
        Label lineEnding = new Label("CRLF");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        statusbar.getChildren().addAll(treeStatus, editorStatus, spacer, saveStatus, target, encoding, lineEnding);
        return statusbar;
    }

    private ChangeListener<Worker.State> installBridgeWhenReady() {
        return (observable, oldState, newState) -> {
            if (newState != Worker.State.SUCCEEDED) {
                return;
            }
            if (!isEditorPageLocation(webEngine.getLocation())) {
                return;
            }
            try {
                JSObject window = (JSObject) webEngine.executeScript("window");
                window.setMember("javaBridge", editorBridge);
                webEngine.executeScript("window.jnoteFocusEditor && window.jnoteFocusEditor()");
                webView.requestFocus();
            } catch (RuntimeException ignored) {
                // WebView can finish an empty load during startup; the next render will attach the bridge.
            }
        };
    }

    private void installAccelerators(Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.B, KeyCombination.CONTROL_DOWN), this::toggleSidebar);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN), () -> {
            searchField.requestFocus();
            searchField.selectAll();
        });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN), this::newFile);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN), this::newFolder);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN), () -> saveActiveDocument());
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN),
                () -> execEditor("document.execCommand('undo')"));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN),
                () -> execEditor("document.execCommand('redo')"));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F2), this::renameSelectedTreePath);
    }

    private void installSceneFilters(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() != KeyCode.V || !event.isControlDown() || event.isAltDown()) {
                return;
            }
            if (pasteImageFromClipboard()) {
                event.consume();
            }
        });
    }

    private void installWindowResize(Scene scene) {
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
            if (resizingWindow || primaryStage.isMaximized()) {
                return;
            }
            windowResizeCursor = resizeCursorAt(scene, event.getSceneX(), event.getSceneY());
            scene.setCursor(windowResizeCursor);
        });
        scene.addEventFilter(MouseEvent.MOUSE_EXITED, event -> {
            if (!resizingWindow) {
                windowResizeCursor = Cursor.DEFAULT;
                scene.setCursor(Cursor.DEFAULT);
            }
        });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY
                    || primaryStage.isMaximized()
                    || windowResizeCursor == Cursor.DEFAULT) {
                return;
            }
            resizingWindow = true;
            windowResizeStartScreenX = event.getScreenX();
            windowResizeStartScreenY = event.getScreenY();
            windowResizeStartX = primaryStage.getX();
            windowResizeStartY = primaryStage.getY();
            windowResizeStartWidth = primaryStage.getWidth();
            windowResizeStartHeight = primaryStage.getHeight();
            event.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!resizingWindow) {
                return;
            }
            resizeWindow(event.getScreenX() - windowResizeStartScreenX,
                    event.getScreenY() - windowResizeStartScreenY);
            event.consume();
        });
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (!resizingWindow) {
                return;
            }
            resizingWindow = false;
            windowResizeCursor = resizeCursorAt(scene, event.getSceneX(), event.getSceneY());
            scene.setCursor(windowResizeCursor);
            event.consume();
        });
    }

    private Cursor resizeCursorAt(Scene scene, double x, double y) {
        final double edge = 6;
        boolean west = x <= edge;
        boolean east = x >= scene.getWidth() - edge;
        boolean north = y <= edge;
        boolean south = y >= scene.getHeight() - edge;
        if (north && west) return Cursor.NW_RESIZE;
        if (north && east) return Cursor.NE_RESIZE;
        if (south && west) return Cursor.SW_RESIZE;
        if (south && east) return Cursor.SE_RESIZE;
        if (west) return Cursor.W_RESIZE;
        if (east) return Cursor.E_RESIZE;
        if (north) return Cursor.N_RESIZE;
        if (south) return Cursor.S_RESIZE;
        return Cursor.DEFAULT;
    }

    private void resizeWindow(double deltaX, double deltaY) {
        boolean west = windowResizeCursor == Cursor.W_RESIZE
                || windowResizeCursor == Cursor.NW_RESIZE
                || windowResizeCursor == Cursor.SW_RESIZE;
        boolean east = windowResizeCursor == Cursor.E_RESIZE
                || windowResizeCursor == Cursor.NE_RESIZE
                || windowResizeCursor == Cursor.SE_RESIZE;
        boolean north = windowResizeCursor == Cursor.N_RESIZE
                || windowResizeCursor == Cursor.NW_RESIZE
                || windowResizeCursor == Cursor.NE_RESIZE;
        boolean south = windowResizeCursor == Cursor.S_RESIZE
                || windowResizeCursor == Cursor.SW_RESIZE
                || windowResizeCursor == Cursor.SE_RESIZE;

        if (west) {
            double width = Math.max(MIN_WINDOW_WIDTH, windowResizeStartWidth - deltaX);
            primaryStage.setWidth(width);
            primaryStage.setX(windowResizeStartX + windowResizeStartWidth - width);
        } else if (east) {
            primaryStage.setWidth(Math.max(MIN_WINDOW_WIDTH, windowResizeStartWidth + deltaX));
        }
        if (north) {
            double height = Math.max(MIN_WINDOW_HEIGHT, windowResizeStartHeight - deltaY);
            primaryStage.setHeight(height);
            primaryStage.setY(windowResizeStartY + windowResizeStartHeight - height);
        } else if (south) {
            primaryStage.setHeight(Math.max(MIN_WINDOW_HEIGHT, windowResizeStartHeight + deltaY));
        }
    }

    private CompletableFuture<PrimaryRestore> beginRestoreState() {
        restoringState = true;
        Map<Path, Set<String>> collapsedHeadings = new LinkedHashMap<>();
        appState.collapsedHeadings().forEach((path, headings) ->
                collapsedHeadings.put(path, Set.copyOf(headings)));
        RestoreRequest request = new RestoreRequest(
                List.copyOf(appState.recentRoots()),
                List.copyOf(appState.openFiles()),
                appState.activeFile(),
                Map.copyOf(collapsedHeadings));
        return CompletableFuture.supplyAsync(() -> preparePrimaryRestore(request));
    }

    private PrimaryRestore preparePrimaryRestore(RestoreRequest request) {
        List<Path> validRoots = request.recentRoots().stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(Files::isDirectory)
                .filter(path -> !isImageDirectory(path))
                .distinct()
                .limit(RECENT_ROOT_LIMIT)
                .toList();

        List<Path> validFiles = request.openFiles().stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(Files::isRegularFile)
                .filter(path -> NoteFormat.fromPath(path).isSupportedFile())
                .distinct()
                .toList();
        Path preferred = request.activeFile() == null
                ? null
                : request.activeFile().toAbsolutePath().normalize();
        List<Path> loadOrder = new ArrayList<>(validFiles.size());
        if (preferred != null && validFiles.contains(preferred)) {
            loadOrder.add(preferred);
        }
        validFiles.stream().filter(path -> !path.equals(preferred)).forEach(loadOrder::add);

        LoadedDocument primary = null;
        for (Path path : loadOrder) {
            try {
                OpenDocument document = new OpenDocument(path, NoteFormat.fromPath(path), NoteIO.read(path));
                primary = new LoadedDocument(
                        document,
                        renderer.render(document, request.collapsedHeadings().getOrDefault(path, Set.of())));
                break;
            } catch (IOException | RuntimeException ignored) {
                // A stale recent file should not delay or block application startup.
            }
        }
        Path primaryPath = primary == null ? null : primary.document().path();
        List<Path> remaining = validFiles.stream()
                .filter(path -> !path.equals(primaryPath))
                .toList();
        return new PrimaryRestore(validRoots, primary, remaining);
    }

    private void applyPrimaryRestore(PrimaryRestore restored, Throwable error) {
        if (!primaryStage.isShowing()) {
            restoringState = false;
            return;
        }
        if (error != null || restored == null) {
            restoringState = false;
            renderTabs();
            renderFileTree();
            webEngine.loadContent(renderer.renderEmpty());
            return;
        }

        appState.recentRoots().clear();
        appState.recentRoots().addAll(restored.recentRoots());
        expandedDirectories.addAll(restored.recentRoots());
        if (restored.primary() != null) {
            OpenDocument document = restored.primary().document();
            openDocuments.putIfAbsent(document.path(), document);
            if (activeDocument == null) {
                selectDocument(document, restored.primary().renderedHtml());
            } else {
                renderTabs();
                renderFileTree();
            }
        } else {
            renderTabs();
            renderFileTree();
            updateStatus();
            webEngine.loadContent(renderer.renderEmpty());
        }
        restoreRemainingDocuments(restored.remainingFiles());
    }

    private void restoreRemainingDocuments(List<Path> paths) {
        if (paths.isEmpty()) {
            restoringState = false;
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            List<OpenDocument> restored = new ArrayList<>();
            for (Path path : paths) {
                try {
                    restored.add(new OpenDocument(path, NoteFormat.fromPath(path), NoteIO.read(path)));
                } catch (IOException | RuntimeException ignored) {
                    // Ignore stale or temporarily unavailable files during session recovery.
                }
            }
            return restored;
        }).whenComplete((documents, error) -> Platform.runLater(() -> {
            restoringState = false;
            if (!primaryStage.isShowing() || error != null || documents == null) {
                return;
            }
            for (OpenDocument document : documents) {
                openDocuments.putIfAbsent(document.path(), document);
            }
            if (activeDocument == null && !openDocuments.isEmpty()) {
                selectDocument(openDocuments.values().iterator().next());
            } else {
                renderTabs();
                updateStatus();
            }
        }));
    }

    private void openRootDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择顶级目录");
        Path initial = selectedDirectory != null ? selectedDirectory : Path.of(System.getProperty("user.home"));
        if (Files.isDirectory(initial)) {
            chooser.setInitialDirectory(initial.toFile());
        }
        java.io.File selected = chooser.showDialog(root.getScene().getWindow());
        if (selected == null) {
            return;
        }
        if (isImageDirectory(selected.toPath())) {
            showError("无法添加目录", "image_ 开头的图片资源目录不会显示在文件树中。");
            return;
        }
        addRecentRoot(selected.toPath());
        selectedDirectory = selected.toPath().toAbsolutePath().normalize();
        selectedTreePath = selectedDirectory;
        selectedTreeRoot = selectedDirectory;
        renderFileTree();
        persistState();
    }

    private void removeSelectedRoot() {
        Path rootToRemove = selectedTreeRoot;
        if (rootToRemove == null && selectedTreePath != null) {
            rootToRemove = rootForPath(selectedTreePath);
        }
        if (rootToRemove == null) {
            showError("无法移除目录", "请先在右侧文件树中选择一个顶级目录或其子项。");
            return;
        }

        Path normalized = rootToRemove.toAbsolutePath().normalize();
        appState.recentRoots().remove(normalized);
        expandedDirectories.removeIf(path -> path.toAbsolutePath().normalize().startsWith(normalized));
        if (normalized.equals(selectedTreeRoot)) {
            selectedTreeRoot = null;
        }
        if (selectedTreePath != null && selectedTreePath.toAbsolutePath().normalize().startsWith(normalized)) {
            selectedTreePath = null;
        }
        if (selectedDirectory != null && selectedDirectory.toAbsolutePath().normalize().startsWith(normalized)) {
            selectedDirectory = null;
        }
        renderFileTree();
        persistState();
    }

    private void renameSelectedTreePath() {
        if (selectedTreePath == null) {
            showError("无法重命名", "请先在右侧文件树中选择文件或文件夹。");
            return;
        }
        Path source = selectedTreePath.toAbsolutePath().normalize();
        if (!Files.exists(source) || source.getFileName() == null || source.getParent() == null) {
            showError("无法重命名", "所选文件或文件夹已不存在。");
            renderFileTree();
            return;
        }

        String currentName = source.getFileName().toString();
        Optional<String> result = promptForName(
                "重命名",
                source.getParent().toString(),
                Files.isDirectory(source) ? "文件夹名" : "文件名",
                currentName,
                "重命名");
        if (result.isEmpty()) {
            return;
        }
        String newName = result.get().trim();
        if (newName.equals(currentName)) {
            return;
        }
        if (!NotePathPolicy.isValidChildName(newName)) {
            showError("无法重命名", "名称包含路径分隔符、Windows 不支持的字符或保留名称。");
            return;
        }

        Path target = NotePathPolicy.resolveChild(source.getParent(), newName);
        if (Files.isRegularFile(source) && NoteFormat.fromPath(source) != NoteFormat.fromPath(target)) {
            showError("无法重命名", "重命名时请保留原文件扩展名。");
            return;
        }
        if (Files.exists(target)) {
            showError("无法重命名", "同名文件或文件夹已存在：" + target);
            return;
        }

        boolean markdownFile = Files.isRegularFile(source) && NoteFormat.fromPath(source) == NoteFormat.MARKDOWN;
        Path sourceImageDirectory = markdownFile ? NoteIO.imageDirectory(source) : null;
        Path targetImageDirectory = markdownFile ? NoteIO.imageDirectory(target) : null;
        boolean moveImageDirectory = sourceImageDirectory != null
                && targetImageDirectory != null
                && !sourceImageDirectory.equals(targetImageDirectory)
                && Files.isDirectory(sourceImageDirectory);
        if (moveImageDirectory && Files.exists(targetImageDirectory)) {
            showError("无法重命名", "关联图片目录已存在：" + targetImageDirectory);
            return;
        }

        syncActiveDocument();
        OpenDocument renamedDocument = openDocuments.get(source);
        boolean renamedDocumentWasDirty = renamedDocument != null && renamedDocument.dirty();
        boolean sourceMoved = false;
        boolean imageDirectoryMoved = false;
        try {
            Files.move(source, target);
            sourceMoved = true;
            if (moveImageDirectory) {
                Files.move(sourceImageDirectory, targetImageDirectory);
                imageDirectoryMoved = true;
            }
        } catch (IOException ex) {
            String rollbackMessage = rollbackRename(
                    source, target, sourceMoved,
                    sourceImageDirectory, targetImageDirectory, imageDirectoryMoved);
            showError("重命名失败", ex.getMessage() + rollbackMessage);
            return;
        }

        remapApplicationPaths(source, target);
        if (moveImageDirectory) {
            updateRenamedMarkdownReferences(
                    target,
                    sourceImageDirectory.getFileName().toString(),
                    targetImageDirectory.getFileName().toString(),
                    renamedDocument,
                    renamedDocumentWasDirty);
        }
        reloadActiveEditor();
        renderTabs();
        renderFileTree();
        updateStatus();
        persistState();
    }

    private String rollbackRename(
            Path source,
            Path target,
            boolean sourceMoved,
            Path sourceImageDirectory,
            Path targetImageDirectory,
            boolean imageDirectoryMoved) {
        List<String> failures = new ArrayList<>();
        if (imageDirectoryMoved) {
            try {
                Files.move(targetImageDirectory, sourceImageDirectory);
            } catch (IOException rollbackError) {
                failures.add(rollbackError.getMessage());
            }
        }
        if (sourceMoved) {
            try {
                Files.move(target, source);
            } catch (IOException rollbackError) {
                failures.add(rollbackError.getMessage());
            }
        }
        return failures.isEmpty() ? "" : "\n回滚未完全成功：" + String.join("；", failures);
    }

    private void remapApplicationPaths(Path source, Path target) {
        Map<Path, OpenDocument> remappedDocuments = new LinkedHashMap<>();
        for (OpenDocument document : openDocuments.values()) {
            Path movedPath = remapPath(document.path(), source, target);
            if (!movedPath.equals(document.path())) {
                document.moveTo(movedPath);
            }
            remappedDocuments.put(document.path(), document);
        }
        openDocuments.clear();
        openDocuments.putAll(remappedDocuments);

        for (int i = 0; i < appState.recentRoots().size(); i++) {
            appState.recentRoots().set(i, remapPath(appState.recentRoots().get(i), source, target));
        }
        Set<Path> remappedDirectories = new LinkedHashSet<>();
        for (Path directory : expandedDirectories) {
            remappedDirectories.add(remapPath(directory, source, target));
        }
        expandedDirectories.clear();
        expandedDirectories.addAll(remappedDirectories);

        selectedTreePath = remapPath(selectedTreePath, source, target);
        selectedTreeRoot = remapPath(selectedTreeRoot, source, target);
        selectedDirectory = remapPath(selectedDirectory, source, target);
        appState.setActiveFile(remapPath(appState.activeFile(), source, target));

        Map<Path, Set<String>> remappedHeadingState = new LinkedHashMap<>();
        appState.collapsedHeadings().forEach((path, headings) -> {
            Path movedPath = remapPath(path, source, target);
            remappedHeadingState.computeIfAbsent(movedPath, ignored -> new LinkedHashSet<>()).addAll(headings);
        });
        appState.collapsedHeadings().clear();
        appState.collapsedHeadings().putAll(remappedHeadingState);
    }

    private Path remapPath(Path path, Path source, Path target) {
        if (path == null) {
            return null;
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(source)) {
            return normalized;
        }
        return target.resolve(source.relativize(normalized)).toAbsolutePath().normalize();
    }

    private void updateRenamedMarkdownReferences(
            Path target,
            String oldDirectoryName,
            String newDirectoryName,
            OpenDocument document,
            boolean wasDirty) {
        try {
            if (document != null) {
                String updated = replaceImageDirectoryReferences(
                        document.content(), oldDirectoryName, newDirectoryName);
                if (!updated.equals(document.content())) {
                    document.setContent(updated);
                    if (!wasDirty) {
                        NoteIO.write(target, document.format(), updated);
                        document.setDirty(false);
                    }
                }
                return;
            }

            String content = NoteIO.read(target);
            String updated = replaceImageDirectoryReferences(content, oldDirectoryName, newDirectoryName);
            if (!updated.equals(content)) {
                NoteIO.write(target, NoteFormat.MARKDOWN, updated);
            }
        } catch (IOException ex) {
            if (document != null) {
                document.setDirty(true);
            }
            showError("图片引用更新失败", "文件已完成重命名，但图片引用需要保存后才能更新：\n" + ex.getMessage());
        }
    }

    private String replaceImageDirectoryReferences(String content, String oldName, String newName) {
        return content
                .replace("](" + oldName + "/", "](" + newName + "/")
                .replace("](" + oldName + "\\", "](" + newName + "/");
    }

    private void reloadActiveEditor() {
        if (activeDocument == null) {
            return;
        }
        loadingEditor = true;
        webEngine.loadContent(renderDocument(activeDocument));
        loadingEditor = false;
    }

    private void deleteSelectedTreePath() {
        if (selectedTreePath == null) {
            showError("无法删除", "请先在右侧文件树中选择要删除的文件或文件夹。");
            return;
        }
        Path target = selectedTreePath.toAbsolutePath().normalize();
        if (!Files.exists(target)) {
            renderFileTree();
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("删除确认");
        alert.setHeaderText("确认删除：" + target.getFileName());
        alert.setContentText(Files.isDirectory(target)
                ? "将递归删除该文件夹及其中所有内容。此操作不可撤销。"
                : "将删除该文件。此操作不可撤销。");
        ButtonType delete = new ButtonType("删除");
        ButtonType cancel = ButtonType.CANCEL;
        alert.getButtonTypes().setAll(delete, cancel);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() != delete) {
            return;
        }

        try {
            if (!closeDocumentsUnder(target)) {
                return;
            }
            deleteRecursively(target);
            appState.recentRoots().removeIf(path -> path.toAbsolutePath().normalize().equals(target));
            appState.collapsedHeadings().keySet().removeIf(path ->
                    path.toAbsolutePath().normalize().startsWith(target));
            expandedDirectories.removeIf(path -> path.toAbsolutePath().normalize().startsWith(target));
            if (selectedTreePath != null && selectedTreePath.toAbsolutePath().normalize().startsWith(target)) {
                selectedTreePath = null;
            }
            if (selectedTreeRoot != null && selectedTreeRoot.toAbsolutePath().normalize().startsWith(target)) {
                selectedTreeRoot = null;
            }
            if (selectedDirectory != null && selectedDirectory.toAbsolutePath().normalize().startsWith(target)) {
                selectedDirectory = null;
            }
            renderTabs();
            renderFileTree();
            persistState();
        } catch (IOException ex) {
            showError("删除失败", ex.getMessage());
        }
    }

    private Path rootForPath(Path path) {
        if (path == null) {
            return null;
        }
        Path normalized = path.toAbsolutePath().normalize();
        for (Path rootPath : appState.recentRoots()) {
            Path root = rootPath.toAbsolutePath().normalize();
            if (normalized.startsWith(root)) {
                return root;
            }
        }
        return null;
    }

    private boolean closeDocumentsUnder(Path target) {
        Path normalized = target.toAbsolutePath().normalize();
        List<OpenDocument> affected = openDocuments.values()
                .stream()
                .filter(document -> document.path().toAbsolutePath().normalize().startsWith(normalized))
                .toList();
        for (OpenDocument document : affected) {
            if (!confirmSaved(document)) {
                return false;
            }
            openDocuments.remove(document.path());
            if (document == activeDocument) {
                activeDocument = null;
            }
        }
        if (activeDocument == null && !openDocuments.isEmpty()) {
            selectDocument(openDocuments.values().iterator().next());
        } else if (activeDocument == null) {
            appState.setActiveFile(null);
            webEngine.loadContent(renderer.renderEmpty());
        }
        return true;
    }

    private void deleteRecursively(Path target) throws IOException {
        if (Files.isDirectory(target)) {
            try (Stream<Path> walk = Files.walk(target)) {
                List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
                for (Path path : paths) {
                    Files.deleteIfExists(path);
                }
            }
        } else {
            Files.deleteIfExists(target);
        }
    }

    private void addRecentRoot(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        appState.recentRoots().remove(normalized);
        appState.recentRoots().add(0, normalized);
        while (appState.recentRoots().size() > RECENT_ROOT_LIMIT) {
            appState.recentRoots().remove(appState.recentRoots().size() - 1);
        }
        expandedDirectories.add(normalized);
    }

    private void renderFileTree() {
        double scrollOffset = treeScrollOffset();
        long renderGeneration = ++treeRenderGeneration;
        treeBody.getChildren().clear();
        int count = 0;
        for (Path rootPath : appState.recentRoots()) {
            if (!Files.isDirectory(rootPath) || isImageDirectory(rootPath)) {
                continue;
            }
            Path normalizedRoot = rootPath.toAbsolutePath().normalize();
            count++;
            VBox rootBlock = new VBox();
            rootBlock.getStyleClass().add("root-block");
            Label icon = new Label(expandedDirectories.contains(normalizedRoot) ? "▾" : "▸");
            Label rootLabel = compactLabel(rootPath.toString(), "root-path");
            rootLabel.setMinWidth(0);
            HBox.setHgrow(rootLabel, Priority.ALWAYS);
            StackPane dragHandle = rootDragHandle();
            HBox head = new HBox(7, icon, rootLabel, dragHandle);
            head.getStyleClass().add("root-head");
            if (normalizedRoot.equals(selectedTreePath)) {
                head.getStyleClass().add("selected");
            }
            installDirectoryInteraction(head, normalizedRoot, normalizedRoot);
            installRootReordering(head, normalizedRoot);
            VBox list = new VBox();
            list.getStyleClass().add("tree-list");
            if (expandedDirectories.contains(normalizedRoot)) {
                renderDirectoryChildren(normalizedRoot, normalizedRoot, list, 0);
            }
            rootBlock.getChildren().addAll(head, list);
            treeBody.getChildren().add(rootBlock);
        }
        treeStatus.setText(count + " 个顶级目录 · 最近目录上限 10");
        Platform.runLater(() -> restoreTreeScrollOffset(renderGeneration, scrollOffset));
    }

    private StackPane rootDragHandle() {
        SVGPath dots = new SVGPath();
        dots.setContent(ICON_DRAG_HANDLE);
        dots.getStyleClass().add("root-drag-icon");
        StackPane handle = new StackPane(dots);
        handle.getStyleClass().add("root-drag-handle");
        handle.setMinSize(22, 24);
        handle.setPrefSize(22, 24);
        handle.setMaxSize(22, 24);
        Tooltip.install(handle, new Tooltip("拖动以调整顶级目录顺序"));
        return handle;
    }

    private void installDirectoryInteraction(HBox node, Path rootPath, Path directory) {
        long[] pressedAt = {0L};
        node.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            pressedAt[0] = System.nanoTime();
            selectedTreePath = directory;
            selectedTreeRoot = rootPath;
            selectedDirectory = directory;
        });
        node.setOnMouseReleased(event -> {
            if (event.getButton() != MouseButton.PRIMARY || pressedAt[0] == 0L) {
                return;
            }
            long pressDuration = System.nanoTime() - pressedAt[0];
            pressedAt[0] = 0L;
            if (shouldToggleDirectory(pressDuration, event.isStillSincePress())) {
                toggleExpanded(directory);
            }
            renderFileTree();
            event.consume();
        });
    }

    static boolean shouldToggleDirectory(long pressDurationNanos, boolean stillSincePress) {
        return stillSincePress && pressDurationNanos < DIRECTORY_LONG_PRESS_NANOS;
    }

    private void installRootReordering(HBox head, Path rootPath) {
        head.setOnDragDetected(event -> {
            if (event.getButton() != MouseButton.PRIMARY || appState.recentRoots().size() < 2) {
                return;
            }
            draggedTreeRoot = rootPath;
            ClipboardContent content = new ClipboardContent();
            content.putString(rootPath.toString());
            head.startDragAndDrop(TransferMode.MOVE).setContent(content);
            event.consume();
        });
        head.setOnDragOver(event -> {
            if (!isRootReorderTarget(rootPath)) {
                return;
            }
            event.acceptTransferModes(TransferMode.MOVE);
            updateRootDropIndicator(head, event.getY());
            event.consume();
        });
        head.setOnDragEntered(event -> {
            if (isRootReorderTarget(rootPath)) {
                updateRootDropIndicator(head, event.getY());
            }
        });
        head.setOnDragExited(event -> clearRootDropIndicator(head));
        head.setOnDragDropped(event -> {
            if (!isRootReorderTarget(rootPath)) {
                event.setDropCompleted(false);
                return;
            }
            boolean placeAfterTarget = event.getY() >= head.getHeight() / 2;
            boolean moved = appState.moveRecentRoot(draggedTreeRoot, rootPath, placeAfterTarget);
            draggedTreeRoot = null;
            clearRootDropIndicator(head);
            event.setDropCompleted(true);
            if (moved) {
                persistState();
            }
            renderFileTree();
            event.consume();
        });
        head.setOnDragDone(event -> {
            if (draggedTreeRoot == null) {
                return;
            }
            draggedTreeRoot = null;
            renderFileTree();
        });
    }

    private boolean isRootReorderTarget(Path targetRoot) {
        return draggedTreeRoot != null && !draggedTreeRoot.equals(targetRoot);
    }

    private void updateRootDropIndicator(HBox head, double y) {
        clearRootDropIndicator(head);
        head.getStyleClass().add(y < head.getHeight() / 2 ? "drag-before" : "drag-after");
    }

    private void clearRootDropIndicator(HBox head) {
        head.getStyleClass().removeAll("drag-before", "drag-after");
    }

    private double treeScrollOffset() {
        if (treeScroll == null) {
            return 0;
        }
        double overflow = treeBody.getBoundsInLocal().getHeight() - treeScroll.getViewportBounds().getHeight();
        return Math.max(0, overflow) * treeScroll.getVvalue();
    }

    private void restoreTreeScrollOffset(long renderGeneration, double scrollOffset) {
        if (treeScroll == null || treeRenderGeneration != renderGeneration) {
            return;
        }
        double overflow = treeBody.getBoundsInLocal().getHeight() - treeScroll.getViewportBounds().getHeight();
        treeScroll.setVvalue(overflow <= 0 ? 0 : clamp(scrollOffset / overflow, 0, 1));
    }

    private void renderDirectoryChildren(Path rootPath, Path directory, VBox target, int depth) {
        if (depth > 5) {
            return;
        }
        List<Path> children;
        try (Stream<Path> stream = Files.list(directory)) {
            children = stream
                    .filter(path -> !isHidden(path))
                    .filter(path -> !isImageDirectory(path))
                    .filter(path -> Files.isDirectory(path) || NoteFormat.fromPath(path).isSupportedFile())
                    .sorted(Comparator
                            .comparing((Path path) -> !Files.isDirectory(path))
                            .thenComparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException ignored) {
            return;
        }

        for (Path child : children) {
            HBox row = fileRow(rootPath, child.toAbsolutePath().normalize(), depth);
            target.getChildren().add(row);
            Path normalizedChild = child.toAbsolutePath().normalize();
            if (Files.isDirectory(normalizedChild) && expandedDirectories.contains(normalizedChild)) {
                renderDirectoryChildren(rootPath, normalizedChild, target, depth + 1);
            }
        }
    }

    private HBox fileRow(Path rootPath, Path path, int depth) {
        boolean directory = Files.isDirectory(path);
        NoteFormat format = NoteFormat.fromPath(path);
        Label branch = new Label("");
        branch.getStyleClass().add("tree-branch");
        branch.setMinWidth(0);
        branch.setPrefWidth(0);
        branch.setMaxWidth(0);
        String iconText = directory ? (expandedDirectories.contains(path) ? "▾" : "▸") : "•";
        Label icon = new Label(iconText);
        icon.getStyleClass().add("tree-icon");
        Label name = compactLabel(path.getFileName().toString(), null);
        Label meta = new Label(directory ? "DIR" : format.label());
        meta.getStyleClass().add("file-meta");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(6, branch, icon, name, spacer, meta);
        row.getStyleClass().addAll("file-row", rowClass(path, directory, format));
        VBox.setMargin(row, new Insets(0, 0, 0, Math.max(0, depth) * TREE_INDENT_WIDTH));
        row.setPadding(new Insets(4, 7, 4, 7));
        if (activeDocument != null && path.equals(activeDocument.path())) {
            row.getStyleClass().add("active");
        }
        if (path.equals(selectedTreePath)) {
            row.getStyleClass().add("selected");
        }
        if (directory) {
            installDirectoryInteraction(row, rootPath, path);
        } else {
            row.setOnMouseClicked(event -> {
                selectedTreePath = path;
                selectedTreeRoot = rootPath;
                openFile(path, true);
            });
        }
        return row;
    }

    private String rowClass(Path path, boolean directory, NoteFormat format) {
        if (directory) {
            return "folder";
        }
        return switch (format) {
            case MARKDOWN -> "md";
            case TEXT -> "txt";
            case WORD -> "doc";
            case UNKNOWN -> "txt";
        };
    }

    private Label compactLabel(String text, String styleClass) {
        Label label = new Label(text);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setTooltip(new Tooltip(text));
        if (styleClass != null) {
            label.getStyleClass().add(styleClass);
        }
        return label;
    }

    private boolean isHidden(Path path) {
        try {
            return Files.isHidden(path);
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean isImageDirectory(Path path) {
        return Files.isDirectory(path)
                && path.getFileName() != null
                && path.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("image_");
    }

    private void toggleExpanded(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (expandedDirectories.contains(normalized)) {
            expandedDirectories.remove(normalized);
        } else {
            expandedDirectories.add(normalized);
        }
    }

    private void openFile(Path path, boolean select) {
        syncActiveDocument();
        Path normalized = path.toAbsolutePath().normalize();
        if (openDocuments.containsKey(normalized)) {
            if (select) {
                selectDocument(openDocuments.get(normalized));
            }
            return;
        }
        try {
            NoteFormat format = NoteFormat.fromPath(normalized);
            OpenDocument document = new OpenDocument(normalized, format, NoteIO.read(normalized));
            openDocuments.put(normalized, document);
            if (select) {
                selectDocument(document);
            } else {
                renderTabs();
            }
        } catch (IOException ex) {
            showError("无法打开文件", ex.getMessage());
        }
    }

    private void selectDocument(OpenDocument document) {
        selectDocument(document, null);
    }

    private void selectDocument(OpenDocument document, String preparedHtml) {
        if (document == null || openDocuments.get(document.path()) != document) {
            return;
        }
        if (document == activeDocument) {
            return;
        }
        syncActiveDocument();
        activeDocument = document;
        appState.setActiveFile(document.path());
        revealFileInExistingRoot(document.path());
        loadingEditor = true;
        webEngine.loadContent(preparedHtml == null ? renderDocument(document) : preparedHtml);
        loadingEditor = false;
        renderTabs();
        renderFileTree();
        updateStatus();
    }

    private void revealFileInExistingRoot(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        Path rootPath = null;
        if (selectedTreeRoot != null) {
            Path normalizedSelectedRoot = selectedTreeRoot.toAbsolutePath().normalize();
            if (normalized.startsWith(normalizedSelectedRoot) && appState.recentRoots().contains(normalizedSelectedRoot)) {
                rootPath = normalizedSelectedRoot;
            }
        }
        if (rootPath == null) {
            rootPath = rootForPath(normalized);
        }
        if (rootPath == null) {
            return;
        }

        selectedTreeRoot = rootPath;
        selectedTreePath = normalized;
        Path parent = normalized.getParent();
        while (parent != null && parent.startsWith(rootPath)) {
            expandedDirectories.add(parent.toAbsolutePath().normalize());
            if (parent.equals(rootPath)) {
                break;
            }
            parent = parent.getParent();
        }
    }

    private void renderTabs() {
        tabsBox.getChildren().clear();
        for (OpenDocument document : openDocuments.values()) {
            tabsBox.getChildren().add(tabNode(document));
        }
    }

    private HBox tabNode(OpenDocument document) {
        Label name = new Label((document.dirty() ? "• " : "") + document.displayName());
        name.getStyleClass().add("tab-name");
        name.setMaxWidth(Double.MAX_VALUE);
        Button close = new Button("×");
        close.getStyleClass().add("tab-close");
        close.setOnAction(event -> closeDocument(document));
        close.setOnMouseClicked(event -> event.consume());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox tab = new HBox(8, name, spacer, close);
        tab.getStyleClass().add("tab-pill");
        if (document == activeDocument) {
            tab.getStyleClass().add("current");
        }
        Tooltip.install(tab, new Tooltip(document.path().toString()));
        tab.setOnMouseClicked(event -> {
            if (event.getClickCount() == 1 && openDocuments.get(document.path()) == document) {
                selectDocument(document);
            }
        });
        return tab;
    }

    private void closeDocument(OpenDocument document) {
        if (!confirmSaved(document)) {
            return;
        }
        boolean closingActiveDocument = document == activeDocument;
        openDocuments.remove(document.path());
        if (closingActiveDocument) {
            activeDocument = null;
            OpenDocument replacement = openDocuments.values().stream().findFirst().orElse(null);
            if (replacement != null) {
                selectDocument(replacement);
            } else {
                appState.setActiveFile(null);
                webEngine.loadContent(renderer.renderEmpty());
            }
        }
        renderTabs();
        renderFileTree();
        updateStatus();
    }

    private void newFile() {
        Path directory = chooseWorkingDirectory();
        if (directory == null) {
            return;
        }
        Optional<String> result = promptForName("新建文件", directory.toString(), "文件名", "untitled.md");
        if (result.isEmpty() || result.get().isBlank()) {
            return;
        }
        String fileName = result.get().trim();
        if (!fileName.contains(".")) {
            fileName += ".md";
        }
        if (!NotePathPolicy.isValidChildName(fileName)) {
            showError("无法新建文件", "文件名包含路径分隔符、Windows 不支持的字符或保留名称。");
            return;
        }
        Path target = NotePathPolicy.resolveChild(directory, fileName);
        if (!NotePathPolicy.isCreatableNote(target)) {
            showError("无法新建文件", "支持新建 .md、.markdown、.txt 或 .docx 文件。");
            return;
        }
        if (Files.exists(target)) {
            showError("无法新建文件", "文件已存在：" + target);
            return;
        }
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, "");
            openFile(target, true);
        } catch (IOException ex) {
            showError("无法新建文件", ex.getMessage());
        }
    }

    private void newFolder() {
        Path directory = chooseWorkingDirectory();
        if (directory == null) {
            return;
        }
        Optional<String> result = promptForName("新建文件夹", directory.toString(), "文件夹名", "new-folder");
        if (result.isEmpty() || result.get().isBlank()) {
            return;
        }
        String folderName = result.get().trim();
        if (!NotePathPolicy.isValidChildName(folderName)) {
            showError("无法新建文件夹", "文件夹名包含路径分隔符、Windows 不支持的字符或保留名称。");
            return;
        }
        Path target = NotePathPolicy.resolveChild(directory, folderName);
        if (Files.exists(target)) {
            showError("无法新建文件夹", "文件或文件夹已存在：" + target);
            return;
        }
        try {
            Files.createDirectory(target);
            expandedDirectories.add(directory.toAbsolutePath().normalize());
            selectedDirectory = target;
            renderFileTree();
        } catch (IOException ex) {
            showError("无法新建文件夹", ex.getMessage());
        }
    }

    private Optional<String> promptForName(String title, String subtitle, String fieldLabel, String defaultValue) {
        return promptForName(title, subtitle, fieldLabel, defaultValue, "创建");
    }

    private Optional<String> promptForName(
            String title,
            String location,
            String fieldLabel,
            String defaultValue,
            String confirmText) {
        boolean renaming = title.contains("重命名");
        boolean folder = fieldLabel.contains("文件夹");
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        configureDialog(dialog, "name-dialog", 450);

        ButtonType create = new ButtonType(confirmText, javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(create, ButtonType.CANCEL);

        Label inputLabel = new Label(fieldLabel);
        inputLabel.getStyleClass().add("dialog-label");
        TextField field = new TextField(defaultValue);
        field.getStyleClass().add("dialog-input");
        field.setPromptText(folder ? "例如：项目资料" : "例如：会议记录.md");
        Label fieldHint = new Label(renaming
                ? "内容和位置不会改变"
                : folder
                ? "建议使用简短名称"
                : "未填写扩展名时默认使用 .md");
        fieldHint.getStyleClass().add("dialog-field-hint");
        fieldHint.setWrapText(true);

        VBox fieldGroup = new VBox(7, inputLabel, field, fieldHint);
        fieldGroup.getStyleClass().add("dialog-field-group");

        String iconPath = renaming ? ICON_RENAME : folder ? ICON_DIALOG_FOLDER : ICON_DIALOG_FILE;
        HBox header = dialogHeader(dialog, iconPath, title, false);
        VBox locationCard = dialogLocationCard("位置", location);
        VBox content = new VBox(16, header, locationCard, fieldGroup);
        setDialogBody(dialog, content);
        dialog.setResultConverter(button -> button == create ? field.getText().trim() : null);

        Button createButton = (Button) dialog.getDialogPane().lookupButton(create);
        createButton.getStyleClass().add("primary-dialog-button");
        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        field.textProperty().addListener((observable, oldValue, newValue) ->
                createButton.setDisable(newValue == null || newValue.trim().isEmpty()));
        createButton.setDisable(field.getText().trim().isEmpty());
        Platform.runLater(() -> {
            field.requestFocus();
            field.selectAll();
        });
        return dialog.showAndWait().filter(value -> !value.isBlank());
    }

    private void configureDialog(Dialog<?> dialog, String styleClass, double preferredWidth) {
        dialog.initOwner(root.getScene().getWindow());
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.getDialogPane().setHeaderText(null);
        dialog.getDialogPane().setGraphic(null);
        dialog.getDialogPane().setPrefWidth(preferredWidth);
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/com/jnote/styles/app.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().addAll("jnote-dialog", styleClass);
        dialog.setOnShown(event -> {
            dialog.getDialogPane().getScene().setFill(Color.TRANSPARENT);
            dialog.getDialogPane().requestLayout();
        });
    }

    private void setDialogBody(Dialog<?> dialog, VBox body) {
        body.getStyleClass().add("dialog-content");
        StackPane shell = new StackPane(body);
        shell.setAlignment(Pos.TOP_LEFT);
        shell.getStyleClass().add("dialog-content-shell");
        dialog.getDialogPane().setContent(shell);
    }

    private HBox dialogHeader(
            Dialog<?> dialog,
            String iconPath,
            String title,
            boolean warning) {
        SVGPath icon = new SVGPath();
        icon.setContent(iconPath);
        icon.getStyleClass().add("dialog-icon-shape");
        StackPane badge = new StackPane(icon);
        badge.getStyleClass().add("dialog-icon");
        if (warning) {
            badge.getStyleClass().add("warning");
        }
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dialog-title");
        VBox copy = new VBox(titleLabel);
        copy.getStyleClass().add("dialog-copy");
        HBox.setHgrow(copy, Priority.ALWAYS);
        Button close = new Button("×");
        close.getStyleClass().add("dialog-close-button");
        close.setCancelButton(true);
        close.setOnAction(event -> dialog.close());
        HBox header = new HBox(10, badge, copy, close);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("dialog-header");
        installDialogDrag(header);
        return header;
    }

    private VBox dialogLocationCard(String caption, String pathText) {
        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("dialog-location-label");
        Label pathLabel = new Label(pathText);
        pathLabel.getStyleClass().add("dialog-location-value");
        pathLabel.setWrapText(true);
        VBox copy = new VBox(4, captionLabel, pathLabel);
        VBox card = new VBox(copy);
        card.getStyleClass().add("dialog-location-card");
        return card;
    }

    private void installDialogDrag(HBox header) {
        double[] offset = new double[2];
        header.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY || header.getScene() == null) {
                return;
            }
            offset[0] = event.getScreenX() - header.getScene().getWindow().getX();
            offset[1] = event.getScreenY() - header.getScene().getWindow().getY();
        });
        header.setOnMouseDragged(event -> {
            if (event.getButton() != MouseButton.PRIMARY || header.getScene() == null) {
                return;
            }
            header.getScene().getWindow().setX(event.getScreenX() - offset[0]);
            header.getScene().getWindow().setY(event.getScreenY() - offset[1]);
        });
    }

    private Path chooseWorkingDirectory() {
        if (selectedDirectory != null && Files.isDirectory(selectedDirectory)) {
            return selectedDirectory;
        }
        if (activeDocument != null && activeDocument.path().getParent() != null) {
            return activeDocument.path().getParent();
        }
        for (Path rootPath : appState.recentRoots()) {
            if (Files.isDirectory(rootPath)) {
                return rootPath;
            }
        }
        openRootDirectory();
        return selectedDirectory;
    }

    private boolean saveActiveDocument() {
        if (activeDocument == null) {
            return true;
        }
        syncActiveDocument();
        try {
            NoteIO.write(activeDocument.path(), activeDocument.format(), activeDocument.content());
            activeDocument.setDirty(false);
            resetEditorUserChanges();
            renderTabs();
            updateStatus();
            renderFileTree();
            persistState();
            return true;
        } catch (IOException ex) {
            showError("保存失败", ex.getMessage());
            return false;
        }
    }

    private void syncActiveDocument() {
        if (activeDocument == null || loadingEditor) {
            return;
        }
        try {
            if (!activeDocument.dirty() && !editorHasUserChanges()) {
                return;
            }
            Object serialized = webEngine.executeScript("window.jnoteSerialize ? window.jnoteSerialize() : null");
            if (serialized != null) {
                boolean wasDirty = activeDocument.dirty();
                String previousContent = activeDocument.content();
                String content = serialized.toString();
                activeDocument.setContent(content);
                if (!content.equals(previousContent) || wasDirty != activeDocument.dirty()) {
                    renderTabs();
                    updateStatus();
                }
            }
        } catch (RuntimeException ignored) {
            // Ignore sync attempts while WebView is between loads.
        }
    }

    private boolean editorHasUserChanges() {
        try {
            Object changed = webEngine.executeScript("window.jnoteHasUserChanges ? window.jnoteHasUserChanges() : false");
            return Boolean.TRUE.equals(changed) || "true".equalsIgnoreCase(String.valueOf(changed));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void insertImage() {
        if (activeDocument == null || activeDocument.format() != NoteFormat.MARKDOWN) {
            showError("无法插入图片", "请先打开 Markdown 文件。");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择图片");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "图片文件", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp", "*.bmp"));
        java.io.File selected = chooser.showOpenDialog(root.getScene().getWindow());
        if (selected == null) {
            return;
        }
        try {
            insertImagePath(NoteIO.copyImageForDocument(activeDocument.path(), selected.toPath()));
        } catch (IOException ex) {
            showError("插入图片失败", ex.getMessage());
        }
    }

    private boolean pasteImageFromClipboard() {
        if (activeDocument == null || activeDocument.format() != NoteFormat.MARKDOWN) {
            return false;
        }
        Clipboard clipboard = Clipboard.getSystemClipboard();
        try {
            if (clipboard.hasFiles()) {
                for (java.io.File file : clipboard.getFiles()) {
                    Path path = file.toPath();
                    if (Files.isRegularFile(path) && NoteIO.isImage(path)) {
                        insertImagePath(NoteIO.copyImageForDocument(activeDocument.path(), path));
                        return true;
                    }
                }
            }

            BufferedImage clipboardImage = preferredClipboardImage(
                    this::readAwtClipboardImage,
                    () -> {
                        Image fxImage = clipboard.getImage();
                        return fxImage == null ? null : SwingFXUtils.fromFXImage(fxImage, null);
                    });
            if (clipboardImage != null) {
                savePastedBufferedImage(clipboardImage);
                return true;
            }
        } catch (IOException ex) {
            showError("粘贴图片失败", ex.getMessage());
            return true;
        }
        return false;
    }

    static BufferedImage preferredClipboardImage(
            Supplier<BufferedImage> nativeImageReader,
            Supplier<BufferedImage> javafxImageReader) {
        // JavaFX 17 can decode Windows Format32bppRgb clipboard screenshots as
        // transparent black. AWT treats that native format as opaque RGB, so the
        // JavaFX decoder is intentionally a lazy fallback and must not run first.
        BufferedImage nativeImage = nativeImageReader.get();
        return nativeImage != null ? nativeImage : javafxImageReader.get();
    }

    private void savePastedBufferedImage(BufferedImage image) throws IOException {
        Path imageDirectory = NoteIO.imageDirectory(activeDocument.path());
        Files.createDirectories(imageDirectory);
        Path target = uniquePastedImagePath(imageDirectory, ".png");
        BufferedImage visibleImage = NoteIO.repairFullyTransparentImage(image);
        if (!ImageIO.write(visibleImage, "png", target.toFile())) {
            throw new IOException("当前环境无法编码 PNG 图片。");
        }
        insertImagePath(target);
    }

    private BufferedImage readAwtClipboardImage() {
        try {
            Transferable contents = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (contents == null || !contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                return null;
            }
            Object value = contents.getTransferData(DataFlavor.imageFlavor);
            if (!(value instanceof java.awt.Image image)) {
                return null;
            }
            if (image instanceof BufferedImage bufferedImage) {
                return bufferedImage;
            }
            int width = image.getWidth(null);
            int height = image.getHeight(null);
            if (width <= 0 || height <= 0) {
                return null;
            }
            BufferedImage converted = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = converted.createGraphics();
            try {
                graphics.drawImage(image, 0, 0, null);
            } finally {
                graphics.dispose();
            }
            return converted;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void savePastedImageData(String dataUrl, String mimeType) {
        if (activeDocument == null || activeDocument.format() != NoteFormat.MARKDOWN) {
            return;
        }
        try {
            int comma = dataUrl == null ? -1 : dataUrl.indexOf(',');
            String header = comma < 0 ? "" : dataUrl.substring(0, comma);
            if (comma < 0 || !header.toLowerCase(Locale.ROOT).contains(";base64")) {
                throw new IOException("剪贴板图片格式不受支持。");
            }
            byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
            if (bytes.length == 0) {
                throw new IOException("剪贴板图片内容为空。");
            }
            byte[] visibleBytes = NoteIO.repairFullyTransparentImage(bytes);
            boolean repairedTransparency = visibleBytes != bytes;
            Path imageDirectory = NoteIO.imageDirectory(activeDocument.path());
            Files.createDirectories(imageDirectory);
            Path target = uniquePastedImagePath(
                    imageDirectory,
                    repairedTransparency ? ".png" : imageExtension(mimeType));
            Files.write(target, visibleBytes);
            insertImagePath(target);
        } catch (IllegalArgumentException | IOException ex) {
            showError("粘贴图片失败", ex.getMessage());
        }
    }

    private String imageExtension(String mimeType) {
        String normalized = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (normalized.contains("jpeg") || normalized.contains("jpg")) return ".jpg";
        if (normalized.contains("gif")) return ".gif";
        if (normalized.contains("webp")) return ".webp";
        if (normalized.contains("bmp")) return ".bmp";
        return ".png";
    }

    private void insertImagePath(Path imagePath) {
        Path relative = activeDocument.path().getParent().relativize(imagePath);
        String uri;
        try {
            uri = NoteIO.imageDataUri(imagePath);
        } catch (IOException ignored) {
            uri = imagePath.toUri().toString();
        }
        execEditor("window.jnoteInsertImage('"
                + js(uri)
                + "', '"
                + js(relative.toString().replace('\\', '/'))
                + "', '"
                + js(activeDocument.imageDirectoryName())
                + "')");
        activeDocument.setDirty(true);
        renderTabs();
        updateStatus();
        renderFileTree();
    }

    private Path uniquePastedImagePath(Path imageDirectory, String extension) {
        String base = "pasted_" + PASTE_IMAGE_NAME.format(LocalDateTime.now());
        Path target = imageDirectory.resolve(base + extension);
        int index = 1;
        while (Files.exists(target)) {
            target = imageDirectory.resolve(base + "_" + index + extension);
            index++;
        }
        return target;
    }

    private void findInDocument() {
        String query = searchField.getText();
        if (query == null || query.isBlank()) {
            return;
        }
        execEditor("window.jnoteFind('" + js(query) + "')");
    }

    private void toggleSidebar() {
        sidebarVisible = !sidebarVisible;
        sidebarShell.setVisible(sidebarVisible);
        sidebarShell.setManaged(sidebarVisible);
    }

    private void execEditor(String script) {
        try {
            webEngine.executeScript(script);
        } catch (RuntimeException ignored) {
            // No active editable document.
        }
    }

    private String renderDocument(OpenDocument document) {
        return renderer.render(document, appState.collapsedHeadings(document.path()));
    }

    private void resetEditorUserChanges() {
        execEditor("window.jnoteResetUserChanges && window.jnoteResetUserChanges()");
    }

    private void updateStatus() {
        if (activeDocument == null) {
            editorStatus.setText("准备就绪");
            saveStatus.setText("● 已自动保存");
            return;
        }
        editorStatus.setText(activeDocument.displayName()
                + " · " + activeDocument.format().label()
                + (activeDocument.dirty() ? " · 未保存" : " · 已保存"));
        saveStatus.setText(activeDocument.dirty() ? "● 未保存" : "● 已自动保存");
    }

    private boolean confirmAllSaved() {
        for (OpenDocument document : new ArrayList<>(openDocuments.values())) {
            if (!confirmSaved(document)) {
                return false;
            }
        }
        return true;
    }

    private boolean confirmSaved(OpenDocument document) {
        if (document == activeDocument) {
            syncActiveDocument();
        }
        if (document == null || !document.dirty()) {
            return true;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("保存更改");
        configureDialog(dialog, "unsaved-dialog", 470);
        ButtonType save = new ButtonType("保存", javafx.scene.control.ButtonBar.ButtonData.NO);
        ButtonType discard = new ButtonType("不保存", javafx.scene.control.ButtonBar.ButtonData.YES);
        ButtonType cancel = new ButtonType("取消", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(save, discard, cancel);

        HBox header = dialogHeader(
                dialog,
                ICON_DIALOG_WARNING,
                "保存未完成的更改？",
                true);
        SVGPath fileGlyph = new SVGPath();
        fileGlyph.setContent(ICON_DIALOG_FILE);
        fileGlyph.getStyleClass().add("unsaved-file-glyph");
        StackPane fileIcon = new StackPane(fileGlyph);
        fileIcon.getStyleClass().add("unsaved-file-icon");
        Label fileName = new Label(document.displayName());
        fileName.getStyleClass().add("unsaved-file-name");
        Label path = new Label(document.path().toString());
        path.getStyleClass().add("unsaved-file-path");
        path.setWrapText(true);
        VBox fileCopy = new VBox(4, fileName, path);
        HBox.setHgrow(fileCopy, Priority.ALWAYS);
        HBox details = new HBox(12, fileIcon, fileCopy);
        details.setAlignment(Pos.CENTER_LEFT);
        details.getStyleClass().add("unsaved-details");
        Label hint = new Label("不保存将丢失本次修改。");
        hint.getStyleClass().add("unsaved-hint");
        hint.setWrapText(true);
        VBox decisionBody = new VBox(10, details, hint);
        VBox content = new VBox(16, header, decisionBody);
        setDialogBody(dialog, content);
        dialog.setResultConverter(button -> button);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(save);
        saveButton.getStyleClass().add("primary-dialog-button");
        saveButton.setDefaultButton(true);
        Button discardButton = (Button) dialog.getDialogPane().lookupButton(discard);
        discardButton.getStyleClass().add("discard-dialog-button");
        discardButton.setDefaultButton(false);
        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(cancel);
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() == cancel) {
            return false;
        }
        if (result.get() == save) {
            if (document == activeDocument) {
                return saveActiveDocument();
            } else {
                try {
                    NoteIO.write(document.path(), document.format(), document.content());
                    document.setDirty(false);
                    renderTabs();
                    updateStatus();
                } catch (IOException ex) {
                    showError("保存失败", ex.getMessage());
                    return false;
                }
            }
        }
        return true;
    }

    private void persistState() {
        storeCurrentWindowSize();
        if (!restoringState) {
            appState.openFiles().clear();
            appState.openFiles().addAll(openDocuments.keySet());
            appState.setActiveFile(activeDocument == null ? null : activeDocument.path());
        }
        try {
            stateStore.save(appState);
        } catch (IOException ignored) {
            // State persistence should not block editing.
        }
    }

    private void storeCurrentWindowSize() {
        if (primaryStage == null || primaryStage.getScene() == null) {
            return;
        }
        double width = primaryStage.getScene().getWidth();
        double height = primaryStage.getScene().getHeight();
        if (width > 0 && height > 0) {
            appState.setWindowSize(width, height);
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message == null ? "" : message);
        alert.showAndWait();
    }

    private String js(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "")
                .replace("\n", "\\n");
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class SmoothScrollAnimator extends AnimationTimer {
        private static final double RESPONSE_MILLIS = 72.0;
        private static final double WHEEL_SCALE = 1.18;
        private final ScrollPane scrollPane;
        private final boolean horizontal;
        private double targetPixels;
        private boolean running;
        private long lastFrame;

        private SmoothScrollAnimator(ScrollPane scrollPane, boolean horizontal) {
            this.scrollPane = scrollPane;
            this.horizontal = horizontal;
        }

        private boolean scrollBy(double delta) {
            double overflow = overflow();
            if (overflow <= 0 || Math.abs(delta) < 0.01) {
                return false;
            }
            double current = currentPixels(overflow);
            if (!running) {
                targetPixels = current;
            }
            double viewport = horizontal
                    ? scrollPane.getViewportBounds().getWidth()
                    : scrollPane.getViewportBounds().getHeight();
            double maxLead = Math.max(240, viewport * 1.35);
            targetPixels = clamp(targetPixels - clamp(delta, -180, 180) * WHEEL_SCALE,
                    Math.max(0, current - maxLead), Math.min(overflow, current + maxLead));
            targetPixels = clamp(targetPixels, 0, overflow);
            if (!running) {
                running = true;
                lastFrame = 0;
                start();
            }
            return true;
        }

        @Override
        public void handle(long now) {
            double overflow = overflow();
            if (overflow <= 0) {
                finish(0, 0);
                return;
            }
            targetPixels = clamp(targetPixels, 0, overflow);
            double current = currentPixels(overflow);
            double distance = targetPixels - current;
            if (Math.abs(distance) < 0.35) {
                finish(targetPixels, overflow);
                return;
            }
            double elapsedMillis = lastFrame == 0 ? 16 : Math.min(48, (now - lastFrame) / 1_000_000.0);
            lastFrame = now;
            double progress = 1 - Math.exp(-elapsedMillis / RESPONSE_MILLIS);
            setPixels(current + distance * progress, overflow);
        }

        private double overflow() {
            if (scrollPane.getContent() == null) {
                return 0;
            }
            double content = horizontal
                    ? scrollPane.getContent().getBoundsInLocal().getWidth()
                    : scrollPane.getContent().getBoundsInLocal().getHeight();
            double viewport = horizontal
                    ? scrollPane.getViewportBounds().getWidth()
                    : scrollPane.getViewportBounds().getHeight();
            return Math.max(0, content - viewport);
        }

        private double currentPixels(double overflow) {
            return (horizontal ? scrollPane.getHvalue() : scrollPane.getVvalue()) * overflow;
        }

        private void setPixels(double pixels, double overflow) {
            double value = overflow <= 0 ? 0 : clamp(pixels / overflow, 0, 1);
            if (horizontal) {
                scrollPane.setHvalue(value);
            } else {
                scrollPane.setVvalue(value);
            }
        }

        private void finish(double pixels, double overflow) {
            setPixels(pixels, overflow);
            running = false;
            lastFrame = 0;
            stop();
        }

        private void cancel() {
            if (!running) {
                return;
            }
            running = false;
            lastFrame = 0;
            stop();
        }

        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    public final class EditorBridge {
        public void markDirty() {
            if (activeDocument == null || loadingEditor) {
                return;
            }
            Runnable mark = () -> {
                activeDocument.setDirty(true);
                renderTabs();
                updateStatus();
            };
            if (Platform.isFxApplicationThread()) {
                mark.run();
            } else {
                Platform.runLater(mark);
            }
        }

        public void setHeadingCollapsed(String headingKey, boolean collapsed) {
            OpenDocument document = activeDocument;
            if (document == null || document.format() != NoteFormat.MARKDOWN
                    || headingKey == null || headingKey.isBlank()) {
                return;
            }
            Path documentPath = document.path();
            Runnable update = () -> {
                appState.setHeadingCollapsed(documentPath, headingKey, collapsed);
                persistState();
            };
            if (Platform.isFxApplicationThread()) {
                update.run();
            } else {
                Platform.runLater(update);
            }
        }

        public void openLink(String href) {
            if (href == null || href.isBlank()) {
                return;
            }
            Platform.runLater(() -> openWithDesktop(href));
        }

        public void openImage(String source) {
            if (source == null || source.isBlank()) {
                return;
            }
            Platform.runLater(() -> showImage(source));
        }

        public void pasteImageData(String dataUrl, String mimeType) {
            Platform.runLater(() -> savePastedImageData(dataUrl, mimeType));
        }

        public String imageDataUri(String source) {
            try {
                Path imagePath = resolveImageSource(source);
                return Files.isRegularFile(imagePath) ? NoteIO.imageDataUri(imagePath) : "";
            } catch (IOException | RuntimeException ignored) {
                return "";
            }
        }
    }

    private void openWithDesktop(String href) {
        try {
            URI uri = URI.create(href);
            String scheme = uri.getScheme();
            if (scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("不支持的链接协议");
            }
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new UnsupportedOperationException("当前系统不支持默认浏览器调用");
            }
            Desktop.getDesktop().browse(uri);
        } catch (Exception ex) {
            showError("无法打开链接", href);
        }
    }

    private static boolean isExternalWebLocation(String location) {
        if (location == null || location.isBlank()) {
            return false;
        }
        try {
            String scheme = URI.create(location).getScheme();
            return scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isEditorPageLocation(String location) {
        if (location == null || location.isBlank()) {
            return true;
        }
        String normalized = location.toLowerCase(Locale.ROOT);
        return normalized.equals("about:blank");
    }

    private void showImage(String source) {
        try {
            Path imagePath = resolveImageSource(source);
            if (!Files.isRegularFile(imagePath)) {
                throw new IllegalArgumentException("图片文件不存在");
            }
            Image image = new Image(imagePath.toUri().toString(), false);
            if (image.isError()) {
                throw new IllegalArgumentException("图片文件无法读取", image.getException());
            }
            ImageView imageView = new ImageView(image);
            imageView.setPreserveRatio(true);
            imageView.setFitWidth(920);
            imageView.setFitHeight(720);
            StackPane pane = new StackPane(imageView);
            pane.setPadding(new Insets(18));
            pane.setStyle("-fx-background-color: #f5f4ef;");
            Stage stage = new Stage();
            stage.setTitle("图片预览");
            stage.getIcons().setAll(applicationIcons);
            stage.setScene(new Scene(pane, 960, 760));
            stage.show();
        } catch (RuntimeException ex) {
            showError("无法打开图片", source + "\n" + (ex.getMessage() == null ? "" : ex.getMessage()));
        }
    }

    private Path resolveImageSource(String source) {
        try {
            if (source.startsWith("file:")) {
                return Path.of(URI.create(source));
            }
            if (activeDocument != null && activeDocument.path().getParent() != null) {
                return activeDocument.path().getParent().resolve(source).normalize();
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return Path.of(source);
    }
}
