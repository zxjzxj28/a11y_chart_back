package com.eagle.android.overlay;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import java.util.ArrayList;
import java.util.List;

public class SimpleOverLay {

    public interface Callbacks {
        void onChartClicked();       // 用户在“图表（入口）”双击
        void onExitFocusReached();   // 用户在图表层里右划，焦点移动到了“离开图表”
        void onExitClicked();        // 用户双击“离开图表”
    }

    private final AccessibilityService svc;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Callbacks cb;

    private WindowManager.LayoutParams lp;
    private HostView host;
    private boolean shown = false;

    public SimpleOverLay(AccessibilityService service, Callbacks cb) {
        this.svc = service; this.cb = cb;
        this.wm = (WindowManager) service.getSystemService(AccessibilityService.WINDOW_SERVICE);
    }

    public void show() {
        if (shown) return;
        host = new HostView(svc, cb);
        lp = new WindowManager.LayoutParams(
                dp(160), dp(100),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(12); lp.y = dp(12);
        lp.packageName = svc.getPackageName();
        wm.addView(host, lp);
        shown = true;

        setNodesExposed(true);
        setOverlayFocusable(false);
        host.announcePane();
    }

    /** 把窗口移动/缩放到图表矩形；内部虚拟节点使用相对父坐标 */
    public void showAt(Rect screenRect) {
        if (!shown) show();
        lp.x = screenRect.left; lp.y = screenRect.top;
        lp.width = screenRect.width(); lp.height = screenRect.height();
        wm.updateViewLayout(host, lp);
        host.updateBounds(new Rect(0,0,lp.width,lp.height));
        host.invalidateA11y();
    }

    public void setOverlayFocusable(boolean focusable) {
        if (!shown) return;
        if (focusable) lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        else           lp.flags |=  WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        wm.updateViewLayout(host, lp);
    }

    public void setNodesExposed(boolean exposed) {
        if (shown) host.setExposed(exposed);
    }

    public boolean isShown() { return shown; }

    public void hide() {
        if (!shown) return;
        try { wm.removeView(host); } catch (Throwable ignore) {}
        host = null; lp = null; shown = false;
    }

    /** 主动把无障碍焦点移到“图表（入口）” */
    public void focusChart() { if (shown) host.requestA11yFocusChart(); }
    /** 主动清掉“图表（入口）”的无障碍焦点 */
    public void clearChartFocus() { if (shown) host.clearA11yFocusChart(); }

    private int dp(int v) { return Math.round(v * svc.getResources().getDisplayMetrics().density); }

    // ================== HostView ==================
    static class HostView extends View {
        private static final int VID_CHART = 1;
        private static final int VID_EXIT  = 2;

        private final ExploreByTouchHelper a11y;
        private final Callbacks cb;
        private Rect bounds = new Rect(0,0,1,1);
        private boolean exposed = true;
        private int focusedVid = -1;

        HostView(AccessibilityService svc, Callbacks cb) {
            super(svc);
            this.cb = cb;

            a11y = new ExploreByTouchHelper(this) {
                @Override protected int getVirtualViewAt(float x, float y) {
                    if (!exposed || !bounds.contains((int)x, (int)y)) return HOST_ID;
                    // 简化：两项都在这个矩形内，命中任意处默认返回“图表（入口）”
                    return VID_CHART;
                }
                @Override protected void getVisibleVirtualViews(List<Integer> out) {
                    if (exposed) { out.add(VID_CHART); out.add(VID_EXIT); }
                }
                @Override protected void onPopulateNodeForVirtualView(int id, AccessibilityNodeInfoCompat info) {
                    if (id == VID_CHART) {
                        CharSequence desc = "图表区域，双击进入，右划到“离开图表”";
                        info.setText(desc);
                        info.setContentDescription(desc);
                        info.setBoundsInParent(bounds);
                        info.setBoundsInScreen(toScreen(bounds));
                        info.setClassName("android.widget.Button");
                        info.setClickable(true); info.setFocusable(true);
                        info.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
                    } else {
                        // 为了让“右划”能命中第二个虚拟项，把它放在与 VID_CHART 同一矩形但不同的顺序槽位中
                        Rect half = new Rect(bounds);
                        CharSequence desc = "离开图表，双击返回页面";
                        info.setText(desc);
                        info.setContentDescription(desc);
                        info.setBoundsInParent(half);
                        info.setBoundsInScreen(toScreen(half));
                        info.setClassName("android.widget.Button");
                        info.setClickable(true); info.setFocusable(true);
                        info.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
                    }
                }
                @Override protected boolean onPerformActionForVirtualView(int id, int action, Bundle args) {
                    if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
                        if (id == VID_CHART && cb != null) cb.onChartClicked();
                        if (id == VID_EXIT  && cb != null) cb.onExitClicked();
                        sendEventForVirtualView(id, AccessibilityEvent.TYPE_VIEW_CLICKED);
                        return true;
                    }
                    if (action == AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS) {
                        focusedVid = id;
                        if (id == VID_EXIT && cb != null) cb.onExitFocusReached(); // 在图表里右划到了“离开图表”
                        sendEventForVirtualView(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                        invalidate();
                        return true;
                    }
                    if (action == AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS) {
                        focusedVid = -1;
                        sendEventForVirtualView(id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
                        invalidate();
                        return true;
                    }
                    return false;
                }
            };
            ViewCompat.setAccessibilityDelegate(this, a11y);
            ViewCompat.setImportantForAccessibility(this, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
            ViewCompat.setAccessibilityPaneTitle(this, "图表区域");
        }

        void updateBounds(Rect parentRect) { this.bounds.set(parentRect); }
        void setExposed(boolean e) { this.exposed = e; invalidateA11y(); }
        void invalidateA11y() { a11y.invalidateRoot(); sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED); invalidate(); }
        void announcePane() { sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED); }

        void requestA11yFocusChart() {
            // 确保在主线程调用 & overlay 已经是 focusable=true，且节点已暴露
            androidx.core.view.accessibility.AccessibilityNodeProviderCompat p =
                    androidx.core.view.ViewCompat.getAccessibilityNodeProvider(this);
            if (p != null) {
                p.performAction(VID_CHART,
                        androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS,
                        null);
            }
        }

        void clearA11yFocusChart() {
            androidx.core.view.accessibility.AccessibilityNodeProviderCompat p =
                    androidx.core.view.ViewCompat.getAccessibilityNodeProvider(this);
            if (p != null) {
                p.performAction(VID_CHART,
                        androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
                        null);
            }
        }

        private Rect toScreen(Rect parent) {
            int[] loc = new int[2]; getLocationOnScreen(loc);
            return new Rect(parent.left + loc[0], parent.top + loc[1], parent.right + loc[0], parent.bottom + loc[1]);
        }

        @Override protected void onDraw(Canvas c) {
            // 简单可视化，便于调试
            c.drawColor(exposed ? 0x3332CD32 : 0x22000000);
            // 不画更多内容，避免遮挡
        }
    }
}
