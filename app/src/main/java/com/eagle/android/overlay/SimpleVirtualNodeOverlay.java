package com.eagle.android.overlay;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import java.util.List;

/**
 * 左上角一直存在的虚拟结点（最小可用版）
 */
public class SimpleVirtualNodeOverlay {

    public interface OnClick { void onClick(); }

    private final AccessibilityService svc;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final OnClick onClick;

    private WindowManager.LayoutParams lp;
    private HostView host;
    private boolean shown = false;

    public SimpleVirtualNodeOverlay(AccessibilityService service, OnClick onClick) {
        this.svc = service;
        this.onClick = onClick;
        this.wm = (WindowManager) service.getSystemService(AccessibilityService.WINDOW_SERVICE);
    }

    /** 显示：固定在左上角 */
    public void show() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(this::show); return; }
        if (shown) return;

        int w = dp(160), h = dp(100);

        host = new HostView(svc, onClick, w, h);
        lp = new WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(12);
        lp.y = dp(12);
        lp.packageName = svc.getPackageName();

        wm.addView(host, lp);
        shown = true;

        // 可选：尝试把无障碍焦点引到这个窗（安全做法：不在 populate 里添加 ACCESSIBILITY_FOCUS）
        host.requestA11yFocusOnce();
    }

    public boolean isShown() { return shown; }
    public void setOverlayFocusable(boolean focusable) {
        if (!shown || lp == null || host == null) return;
        if (focusable) {
            lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        } else {
            lp.flags |=  WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
        try { wm.updateViewLayout(host, lp); } catch (Throwable ignore) {}
    }
    public void hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(this::hide); return; }
        if (!shown) return;
        wm.removeView(host);
        shown = false;
        host = null; lp = null;
    }

    private int dp(int v) {
        float d = svc.getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }

    // ===== 宿主 View（一个虚拟结点） =====
    static class HostView extends View {
        private static final int VID = 1;

        private final ExploreByTouchHelper a11y;
        private final OnClick onClick;
        private final Rect nodeRect;

        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

        HostView(AccessibilityService svc, OnClick onClick, int w, int h) {
            super(svc);
            this.onClick = onClick;
            this.nodeRect = new Rect(0, 0, w, h);

            bg.setColor(Color.argb(90, 0, 200, 0));
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(4f);
            border.setColor(Color.GREEN);
            text.setColor(Color.BLACK);
            text.setTextSize(32f);

            a11y = new ExploreByTouchHelper(this) {
                @Override protected int getVirtualViewAt(float x, float y) {
                    // 未命中返回 HOST_ID（不是 INVALID_ID）
                    return nodeRect.contains((int)x, (int)y) ? VID : HOST_ID;
                }
                @Override protected void getVisibleVirtualViews(List<Integer> out) {
                    out.add(VID);
                }
                @Override protected void onPopulateNodeForVirtualView(int id, AccessibilityNodeInfoCompat info) {
                    // 关键：不要添加 ACTION_ACCESSIBILITY_FOCUS/CLEAR！
                    info.setText("1");
                    info.setBoundsInParent(nodeRect);
                    info.setContentDescription("图表区域（演示）—双击进入");
                    info.setClassName("android.widget.Button");
//                    info.setClickable(true);
                    info.setFocusable(true);
                    info.setVisibleToUser(true);
                    info.setEnabled(true);

//                    info.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
                    // 不要：info.addAction(ACTION_ACCESSIBILITY_FOCUS/ CLEAR)
                }
                @Override protected boolean onPerformActionForVirtualView(int id, int action, android.os.Bundle args) {
                    if (id != VID) return false;
                    if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
                        if (onClick != null) onClick.onClick();
                        sendEventForVirtualView(VID, AccessibilityEvent.TYPE_VIEW_CLICKED);
                        return true;
                    }
                    return false;
                }
            };

            ViewCompat.setAccessibilityDelegate(this, a11y);
            ViewCompat.setImportantForAccessibility(this, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
            ViewCompat.setAccessibilityPaneTitle(this, "图表区域");
        }

        /** 尝试引导一次无障碍焦点（安全：不依赖 populate 里的 ACCESSIBILITY_FOCUS action） */
        void requestA11yFocusOnce() {
            // 这行只是发一个事件提示 TalkBack 这里有个可聚焦对象；
            // 真正的“获得可访问焦点”仍由 TalkBack根据上下文决定。
            a11y.invalidateRoot();
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        }

        // 没开 TalkBack 时，也支持普通触摸点击进入
        @Override public boolean onTouchEvent(MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_UP && nodeRect.contains((int)ev.getX(), (int)ev.getY())) {
                if (onClick != null) onClick.onClick();
                return true;
            }
            return super.onTouchEvent(ev);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.drawRect(nodeRect, bg);
            c.drawRect(nodeRect, border);
            c.drawText("虚拟结点", nodeRect.left + dp(8), nodeRect.top + dp(28), text);
            c.drawText("双击进入", nodeRect.left + dp(8), nodeRect.top + dp(56), text);
        }

        private int dp(int v) {
            float d = getResources().getDisplayMetrics().density;
            return Math.round(v * d);
        }
    }
}
