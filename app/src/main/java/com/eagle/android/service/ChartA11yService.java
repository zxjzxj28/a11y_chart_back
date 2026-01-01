package com.eagle.android.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityButtonController;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Region;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.eagle.android.a11y.ReadingOrderHelper;
import com.eagle.android.detector.ChartDetector;
import com.eagle.android.detector.DemoChartDetector;
import com.eagle.android.detector.YOLOv11Detector;
import com.eagle.android.model.ChartResult;
import com.eagle.android.model.NodeSpec;
import com.eagle.android.overlay.ChartPanelWindow;
import com.eagle.android.overlay.ChartAccessOverlayDemo;
import com.eagle.android.overlay.ChartAccessOverlayManager;
import com.eagle.android.overlay.DebugMarkOverlay;
import com.eagle.android.overlay.FocusSwitchOverlay;
import com.eagle.android.overlay.SimpleOverLay;
import com.eagle.android.overlay.SimpleVirtualNodeOverlay;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 增强点：
 * 1) 页面变动 → 去抖截屏 → detector → 若发现图表则挂载“提示虚拟结点”
 * 2) 用户滑到/激活该结点（TalkBack 双击）→ 进入图表模式（显示 ChartPanelWindow）
 * 3) 不打断原 App 遍历；不需要无障碍按钮
 */
public class ChartA11yService extends AccessibilityService {

    public static final String ACTION_SHOW_MOCK_ACCESS_OVERLAY =
            "com.eagle.android.service.action.SHOW_MOCK_ACCESS_OVERLAY";

    // ============ 新增：多指手势回调接口 ============
    public interface ChartGestureCallback {
        void onTwoFingerDoubleTap();
        void onThreeFingerDoubleTap();
        void onTwoFingerSwipe(int direction); // 0左 1右 2上 3下
        void onThreeFingerSwipe(int direction);
    }

    private ChartGestureCallback chartGestureCallback;
    private ChartPanelWindow panel;
    private AccessibilityButtonController ab;
    private AccessibilityButtonController.AccessibilityButtonCallback abCb;
    private Handler mainHandler;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private ChartDetector detector;
    private YOLOv11Detector yoloDetector; // YOLOv11检测器实例
    private ChartAccessOverlayManager demoAccessOverlayManager;

    // 是否使用YOLOv11检测器（可通过设置切换）
    private static final boolean USE_YOLO_DETECTOR = true;

    // 截屏节流
    private static final long SHOT_INTERVAL_MS = 1000;
    private long lastShotAt = 0L;

    // 自动检测去抖/最小间隔
    private static final long DETECT_DEBOUNCE_MS = 250;
    private static final long DETECT_MIN_INTERVAL_MS = 650;
    private long lastDetectAt = 0L;
    private final Runnable detectRunnable = this::detectOnce;

    private ChartResult curResult;
    // 提示虚拟结点 Overlay
//    private ChartHintOverlay hintOverlay;
//    private TestOverLay hintOverlay;

