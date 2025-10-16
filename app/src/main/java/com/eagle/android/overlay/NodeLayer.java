package com.eagle.android.overlay;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;
import com.eagle.android.model.NodeSpec;
import java.util.*;
public class NodeLayer extends View {
    private final ChartPanelWindow.Tapper tapper;
    private final Paint focusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap chartBmp;
    private Rect chartRectScreen = new Rect();
    private final List<NodeSpec> nodes = new ArrayList<>();

    // 用于把原图表坐标映射到小窗显示区域（FIT_CENTER）
    private final Rect imageDstLocal = new Rect();
    private float scale = 1f;


    private Rect focusedScreenRect = null;


    private final ExploreByTouchHelper a11yHelper = new ExploreByTouchHelper(this) {
        //标识每一个坐标属于哪一个虚拟子节点，也就是数据点
        @Override protected int getVirtualViewAt(float x, float y) {
            int[] loc = new int[2]; getLocationOnScreen(loc);
            int sx = (int)x + loc[0];
            int sy = (int)y + loc[1];
            if (!imageDstLocal.contains((int)x, (int)y)) return ExploreByTouchHelper.INVALID_ID;; // 小窗外不接管
            for (NodeSpec n : nodes) {
                if (n.rectScreen.contains(sx, sy)) return n.id;
            }
            return ExploreByTouchHelper.INVALID_ID;
        }
        // 可以交互的虚拟子节点总数
        @Override protected void getVisibleVirtualViews(List<Integer> out) {
//            for (NodeSpec n : nodes) out.add(n.id);
            out.clear(); // ✅ 新增
            for (NodeSpec n : nodes) {
                Rect local = mapScreenRectToLocalInt(n.rectScreen);
                if (Rect.intersects(local, imageDstLocal)) {
                    out.add(n.id);
                }
            }
        }

        // 设置每一个虚拟子节点的无障碍属性
        @Override protected void onPopulateNodeForVirtualView(int id, AccessibilityNodeInfoCompat info) {
            NodeSpec n = null;
            for (NodeSpec it : nodes) if (it.id == id) { n = it; break; }
            if (n == null) return;
            Rect local = mapScreenRectToLocalInt(n.rectScreen);
            info.setBoundsInParent(local);
            info.setContentDescription(n.label);
            info.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);

            info.setClassName("android.view.View");
            info.setPackageName(getContext().getPackageName());

            info.setFocusable(true); info.setClickable(true);
        }
        // 设置对每一个虚拟子节点的无障碍操作如何响应
        @Override protected boolean onPerformActionForVirtualView(int id, int action, Bundle args) {
            NodeSpec n = null;
            for (NodeSpec it : nodes) if (it.id == id) { n = it; break; }
            if (n == null) return false;
            if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
                System.out.println("点击到了" + n.label);

                boolean ok = tapper.tap(n.rectScreen.centerX(), n.rectScreen.centerY());
                if (ok) {
                    sendEventForVirtualView(id, android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED);
                    announceForAccessibility("已选择 " + n.label);
                }
                return ok;
            } else if (action == AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS) {
                System.out.println("聚焦到了" + n.label);
                focusedScreenRect = n.rectScreen; invalidate(); return false;
            } else if (action == AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS) {
                focusedScreenRect = null; invalidate(); return false;
            }
            return false;
        }
    };
    public NodeLayer(Context ctx, ChartPanelWindow.Tapper tapper) {
        super(ctx);
        this.tapper = tapper;
        focusPaint.setStyle(Paint.Style.STROKE);
        focusPaint.setStrokeWidth(6f);
        focusPaint.setColor(Color.argb(220, 30, 144, 255));

        ViewCompat.setImportantForAccessibility(this, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setFocusable(true);
        setFocusableInTouchMode(true);
        ViewCompat.setAccessibilityDelegate(this, a11yHelper);
    }


    public void setChartBitmap(Bitmap bmp, Rect rectOnScreen, List<NodeSpec> list) {
        this.chartBmp = bmp;
        this.chartRectScreen.set(rectOnScreen);
        this.nodes.clear(); this.nodes.addAll(list);
        Collections.sort(this.nodes, (a, b) -> {
            int d = a.rectScreen.left - b.rectScreen.left; return d != 0 ? d : a.rectScreen.top - b.rectScreen.top;
        });
        recomputeImageMapping(getWidth(), getHeight());
        a11yHelper.invalidateRoot();
        invalidate();
        post(() -> {
            if (Build.VERSION.SDK_INT >= 28) {
                setAccessibilityPaneTitle("图表面板"); // 让读屏知道来了一个新 pane
            }
            // 触发一次内容变更，让 TalkBack 刷新
            sendAccessibilityEvent(
                    android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            );

            if (!nodes.isEmpty()) {
                androidx.core.view.accessibility.AccessibilityNodeProviderCompat p =
                        ViewCompat.getAccessibilityNodeProvider(this);
                if (p != null) {
                    int firstId = nodes.get(0).id;
                    p.performAction(firstId,
                            AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS, null);
                }
            }
        });
    }

    private Rect mapScreenRectToLocalInt(Rect screenRect) {
        RectF rf = mapScreenRectToLocal(screenRect);
        Rect out = new Rect();
        rf.round(out); // 四舍五入为整数像素
        return out;
    }
    private void recomputeImageMapping(int vw, int vh) {
        if (chartBmp == null || vw <= 0 || vh <= 0) return;
        int bw = chartBmp.getWidth(), bh = chartBmp.getHeight();
        float sx = vw * 1f / bw, sy = vh * 1f / bh;
        scale = Math.min(sx, sy);
        int dw = (int)(bw * scale), dh = (int)(bh * scale);
        int left = (vw - dw)/2, top = (vh - dh)/2;
        imageDstLocal.set(left, top, left + dw, top + dh);
    }


    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recomputeImageMapping(w, h);
    }


    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (focusedScreenRect != null && chartBmp != null) {
            RectF local = mapScreenRectToLocal(focusedScreenRect);
            c.drawRect(local, focusPaint);
        }
    }
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        return a11yHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }
    private int hitTestVirtualId(float x, float y) {
        if (!imageDstLocal.contains((int) x, (int) y)) {
            return ExploreByTouchHelper.INVALID_ID;
        }
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        int sx = (int) x + loc[0];
        int sy = (int) y + loc[1];
        for (NodeSpec n : nodes) {
            if (n.rectScreen.contains(sx, sy)) return n.id;
        }
        return ExploreByTouchHelper.INVALID_ID;
    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // ✅ 关键：拿住事件流，避免穿透
                return true;

            case MotionEvent.ACTION_UP: {
                int virtualId = hitTestVirtualId(event.getX(), event.getY());
                if (virtualId != ExploreByTouchHelper.INVALID_ID) {
                    androidx.core.view.accessibility.AccessibilityNodeProviderCompat p =
                            ViewCompat.getAccessibilityNodeProvider(this);
                    if (p != null) {
                        // ✅ 转成无障碍点击，最终会回到 onPerformActionForVirtualView()
                        boolean ok = p.performAction(
                                virtualId,
                                AccessibilityNodeInfoCompat.ACTION_CLICK,
                                null
                        );
                        return ok || super.onTouchEvent(event);
                    }
                }
                return super.onTouchEvent(event);
            }
        }
        return super.onTouchEvent(event);
    }
    private RectF mapScreenRectToLocal(Rect screenRect) {
        float rx = screenRect.left - chartRectScreen.left;
        float ry = screenRect.top - chartRectScreen.top;
        float rw = screenRect.width();
        float rh = screenRect.height();
        float lx = imageDstLocal.left + rx * scale;
        float ly = imageDstLocal.top + ry * scale;
        return new RectF(lx, ly, lx + rw * scale, ly + rh * scale);
    }
}