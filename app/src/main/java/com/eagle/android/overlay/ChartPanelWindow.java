package com.eagle.android.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.WindowManager;
import com.eagle.android.model.NodeSpec;
import java.util.List;


public class ChartPanelWindow {
    private final Context ctx;
    private final WindowManager wm;
    private final Tapper tapper;
    private ChartPanelView view;
    private WindowManager.LayoutParams lp;


    public interface Tapper { boolean tap(int x, int y); }


    public ChartPanelWindow(Context c, Tapper t) {
        this.ctx = c; this.tapper = t; this.wm = (WindowManager)c.getSystemService(Context.WINDOW_SERVICE);
    }


    public boolean isShowing() { return view != null; }


    public void show(Bitmap bmp, Rect chartRectOnScreen, List<NodeSpec> nodes) {
        if (view != null) { update(bmp, chartRectOnScreen, nodes); return; }
        view = new ChartPanelView(ctx, tapper);
        int W = ctx.getResources().getDisplayMetrics().widthPixels;
        int H = ctx.getResources().getDisplayMetrics().heightPixels;
        int w = (int)(W * 0.92f), h = (int)(H * 0.50f);
        lp = new WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
//                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.CENTER;
        wm.addView(view, lp);
        view.bindData(bmp, chartRectOnScreen, nodes);
    }


    public void update(Bitmap bmp, Rect chartRectOnScreen, List<NodeSpec> nodes) {
        if (view != null) view.bindData(bmp, chartRectOnScreen, nodes);
    }


    public void hide() {
        if (view != null) { wm.removeView(view); view = null; lp = null; }
    }
}