    // 最近一次检测结果（进入模式时使用）
    private Rect lastChartRect = null;
    private List<NodeSpec> lastNodes = null;
    private Bitmap lastChartBmp = null;
//    private SimpleVirtualNodeOverlay simpleVirtualNodeOverlay;
//    private SimpleOverLay simpleOverLay;
//    private DebugMarkOverlay debugMarkOverlay;
//    private AccessibilityNodeInfo prevNode, nextNode;  // 前驱/后继
//    private boolean armedFromPrev = false;             // 已在前驱上“武装就绪”
//    private long prevNodeSourceId = -1;                // 记录前驱的sourceId（辅助判断）
//    private FocusSwitchOverlay focusSwitch;
    @Override
    public void onServiceConnected() {
        super.onServiceConnected();

        mainHandler = new Handler(Looper.getMainLooper());

        // 初始化检测器
        initializeDetector();

        // 创建手势回调
        chartGestureCallback = new ChartGestureCallback() {
            @Override
            public void onTwoFingerDoubleTap() {
                // 双指双击 - 播放/暂停摘要
                mainHandler.post(() -> {
                    Toast.makeText(ChartA11yService.this, "双指双击：播放摘要", Toast.LENGTH_SHORT).show();
                    // TODO: 实现摘要播放逻辑
                });
            }

            @Override
            public void onThreeFingerDoubleTap() {
                // 三指双击 - 自动播报开关
                mainHandler.post(() -> {
                    Toast.makeText(ChartA11yService.this, "三指双击：自动播报", Toast.LENGTH_SHORT).show();
                    // TODO: 实现自动播报逻辑
                });
            }

            @Override
            public void onTwoFingerSwipe(int direction) {
                mainHandler.post(() -> {
                    String[] dirs = {"左", "右", "上", "下"};
                    Toast.makeText(ChartA11yService.this, "双指" + dirs[direction] + "滑", Toast.LENGTH_SHORT).show();

                    if (direction == 3) { // 下滑退出
                        exitChartMode();
                    }
                });
            }

            @Override
            public void onThreeFingerSwipe(int direction) {
                mainHandler.post(() -> {
                    String[] dirs = {"左", "右", "上", "下"};
                    Toast.makeText(ChartA11yService.this, "三指" + dirs[direction] + "滑", Toast.LENGTH_SHORT).show();
                    // TODO: 实现三指滑动逻辑
                });
            }
        };

        // tap模拟点击操作 - 修改为传入手势回调
        panel = new ChartPanelWindow(this, this::tap, chartGestureCallback);
        SharedPreferences sp = getSharedPreferences("a11y_prefs", MODE_PRIVATE);

// 顶层开关
        boolean volumeEnabled  = sp.getBoolean("feature_shortcut_volume_enabled", false);
        boolean voiceEnabled   = sp.getBoolean("feature_shortcut_voice_enabled", false);
        boolean gestureEnabled = sp.getBoolean("feature_shortcut_gesture_enabled", false);
        System.out.println("volumeEnabled:" +  volumeEnabled + "voiceEnabled:" + voiceEnabled + "gestureEnabled:" +  gestureEnabled);
// 其他功能
        boolean chartVoiceQA   = sp.getBoolean("feature_chart_voice_qa_enabled", false);
        boolean sortByData     = sp.getBoolean("feature_sort_by_data_enabled", false);
        System.out.println("chartVoiceQA:" +  chartVoiceQA + "sortByData:" + sortByData );

// 具体参数
        volPattern = sp.getString("volume_combo_pattern", "BOTH");
        volWindowMs   = Integer.parseInt(sp.getString("volume_combo_window_ms", "500"));
        System.out.println("volPattern:" +  volPattern + "volWindowMs:" + volWindowMs );

        String gestureClose = sp.getString("gesture_close_action", "SWIPE_DOWN_LEFT");
        String gestureRepeat = sp.getString("gesture_repeat_action", "ONE_FINGER_DOUBLE_TAP");
        String gestureAuto = sp.getString("gesture_auto_broadcast_action", "THREE_FINGER_SWIPE");
        String prevCommand = sp.getString("voice_command_prev_focus", "上一个");
        String nextCommand = sp.getString("voice_command_next_focus", "下一个");
        String repeatCommand = sp.getString("voice_command_repeat", "重复朗读");
        String summaryCommand = sp.getString("voice_command_summary", "播放摘要");
        String autoCommand = sp.getString("voice_command_auto", "自动播报");
        String exitCommand = sp.getString("voice_command_exit", "退出");
        System.out.println("gestureClose:" + gestureClose + " gestureRepeat:" + gestureRepeat +
                " gestureAuto:" + gestureAuto);
        System.out.println("prevCommand:" + prevCommand + " nextCommand:" + nextCommand +
                " repeatCommand:" + repeatCommand + " summaryCommand:" + summaryCommand +
                " autoCommand:" + autoCommand + " exitCommand:" + exitCommand);
        AccessibilityServiceInfo info = getServiceInfo();
        info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);
//        debugMarkOverlay = new com.eagle.android.overlay.DebugMarkOverlay(this);
//        debugMarkOverlay.setEnabled(true);
//        hintOverlay = new ChartHintOverlay(this, this::enterChartMode);
//        hintOverlay = new TestOverLay(this, new TestOverLay.EnterCallback() {
//            @Override
//            public void onEnterChartMode() {
//                System.out.println("虚拟节点被点击 - 进入图表模式");
//                enterChartMode();
//            }
//
//            @Override
//            public void onFocusChanged(boolean hasFocus) {
//                System.out.println( "服务收到焦点变化: " + (hasFocus ? "获得焦点" : "失去焦点"));
//                // 可以在这里添加额外的焦点处理逻辑
//                if (hasFocus) {
//                    Toast.makeText(ChartA11yService.this, "虚拟节点获得焦点", Toast.LENGTH_SHORT).show();
//                }
//            }
//        });
//        simpleVirtualNodeOverlay = new com.eagle.android.overlay.SimpleVirtualNodeOverlay(
//                this,
//                () -> {
//                    // 点击回调（TalkBack 双击或普通点击）
//                    android.widget.Toast.makeText(this, "虚拟结点被点击！", android.widget.Toast.LENGTH_SHORT).show();
//                    // 这里你也可以直接调用 enterChartMode();
//                }
//        );
//        simpleVirtualNodeOverlay.show();
//        simpleVirtualNodeOverlay.setOverlayFocusable(false);

//        simpleVirtualNodeOverlay.setOverlayFocusable(true);
//        simpleOverLay = new SimpleOverLay(
//                this,
//                new com.eagle.android.overlay.SimpleOverLay.Callbacks() {
//                    @Override public void onChartClicked() {
//                        // 可选：用户双击“图表（入口）”时进入你自己的图表模式
//                        // enterChartMode();
//                    }
//                    @Override public void onExitFocusReached() {
//                        // 用户在图表层里右划 → 该把焦点交给后继 & 降级
//                        jumpToNextAndDemote();
//                    }
//                    @Override public void onExitClicked() {
//                        jumpToNextAndDemote();
//                    }
//                }
//        );
//        simpleOverLay.show();
//        simpleOverLay.setNodesExposed(true);
//        simpleOverLay.setOverlayFocusable(false);
//        showFixedVirtualNode();

