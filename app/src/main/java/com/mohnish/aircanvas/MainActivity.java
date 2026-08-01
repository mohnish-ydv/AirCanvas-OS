package com.mohnish.aircanvas;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.appcompat.widget.SwitchCompat;

import com.mohnish.aircanvas.data.ProjectStore;
import com.mohnish.aircanvas.data.DocumentCodec;
import com.mohnish.aircanvas.export.ExportManager;
import com.mohnish.aircanvas.gesture.AdaptiveGestureProfile;
import com.mohnish.aircanvas.gesture.GestureEngine;
import com.mohnish.aircanvas.gesture.GestureEvent;
import com.mohnish.aircanvas.gesture.GestureFrame;
import com.mohnish.aircanvas.gesture.LandmarkPoint;
import com.mohnish.aircanvas.history.HistoryManager;
import com.mohnish.aircanvas.ink.OnDeviceInkRecognizer;
import com.mohnish.aircanvas.ink.SmartInkRequest;
import com.mohnish.aircanvas.model.CanvasElement;
import com.mohnish.aircanvas.model.DesignDocument;
import com.mohnish.aircanvas.model.InkMode;
import com.mohnish.aircanvas.model.ProjectInsights;
import com.mohnish.aircanvas.model.ShapeKind;
import com.mohnish.aircanvas.model.SelectionOperations;
import com.mohnish.aircanvas.model.StylePreset;
import com.mohnish.aircanvas.model.TemplateFactory;
import com.mohnish.aircanvas.model.Tool;
import com.mohnish.aircanvas.model.TransformAxis;
import com.mohnish.aircanvas.settings.AppSettings;
import com.mohnish.aircanvas.ui.AirCanvasView;
import com.mohnish.aircanvas.ui.GestureModePaletteView;
import com.mohnish.aircanvas.ui.GestureShapePaletteView;
import com.mohnish.aircanvas.ui.HackerMaskView;
import com.mohnish.aircanvas.ui.HandOverlayView;
import com.mohnish.aircanvas.ui.UiKit;
import com.mohnish.aircanvas.vision.HandLandmarkerController;
import com.mohnish.aircanvas.vision.PerformanceTelemetry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@SuppressLint("SetTextI18n") // The codename build intentionally ships with one English UI.
public final class MainActivity extends AppCompatActivity
        implements AirCanvasView.Listener, HandLandmarkerController.Listener {

    private static final int MENU_NEW = 100;
    private static final int MENU_LIBRARY = 101;
    private static final int MENU_RENAME = 102;
    private static final int MENU_EDIT = 103;
    private static final int MENU_GROUP = 104;
    private static final int MENU_UNGROUP = 105;
    private static final int MENU_FRONT = 106;
    private static final int MENU_FIT = 107;
    private static final int MENU_CAMERA = 108;
    private static final int MENU_SETTINGS = 109;
    private static final int MENU_CALIBRATE = 110;
    private static final int MENU_GUIDE = 111;
    private static final int MENU_ABOUT = 112;
    private static final int MENU_IMPORT = 113;
    private static final int MENU_OVERLAY = 114;
    private static final int MENU_PRESENT = 115;
    private static final int MENU_SHOWCASE = 116;
    private static final int MENU_INSIGHTS = 117;
    private static final int MENU_FOCUS = 118;
    private static final int MENU_HACKER_MASK = 119;
    private static final long SMART_INK_BATCH_DELAY_MS = 680L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "aircanvas-project-io");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private final HistoryManager history = new HistoryManager(
            32,
            12L * 1024L * 1024L
    );
    private final GestureEngine gestureEngine = new GestureEngine();
    private final PerformanceTelemetry performanceTelemetry = new PerformanceTelemetry();
    private final Map<Tool, Button> toolButtons = new EnumMap<>(Tool.class);
    private final AtomicReference<PendingHandFrame> pendingHandFrame = new AtomicReference<>();
    private final AtomicBoolean handUiPosted = new AtomicBoolean(false);
    private final AtomicLong lastErrorDispatchAt = new AtomicLong(0L);

    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private ActivityResultLauncher<String> pngExportLauncher;
    private ActivityResultLauncher<String> portfolioExportLauncher;
    private ActivityResultLauncher<String> pdfExportLauncher;
    private ActivityResultLauncher<String> jsonExportLauncher;
    private ActivityResultLauncher<String> svgExportLauncher;
    private ActivityResultLauncher<String[]> jsonImportLauncher;
    private FrameLayout root;
    private androidx.camera.view.PreviewView previewView;
    private AirCanvasView canvasView;
    private HandOverlayView handOverlayView;
    private HackerMaskView hackerMaskView;
    private GestureModePaletteView modePalette;
    private GestureShapePaletteView shapePalette;
    private LinearLayout topBar;
    private LinearLayout statusRow;
    private HorizontalScrollView toolScroll;
    private HorizontalScrollView selectionScroll;
    private LinearLayout selectionRow;
    private TextView documentTitle;
    private TextView documentSubtitle;
    private TextView poseChip;
    private TextView metricChip;
    private TextView templateChip;
    private TextView selectionLabel;
    private Button undoButton;
    private Button redoButton;
    private Button multiButton;
    private Button shapeButton;
    private Button smartInkButton;
    private Button axisButton;
    private Button presentationExitButton;
    private Button focusExitButton;
    private LinearLayout permissionCard;
    private TextView permissionBody;
    private Button permissionButton;
    private LinearLayout calibrationCard;
    private TextView calibrationTitle;
    private TextView calibrationBody;
    private ProgressBar calibrationProgress;
    private AppSettings appSettings;
    private ProjectStore projectStore;
    private OnDeviceInkRecognizer smartInkRecognizer;
    private String smartInkPreContext = "";
    private final List<SmartInkRequest> pendingSmartInkBatch = new ArrayList<>();
    private Runnable pendingSmartInkFlush;
    private long lastAdaptivePersistAt;
    private long lastAdaptiveRevision;
    private HandLandmarkerController handController;
    private Runnable pendingAutosave;
    private boolean permissionAskedThisSession;
    private long lastMetricUpdateAt;
    private long lastErrorToastAt;
    private int calibrationStep = -1;
    private int calibrationStableFrames;
    private float calibrationPinchSum;
    private int calibrationPinchSamples;
    private boolean palettePinchConsumed;
    private boolean focusMode;
    private int systemInsetLeft;
    private int systemInsetTop;
    private int systemInsetRight;
    private int systemInsetBottom;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        registerLaunchers();
        appSettings = new AppSettings(this);
        appSettings.applyPinchRescueV22Migration();
        appSettings.applyPinchReliabilityV23Migration();
        if (appSettings.landscapeFirst()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
        projectStore = new ProjectStore(this);
        gestureEngine.configure(
                appSettings.gestureSensitivity(),
                appSettings.cursorSmoothing()
        );
        AdaptiveGestureProfile.Snapshot adaptiveSnapshot =
                appSettings.adaptiveGestureSnapshot();
        gestureEngine.setAdaptiveProfile(adaptiveSnapshot);
        lastAdaptiveRevision = adaptiveSnapshot.revision();
        buildInterface();

        DesignDocument restored = restoreAutosave();
        canvasView.setDocument(restored == null
                ? TemplateFactory.create(TemplateFactory.Template.BLANK)
                : restored
        );
        canvasView.setShowGrid(appSettings.showGrid());
        canvasView.setSmartSnap(appSettings.smartSnap());
        handOverlayView.setShowLandmarks(appSettings.showLandmarks());
        updateDocumentChrome();
        updateHistoryButtons();
        selectTool(Tool.SELECT);

        if (appSettings.onboardingComplete()) {
            ensureCameraAccess(false);
        } else {
            showOnboarding();
        }
    }

    private void registerLaunchers() {
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    permissionAskedThisSession = true;
                    if (granted) {
                        permissionCard.setVisibility(View.GONE);
                        startCamera();
                    } else {
                        showPermissionCard();
                    }
                }
        );
        pngExportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("image/png"),
                uri -> completeExport(ExportManager.Format.PNG, uri)
        );
        portfolioExportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("image/png"),
                uri -> completeExport(ExportManager.Format.PORTFOLIO_PNG, uri)
        );
        pdfExportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/pdf"),
                uri -> completeExport(ExportManager.Format.PDF, uri)
        );
        jsonExportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                uri -> completeExport(ExportManager.Format.JSON, uri)
        );
        svgExportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("image/svg+xml"),
                uri -> completeExport(ExportManager.Format.SVG, uri)
        );
        jsonImportLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::importProject
        );
    }

    private void buildInterface() {
        root = new FrameLayout(this);
        root.setBackgroundColor(UiKit.BACKGROUND);
        setContentView(root);

        previewView = new androidx.camera.view.PreviewView(this);
        root.addView(previewView, matchParent());

        View cameraTint = new View(this);
        cameraTint.setBackgroundColor(0x12020A14);
        root.addView(cameraTint, matchParent());

        // Privacy mask stays below the design canvas, so every shape and stroke
        // remains visible above it during screen recording.
        hackerMaskView = new HackerMaskView(this);
        hackerMaskView.configure(
                appSettings.hackerMaskEnabled(),
                appSettings.hackerMaskCenterX(),
                appSettings.hackerMaskCenterY(),
                appSettings.hackerMaskSize()
        );
        root.addView(hackerMaskView, matchParent());

        canvasView = new AirCanvasView(this);
        canvasView.setListener(this);
        root.addView(canvasView, matchParent());

        handOverlayView = new HandOverlayView(this);
        root.addView(handOverlayView, matchParent());

        buildTopBar();
        buildStatusRow();
        buildToolBar();
        buildSelectionBar();
        buildPermissionCard();
        buildCalibrationCard();
        buildGesturePalette();
        applyInsets();
        updateResponsiveChrome();
    }

    private void buildGesturePalette() {
        modePalette = new GestureModePaletteView(this);
        modePalette.setListener(tool -> {
            selectTool(tool);
            feedback();
            poseChip.setText(tool.label + " mode");
        });
        root.addView(modePalette, matchParent());

        shapePalette = new GestureShapePaletteView(this);
        shapePalette.setListener(shape -> {
            canvasView.setActiveShape(shape);
            selectTool(Tool.BLOCK);
            if (shapeButton != null) {
                shapeButton.setText("Shape • " + shape.label);
            }
            feedback();
            poseChip.setText(shape.label + " ready • pinch and drag");
        });
        root.addView(shapePalette, matchParent());

        presentationExitButton = UiKit.button(this, "Exit presentation", true);
        presentationExitButton.setVisibility(View.GONE);
        presentationExitButton.setOnClickListener(view -> exitPresentation());
        FrameLayout.LayoutParams exitParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END
        );
        exitParams.topMargin = dp(14);
        exitParams.rightMargin = dp(14);
        root.addView(presentationExitButton, exitParams);

        focusExitButton = UiKit.button(this, "Exit focus", true);
        focusExitButton.setVisibility(View.GONE);
        focusExitButton.setOnClickListener(view -> setFocusMode(false));
        FrameLayout.LayoutParams focusParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END
        );
        focusParams.topMargin = dp(14);
        focusParams.rightMargin = dp(14);
        root.addView(focusExitButton, focusParams);
    }

    private void buildTopBar() {
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(8), dp(6), dp(8), dp(6));
        topBar.setBackgroundColor(0xE607111F);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0,
                dp(54),
                1f
        );
        titleParams.setMarginStart(dp(6));
        titles.setLayoutParams(titleParams);

        documentTitle = UiKit.title(this, "AIR CANVAS / PORTFOLIO");
        documentSubtitle = new TextView(this);
        documentSubtitle.setTextColor(UiKit.MUTED);
        documentSubtitle.setTextSize(11f);
        documentSubtitle.setSingleLine(true);
        titles.addView(documentTitle);
        titles.addView(documentSubtitle);
        topBar.addView(titles);

        undoButton = UiKit.button(this, "↶", true);
        undoButton.setContentDescription("Undo");
        undoButton.setOnClickListener(view -> undo());
        redoButton = UiKit.button(this, "↷", true);
        redoButton.setContentDescription("Redo");
        redoButton.setOnClickListener(view -> redo());
        Button save = UiKit.button(this, "Save", true);
        save.setOnClickListener(view -> saveDocument(true));
        Button export = UiKit.button(this, "Export", true);
        export.setOnClickListener(view -> showExportDialog());
        Button menu = UiKit.button(this, "⋮", true);
        menu.setContentDescription("More actions");
        menu.setOnClickListener(this::showMoreMenu);

        topBar.addView(undoButton);
        topBar.addView(redoButton);
        topBar.addView(save);
        topBar.addView(export);
        topBar.addView(menu);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
        );
        root.addView(topBar, params);
    }

    private void buildStatusRow() {
        statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        poseChip = UiKit.chip(this, "Touch mode ready");
        metricChip = UiKit.chip(this, "Balanced");
        templateChip = UiKit.chip(this, "Blank");
        metricChip.setOnClickListener(view -> showSettings());
        templateChip.setOnClickListener(view -> showTemplateDialog());
        LinearLayout.LayoutParams chipMargin = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(34)
        );
        chipMargin.setMarginEnd(dp(6));
        poseChip.setLayoutParams(new LinearLayout.LayoutParams(chipMargin));
        metricChip.setLayoutParams(new LinearLayout.LayoutParams(chipMargin));
        statusRow.addView(poseChip);
        statusRow.addView(metricChip);
        statusRow.addView(templateChip);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START
        );
        params.leftMargin = dp(12);
        params.topMargin = dp(70);
        params.rightMargin = dp(12);
        root.addView(statusRow, params);
    }

    private void buildToolBar() {
        LinearLayout toolRow = new LinearLayout(this);
        toolRow.setOrientation(LinearLayout.HORIZONTAL);
        toolRow.setGravity(Gravity.CENTER_VERTICAL);
        toolRow.setPadding(dp(8), dp(8), dp(8), dp(8));

        for (Tool tool : Tool.values()) {
            Button button = UiKit.button(this, tool.label, false);
            button.setContentDescription(tool.label + " tool");
            button.setOnClickListener(view -> {
                if (tool == Tool.BLOCK && canvasView.getTool() == Tool.BLOCK) {
                    showShapeDialog();
                } else if (tool == Tool.SMART_INK
                        && canvasView.getTool() == Tool.SMART_INK) {
                    showInkModeDialog();
                } else {
                    selectTool(tool);
                }
            });
            toolButtons.put(tool, button);
            if (tool == Tool.BLOCK) {
                shapeButton = button;
            } else if (tool == Tool.SMART_INK) {
                smartInkButton = button;
            }
            toolRow.addView(button);
        }

        Button template = UiKit.button(this, "Templates", false);
        template.setOnClickListener(view -> showTemplateDialog());
        Button fit = UiKit.button(this, "Fit", false);
        fit.setOnClickListener(view -> canvasView.resetViewport());
        toolRow.addView(template);
        toolRow.addView(fit);

        toolScroll = new HorizontalScrollView(this);
        toolScroll.setHorizontalScrollBarEnabled(false);
        toolScroll.setFillViewport(false);
        toolScroll.setBackgroundColor(0xE607111F);
        toolScroll.addView(toolRow);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        root.addView(toolScroll, params);
    }

    private void buildSelectionBar() {
        selectionRow = new LinearLayout(this);
        selectionRow.setOrientation(LinearLayout.HORIZONTAL);
        selectionRow.setGravity(Gravity.CENTER_VERTICAL);
        selectionRow.setPadding(dp(8), dp(6), dp(8), dp(6));
        selectionRow.setBackground(UiKit.background(
                this,
                0xF0101D31,
                16f,
                0x505EE7F7
        ));

        selectionLabel = UiKit.chip(this, "1 selected");
        selectionRow.addView(selectionLabel);

        Button edit = UiKit.button(this, "Edit text", true);
        edit.setOnClickListener(view -> canvasView.requestEditSelectedText());
        Button delete = UiKit.button(this, "Delete", true);
        delete.setOnClickListener(view -> {
            if (!canvasView.deleteSelection()) {
                toast("Select an unlocked object first");
            }
        });
        Button group = UiKit.button(this, "Group", true);
        group.setOnClickListener(view -> {
            if (!canvasView.groupSelection()) {
                toast("Select two or more objects");
            }
        });
        Button ungroup = UiKit.button(this, "Ungroup", true);
        ungroup.setOnClickListener(view -> {
            if (!canvasView.ungroupSelection()) {
                toast("Selected objects are not grouped");
            }
        });
        Button smaller = UiKit.button(this, "−", true);
        smaller.setContentDescription("Scale selected objects down");
        smaller.setOnClickListener(view -> canvasView.scaleSelection(0.9f));
        Button larger = UiKit.button(this, "+", true);
        larger.setContentDescription("Scale selected objects up");
        larger.setOnClickListener(view -> canvasView.scaleSelection(1.1f));
        Button duplicate = UiKit.button(this, "Duplicate", true);
        duplicate.setOnClickListener(view -> canvasView.duplicateSelection());
        Button style = UiKit.button(this, "Style studio", true);
        style.setOnClickListener(view -> showStyleStudio());
        Button arrange = UiKit.button(this, "Arrange", true);
        arrange.setOnClickListener(view -> showArrangeDialog());
        Button lock = UiKit.button(this, "Lock", true);
        lock.setOnClickListener(view -> showLockDialog());
        axisButton = UiKit.button(this, "Axis XYZ", true);
        axisButton.setOnClickListener(view -> {
            TransformAxis axis = canvasView.cycleTransformAxis(1);
            updateAxisButton(axis);
        });
        Button rotateLeft = UiKit.button(this, "↺ 15°", true);
        rotateLeft.setOnClickListener(view -> canvasView.rotateSelection(
                canvasView.getTransformAxis(),
                -15f
        ));
        Button rotateRight = UiKit.button(this, "↻ 15°", true);
        rotateRight.setOnClickListener(view -> canvasView.rotateSelection(
                canvasView.getTransformAxis(),
                15f
        ));
        Button resetRotation = UiKit.button(this, "Reset axes", true);
        resetRotation.setOnClickListener(view -> canvasView.resetSelectionRotation());
        Button back = UiKit.button(this, "To back", true);
        back.setOnClickListener(view -> canvasView.sendSelectionToBack());
        Button front = UiKit.button(this, "To front", true);
        front.setOnClickListener(view -> canvasView.bringSelectionToFront());
        multiButton = UiKit.button(this, "Multi off", true);
        multiButton.setOnClickListener(view -> {
            canvasView.setMultiSelect(!canvasView.isMultiSelect());
            updateMultiButton();
        });

        selectionRow.addView(edit);
        selectionRow.addView(delete);
        selectionRow.addView(group);
        selectionRow.addView(ungroup);
        selectionRow.addView(smaller);
        selectionRow.addView(larger);
        selectionRow.addView(duplicate);
        selectionRow.addView(style);
        selectionRow.addView(arrange);
        selectionRow.addView(lock);
        selectionRow.addView(axisButton);
        selectionRow.addView(rotateLeft);
        selectionRow.addView(rotateRight);
        selectionRow.addView(resetRotation);
        selectionRow.addView(back);
        selectionRow.addView(front);
        selectionRow.addView(multiButton);

        selectionScroll = new HorizontalScrollView(this);
        selectionScroll.setHorizontalScrollBarEnabled(false);
        selectionScroll.setFillViewport(false);
        selectionScroll.addView(selectionRow);
        selectionScroll.setVisibility(View.GONE);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        params.leftMargin = dp(8);
        params.rightMargin = dp(8);
        params.bottomMargin = dp(68);
        root.addView(selectionScroll, params);
    }

    private void buildPermissionCard() {
        permissionCard = new LinearLayout(this);
        permissionCard.setOrientation(LinearLayout.VERTICAL);
        permissionCard.setGravity(Gravity.CENTER_HORIZONTAL);
        permissionCard.setPadding(dp(22), dp(22), dp(22), dp(22));
        permissionCard.setBackground(UiKit.background(
                this,
                0xFA101D31,
                22f,
                0x705EE7F7
        ));
        permissionCard.setVisibility(View.GONE);

        TextView title = UiKit.title(this, "Camera access required");
        title.setGravity(Gravity.CENTER);
        permissionBody = new TextView(this);
        permissionBody.setText(
                "Hand landmarks run on this device. Frames are never saved or uploaded. "
                        + "Touch editing remains available without the camera."
        );
        permissionBody.setTextColor(UiKit.MUTED);
        permissionBody.setTextSize(14f);
        permissionBody.setGravity(Gravity.CENTER);
        permissionBody.setPadding(0, dp(12), 0, dp(16));
        permissionButton = UiKit.button(this, "Enable camera", false);
        permissionButton.setOnClickListener(view -> requestOrOpenCameraPermission());
        permissionCard.addView(title);
        permissionCard.addView(permissionBody);
        permissionCard.addView(permissionButton);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        params.leftMargin = dp(28);
        params.rightMargin = dp(28);
        root.addView(permissionCard, params);
    }

    private void buildCalibrationCard() {
        calibrationCard = new LinearLayout(this);
        calibrationCard.setOrientation(LinearLayout.VERTICAL);
        calibrationCard.setPadding(dp(20), dp(18), dp(20), dp(18));
        calibrationCard.setBackground(UiKit.background(
                this,
                0xFA101D31,
                22f,
                0x80A889FF
        ));
        calibrationCard.setVisibility(View.GONE);

        calibrationTitle = UiKit.title(this, "Gesture calibration");
        calibrationBody = new TextView(this);
        calibrationBody.setTextColor(UiKit.MUTED);
        calibrationBody.setTextSize(14f);
        calibrationBody.setPadding(0, dp(10), 0, dp(12));
        calibrationProgress = new ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
        );
        calibrationProgress.setMax(24);
        Button cancel = UiKit.button(this, "Cancel calibration", true);
        cancel.setOnClickListener(view -> stopCalibration(false));
        calibrationCard.addView(calibrationTitle);
        calibrationCard.addView(calibrationBody);
        calibrationCard.addView(calibrationProgress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(8)
        ));
        calibrationCard.addView(cancel);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        params.leftMargin = dp(28);
        params.rightMargin = dp(28);
        root.addView(calibrationCard, params);
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            systemInsetLeft = bars.left;
            systemInsetTop = bars.top;
            systemInsetRight = bars.right;
            systemInsetBottom = bars.bottom;
            topBar.setPadding(dp(8), bars.top + dp(5), dp(8), dp(5));

            FrameLayout.LayoutParams statusParams =
                    (FrameLayout.LayoutParams) statusRow.getLayoutParams();
            statusParams.topMargin = bars.top + dp(68);
            statusRow.setLayoutParams(statusParams);

            toolScroll.setPadding(0, 0, 0, bars.bottom);
            FrameLayout.LayoutParams selectionParams =
                    (FrameLayout.LayoutParams) selectionScroll.getLayoutParams();
            selectionParams.bottomMargin = bars.bottom + dp(68);
            selectionScroll.setLayoutParams(selectionParams);

            FrameLayout.LayoutParams exitParams =
                    (FrameLayout.LayoutParams) presentationExitButton.getLayoutParams();
            exitParams.topMargin = bars.top + dp(8);
            exitParams.rightMargin = dp(12);
            presentationExitButton.setLayoutParams(exitParams);
            updateResponsiveChrome();
            return windowInsets;
        });
    }

    private void updateResponsiveChrome() {
        if (canvasView == null || topBar == null || toolScroll == null) {
            return;
        }
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        if (documentSubtitle != null) {
            documentSubtitle.setVisibility(landscape ? View.GONE : View.VISIBLE);
        }
        if (documentTitle != null) {
            documentTitle.setTextSize(landscape ? 15f : 17f);
        }
        if (poseChip != null) {
            poseChip.setMaxWidth(dp(landscape ? 220 : 300));
        }
        FrameLayout.LayoutParams statusParams =
                (FrameLayout.LayoutParams) statusRow.getLayoutParams();
        statusParams.topMargin = systemInsetTop + dp(landscape ? 56 : 68);
        statusParams.leftMargin = systemInsetLeft + dp(landscape ? 8 : 12);
        statusRow.setLayoutParams(statusParams);

        boolean chromeHidden = focusMode || canvasView.isPresentationMode();
        int top = chromeHidden ? 0 : systemInsetTop + dp(landscape ? 96 : 112);
        int bottom = chromeHidden ? 0 : systemInsetBottom + dp(landscape ? 60 : 68);
        if (!chromeHidden && selectionScroll != null
                && selectionScroll.getVisibility() == View.VISIBLE) {
            bottom += dp(landscape ? 54 : 60);
        }
        canvasView.setEditorInsets(
                chromeHidden ? 0 : systemInsetLeft + dp(6),
                top,
                chromeHidden ? 0 : systemInsetRight + dp(6),
                bottom
        );
    }

    private void ensureCameraAccess(boolean requestImmediately) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            permissionCard.setVisibility(View.GONE);
            startCamera();
            return;
        }
        showPermissionCard();
        if (requestImmediately || !permissionAskedThisSession) {
            permissionAskedThisSession = true;
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void requestOrOpenCameraPermission() {
        boolean permanentlyDenied = permissionAskedThisSession
                && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA);
        if (permanentlyDenied) {
            Intent intent = new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(intent);
        } else {
            permissionAskedThisSession = true;
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void showPermissionCard() {
        permissionCard.setVisibility(View.VISIBLE);
        boolean settingsRequired = permissionAskedThisSession
                && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA);
        permissionButton.setText(settingsRequired ? "Open app settings" : "Enable camera");
        if (settingsRequired) {
            permissionBody.setText(
                    "Camera permission is disabled. Open app settings to enable gesture tracking. "
                            + "All touch editing tools still work."
            );
        }
    }

    private void startCamera() {
        if (handController != null) {
            handController.close();
        }
        gestureEngine.reset();
        performanceTelemetry.reset();
        pendingHandFrame.set(null);
        poseChip.setText("Loading hand model…");
        handController = new HandLandmarkerController(
                this,
                this,
                previewView,
                appSettings,
                this
        );
        handController.start();
    }

    private void switchCamera() {
        int next = appSettings.lensFacing() == CameraSelector.LENS_FACING_FRONT
                ? CameraSelector.LENS_FACING_BACK
                : CameraSelector.LENS_FACING_FRONT;
        appSettings.setLensFacing(next);
        startCamera();
        toast(next == CameraSelector.LENS_FACING_FRONT
                ? "Front camera selected"
                : "Rear camera selected"
        );
    }

    private void selectTool(Tool selected) {
        if (selected == Tool.PRESENT) {
            enterPresentation();
        } else if (canvasView.isPresentationMode()) {
            restoreEditorChrome();
        }
        canvasView.setTool(selected);
        for (Map.Entry<Tool, Button> entry : toolButtons.entrySet()) {
            boolean active = entry.getKey() == selected;
            entry.getValue().setTextColor(active ? UiKit.PRIMARY : UiKit.TEXT);
            entry.getValue().setBackground(UiKit.background(
                    this,
                    active ? UiKit.SURFACE_SELECTED : UiKit.SURFACE,
                    16f,
                    active ? UiKit.PRIMARY : 0x485EE7F7
            ));
        }
        if (shapeButton != null) {
            shapeButton.setText("Shape • " + canvasView.getActiveShape().label);
        }
        if (smartInkButton != null) {
            smartInkButton.setText("Smart • " + canvasView.getInkMode().label);
        }
        if (selected == Tool.SMART_INK) {
            OnDeviceInkRecognizer recognizer = ensureSmartInkRecognizer();
            if (recognizer != null) {
                recognizer.prepare(smartInkCallback());
            }
        }
        if (selected == Tool.TRANSFORM) {
            updateAxisButton(canvasView.getTransformAxis());
        }
    }

    private void enterPresentation() {
        focusMode = false;
        focusExitButton.setVisibility(View.GONE);
        canvasView.setPresentationMode(true);
        handOverlayView.setShowLandmarks(false);
        topBar.setVisibility(View.GONE);
        statusRow.setVisibility(View.GONE);
        toolScroll.setVisibility(View.GONE);
        selectionScroll.setVisibility(View.GONE);
        presentationExitButton.setVisibility(View.VISIBLE);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(),
                root
        );
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );
        controller.hide(WindowInsetsCompat.Type.systemBars());
        updateResponsiveChrome();
    }

    private void exitPresentation() {
        restoreEditorChrome();
        selectTool(Tool.SELECT);
    }

    private void restoreEditorChrome() {
        canvasView.setPresentationMode(false);
        handOverlayView.setShowLandmarks(appSettings.showLandmarks());
        topBar.setVisibility(View.VISIBLE);
        statusRow.setVisibility(View.VISIBLE);
        toolScroll.setVisibility(View.VISIBLE);
        presentationExitButton.setVisibility(View.GONE);
        focusExitButton.setVisibility(View.GONE);
        focusMode = false;
        WindowCompat.getInsetsController(getWindow(), root)
                .show(WindowInsetsCompat.Type.systemBars());
        if (canvasView.selectionCount() > 0 && calibrationStep < 0) {
            selectionScroll.setVisibility(View.VISIBLE);
        }
        updateResponsiveChrome();
    }

    private void setFocusMode(boolean enabled) {
        if (canvasView.isPresentationMode()) {
            restoreEditorChrome();
        }
        focusMode = enabled;
        topBar.setVisibility(enabled ? View.GONE : View.VISIBLE);
        statusRow.setVisibility(enabled ? View.GONE : View.VISIBLE);
        toolScroll.setVisibility(enabled ? View.GONE : View.VISIBLE);
        selectionScroll.setVisibility(
                enabled || canvasView.selectionCount() == 0
                        ? View.GONE
                        : View.VISIBLE
        );
        focusExitButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
        handOverlayView.setShowLandmarks(enabled ? false : appSettings.showLandmarks());
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(),
                root
        );
        if (enabled) {
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
            controller.hide(WindowInsetsCompat.Type.systemBars());
            toast("Focus mode • editing stays active");
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars());
        }
        updateResponsiveChrome();
    }

    private void updateAxisButton(TransformAxis axis) {
        if (axisButton != null) {
            axisButton.setText("Axis " + (axis == TransformAxis.FREE ? "XYZ" : axis.name()));
        }
    }

    private void undo() {
        if (!history.canUndo()) {
            return;
        }
        DesignDocument restored = history.undo(canvasView.getDocument());
        canvasView.setDocument(restored, false);
        updateDocumentChrome();
        updateHistoryButtons();
        scheduleAutosave();
        feedback();
    }

    private void redo() {
        if (!history.canRedo()) {
            return;
        }
        DesignDocument restored = history.redo(canvasView.getDocument());
        canvasView.setDocument(restored, false);
        updateDocumentChrome();
        updateHistoryButtons();
        scheduleAutosave();
        feedback();
    }

    private void updateHistoryButtons() {
        undoButton.setEnabled(history.canUndo());
        redoButton.setEnabled(history.canRedo());
        undoButton.setAlpha(history.canUndo() ? 1f : 0.38f);
        redoButton.setAlpha(history.canRedo() ? 1f : 0.38f);
    }

    private void updateDocumentChrome() {
        DesignDocument document = canvasView.getDocument();
        documentSubtitle.setText(
                document.name
                        + "  •  "
                        + (canvasView.isSpatialOverlay() ? "Spatial overlay" : "Design board")
                        + "  •  "
                        + document.elements.size()
                        + " objects"
        );
        templateChip.setText(document.template);
        metricChip.setText(appSettings.performanceProfile().label);
        if (shapeButton != null) {
            shapeButton.setText("Shape • " + canvasView.getActiveShape().label);
        }
    }

    private void updateMultiButton() {
        boolean active = canvasView.isMultiSelect();
        multiButton.setText(active ? "Multi on" : "Multi off");
        multiButton.setTextColor(active ? UiKit.PRIMARY : UiKit.TEXT);
    }

    private void showMoreMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, MENU_NEW, 0, "New / choose template");
        popup.getMenu().add(0, MENU_LIBRARY, 1, "My saved designs");
        popup.getMenu().add(0, MENU_RENAME, 2, "Rename design");
        MenuItem edit = popup.getMenu().add(0, MENU_EDIT, 3, "Edit selected text");
        MenuItem group = popup.getMenu().add(0, MENU_GROUP, 4, "Group selection");
        MenuItem ungroup = popup.getMenu().add(0, MENU_UNGROUP, 5, "Ungroup selection");
        MenuItem front = popup.getMenu().add(0, MENU_FRONT, 6, "Bring selection to front");
        popup.getMenu().add(0, MENU_FIT, 7, "Fit canvas");
        popup.getMenu().add(0, MENU_CAMERA, 8, "Switch camera");
        popup.getMenu().add(0, MENU_SETTINGS, 9, "Settings & performance");
        popup.getMenu().add(0, MENU_CALIBRATE, 10, "Calibrate gestures");
        popup.getMenu().add(0, MENU_GUIDE, 11, "Gesture guide");
        popup.getMenu().add(0, MENU_IMPORT, 12, "Import editable JSON");
        popup.getMenu().add(
                0,
                MENU_OVERLAY,
                13,
                canvasView.isSpatialOverlay()
                        ? "Switch to design board"
                        : "Switch to camera overlay"
        );
        popup.getMenu().add(0, MENU_PRESENT, 14, "Full-screen presentation");
        popup.getMenu().add(0, MENU_SHOWCASE, 15, "Portfolio showcase demo");
        popup.getMenu().add(0, MENU_INSIGHTS, 16, "Project insights");
        popup.getMenu().add(
                0,
                MENU_FOCUS,
                17,
                focusMode ? "Exit focus mode" : "Focus mode"
        );
        popup.getMenu().add(
                0,
                MENU_HACKER_MASK,
                18,
                appSettings.hackerMaskEnabled()
                        ? "Hacker face mask • On"
                        : "Hacker face mask • Off"
        );
        popup.getMenu().add(0, MENU_ABOUT, 19, "About");

        boolean hasSelection = canvasView.selectionCount() > 0;
        edit.setEnabled(canvasView.selectionCount() == 1);
        group.setEnabled(canvasView.selectionCount() >= 2);
        ungroup.setEnabled(hasSelection);
        front.setEnabled(hasSelection);

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case MENU_NEW -> showTemplateDialog();
                case MENU_LIBRARY -> loadProjectLibrary();
                case MENU_RENAME -> showRenameDialog();
                case MENU_EDIT -> canvasView.requestEditSelectedText();
                case MENU_GROUP -> canvasView.groupSelection();
                case MENU_UNGROUP -> canvasView.ungroupSelection();
                case MENU_FRONT -> canvasView.bringSelectionToFront();
                case MENU_FIT -> canvasView.resetViewport();
                case MENU_CAMERA -> switchCamera();
                case MENU_SETTINGS -> showSettings();
                case MENU_CALIBRATE -> startCalibration();
                case MENU_GUIDE -> showGestureGuide();
                case MENU_IMPORT -> jsonImportLauncher.launch(new String[]{
                        "application/json",
                        "text/json",
                        "text/plain"
                });
                case MENU_OVERLAY -> canvasView.setSpatialOverlay(
                        !canvasView.isSpatialOverlay()
                );
                case MENU_PRESENT -> selectTool(Tool.PRESENT);
                case MENU_SHOWCASE -> showPortfolioShowcaseDialog();
                case MENU_INSIGHTS -> showProjectInsights();
                case MENU_FOCUS -> setFocusMode(!focusMode);
                case MENU_HACKER_MASK -> showHackerMaskDialog();
                case MENU_ABOUT -> showAbout();
                default -> {
                    return false;
                }
            }
            return true;
        });
        popup.show();
    }


    private void showHackerMaskDialog() {
        boolean originalEnabled = appSettings.hackerMaskEnabled();
        float originalX = appSettings.hackerMaskCenterX();
        float originalY = appSettings.hackerMaskCenterY();
        float originalSize = appSettings.hackerMaskSize();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(8));

        SwitchCompat enabled = new SwitchCompat(this);
        enabled.setText("Enable hacker privacy mask");
        enabled.setTextColor(UiKit.TEXT);
        enabled.setChecked(originalEnabled);
        content.addView(enabled);

        TextView layerNote = settingsLabel(
                "The mask is rendered above the camera but below all shapes, text and ink."
        );
        layerNote.setTextColor(UiKit.MUTED);
        content.addView(layerNote);

        TextView xLabel = settingsLabel("Horizontal position • " + Math.round(originalX * 100f) + "%");
        SeekBar xSeek = new SeekBar(this);
        xSeek.setMax(70);
        xSeek.setProgress(Math.round((originalX - 0.15f) * 100f));
        content.addView(xLabel);
        content.addView(xSeek);

        TextView yLabel = settingsLabel("Vertical position • " + Math.round(originalY * 100f) + "%");
        SeekBar ySeek = new SeekBar(this);
        ySeek.setMax(60);
        ySeek.setProgress(Math.round((originalY - 0.16f) * 100f));
        content.addView(yLabel);
        content.addView(ySeek);

        TextView sizeLabel = settingsLabel("Mask size • " + Math.round(originalSize * 100f) + "%");
        SeekBar sizeSeek = new SeekBar(this);
        sizeSeek.setMax(40);
        sizeSeek.setProgress(Math.round((originalSize - 0.20f) * 100f));
        content.addView(sizeLabel);
        content.addView(sizeSeek);

        Runnable preview = () -> {
            float x = 0.15f + xSeek.getProgress() / 100f;
            float y = 0.16f + ySeek.getProgress() / 100f;
            float size = 0.20f + sizeSeek.getProgress() / 100f;
            xLabel.setText("Horizontal position • " + Math.round(x * 100f) + "%");
            yLabel.setText("Vertical position • " + Math.round(y * 100f) + "%");
            sizeLabel.setText("Mask size • " + Math.round(size * 100f) + "%");
            hackerMaskView.configure(enabled.isChecked(), x, y, size);
        };
        SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                preview.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
        xSeek.setOnSeekBarChangeListener(seekListener);
        ySeek.setOnSeekBarChangeListener(seekListener);
        sizeSeek.setOnSeekBarChangeListener(seekListener);
        enabled.setOnCheckedChangeListener((button, checked) -> preview.run());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Hacker face mask")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnCancelListener(ignored -> hackerMaskView.configure(
                originalEnabled,
                originalX,
                originalY,
                originalSize
        ));
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view -> {
                hackerMaskView.configure(originalEnabled, originalX, originalY, originalSize);
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                float x = 0.15f + xSeek.getProgress() / 100f;
                float y = 0.16f + ySeek.getProgress() / 100f;
                float size = 0.20f + sizeSeek.getProgress() / 100f;
                appSettings.setHackerMaskEnabled(enabled.isChecked());
                appSettings.setHackerMaskGeometry(x, y, size);
                hackerMaskView.configure(enabled.isChecked(), x, y, size);
                toast(enabled.isChecked()
                        ? "Hacker mask enabled • canvas stays on top"
                        : "Hacker mask disabled");
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void showPortfolioShowcaseDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Portfolio showcase")
                .setMessage(
                        "Open a polished scene that demonstrates gesture control, Smart Ink, "
                                + "true 3D, privacy-first processing and professional export. "
                                + "Your current project is autosaved first."
                )
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Open editable", (dialog, which) ->
                        loadPortfolioShowcase(false)
                )
                .setPositiveButton("Play full-screen", (dialog, which) ->
                        loadPortfolioShowcase(true)
                )
                .show();
    }

    private void loadPortfolioShowcase(boolean present) {
        enqueueAutosaveSnapshot();
        history.clear();
        DesignDocument showcase = TemplateFactory.create(
                TemplateFactory.Template.PORTFOLIO_SHOWCASE
        );
        canvasView.setDocument(showcase);
        canvasView.setSpatialOverlay(true);
        updateDocumentChrome();
        updateHistoryButtons();
        scheduleAutosave();
        if (present) {
            selectTool(Tool.PRESENT);
            toast("Fist to pan • pinch to use the air laser");
        } else {
            selectTool(Tool.SELECT);
            toast("Portfolio showcase ready to customize");
        }
    }

    private void showProjectInsights() {
        ProjectInsights insights = ProjectInsights.analyze(canvasView.getDocument());
        String message = "Objects: " + insights.objects()
                + "\nTrue 3D solids: " + insights.spatialObjects()
                + "\nConnectors: " + insights.connectors()
                + "\nFreehand strokes: " + insights.strokes()
                + "\nText objects: " + insights.textObjects()
                + "\nWords: " + insights.words()
                + "\nGroups: " + insights.groups()
                + "\nLocked objects: " + insights.lockedObjects()
                + "\n\nLive engine: " + performanceTelemetry.snapshot().compactLabel();
        new AlertDialog.Builder(this)
                .setTitle("Project insights")
                .setMessage(message)
                .setNegativeButton("Close", null)
                .setPositiveButton("Export portfolio poster", (dialog, which) -> {
                    String name = ExportManager.safeBaseName(canvasView.getDocument().name)
                            + "-portfolio.png";
                    portfolioExportLauncher.launch(name);
                })
                .show();
    }

    private void showStyleStudio() {
        if (canvasView.selectionCount() == 0) {
            toast("Select one or more objects first");
            return;
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(12), dp(18), dp(8));

        TextView intro = new TextView(this);
        intro.setText("Apply a cohesive visual system to every selected object.");
        intro.setTextColor(UiKit.MUTED);
        intro.setTextSize(14f);
        intro.setPadding(0, 0, 0, dp(10));
        content.addView(intro);

        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        for (StylePreset preset : StylePreset.values()) {
            Button button = UiKit.button(this, preset.label, true);
            button.setOnClickListener(view -> {
                if (canvasView.applySelectionStyle(preset)) {
                    feedback();
                    poseChip.setText(preset.label + " applied");
                }
            });
            presetRow.addView(button);
        }
        HorizontalScrollView presetScroll = new HorizontalScrollView(this);
        presetScroll.setHorizontalScrollBarEnabled(false);
        presetScroll.addView(presetRow);
        content.addView(presetScroll);

        TextView strokeLabel = settingsLabel("Stroke width • 5");
        SeekBar stroke = new SeekBar(this);
        stroke.setMax(39);
        stroke.setProgress(4);
        stroke.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                strokeLabel.setText("Stroke width • " + (progress + 1));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                canvasView.setSelectionStrokeWidth(seekBar.getProgress() + 1f);
            }
        });
        content.addView(strokeLabel);
        content.addView(stroke);

        TextView opacityLabel = settingsLabel("Opacity • 100%");
        SeekBar opacity = new SeekBar(this);
        opacity.setMax(92);
        opacity.setProgress(92);
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                opacityLabel.setText("Opacity • " + (progress + 8) + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                canvasView.setSelectionOpacity((seekBar.getProgress() + 8) / 100f);
            }
        });
        content.addView(opacityLabel);
        content.addView(opacity);

        ScrollView styleScroll = new ScrollView(this);
        styleScroll.setFillViewport(true);
        styleScroll.addView(content);
        new AlertDialog.Builder(this)
                .setTitle("Style Studio")
                .setView(styleScroll)
                .setPositiveButton("Done", null)
                .show();
    }

    private void showArrangeDialog() {
        if (canvasView.selectionCount() < 2) {
            toast("Select at least two objects");
            return;
        }
        SelectionOperations.Alignment[] alignments = SelectionOperations.Alignment.values();
        SelectionOperations.Distribution[] distributions = SelectionOperations.Distribution.values();
        String[] labels = new String[alignments.length + distributions.length];
        for (int index = 0; index < alignments.length; index++) {
            labels[index] = alignments[index].label;
        }
        for (int index = 0; index < distributions.length; index++) {
            labels[alignments.length + index] = distributions[index].label;
        }
        new AlertDialog.Builder(this)
                .setTitle("Arrange selection")
                .setItems(labels, (dialog, which) -> {
                    boolean changed;
                    if (which < alignments.length) {
                        changed = canvasView.alignSelection(alignments[which]);
                    } else {
                        changed = canvasView.distributeSelection(
                                distributions[which - alignments.length]
                        );
                    }
                    if (!changed) {
                        toast("Distribution needs three unlocked objects");
                    }
                })
                .show();
    }

    private void showLockDialog() {
        if (canvasView.selectionCount() == 0) {
            toast("Select one or more objects first");
            return;
        }
        boolean hasLocked = canvasView.hasLockedSelection();
        String[] actions = hasLocked
                ? new String[]{"Unlock selected", "Lock all selected"}
                : new String[]{"Lock selected", "Unlock selected"};
        new AlertDialog.Builder(this)
                .setTitle("Selection protection")
                .setItems(actions, (dialog, which) -> {
                    boolean lock = hasLocked ? which == 1 : which == 0;
                    if (canvasView.setSelectionLocked(lock)) {
                        toast(lock ? "Selection locked" : "Selection unlocked");
                    }
                })
                .show();
    }

    private void showTemplateDialog() {
        TemplateFactory.Template[] templates = TemplateFactory.Template.values();
        String[] labels = new String[templates.length];
        for (int index = 0; index < templates.length; index++) {
            labels[index] = templates[index].label;
        }
        int[] selected = {0};
        new AlertDialog.Builder(this)
                .setTitle("Start a creator canvas")
                .setSingleChoiceItems(labels, 0, (dialog, which) -> selected[0] = which)
                .setMessage("The current design is autosaved before the new canvas opens.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", (dialog, which) -> {
                    saveDocument(false);
                    DesignDocument replacement = TemplateFactory.create(
                            templates[selected[0]]
                    );
                    history.clear();
                    canvasView.setDocument(replacement);
                    selectTool(Tool.SELECT);
                    updateDocumentChrome();
                    updateHistoryButtons();
                    scheduleAutosave();
                })
                .show();
    }

    private void showShapeDialog() {
        ShapeKind[] shapes = ShapeKind.values();
        String[] labels = new String[shapes.length];
        for (int index = 0; index < shapes.length; index++) {
            labels[index] = shapes[index].label;
        }
        new AlertDialog.Builder(this)
                .setTitle("Choose spatial shape")
                .setSingleChoiceItems(
                        labels,
                        canvasView.getActiveShape().ordinal(),
                        (dialog, which) -> {
                            canvasView.setActiveShape(shapes[which]);
                            selectTool(Tool.BLOCK);
                            dialog.dismiss();
                            toast(shapes[which].label + " ready • pinch and drag");
                        }
                )
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showInkModeDialog() {
        InkMode[] modes = InkMode.values();
        String[] labels = {
                "Auto — clean shapes first, otherwise recognize text",
                "Shape — keep only confident geometric cleanup",
                "Text — recognize letters and continuous words"
        };
        new AlertDialog.Builder(this)
                .setTitle("Smart Ink mode")
                .setSingleChoiceItems(
                        labels,
                        canvasView.getInkMode().ordinal(),
                        (dialog, which) -> {
                            canvasView.setInkMode(modes[which]);
                            selectTool(Tool.SMART_INK);
                            dialog.dismiss();
                            toast(modes[which].label + " Smart Ink ready");
                        }
                )
                .setMessage(
                        "Pinch, draw one shape/letter/word, then release. "
                                + "The original ink stays unchanged if recognition is uncertain."
                )
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRenameDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(canvasView.getDocument().name);
        input.setSelectAllOnFocus(true);
        int padding = dp(20);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(padding, 0, padding, 0);
        container.addView(input, matchParentWidthWrap());
        new AlertDialog.Builder(this)
                .setTitle("Rename design")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Rename", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty()) {
                        canvasView.getDocument().name = value.substring(
                                0,
                                Math.min(value.length(), 160)
                        );
                        canvasView.getDocument().touch();
                        updateDocumentChrome();
                        scheduleAutosave();
                    }
                })
                .show();
    }

    private void loadProjectLibrary() {
        ioExecutor.execute(() -> {
            List<ProjectStore.ProjectInfo> projects = projectStore.list();
            runOnUiThread(() -> showProjectLibrary(projects));
        });
    }

    private void showProjectLibrary(List<ProjectStore.ProjectInfo> projects) {
        if (projects.isEmpty()) {
            toast("No manually saved designs yet");
            return;
        }
        DateFormat format = DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT
        );
        String[] labels = new String[projects.size()];
        for (int index = 0; index < projects.size(); index++) {
            ProjectStore.ProjectInfo info = projects.get(index);
            labels[index] = info.name()
                    + "\n"
                    + info.template()
                    + " • "
                    + info.elementCount()
                    + " objects • "
                    + format.format(new Date(info.updatedAt()));
        }
        int[] selected = {0};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("My designs")
                .setSingleChoiceItems(labels, 0, (target, which) -> selected[0] = which)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Delete", null)
                .setPositiveButton("Open", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                ProjectStore.ProjectInfo info = projects.get(selected[0]);
                dialog.dismiss();
                openProject(info.id());
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                ProjectStore.ProjectInfo info = projects.get(selected[0]);
                new AlertDialog.Builder(this)
                        .setTitle("Delete " + info.name() + "?")
                        .setMessage("This removes the manually saved copy. The current autosave is unaffected.")
                        .setNegativeButton("Keep", null)
                        .setPositiveButton("Delete", (confirm, which) -> {
                            ioExecutor.execute(() -> {
                                boolean deleted = projectStore.delete(info.id());
                                runOnUiThread(() -> toast(
                                        deleted ? "Design deleted" : "Could not delete design"
                                ));
                            });
                            dialog.dismiss();
                        })
                        .show();
            });
        });
        dialog.show();
    }

    private void openProject(String id) {
        ioExecutor.execute(() -> {
            try {
                DesignDocument loaded = projectStore.load(id);
                runOnUiThread(() -> {
                    history.clear();
                    canvasView.setDocument(loaded);
                    selectTool(Tool.SELECT);
                    updateDocumentChrome();
                    updateHistoryButtons();
                    scheduleAutosave();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> toast("Could not open this design"));
            }
        });
    }

    private void saveDocument(boolean announce) {
        DesignDocument snapshot = canvasView.getDocument().copy();
        ioExecutor.execute(() -> {
            try {
                projectStore.save(snapshot);
                if (announce) {
                    runOnUiThread(() -> toast("Design saved"));
                }
            } catch (IOException exception) {
                runOnUiThread(() -> toast("Save failed: " + safeMessage(exception)));
            }
        });
    }

    private void scheduleAutosave() {
        if (pendingAutosave != null) {
            mainHandler.removeCallbacks(pendingAutosave);
        }
        pendingAutosave = this::enqueueAutosaveSnapshot;
        mainHandler.postDelayed(pendingAutosave, 650L);
    }

    private void enqueueAutosaveSnapshot() {
        pendingAutosave = null;
        DesignDocument snapshot = canvasView.getDocument().copy();
        ioExecutor.execute(() -> {
            try {
                projectStore.autosave(snapshot);
            } catch (IOException exception) {
                runOnUiThread(() -> poseChip.setText("Autosave unavailable"));
            }
        });
    }

    @Nullable
    private DesignDocument restoreAutosave() {
        try {
            return projectStore.loadAutosave();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void showExportDialog() {
        ExportManager.Format[] formats = ExportManager.Format.values();
        String[] labels = new String[formats.length];
        for (int index = 0; index < formats.length; index++) {
            labels[index] = formats[index].label;
        }
        new AlertDialog.Builder(this)
                .setTitle("Export design")
                .setItems(labels, (dialog, which) -> {
                    ExportManager.Format format = formats[which];
                    String name = ExportManager.safeBaseName(canvasView.getDocument().name)
                            + "."
                            + format.extension;
                    switch (format) {
                        case PNG -> pngExportLauncher.launch(name);
                        case PORTFOLIO_PNG -> portfolioExportLauncher.launch(
                                ExportManager.safeBaseName(canvasView.getDocument().name)
                                        + "-portfolio.png"
                        );
                        case PDF -> pdfExportLauncher.launch(name);
                        case SVG -> svgExportLauncher.launch(name);
                        case JSON -> jsonExportLauncher.launch(name);
                    }
                })
                .show();
    }

    private void completeExport(ExportManager.Format format, @Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        DesignDocument snapshot = canvasView.getDocument().copy();
        toast("Preparing " + format.label + "…");
        ioExecutor.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                if (output == null) {
                    throw new IOException("Destination is unavailable");
                }
                ExportManager.write(format, output, snapshot);
                runOnUiThread(() -> toast(format.label + " exported"));
            } catch (Exception exception) {
                runOnUiThread(() -> toast("Export failed: " + safeMessage(exception)));
            } catch (OutOfMemoryError error) {
                runOnUiThread(() -> toast(
                        "Export needs more free memory; close other apps and retry"
                ));
            }
        });
    }

    private void importProject(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        ioExecutor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) {
                    throw new IOException("Selected file is unavailable");
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int total = 0;
                while (true) {
                    int read = input.read(buffer);
                    if (read < 0) {
                        break;
                    }
                    total += read;
                    if (total > 20 * 1024 * 1024) {
                        throw new IOException("Project is larger than 20 MB");
                    }
                    bytes.write(buffer, 0, read);
                }
                DesignDocument imported = DocumentCodec.decode(
                        new String(bytes.toByteArray(), StandardCharsets.UTF_8)
                );
                runOnUiThread(() -> {
                    history.clear();
                    canvasView.setDocument(imported);
                    selectTool(Tool.SELECT);
                    updateDocumentChrome();
                    updateHistoryButtons();
                    scheduleAutosave();
                    toast("Editable scene imported");
                });
            } catch (Exception exception) {
                runOnUiThread(() -> toast(
                        "Import failed: " + safeMessage(exception)
                ));
            }
        });
    }

    private void showSettings() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), 0, dp(22), 0);

        TextView sensitivityLabel = settingsLabel("Pinch sensitivity");
        SeekBar sensitivity = new SeekBar(this);
        sensitivity.setMax(100);
        sensitivity.setProgress(Math.round(appSettings.gestureSensitivity() * 100f));

        TextView smoothingLabel = settingsLabel("Cursor smoothing");
        SeekBar smoothing = new SeekBar(this);
        smoothing.setMax(100);
        smoothing.setProgress(Math.round(appSettings.cursorSmoothing() * 100f));

        TextView performanceLabel = settingsLabel("Performance profile");
        RadioGroup performance = new RadioGroup(this);
        int selectedProfileId = View.generateViewId();
        for (AppSettings.PerformanceProfile profile : AppSettings.PerformanceProfile.values()) {
            RadioButton radio = new RadioButton(this);
            radio.setId(profile == appSettings.performanceProfile()
                    ? selectedProfileId
                    : View.generateViewId()
            );
            radio.setTag(profile);
            radio.setText(profile.label + "  •  "
                    + Math.round(1000f / profile.frameIntervalMs) + " FPS target");
            radio.setTextColor(UiKit.TEXT);
            performance.addView(radio);
        }
        performance.check(selectedProfileId);

        SwitchCompat landmarks = new SwitchCompat(this);
        landmarks.setText("Show hand landmarks");
        landmarks.setTextColor(UiKit.TEXT);
        landmarks.setChecked(appSettings.showLandmarks());
        SwitchCompat grid = new SwitchCompat(this);
        grid.setText("Show canvas grid");
        grid.setTextColor(UiKit.TEXT);
        grid.setChecked(appSettings.showGrid());
        SwitchCompat snap = new SwitchCompat(this);
        snap.setText("Magnetic alignment and connector anchors");
        snap.setTextColor(UiKit.TEXT);
        snap.setChecked(appSettings.smartSnap());
        SwitchCompat overlay = new SwitchCompat(this);
        overlay.setText("Full-screen camera overlay workspace");
        overlay.setTextColor(UiKit.TEXT);
        overlay.setChecked(canvasView.isSpatialOverlay());
        SwitchCompat landscape = new SwitchCompat(this);
        landscape.setText("Landscape-first workspace (recommended)");
        landscape.setTextColor(UiKit.TEXT);
        landscape.setChecked(appSettings.landscapeFirst());
        Button resetLearning = UiKit.button(this, "Reset adaptive gesture learning", false);
        resetLearning.setOnClickListener(view -> {
            appSettings.resetAdaptiveGestureProfile();
            gestureEngine.setAdaptiveProfile(appSettings.adaptiveGestureSnapshot());
            lastAdaptiveRevision = 0L;
            toast("Adaptive gesture profile reset");
        });

        content.addView(sensitivityLabel);
        content.addView(sensitivity);
        content.addView(smoothingLabel);
        content.addView(smoothing);
        content.addView(performanceLabel);
        content.addView(performance);
        content.addView(landmarks);
        content.addView(grid);
        content.addView(snap);
        content.addView(overlay);
        content.addView(landscape);
        content.addView(resetLearning);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Tracking & canvas settings")
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Calibrate", (target, which) -> startCalibration())
                .setPositiveButton("Apply", (target, which) -> {
                    appSettings.setGestureSensitivity(sensitivity.getProgress() / 100f);
                    appSettings.setCursorSmoothing(smoothing.getProgress() / 100f);
                    int checked = performance.getCheckedRadioButtonId();
                    View selectedView = performance.findViewById(checked);
                    if (selectedView != null
                            && selectedView.getTag() instanceof AppSettings.PerformanceProfile profile) {
                        appSettings.setPerformanceProfile(profile);
                    }
                    appSettings.setShowLandmarks(landmarks.isChecked());
                    appSettings.setShowGrid(grid.isChecked());
                    appSettings.setSmartSnap(snap.isChecked());
                    boolean orientationChanged = appSettings.landscapeFirst()
                            != landscape.isChecked();
                    appSettings.setLandscapeFirst(landscape.isChecked());
                    gestureEngine.configure(
                            appSettings.gestureSensitivity(),
                            appSettings.cursorSmoothing()
                    );
                    handOverlayView.setShowLandmarks(appSettings.showLandmarks());
                    canvasView.setShowGrid(appSettings.showGrid());
                    canvasView.setSmartSnap(appSettings.smartSnap());
                    if (canvasView.isSpatialOverlay() != overlay.isChecked()) {
                        canvasView.setSpatialOverlay(overlay.isChecked());
                    }
                    if (orientationChanged) {
                        setRequestedOrientation(landscape.isChecked()
                                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    }
                    updateResponsiveChrome();
                    updateDocumentChrome();
                })
                .create();
        dialog.show();
    }

    private TextView settingsLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(UiKit.TEXT);
        label.setTextSize(14f);
        label.setPadding(0, dp(12), 0, dp(2));
        return label;
    }

    private void startCalibration() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ensureCameraAccess(true);
            toast("Enable the camera, then start calibration again");
            return;
        }
        modePalette.close();
        shapePalette.close();
        canvasView.cancelActiveInteraction();
        gestureEngine.reset();
        calibrationStep = 0;
        calibrationStableFrames = 0;
        calibrationPinchSum = 0f;
        calibrationPinchSamples = 0;
        calibrationProgress.setProgress(0);
        calibrationTitle.setText("Step 1 of 3 — Open palm");
        calibrationBody.setText(
                "Hold one open palm comfortably inside the camera frame."
        );
        calibrationCard.setVisibility(View.VISIBLE);
        selectionScroll.setVisibility(View.GONE);
    }

    private void processCalibration(GestureFrame frame) {
        if (calibrationStep < 0 || calibrationStep > 2 || frame.hands.isEmpty()) {
            return;
        }
        String expected = switch (calibrationStep) {
            case 0 -> GestureEngine.Pose.OPEN_PALM.label;
            case 1 -> GestureEngine.Pose.PINCH.label;
            default -> GestureEngine.Pose.FIST.label;
        };
        if (expected.equals(frame.pose)) {
            calibrationStableFrames++;
            if (calibrationStep == 1) {
                float ratio = GestureEngine.pinchRatio(frame.hands.get(0));
                if (Float.isFinite(ratio)) {
                    calibrationPinchSum += ratio;
                    calibrationPinchSamples++;
                }
            }
        } else {
            calibrationStableFrames = Math.max(0, calibrationStableFrames - 1);
        }
        calibrationProgress.setProgress(calibrationStep * 8 + calibrationStableFrames);
        if (calibrationStableFrames < 8) {
            return;
        }
        calibrationStableFrames = 0;
        calibrationStep++;
        if (calibrationStep == 1) {
            calibrationTitle.setText("Step 2 of 3 — Pinch");
            calibrationBody.setText(
                    "Touch thumb and index fingertips together, then hold briefly."
            );
        } else if (calibrationStep == 2) {
            calibrationTitle.setText("Step 3 of 3 — Fist");
            calibrationBody.setText(
                    "Close your hand into a relaxed fist and hold it steady."
            );
        } else {
            finishCalibration();
        }
    }

    private void finishCalibration() {
        if (calibrationPinchSamples > 0) {
            float observed = calibrationPinchSum / calibrationPinchSamples;
            float targetThreshold = clamp(observed + 0.10f, 0.32f, 0.48f);
            float calibrated = clamp(
                    (targetThreshold - 0.38f) / 0.16f + 0.5f,
                    0f,
                    1f
            );
            appSettings.setGestureSensitivity(calibrated);
        }
        gestureEngine.configure(
                appSettings.gestureSensitivity(),
                appSettings.cursorSmoothing()
        );
        calibrationTitle.setText("Calibration complete");
        calibrationBody.setText(
                "Pinch, palm and fist were recognized. Your sensitivity has been saved."
        );
        calibrationProgress.setProgress(24);
        feedback();
        mainHandler.postDelayed(() -> stopCalibration(true), 1100L);
    }

    private void stopCalibration(boolean completed) {
        calibrationStep = -1;
        gestureEngine.reset();
        performanceTelemetry.reset();
        pendingHandFrame.set(null);
        calibrationCard.setVisibility(View.GONE);
        if (canvasView.selectionCount() > 0) {
            selectionScroll.setVisibility(View.VISIBLE);
        }
        if (!completed) {
            toast("Calibration cancelled");
        }
    }

    private void showGestureGuide() {
        new AlertDialog.Builder(this)
                .setTitle("Air gesture guide")
                .setMessage(
                        "Pinch — create, draw, or drag with the active tool\n\n"
                                + "Open-palm dwell — select the object under your palm\n\n"
                                + "Fist + move — move the selection; with nothing selected, pan the canvas\n\n"
                                + "Palm swipe left / right — undo / redo\n\n"
                                + "Palm swipe up / down — cycle shape, Smart Ink mode, or transform axis\n\n"
                                + "Hold a V sign — open the tool command palette; hover and pinch to choose\n\n"
                                + "Thumbs up — open every shape in one palette; hover, pinch, then release\n\n"
                                + "Thumb + sideways index pose — auto-spin selected 3D shapes like a planet\n\n"
                                + "Pinch with both hands — resize; twist both hands to rotate on Z\n\n"
                                + "True 3D mode + pinch drag — rotate real solids through 360° on X/Y/Z\n\n"
                                + "Smart Ink — rough shape, A, or continuous word becomes clean on release\n\n"
                                + "Touch controls always remain available. Use Calibrate if your hand size "
                                + "or camera distance changes."
                )
                .setPositiveButton("Got it", null)
                .show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("AirCanvas OS — Codename")
                .setMessage(
                        "Version 2.3.0 • Pinch Rebuild\n\n"
                                + "A full-screen gesture-driven spatial workspace with transparent "
                                + "camera overlay, magnetic objects and real 3D primitives.\n\n"
                                + "Privacy: camera frames and handwriting recognition stay on-device "
                                + "and are never uploaded. Internet is used only for the optional "
                                + "one-time handwriting language-model download.\n\n"
                                + "Developer: Mohnish Raj\n"
                                + "Built with Android CameraX and MediaPipe Hand Landmarker."
                )
                .setPositiveButton("Close", null)
                .show();
    }

    private void showOnboarding() {
        String[] titles = {
                "The whole screen is your workspace",
                "Create and transform in the air",
                "Professional creator controls",
                "Turn work into a portfolio asset"
        };
        String[] bodies = {
                "The live camera is the visual background and the transparent spatial plane "
                        + "covers the complete screen. Touch editing always remains available.",
                "Build shapes, connectors, notes, Smart Ink and real 3D solids. Move with a "
                        + "fist, resize with two pinches, twist on Z, or rotate on X/Y/Z.",
                "Style Studio, magnetic guides, align and distribute, grouping, locking, Focus "
                        + "mode and live engine health turn rough ideas into polished scenes.",
                "Open Portfolio Showcase from the menu, present it full-screen, then export a "
                        + "share-ready 16:9 poster with your project and developer credit."
        };

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(22), dp(24), dp(18));
        content.setBackground(UiKit.background(
                this,
                0xFF101D31,
                24f,
                0x705EE7F7
        ));
        TextView eyebrow = UiKit.chip(this, "AIR CANVAS / PORTFOLIO EDITION");
        TextView title = UiKit.title(this, titles[0]);
        title.setTextSize(23f);
        title.setPadding(0, dp(20), 0, dp(10));
        TextView body = new TextView(this);
        body.setText(bodies[0]);
        body.setTextColor(UiKit.MUTED);
        body.setTextSize(16f);
        body.setLineSpacing(0f, 1.15f);
        TextView step = new TextView(this);
        step.setText("1 / 4");
        step.setTextColor(UiKit.SECONDARY);
        step.setPadding(0, dp(18), 0, dp(8));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        Button back = UiKit.button(this, "Back", false);
        back.setVisibility(View.INVISIBLE);
        Button next = UiKit.button(this, "Next", false);
        actions.addView(back);
        actions.addView(next);
        content.addView(eyebrow);
        content.addView(title);
        content.addView(body);
        content.addView(step);
        content.addView(actions);

        ScrollView onboardingScroll = new ScrollView(this);
        onboardingScroll.setFillViewport(true);
        onboardingScroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(onboardingScroll)
                .create();
        dialog.setCancelable(false);
        int[] page = {0};
        Runnable render = () -> {
            title.setText(titles[page[0]]);
            body.setText(bodies[page[0]]);
            step.setText((page[0] + 1) + " / " + titles.length);
            back.setVisibility(page[0] == 0 ? View.INVISIBLE : View.VISIBLE);
            next.setText(page[0] == titles.length - 1 ? "Start creating" : "Next");
        };
        back.setOnClickListener(view -> {
            if (page[0] > 0) {
                page[0]--;
                render.run();
            }
        });
        next.setOnClickListener(view -> {
            if (page[0] < titles.length - 1) {
                page[0]++;
                render.run();
            } else {
                appSettings.setOnboardingComplete(true);
                dialog.dismiss();
                ensureCameraAccess(true);
            }
        });
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
        });
        dialog.show();
    }

    @Override
    public void onMutationStart() {
        history.checkpoint(canvasView.getDocument());
        updateHistoryButtons();
    }

    @Override
    public void onDocumentChanged() {
        updateDocumentChrome();
        updateHistoryButtons();
        scheduleAutosave();
    }

    @Override
    public void onSelectionChanged(int count) {
        if (selectionScroll == null
                || calibrationStep >= 0
                || focusMode
                || canvasView.isPresentationMode()) {
            return;
        }
        selectionScroll.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        selectionLabel.setText(count + (count == 1 ? " selected" : " selected"));
        updateResponsiveChrome();
    }

    @Override
    public void onRequestText(
            float worldX,
            float worldY,
            @Nullable CanvasElement existing
    ) {
        EditText input = new EditText(this);
        input.setMinLines(2);
        input.setMaxLines(5);
        input.setHint("Label or note");
        if (existing != null) {
            input.setText(existing.text);
            input.setSelection(input.getText().length());
        }
        FrameLayout container = new FrameLayout(this);
        container.setPadding(dp(20), 0, dp(20), 0);
        container.addView(input, matchParentWidthWrap());
        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add text" : "Edit text")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(existing == null ? "Add" : "Update", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (existing == null) {
                        canvasView.insertText(worldX, worldY, value);
                    } else {
                        canvasView.editSelectedText(value);
                    }
                })
                .show();
    }

    @Override
    public void onSmartInkRequest(SmartInkRequest request) {
        if (request == null || request.points.size() < 4) {
            return;
        }
        if (!pendingSmartInkBatch.isEmpty()) {
            SmartInkRequest previous = pendingSmartInkBatch.get(
                    pendingSmartInkBatch.size() - 1
            );
            if (!canBatchInk(previous, request)) {
                flushSmartInkBatch();
            }
        }
        pendingSmartInkBatch.add(request);
        if (pendingSmartInkFlush != null) {
            mainHandler.removeCallbacks(pendingSmartInkFlush);
        }
        pendingSmartInkFlush = this::flushSmartInkBatch;
        mainHandler.postDelayed(pendingSmartInkFlush, SMART_INK_BATCH_DELAY_MS);
        poseChip.setText(pendingSmartInkBatch.size() > 1
                ? "Auto Text • keep writing…"
                : "Auto Text • add the next stroke…");
    }

    private boolean canBatchInk(SmartInkRequest previous, SmartInkRequest next) {
        long gap = Math.max(0L, next.completedAtMs - previous.completedAtMs);
        if (gap > 1100L) {
            return false;
        }
        float verticalDistance = Math.abs(previous.bounds.centerY() - next.bounds.centerY());
        float maxHeight = Math.max(previous.bounds.height(), next.bounds.height());
        float horizontalGap = next.bounds.left - previous.bounds.right;
        return verticalDistance <= Math.max(180f, maxHeight * 1.35f)
                && horizontalGap <= Math.max(320f, maxHeight * 2.4f)
                && horizontalGap >= -Math.max(220f, maxHeight * 1.5f);
    }

    private void flushSmartInkBatch() {
        if (pendingSmartInkFlush != null) {
            mainHandler.removeCallbacks(pendingSmartInkFlush);
            pendingSmartInkFlush = null;
        }
        if (pendingSmartInkBatch.isEmpty()) {
            return;
        }
        SmartInkRequest request = SmartInkRequest.merge(
                new ArrayList<>(pendingSmartInkBatch)
        );
        pendingSmartInkBatch.clear();
        OnDeviceInkRecognizer recognizer = ensureSmartInkRecognizer();
        if (recognizer == null) {
            poseChip.setText("Original ink kept");
            return;
        }
        poseChip.setText(request.strokeCount() > 1
                ? "Recognizing " + request.strokeCount() + " strokes…"
                : "Cleaning handwriting…");
        recognizer.recognize(
                request,
                smartInkPreContext,
                smartInkCallback()
        );
    }

    @Nullable
    private OnDeviceInkRecognizer ensureSmartInkRecognizer() {
        if (smartInkRecognizer != null) {
            return smartInkRecognizer;
        }
        try {
            smartInkRecognizer = new OnDeviceInkRecognizer();
            return smartInkRecognizer;
        } catch (RuntimeException exception) {
            poseChip.setText("Smart Ink unavailable");
            toast("Could not start Smart Ink: " + safeMessage(exception));
            return null;
        }
    }

    private OnDeviceInkRecognizer.Callback smartInkCallback() {
        return new OnDeviceInkRecognizer.Callback() {
            @Override
            public void onStatus(String status) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        poseChip.setText(status);
                    }
                });
            }

            @Override
            public void onRecognized(SmartInkRequest request, String text) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (canvasView.applySmartInkText(request, text)) {
                        smartInkPreContext = (smartInkPreContext + " " + text).trim();
                        if (smartInkPreContext.length() > 20) {
                            smartInkPreContext = smartInkPreContext.substring(
                                    smartInkPreContext.length() - 20
                            );
                        }
                        poseChip.setText("Clean text • " + text);
                        feedback();
                    }
                });
            }

            @Override
            public void onFailure(SmartInkRequest request, String message) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        poseChip.setText("Original ink kept");
                        toast(message);
                    }
                });
            }
        };
    }

    @Override
    public void onUndoGesture() {
        undo();
    }

    @Override
    public void onRedoGesture() {
        redo();
    }

    @Override
    public void onUserFeedback() {
        feedback();
    }

    @Override
    public void onCanvasMessage(String message) {
        toast(message);
    }

    @Override
    public void onReady() {
        runOnUiThread(() -> poseChip.setText("Show one hand"));
    }

    @Override
    public void onHands(
            List<List<LandmarkPoint>> hands,
            int inputWidth,
            int inputHeight,
            long inferenceMs
    ) {
        long frameAt = android.os.SystemClock.uptimeMillis();
        GestureFrame frame = gestureEngine.process(hands, frameAt);
        PerformanceTelemetry.Snapshot telemetry = performanceTelemetry.record(
                frameAt,
                inferenceMs,
                hands.size()
        );
        pendingHandFrame.set(new PendingHandFrame(
                frame,
                inputWidth,
                inputHeight,
                inferenceMs,
                telemetry
        ));
        if (handUiPosted.compareAndSet(false, true)) {
            mainHandler.post(this::drainPendingHandFrame);
        }
    }

    private void drainPendingHandFrame() {
        PendingHandFrame pending = pendingHandFrame.getAndSet(null);
        if (pending != null && !isFinishing() && !isDestroyed()) {
            applyHandFrame(pending);
        }
        handUiPosted.set(false);
        if (pendingHandFrame.get() != null && handUiPosted.compareAndSet(false, true)) {
            mainHandler.post(this::drainPendingHandFrame);
        }
    }

    private void applyHandFrame(PendingHandFrame pending) {
        GestureFrame frame = pending.frame();
        handOverlayView.setHands(frame.hands, pending.inputWidth(), pending.inputHeight());
        canvasView.setInputDimensions(pending.inputWidth(), pending.inputHeight());
        poseChip.setText(frame.pose);
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastMetricUpdateAt >= 300L) {
            lastMetricUpdateAt = now;
            metricChip.setText(
                    appSettings.performanceProfile().label
                            + " • "
                            + pending.telemetry().compactLabel()
            );
        }
        persistAdaptiveGestureProfile(now);
        if (!hasWindowFocus()) {
            canvasView.cancelActiveInteraction();
            return;
        }
        if (calibrationStep >= 0) {
            processCalibration(frame);
            return;
        }
        for (GestureEvent event : frame.events) {
            routeGestureEvent(event);
        }
    }

    private void persistAdaptiveGestureProfile(long now) {
        AdaptiveGestureProfile.Snapshot snapshot = gestureEngine.adaptiveSnapshot();
        if (snapshot.revision() == lastAdaptiveRevision
                || now - lastAdaptivePersistAt < 1800L) {
            return;
        }
        lastAdaptiveRevision = snapshot.revision();
        lastAdaptivePersistAt = now;
        appSettings.setAdaptiveGestureSnapshot(snapshot);
    }

    private void routeGestureEvent(GestureEvent event) {
        if (shapePalette.isOpen()) {
            switch (event.type) {
                case CURSOR -> shapePalette.updateCursor(
                        canvasView.gestureScreenX(event.x),
                        canvasView.gestureScreenY(event.y)
                );
                case PINCH_START -> {
                    // Consume the full pinch cycle even when the cursor settles
                    // on the card a frame later. PaletteSelectionGate late-arms
                    // the hovered card and still commits only on release.
                    shapePalette.beginPinchSelection();
                    palettePinchConsumed = true;
                }
                case PINCH_END -> {
                    shapePalette.commitPinchSelection();
                    palettePinchConsumed = false;
                }
                case HAND_LOST -> {
                    shapePalette.cancelPinchSelection();
                    shapePalette.close();
                    palettePinchConsumed = false;
                }
                default -> {
                    // Editing remains paused while the shape palette is open.
                }
            }
            return;
        }
        if (modePalette.isOpen()) {
            switch (event.type) {
                case CURSOR -> modePalette.updateCursor(
                        canvasView.gestureScreenX(event.x),
                        canvasView.gestureScreenY(event.y)
                );
                case PINCH_START -> {
                    modePalette.beginPinchSelection();
                    palettePinchConsumed = true;
                }
                case PINCH_END -> {
                    modePalette.commitPinchSelection();
                    palettePinchConsumed = false;
                }
                case HAND_LOST -> {
                    modePalette.cancelPinchSelection();
                    modePalette.close();
                    palettePinchConsumed = false;
                }
                default -> {
                    // Editing is intentionally paused while the command palette is open.
                }
            }
            return;
        }
        if (palettePinchConsumed) {
            if (event.type == GestureEvent.Type.PINCH_END
                    || event.type == GestureEvent.Type.HAND_LOST) {
                palettePinchConsumed = false;
            }
            return;
        }
        switch (event.type) {
            case MODE_MENU -> {
                canvasView.cancelActiveInteraction();
                shapePalette.close();
                modePalette.open(canvasView.getTool());
                feedback();
            }
            case SHAPE_MENU -> {
                canvasView.cancelActiveInteraction();
                modePalette.close();
                shapePalette.open(canvasView.getActiveShape());
                poseChip.setText("Shape palette • hover, pinch, release");
                feedback();
            }
            case AUTO_SPIN_START -> {
                if (canvasView.startAutoSpin()) {
                    poseChip.setText("Orbit spin • hold the pose");
                    feedback();
                }
            }
            case AUTO_SPIN_END -> {
                if (canvasView.isAutoSpinActive()) {
                    canvasView.stopAutoSpin();
                    poseChip.setText("3D spin saved");
                }
            }
            case SWIPE_UP -> handleVerticalGesture(1);
            case SWIPE_DOWN -> handleVerticalGesture(-1);
            default -> canvasView.applyGesture(event);
        }
    }

    private void handleVerticalGesture(int direction) {
        if (canvasView.getTool() == Tool.BLOCK) {
            ShapeKind shape = canvasView.cycleShape(direction);
            if (shapeButton != null) {
                shapeButton.setText("Shape • " + shape.label);
            }
            poseChip.setText(shape.label + " ready");
            feedback();
        } else if (canvasView.getTool() == Tool.TRANSFORM) {
            TransformAxis axis = canvasView.cycleTransformAxis(direction);
            updateAxisButton(axis);
            poseChip.setText(axis.label + " transform");
            feedback();
        } else if (canvasView.getTool() == Tool.SMART_INK) {
            InkMode mode = canvasView.cycleInkMode(direction);
            if (smartInkButton != null) {
                smartInkButton.setText("Smart • " + mode.label);
            }
            poseChip.setText(mode.label + " Smart Ink");
            feedback();
        }
    }

    @Override
    public void onStatus(String status) {
        runOnUiThread(() -> poseChip.setText(status));
    }

    @Override
    public void onError(String message) {
        long dispatchAt = android.os.SystemClock.uptimeMillis();
        long previous = lastErrorDispatchAt.get();
        if (dispatchAt - previous < 750L
                || !lastErrorDispatchAt.compareAndSet(previous, dispatchAt)) {
            return;
        }
        runOnUiThread(() -> {
            poseChip.setText("Touch mode • tracking issue");
            metricChip.setText("Tap for settings");
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastErrorToastAt > 5000L) {
                lastErrorToastAt = now;
                toast(message);
            }
        });
    }

    private void feedback() {
        canvasView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appSettings != null
                && appSettings.onboardingComplete()
                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
                && handController == null) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        canvasView.stopAutoSpin();
        modePalette.close();
        shapePalette.close();
        flushSmartInkBatch();
        if (projectStore != null && canvasView != null) {
            if (pendingAutosave != null) {
                mainHandler.removeCallbacks(pendingAutosave);
            }
            enqueueAutosaveSnapshot();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (pendingAutosave != null) {
            mainHandler.removeCallbacks(pendingAutosave);
        }
        pendingHandFrame.set(null);
        handUiPosted.set(false);
        if (handController != null) {
            handController.close();
            handController = null;
        }
        if (pendingSmartInkFlush != null) {
            mainHandler.removeCallbacks(pendingSmartInkFlush);
            pendingSmartInkFlush = null;
        }
        if (appSettings != null) {
            appSettings.setAdaptiveGestureSnapshot(gestureEngine.adaptiveSnapshot());
        }
        if (smartInkRecognizer != null) {
            smartInkRecognizer.close();
            smartInkRecognizer = null;
        }
        ioExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (handController != null) {
            previewView.post(handController::updateTargetRotation);
        }
        gestureEngine.reset();
        performanceTelemetry.reset();
        pendingHandFrame.set(null);
        handOverlayView.clear();
        modePalette.close();
        shapePalette.close();
        canvasView.stopAutoSpin();
        canvasView.cancelActiveInteraction();
        canvasView.post(() -> {
            updateResponsiveChrome();
            canvasView.resetViewport();
        });
        ViewCompat.requestApplyInsets(root);
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : message;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    private FrameLayout.LayoutParams matchParentWidthWrap() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return UiKit.dp(this, value);
    }

    private record PendingHandFrame(
            GestureFrame frame,
            int inputWidth,
            int inputHeight,
            long inferenceMs,
            PerformanceTelemetry.Snapshot telemetry
    ) {
    }
}
