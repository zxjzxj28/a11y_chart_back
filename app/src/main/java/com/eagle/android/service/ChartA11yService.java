package com.eagle.android.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityButtonController;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.eagle.android.a11y.ReadingOrderHelper;
import com.eagle.android.detector.ChartDetector;
import com.eagle.android.detector.DemoChartDetector; // 演示用，接入真模型后替换
import com.eagle.android.model.ChartResult;
import com.eagle.android.model.NodeSpec;
import com.eagle.android.overlay.ChartPanelWindow;
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

    private ChartPanelWindow panel;
    private AccessibilityButtonController ab;
    private AccessibilityButtonController.AccessibilityButtonCallback abCb;
    private Handler mainHandler;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private ChartDetector detector = new DemoChartDetector(); // TODO: 替换为你的真实实现

    // 截屏节流
    private static final long SHOT_INTERVAL_MS = 1000;
    private long lastShotAt = 0L;

    // 自动检测去抖/最小间隔
    private static final long DETECT_DEBOUNCE_MS = 250;
    private static final long DETECT_MIN_INTERVAL_MS = 650;
    private long lastDetectAt = 0L;
    private final Runnable detectRunnable = this::detectOnce;

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
        // tap模拟点击操作
        panel = new ChartPanelWindow(this, this::tap);
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
        String volPattern = sp.getString("volume_combo_pattern", "UP+DOWN");
        int volWindowMs   = Integer.parseInt(sp.getString("volume_combo_window_ms", "500"));
        System.out.println("volPattern:" +  volPattern + "volWindowMs:" + volWindowMs );

        String voicePhrase = sp.getString("voice_trigger_phrase", "打开图表");
        String gestureChoice = sp.getString("a11y_gesture_choice", "SWIPE_UP");
        System.out.println("voicePhrase:" +  voicePhrase + "gestureChoice:" + gestureChoice );

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
        mainHandler = new Handler(Looper.getMainLooper());
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (ab != null && abCb != null) ab.unregisterAccessibilityButtonCallback(abCb);
        io.shutdownNow();
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

    // —— 仍保留原按钮触发逻辑（方便手动测试）——
    private void togglePanel() {
        if (panel.isShowing()) { panel.hide(); return; }
        takeScreenshotSafe(bmp -> {
            if (bmp == null) { Toast.makeText(this, "未获取到屏幕", Toast.LENGTH_SHORT).show(); return; }
            io.execute(() -> {
                ChartResult res = detector.detectSingleChart(bmp);
                mainHandler.post(() -> {
                    if (res != null) {
                        cacheResult(res);
                        panel.show(res.chartBitmap, res.chartRectOnScreen, res.nodes);
//                        if (hintOverlay.isShowing()) hintOverlay.hide();
                    } else {
                        Toast.makeText(this, "未发现图表", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });
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

    private void enterChartMode() {
        if (lastChartRect == null || lastNodes == null || lastChartBmp == null) return;
//        if (hintOverlay.isShowing()) hintOverlay.hide();
        if (!panel.isShowing()) {
            panel.show(lastChartBmp, lastChartRect, lastNodes);
        }
        Toast.makeText(this, "已进入图表模式", Toast.LENGTH_SHORT).show();
    }
}