        // （可选）保留系统无障碍按钮入口
        ab = getAccessibilityButtonController();
        if (ab != null) {
            abCb = new AccessibilityButtonController.AccessibilityButtonCallback() {
                @Override public void onClicked(AccessibilityButtonController controller) { togglePanel(); }
            };
            ab.registerAccessibilityButtonCallback(abCb);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_SHOW_MOCK_ACCESS_OVERLAY.equals(intent.getAction())) {
            showMockAccessOverlay();
        }
        return super.onStartCommand(intent, flags, startId);
    }
    private String volPattern = "BOTH";
    private int volWindowMs = 1000;
    private int longPressMs = 500;
    private static final long TOGGLE_COOLDOWN_MS = 800;
    private static class KeyHit {
        final String token; // "UP" or "DOWN"
        final long t;
        KeyHit(String token, long t) { this.token = token; this.t = t; }
    }

    private final java.util.ArrayDeque<KeyHit> volHits = new java.util.ArrayDeque<>();
    private final Handler volHandler = new Handler(Looper.getMainLooper());
    private boolean upDownPressed = false, downDownPressed = false;
    private boolean upLongFired = false, downLongFired = false;
    private long lastToggleAt = 0L;
    private boolean consumeOnTrigger = false;   // 可做成偏好项：匹配时是否“吃掉”按键
    @Override
    public boolean onKeyEvent(KeyEvent event) {
//        System.out.println("进入音量");
        if (!getSharedPreferences("a11y_prefs", MODE_PRIVATE)
                .getBoolean("feature_shortcut_volume_enabled", false)) {
            System.out.println("设置为false");
            return false;
        }
        volPattern = getSharedPreferences("a11y_prefs", MODE_PRIVATE)
                .getString("volume_combo_pattern", "BOTH");
        try {
            volWindowMs = Integer.parseInt(
                    getSharedPreferences("a11y_prefs", MODE_PRIVATE)
                            .getString("volume_combo_window_ms", String.valueOf(volWindowMs))
            );
        } catch (NumberFormatException e) {
            volWindowMs = 500;
        }

//        if (event.getAction() != KeyEvent.ACTION_DOWN) {
//            return false;
//        }
        final int code = event.getKeyCode();
        final int action = event.getAction();
        final long now = event.getEventTime();
        System.out.println("repeat:" + event.getRepeatCount());
        if (action == KeyEvent.ACTION_DOWN) {
            if (code == KeyEvent.KEYCODE_VOLUME_UP) {
                if (!upDownPressed) { // 首次按下
                    upDownPressed = true; upLongFired = false;
                    scheduleLong("UP");
//                    System.out.println("首次按下up");
                } else if (event.getRepeatCount() > 0 && !upLongFired) {
                    // 部分 ROM 会在长按时产生 repeat
                    if ("LONG".equals(volPattern)) {
                        triggerIfMatch("LONG");
                        upLongFired = true;
                    }
                }
            } else { // VOLUME_DOWN
                if (!downDownPressed) {
                    downDownPressed = true; downLongFired = false;
//                    System.out.println("首次按下down");
                    scheduleLong("DOWN");
                } else if (event.getRepeatCount() > 0 && !downLongFired) {
//                    System.out.println("长按down");
                    if ("LONG".equals(volPattern)) {
                        triggerIfMatch("LONG");
                        downLongFired = true;
                    }
                }
            }
        } else if (action == KeyEvent.ACTION_UP) {
            if (code == KeyEvent.KEYCODE_VOLUME_UP) {
                cancelLong("UP");
                upDownPressed = false;
            } else {
                cancelLong("DOWN");
                downDownPressed = false;
            }
        }

        // ---- 短按序列（同时按下/双击）----
        // 只在 "按下" 的瞬间记录一次
        if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            String token = (code == KeyEvent.KEYCODE_VOLUME_UP) ? "UP" : "DOWN";
            volHits.addLast(new KeyHit(token, now));
            pruneOldHits(now);

            // 顺序组合
            if ("BOTH".equals(volPattern)) {
                if (matchBothDirections()) {
                    triggerIfMatch("BOTH");
                }
            } else if ("DOUBLE".equals(volPattern)) {
                if (matchDouble("UP") || matchDouble("DOWN")) {
                    triggerIfMatch("DOUBLE");
                }
            }
            // 长按在定时器/重复里处理
        }


        // 将最近按键放进缓冲，按 volWindowMs 裁剪，然后与 volume_combo_pattern 比对
        // 若匹配：enterChartMode() 或 togglePanel();
        return false; // 返回 true 则“吃掉”按键；false 让系统继续处理音量
    }
    private void pruneOldHits(long now) {
        while (!volHits.isEmpty() && now - volHits.peekFirst().t > volWindowMs) {
            volHits.removeFirst();
        }
    }

    private boolean matchDouble(String x) {
        if (volHits.size() < 2) return false;
        KeyHit[] arr = volHits.toArray(new KeyHit[0]);
        int n = arr.length;
        return x.equals(arr[n-2].token) && x.equals(arr[n-1].token);
    }

    private boolean matchBothDirections() {
        if (volHits.size() < 2) return false;
        KeyHit[] arr = volHits.toArray(new KeyHit[0]);
        int n = arr.length;
        String first = arr[n - 2].token;
        String second = arr[n - 1].token;
        return !first.equals(second)
                && (("UP".equals(first) && "DOWN".equals(second))
                || ("DOWN".equals(first) && "UP".equals(second)));
    }

    private void scheduleLong(String which) {
        if (!"LONG".equals(volPattern)) return;
        if ("UP".equals(which)) {
            volHandler.postDelayed(upLongRunnable, longPressMs);
        } else if ("DOWN".equals(which)) {
            volHandler.postDelayed(downLongRunnable, longPressMs);
        }
    }

    private void cancelLong(String which) {
        if ("UP".equals(which)) volHandler.removeCallbacks(upLongRunnable);
        else volHandler.removeCallbacks(downLongRunnable);
    }

    private final Runnable upLongRunnable = () -> {
        if (!upLongFired) {
            triggerIfMatch("LONG");
            upLongFired = true;
        }
    };
    private final Runnable downLongRunnable = () -> {
        if (!downLongFired) {
            triggerIfMatch("LONG");
            downLongFired = true;
        }
    };

    private void triggerIfMatch(String matched) {
        // 只有匹配当前设置的项才触发
        if (!matched.equals(volPattern)) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastToggleAt < TOGGLE_COOLDOWN_MS) return; // 冷却
        lastToggleAt = now;

        // 触发图表视图的开关
        mainHandler.post(this::togglePanel);

        // 可选：触发时是否“吃掉”该次按键，避免音量变化
        if (consumeOnTrigger) {
            // 没有直接从这里 return true 的通道；在 onKeyEvent 里可根据一个标记返回 true
            // 这里给个标记即可：
            // this.consumeNextKeyEvent = true;  // 若你需要，自己加个布尔变量
        }
    }
    public void announce(@NonNull CharSequence message) {
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (am == null || !am.isEnabled()) return;

        AccessibilityEvent e = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT);
        e.setEventTime(SystemClock.uptimeMillis());
        e.setClassName(getClass().getName()); // 标个来源类名
        e.setPackageName(getPackageName());   // 你的包名
        e.getText().add(message);
        am.sendAccessibilityEvent(e);
    }

    private void showMockAccessOverlay() {
        if (demoAccessOverlayManager != null && demoAccessOverlayManager.isShowing()) {
            return;
        }
        demoAccessOverlayManager = ChartAccessOverlayDemo.showMockOverlay(this);
    }

    /**
     * 初始化检测器
     * 根据配置选择使用YOLOv11检测器或Demo检测器
     */
    private void initializeDetector() {
        if (USE_YOLO_DETECTOR) {
            yoloDetector = new YOLOv11Detector(this);
            // 异步初始化模型
            io.execute(() -> {
                boolean success = yoloDetector.initialize();
                mainHandler.post(() -> {
                    if (success) {
                        detector = yoloDetector;
                        Toast.makeText(this, "YOLOv11模型加载成功", Toast.LENGTH_SHORT).show();
                    } else {
                        // 回退到Demo检测器
                        detector = new DemoChartDetector();
                        Toast.makeText(this, "YOLOv11加载失败，使用演示模式", Toast.LENGTH_SHORT).show();
                    }
                });
            });
            // 在模型加载期间使用Demo检测器
            detector = new DemoChartDetector();
        } else {
            detector = new DemoChartDetector();
        }
    }

    // ============ 修改 onDestroy 方法 ============
    @Override
    public void onDestroy() {
        super.onDestroy();

        // 确保清理手势直通
        disableGesturePassthrough();

        // 释放YOLOv11资源
        if (yoloDetector != null) {
            yoloDetector.release();
            yoloDetector = null;
        }

        if (ab != null && abCb != null) ab.unregisterAccessibilityButtonCallback(abCb);
        io.shutdownNow();
        if (demoAccessOverlayManager != null && demoAccessOverlayManager.isShowing()) {
            demoAccessOverlayManager.dismissAccessView();
        }
        if (panel != null && panel.isShowing()) panel.hide();
//        if (debugMarkOverlay != null) debugMarkOverlay.hide();
//        if(simpleOverLay != null) simpleOverLay.hide();
//        if (hintOverlay != null && hintOverlay.isShowing()) hintOverlay.hide();
//        if (simpleVirtualNodeOverlay != null && simpleVirtualNodeOverlay.isShown()) simpleVirtualNodeOverlay.hide();
    }
    private void recycleNode(AccessibilityNodeInfo n) { if (n != null) n.recycle(); }
