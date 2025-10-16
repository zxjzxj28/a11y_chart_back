package com.eagle.android.overlay;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

public class FocusSwitchOverlay {

    public interface OnToggle {
        void onToggled(boolean focusable);
    }

    private final AccessibilityService svc;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final OnToggle cb;

    private WindowManager.LayoutParams lp;
    private TextView btn;
    private boolean shown = false;
    private boolean focusable = false;

    public FocusSwitchOverlay(AccessibilityService service, OnToggle cb) {
        this.svc = service;
        this.cb = cb;
        this.wm = (WindowManager) service.getSystemService(AccessibilityService.WINDOW_SERVICE);
    }

    /** 显示按钮（右上角小胶囊） */
    public void show(boolean initialFocusable) {
        if (shown) return;
        this.focusable = initialFocusable;

        btn = new TextView(svc);
        btn.setPadding(dp(12), dp(8), dp(12), dp(8));
        btn.setTextSize(14);
        btn.setText(focusable ? "焦点：开" : "焦点：关");
        btn.setContentDescription("切换虚拟结点窗口是否可聚焦");
        btn.setTextColor(0xFF000000);
        btn.setBackground(makeBg(focusable));

        btn.setOnClickListener(v -> toggle());

        // 这个控制按钮自身不占无障碍焦点（不影响遍历），但可以被正常点击
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = dp(12);
        lp.y = dp(72); // 顶部状态栏下方一点

        wm.addView(btn, lp);
        shown = true;
    }

    /** 外部可直接设定状态（不触发回调） */
    public void setState(boolean focusable) {
        this.focusable = focusable;
        refreshUi();
    }

    public void hide() {
        if (!shown) return;
        try { wm.removeView(btn); } catch (Throwable ignore) {}
        btn = null; lp = null; shown = false;
    }

    public boolean isShown() { return shown; }

    private void toggle() {
        focusable = !focusable;
        refreshUi();
        if (cb != null) cb.onToggled(focusable);
    }

    private void refreshUi() {
        if (btn == null) return;
        btn.setText(focusable ? "焦点：开" : "焦点：关");
        btn.setBackground(makeBg(focusable));
        // 动态更新 contentDescription，便于可见性调试
        btn.setContentDescription(focusable ? "当前可聚焦，点按切换为不可聚焦" : "当前不可聚焦，点按切换为可聚焦");
        try { wm.updateViewLayout(btn, lp); } catch (Throwable ignore) {}
    }

    private GradientDrawable makeBg(boolean on) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(16));
        gd.setColor(on ? 0xFFB2FF59 : 0xFFE0E0E0); // 开=浅绿，关=灰
        gd.setStroke(dp(1), 0x55000000);
        return gd;
    }

    private int dp(int v) {
        float d = svc.getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }
}
