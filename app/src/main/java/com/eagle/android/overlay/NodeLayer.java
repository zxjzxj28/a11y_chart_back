package com.eagle.android.overlay;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
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

    // ============ 无障碍事件可视化相关 ============
    public interface AccessibilityEventCallback {
        void onDoubleTap(float x, float y);
        void onLongPress(float x, float y);
        void onScroll(float distanceX, float distanceY, int direction);
    }

    private AccessibilityEventCallback eventCallback;
    private final GestureDetector gestureDetector;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // 事件可视化绘制
    private final Paint eventPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String currentEventText = null;
    private float eventX, eventY;
    private long eventShowTime = 0;
    private static final long EVENT_DISPLAY_DURATION = 1500; // 事件显示1.5秒

    // 滚动事件累积
    private float accumulatedScrollX = 0;
    private float accumulatedScrollY = 0;
    private static final float SCROLL_THRESHOLD = 50f; // 滚动阈值

    public void setAccessibilityEventCallback(AccessibilityEventCallback callback) {
        this.eventCallback = callback;
    }


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
                }
                return ok;
            } else if (action == AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS) {
                System.out.println("聚焦到了" + n.label);
                focusedScreenRect = n.rectScreen; invalidate(); return true;
            } else if (action == AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS) {
                focusedScreenRect = null; invalidate(); return true;
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

        // 初始化事件可视化画笔
        eventPaint.setTextSize(48f);
        eventPaint.setColor(Color.WHITE);
        eventPaint.setTextAlign(Paint.Align.CENTER);

        // 初始化手势检测器
        gestureDetector = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                showEventVisual("双击", e.getX(), e.getY());
                if (eventCallback != null) {
                    eventCallback.onDoubleTap(e.getX(), e.getY());
                }
                // 发送无障碍事件通知
                announceForAccessibility("双击事件");
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                showEventVisual("长按", e.getX(), e.getY());
                if (eventCallback != null) {
                    eventCallback.onLongPress(e.getX(), e.getY());
                }
                // 发送无障碍事件通知
                announceForAccessibility("长按事件");
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                // 累积滚动距离
                accumulatedScrollX += distanceX;
                accumulatedScrollY += distanceY;

                // 当累积达到阈值时触发滚动事件（但不真正滚动视图）
                if (Math.abs(accumulatedScrollX) > SCROLL_THRESHOLD || Math.abs(accumulatedScrollY) > SCROLL_THRESHOLD) {
                    int direction = getScrollDirection(accumulatedScrollX, accumulatedScrollY);
                    String dirText = getScrollDirectionText(direction);
                    showEventVisual("滚动 " + dirText, e2.getX(), e2.getY());

                    if (eventCallback != null) {
                        eventCallback.onScroll(accumulatedScrollX, accumulatedScrollY, direction);
                    }
                    // 发送无障碍事件通知
                    announceForAccessibility("滚动" + dirText);

                    // 重置累积
                    accumulatedScrollX = 0;
                    accumulatedScrollY = 0;
                }
                // 返回true消费事件，不让视图真正滚动
                return true;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                // 重置滚动累积
                accumulatedScrollX = 0;
                accumulatedScrollY = 0;
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // 单击不处理，让默认行为继续
                return false;
            }
        });

        ViewCompat.setImportantForAccessibility(this, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setFocusable(true);
        setFocusableInTouchMode(true);
        ViewCompat.setAccessibilityDelegate(this, a11yHelper);
    }

    private int getScrollDirection(float dx, float dy) {
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? 0 : 1; // 0=左(手指向右滑), 1=右(手指向左滑)
        } else {
            return dy > 0 ? 2 : 3; // 2=上(手指向下滑), 3=下(手指向上滑)
        }
    }

    private String getScrollDirectionText(int direction) {
        switch (direction) {
            case 0: return "向左";
            case 1: return "向右";
            case 2: return "向上";
            case 3: return "向下";
            default: return "";
        }
    }

    private void showEventVisual(String text, float x, float y) {
        currentEventText = text;
        eventX = x;
        eventY = y;
        eventShowTime = System.currentTimeMillis();
        invalidate();

        // 1.5秒后清除事件显示
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
            currentEventText = null;
            invalidate();
        }, EVENT_DISPLAY_DURATION);
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

        // 关键：按窗口可用宽度强制等比缩放
        float s = vw * 1f / bw;
        int dw = vw;                    // 宽度正好等于容器宽
        int dh = Math.round(bh * s);    // 高度按比例

        // 居中（垂直方向）
        int left = 0;
        int top = (vh - dh) / 2;
        if (top < 0) top = 0; // 极端情况下保护

        imageDstLocal.set(left, top, left + dw, top + dh);
        this.scale = s;
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

        // 绘制事件可视化
        if (currentEventText != null) {
            long elapsed = System.currentTimeMillis() - eventShowTime;
            if (elapsed < EVENT_DISPLAY_DURATION) {
                // 计算渐变透明度
                float alpha = 1f - (elapsed / (float) EVENT_DISPLAY_DURATION);
                int baseAlpha = (int) (200 * alpha);

                // 绘制圆形背景
                Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                bgPaint.setColor(Color.argb(baseAlpha, 0, 122, 255));
                float radius = 80f + (elapsed / 10f); // 扩散效果
                c.drawCircle(eventX, eventY, radius, bgPaint);

                // 绘制事件文字
                eventPaint.setAlpha((int) (255 * alpha));
                c.drawText(currentEventText, eventX, eventY + 15, eventPaint);

                // 继续刷新动画
                postInvalidateDelayed(16);
            }
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
        // 先让手势检测器处理（用于双击、长按、滚动检测）
        boolean handled = gestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // ✅ 关键：拿住事件流，避免穿透
                return true;

            case MotionEvent.ACTION_UP: {
                // 如果手势检测器已经处理了（如双击、长按），则不再处理点击
                if (handled) {
                    return true;
                }

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

            case MotionEvent.ACTION_MOVE:
                // 滚动事件由手势检测器处理，返回true阻止视图滚动
                return true;
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