//    private void onChartDetected(Rect chartRect) {
//        // 1) overlay 定位
//        if (simpleOverLay != null) {
//            simpleOverLay.showAt(chartRect);
//            simpleOverLay.setNodesExposed(true);
//            simpleOverLay.setOverlayFocusable(false);
//        }
//        // 2) 计算邻居
//        AccessibilityNodeInfo root = getRootInActiveWindow();
//        ReadingOrderHelper.Neighbors nb =
//               ReadingOrderHelper.computeNeighbors(
//                        root, chartRect, getResources().getDisplayMetrics().density
//                );
//        if (root != null) root.recycle();
//        recycleNode(prevNode); recycleNode(nextNode);
//        prevNode = nb.prev; nextNode = nb.next;
//        armedFromPrev = false;
//        prevNodeSourceId = -1;
//
//        Rect prevRect = null, nextRect = null;
//        if (prevNode != null) {
//            prevRect = new Rect();
//            prevNode.getBoundsInScreen(prevRect);
//            System.out.println("pre_con_des:" + prevNode.isFocusable()+ prevRect.toString());
//        }
//        if (nextNode != null) {
//            nextRect = new Rect();
//            nextNode.getBoundsInScreen(nextRect);
//            System.out.println("next_con_des:" + nextNode.isFocusable() + nextRect.toString());
//        }
//        if (debugMarkOverlay != null) debugMarkOverlay.showMarks(chartRect, prevRect, nextRect);
//    }
//    private boolean looksSame(AccessibilityNodeInfo a, AccessibilityNodeInfo b) {
//        if (a == null || b == null) return false;
//        if (!safeEq(a.getPackageName(), b.getPackageName())) return false;
//        if (!safeEq(a.getClassName(), b.getClassName())) return false;
//        Rect ra = new Rect(), rb = new Rect();
//        a.getBoundsInScreen(ra); b.getBoundsInScreen(rb);
//        if (!ra.equals(rb)) return false;
//        CharSequence da = a.getContentDescription(), db = b.getContentDescription();
//        CharSequence ta = a.getText(), tb = b.getText();
//        if (safeEq(da, db)) return true;
//        return safeEq(ta, tb);
//    }
//    private boolean safeEq(CharSequence a, CharSequence b) { return a==null? b==null : a.equals(b); }
//    private void jumpToNextAndDemote() {
//        // 降级：不再抢线性导航
//        if (simpleOverLay != null) {
//            simpleOverLay.clearChartFocus();
//            simpleOverLay.setOverlayFocusable(false);
//        }
//        // 把焦点交给“后继”
//        if (nextNode != null) {
//            nextNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
//            nextNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS); // 某些 ROM 兜底
//        }
//        armedFromPrev = false;
//    }
    // 监听页面变化 → 去抖检测
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        int t = event.getEventType();
        switch (t) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
            case AccessibilityEvent.TYPE_VIEW_SCROLLED:
                scheduleDetect();
                break;
