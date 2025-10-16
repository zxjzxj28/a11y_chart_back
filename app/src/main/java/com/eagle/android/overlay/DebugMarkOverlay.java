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
import android.view.View;
import android.view.WindowManager;

public class DebugMarkOverlay {

    private final AccessibilityService svc;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());

    private WindowManager.LayoutParams lp;
    private MarkView view;
    private boolean shown = false;
    private boolean enabled = true;

    public DebugMarkOverlay(AccessibilityService service) {
        this.svc = service;
        this.wm = (WindowManager) service.getSystemService(AccessibilityService.WINDOW_SERVICE);
    }

    /** 开关整个可视化层（不影响其他 overlay） */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) hide();
    }

    /** 显示/更新标注；传 null 会清掉对应框 */
    public void showMarks(Rect chartRect, Rect prevRect, Rect nextRect) {
        if (!enabled) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> showMarks(chartRect, prevRect, nextRect));
            return;
        }
        if (!shown) {
            view = new MarkView(svc);
            lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
            );
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.packageName = svc.getPackageName();
            try {
                wm.addView(view, lp);
                shown = true;
            } catch (Throwable t) {
                // 若无障碍服务未完全就绪或厂商限制，失败就不显示
                return;
            }
        }
        view.setRects(chartRect, prevRect, nextRect);
    }

    public void hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(this::hide);
            return;
        }
        if (shown && view != null) {
            try { wm.removeView(view); } catch (Throwable ignore) {}
            shown = false;
            view = null; lp = null;
        }
    }

    // ------------------ 内部绘制 View ------------------
    static class MarkView extends View {
        private final Rect chart = new Rect();
        private final Rect prev = new Rect();
        private final Rect next = new Rect();
        private boolean hasChart, hasPrev, hasNext;

        private final Paint fillChart = new Paint();
        private final Paint strokeChart = new Paint();
        private final Paint fillPrev = new Paint();
        private final Paint strokePrev = new Paint();
        private final Paint fillNext = new Paint();
        private final Paint strokeNext = new Paint();
        private final Paint text = new Paint();

        MarkView(AccessibilityService svc) {
            super(svc);
            setWillNotDraw(false);

            // 颜色与样式
            fillChart.setColor(0x3328A745);   // 绿色半透明
            strokeChart.setColor(0xFF28A745);
            strokeChart.setStyle(Paint.Style.STROKE);
            strokeChart.setStrokeWidth(dp(2));

            fillPrev.setColor(0x333498DB);    // 蓝色半透明
            strokePrev.setColor(0xFF3498DB);
            strokePrev.setStyle(Paint.Style.STROKE);
            strokePrev.setStrokeWidth(dp(2));

            fillNext.setColor(0x33F39C12);    // 橙色半透明
            strokeNext.setColor(0xFFF39C12);
            strokeNext.setStyle(Paint.Style.STROKE);
            strokeNext.setStrokeWidth(dp(2));

            text.setColor(Color.WHITE);
            text.setTextSize(dp(12));
            text.setAntiAlias(true);
        }

        void setRects(Rect c, Rect p, Rect n) {
            hasChart = c != null && !c.isEmpty();
            hasPrev  = p != null && !p.isEmpty();
            hasNext  = n != null && !n.isEmpty();

            chart.setEmpty(); prev.setEmpty(); next.setEmpty();
            if (hasChart) chart.set(c);
            if (hasPrev)  prev.set(p);
            if (hasNext)  next.set(n);
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (hasChart) {
                canvas.drawRect(chart, fillChart);
                canvas.drawRect(chart, strokeChart);
                drawTag(canvas, chart, "图表区域");
            }
            if (hasPrev) {
                canvas.drawRect(prev, fillPrev);
                canvas.drawRect(prev, strokePrev);
                drawTag(canvas, prev, "前驱");
            }
            if (hasNext) {
                canvas.drawRect(next, fillNext);
                canvas.drawRect(next, strokeNext);
                drawTag(canvas, next, "后继");
            }
        }

        private void drawTag(Canvas c, Rect r, String s) {
            float pad = dp(4);
            float tx = r.left + pad;
            float ty = r.top + dp(16);
            // 文本背景
            Paint bg = new Paint();
            bg.setColor(0x88000000);
            float w = text.measureText(s) + pad * 2;
            float h = dp(18);
            c.drawRect(tx - pad, ty - h + dp(4), tx - pad + w, ty + dp(2), bg);
            c.drawText(s, tx, ty, text);
        }

        private float dp(float v) {
            return v * getResources().getDisplayMetrics().density;
        }
    }
}
