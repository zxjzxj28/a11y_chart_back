package com.eagle.android.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.eagle.android.model.NodeSpec;
import com.eagle.android.service.ChartA11yService;

import java.util.List;

public class ChartPanelWindow {
    private final Context ctx;
    private final WindowManager wm;
    private final Tapper tapper;
    private final ChartA11yService.ChartGestureCallback gestureCallback;
    private ChartPanelView view;
    private WindowManager.LayoutParams lp;

    public interface Tapper { boolean tap(int x, int y); }

    // 修改构造函数，添加手势回调参数
    public ChartPanelWindow(Context c, Tapper t, ChartA11yService.ChartGestureCallback gestureCallback) {
        this.ctx = c;
        this.tapper = t;
        this.gestureCallback = gestureCallback;
        this.wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
    }

    public boolean isShowing() { return view != null; }

    public void show(Bitmap bmp, Rect chartRectOnScreen, List<NodeSpec> nodes) {
        show(bmp, chartRectOnScreen, nodes, null);
    }

    public void show(Bitmap bmp, Rect chartRectOnScreen, List<NodeSpec> nodes, CharSequence summary) {
        if (view != null) { update(bmp, chartRectOnScreen, nodes, summary); return; }

        // 传入手势回调
        view = new ChartPanelView(ctx, tapper, this::hide, gestureCallback);

        final int W = ctx.getResources().getDisplayMetrics().widthPixels;
        final int H = ctx.getResources().getDisplayMetrics().heightPixels;
        final int w = (int)(W * 0.92f);

        // 上下兜底占位（与 ChartPanelView 的 minTopPad/minBottomPad 对齐）
        final int minTopPad = dp(48), minBottomPad = dp(56);

        float aspect = (bmp != null && bmp.getWidth() > 0) ? (bmp.getHeight() * 1f / bmp.getWidth()) : 0.6f;
        int imgHeight = Math.round(w * aspect);
        int h = clamp(minTopPad + imgHeight + minBottomPad, (int)(H * 0.35f), (int)(H * 0.92f));

        lp = new WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.CENTER;
        wm.addView(view, lp);

        view.bindData(bmp, chartRectOnScreen, nodes, summary);

        // 按钮测量完成后，用真实 top/bottom 占位精确高度
        ViewTreeObserver.OnGlobalLayoutListener once = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                FrameLayout.LayoutParams hostLp = (FrameLayout.LayoutParams) view.getChildAt(0).getLayoutParams();
                int top = hostLp.topMargin;
                int bottom = hostLp.bottomMargin;

                int imgH2 = Math.round(w * aspect);
                int newH = clamp(top + imgH2 + bottom, (int)(H * 0.35f), (int)(H * 0.92f));

                if (lp.height != newH) {
                    lp.height = newH;
                    wm.updateViewLayout(view, lp);
                }
                view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        };
        view.getViewTreeObserver().addOnGlobalLayoutListener(once);
    }

    public void update(Bitmap bmp, Rect chartRectOnScreen, List<NodeSpec> nodes, CharSequence summary) {
        if (view != null) {
            view.bindData(bmp, chartRectOnScreen, nodes, summary);
            // 如更换了不同尺寸的 bmp，可复用 show() 里的精确期逻辑再算一次高度
        }
    }
    public void update(Bitmap bmp, Rect chartRectOnScreen, List<NodeSpec> nodes) {
        update(bmp, chartRectOnScreen, nodes, null);
    }

    public void hide() {
        if (view != null) { wm.removeView(view); view = null; lp = null; }
    }

    private int dp(int v) { return Math.round(v * ctx.getResources().getDisplayMetrics().density); }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