//            case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED: {
//                AccessibilityNodeInfo src = event.getSource();
//                if (src != null) {
//                    // 1) 若焦点到了“前驱”，进入“武装就绪”态（不立即抢焦点）
//                    if (looksSame(src, prevNode)) {
//                        armedFromPrev = true;
//                        prevNodeSourceId = src.getWindowId(); // 仅做辅助记录
//                        // 提前让 overlay 参与序列，但先不主动抢
//                        if (simpleOverLay != null)
//                            simpleOverLay.setOverlayFocusable(true);
//                    } else {
//                        // 焦点去了别处：若不是在 overlay 内部，则解除武装并降级
//                        armedFromPrev = false;
//                        if (simpleOverLay != null)
//                            simpleOverLay.setOverlayFocusable(false);
//                    }
//                    src.recycle();
//                }
//                break;
//            }

//            case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED: {
//                AccessibilityNodeInfo src = event.getSource();
//                if (src != null) {
//                    // 2) 只有当“前驱”的焦点被清除，才说明用户做了“右划”
//                    if (armedFromPrev && looksSame(src, prevNode)) {
//                        // 抢到我们的图表虚拟结点
//                        if (simpleOverLay != null) {
//                            simpleOverLay.setOverlayFocusable(true);
//                            simpleOverLay.focusChart(); // 主动把焦点移到“图表（入口）”
//                        }
//                    }
//                    src.recycle();
//                }
//                break;
//            }
            default:
                break;
        }
    }

    @Override public void onInterrupt() { }

    // ============ 修改 togglePanel 方法 ============
    private void togglePanel() {
        if (panel.isShowing()) {
            exitChartMode(); // 使用新方法
            return;
        }
        enterChartMode();
//        takeScreenshotSafe(bmp -> {
//            if (bmp == null) { Toast.makeText(this, "未获取到屏幕", Toast.LENGTH_SHORT).show(); return; }
//            io.execute(() -> {
//                ChartResult res = detector.detectSingleChart(bmp);
//                mainHandler.post(() -> {
//                    if (res != null) {
//                        cacheResult(res);
//                        panel.show(res.chartBitmap, res.chartRectOnScreen, res.nodes);
////                        if (hintOverlay.isShowing()) hintOverlay.hide();
//                    } else {
//                        Toast.makeText(this, "未发现图表", Toast.LENGTH_SHORT).show();
//                    }
//                });
//            });
//        });
    }

    // —— 截屏（API 30–34 兼容签名）——
    interface ScreenshotCb { void onBmp(Bitmap bmp); }
    private void takeScreenshotSafe(ScreenshotCb cb) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            cb.onBmp(null); // <30 请自行接入 MediaProjection 回退
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastShotAt < SHOT_INTERVAL_MS) { cb.onBmp(null); return; }
        lastShotAt = now;

        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(),
                new AccessibilityService.TakeScreenshotCallback() {
                    @Override public void onSuccess(AccessibilityService.ScreenshotResult result) {
                        HardwareBuffer hb = result.getHardwareBuffer();
                        if (hb == null) { cb.onBmp(null); return; }
                        Bitmap hw = Bitmap.wrapHardwareBuffer(hb, result.getColorSpace());
                        Bitmap copy = (hw != null) ? hw.copy(Bitmap.Config.ARGB_8888, false) : null;
                        try { hb.close(); } catch (Throwable ignored) {}
                        cb.onBmp(copy);
                    }
                    @Override public void onFailure(int errorCode) { cb.onBmp(null); }
                });
    }

    // —— 手势回放到原屏 —— //
    public boolean tap(int x, int y) {
        Path p = new Path(); p.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(p, 0, 60);
        return dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
    }

    // =========================
    // 自动检测 + 提示虚拟结点
    // =========================
    private void scheduleDetect() {
        if (mainHandler == null) return;
        // 取消前一个任务
        mainHandler.removeCallbacks(detectRunnable);
        // 开启新任务 在250ms后执行
        mainHandler.postDelayed(detectRunnable, DETECT_DEBOUNCE_MS);
    }

    private void detectOnce() {
        long now = SystemClock.uptimeMillis();
        if (now - lastDetectAt < DETECT_MIN_INTERVAL_MS) return;
        lastDetectAt = now;

        if (panel != null && panel.isShowing()) return; // 已在图表模式，不重复提示

        takeScreenshotSafe(bmp -> {
            if (bmp == null) { clearCachedResultAndHint(); return; }
            io.execute(() -> {
                ChartResult res = detector.detectSingleChart(bmp);
                mainHandler.post(() -> {
                    if (res != null && res.chartRectOnScreen != null && res.nodes != null && !res.nodes.isEmpty()) {
                        cacheResult(res); // 你原本的缓存图像/节点可保留
//                        announce("已检测到图表");
//                        onChartDetected(res.chartRectOnScreen);
                    } else {
                        // 清理
//                        recycleNode(prevNode); prevNode = null;
//                        recycleNode(nextNode); nextNode = null;
//                        armedFromPrev = false;
//                        if (simpleOverLay != null) simpleOverLay.setOverlayFocusable(false);
//                        if (debugMarkOverlay != null) debugMarkOverlay.hide();
                    }
                });
            });
        });

    }

    private void cacheResult(ChartResult res) {
        lastChartRect = new Rect(res.chartRectOnScreen);
        lastNodes = res.nodes;
        lastChartBmp = res.chartBitmap;
    }

    private void clearCachedResultAndHint() {
        lastChartRect = null;
        lastNodes = null;
        lastChartBmp = null;
//        if (hintOverlay != null && hintOverlay.isShowing()) hintOverlay.hide();
    }

    // ============ 新增：设置手势直通区域 ============
    private void enableGesturePassthrough() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            int width = getResources().getDisplayMetrics().widthPixels;
            int height = getResources().getDisplayMetrics().heightPixels;
            Rect rect = new Rect(0, 0, width, height);
            Region region = new Region(rect);

            // 只设置手势检测直通，保留触摸探索（单指焦点切换）
            setGestureDetectionPassthroughRegion(Display.DEFAULT_DISPLAY, region);
        }
    }

    private void disableGesturePassthrough() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setGestureDetectionPassthroughRegion(Display.DEFAULT_DISPLAY, new Region());
        }
    }

    // ============ 修改 enterChartMode 方法 ============
    private void enterChartMode() {
        if (lastChartRect == null || lastNodes == null || lastChartBmp == null) return;

        // 启用手势直通
        enableGesturePassthrough();

        if (!panel.isShowing()) {
            panel.show(lastChartBmp, lastChartRect, lastNodes);
        }
        Toast.makeText(this, "已进入图表模式", Toast.LENGTH_SHORT).show();
    }

    // ============ 新增：退出图表模式方法 ============
    private void exitChartMode() {
        // 禁用手势直通
        disableGesturePassthrough();

        if (panel != null && panel.isShowing()) {
            panel.hide();
        }
        Toast.makeText(this, "已退出图表模式", Toast.LENGTH_SHORT).show();
    }
}
