// app/src/main/java/com/eagle/android/overlay/ChartHintOverlay.java
package com.eagle.android.overlay;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import java.util.List;

/** 用于在屏幕上挂一个“无形”的可访问虚拟结点，覆盖图表区域。 */
public class ChartHintOverlay {

    public interface EnterCallback { void onEnterChartMode(); }

    private final AccessibilityService svc;     // ← 关键：用服务 Context
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final EnterCallback cb;

    private WindowManager.LayoutParams lp;
    private HostView host;

    public ChartHintOverlay(AccessibilityService service, EnterCallback cb) {
        this.svc = service;
        this.cb = cb;
        this.wm = (WindowManager) service.getSystemService(AccessibilityService.WINDOW_SERVICE);
    }

    public boolean isShowing() { return host != null; }

    /** 在主线程显示覆盖图表区域的虚拟结点 */
    public void show(Rect chartRectOnScreen, String prompt) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> show(chartRectOnScreen, prompt));
            return;
        }
        if (host == null) {
            host = new HostView(svc, cb);
            lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, // 不吃触摸
                    PixelFormat.TRANSLUCENT
            );
            lp.gravity = Gravity.TOP | Gravity.START;

            // 少数 ROM 更严格，要求带上包名（非必需，但有助于定位）
            lp.packageName = svc.getPackageName();

            // 必须用“服务 Context”来 addView，系统才会附上正确的 overlay token
            wm.addView(host, lp);
            System.out.println("显示完成");
        }
        host.update(chartRectOnScreen, prompt);
    }

    public void hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(this::hide);
            return;
        }
        if (host != null) {
            wm.removeView(host);
            host = null;
            lp = null;
        }
    }

    /** 宿主 View：只暴露一个虚拟结点（覆盖 chartRect） */
    static class HostView extends View {
        private static final int VID_CHART_HINT = 1;
        private final ExploreByTouchHelper a11y;
        private final EnterCallback cb;
        private final Rect chartRect = new Rect();
        private String prompt = "图表区域。双击进入图表模式；向右继续可跳过。";

        HostView(AccessibilityService svc, EnterCallback cb) {
            super(svc);                    // ← 用服务 Context 构造 View
            this.cb = cb;

            a11y = new ExploreByTouchHelper(this) {
                @Override protected int getVirtualViewAt(float x, float y) {
                    return chartRect.isEmpty() ? HOST_ID : VID_CHART_HINT;
                }
                @Override protected void getVisibleVirtualViews(List<Integer> out) {
                    if (!chartRect.isEmpty()) out.add(VID_CHART_HINT);
                }
                @Override protected void onPopulateNodeForVirtualView(int id, AccessibilityNodeInfoCompat info) {

                    info.setBoundsInScreen(chartRect);
                    info.setContentDescription(prompt);
                    info.setClassName("android.widget.Button");
                    info.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
                    info.setClickable(true);
                    info.setFocusable(true);
                    info.setVisibleToUser(true);
                }
                @Override protected boolean onPerformActionForVirtualView(int id, int action, android.os.Bundle args) {
                    if (id == VID_CHART_HINT && action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
                        if (cb != null) cb.onEnterChartMode();
                        return true;
                    }
                    return false;
                }
            };
            ViewCompat.setAccessibilityDelegate(this, a11y);
            ViewCompat.setImportantForAccessibility(this, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        void update(Rect screenRect, String p) {
            if (screenRect != null) this.chartRect.set(screenRect);
            if (p != null) this.prompt = p;
            a11y.invalidateRoot();
            invalidate();
        }
    }
}
