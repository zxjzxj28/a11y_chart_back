package com.eagle.android.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.core.view.ViewCompat;

import com.eagle.android.model.NodeSpec;
import com.eagle.android.service.ChartA11yService;

import java.util.List;

public class ChartPanelView extends FrameLayout {

    private final ImageView image;
    private final NodeLayer nodeLayer;
    private final FrameLayout imageHost;   // 图像+NodeLayer的容器（仅上下留白）

    private final Button speakBtn; // 左上角 摘要（图像外部）
    private final Button exitBtn;  // 右下角 退出（图像外部）

    private CharSequence summaryText = "正在播报摘要";

    private volatile boolean isReading = false;

    private final Runnable onExit;

    // 新增：手势回调
    private final ChartA11yService.ChartGestureCallback gestureCallback;

    // 额外留白
    private final int gap8dp;
    // 兜底上下留白（按钮未测量前）
    private final int minTopPad, minBottomPad;

    // ============ 新增：多指手势检测变量 ============
    private int maxPointers = 0;  // 本次手势中出现的最大手指数
    private long gestureStartTime = 0;
    private final float[][] startXY = new float[10][2];  // 支持最多10根手指
    private long lastTapUpTime = 0;
    private int lastTapFingerCount = 0;
    private static final long DOUBLE_TAP_TIMEOUT = 300;
    private static final float SWIPE_THRESHOLD = 100f;
    private static final long TAP_TIMEOUT = 250;  // 轻触最大持续时间
    private boolean isSwipeDetected = false;  // 是否已检测到滑动

    // 修改构造函数
    public ChartPanelView(Context ctx, ChartPanelWindow.Tapper tapper, Runnable onExit,
                          ChartA11yService.ChartGestureCallback gestureCallback) {
        super(ctx);
        this.onExit = onExit;
        this.gestureCallback = gestureCallback;

        gap8dp = dp(8);
        minTopPad = dp(48);
        minBottomPad = dp(56);

        // ===== 背景 + 边框 =====
        GradientDrawable border = new GradientDrawable();
        border.setColor(0xFFFFFFFF);              // 白色背景，底部自然填充
        border.setStroke(dp(2), 0xFF9AA0A6);      // 边框
        border.setCornerRadius(dp(10));
        setBackground(border);

        // ===== 居中图像区域：imageHost（左右0，上下占位）=====
        imageHost = new FrameLayout(ctx);
        LayoutParams hostLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        hostLp.topMargin = minTopPad;
        hostLp.bottomMargin = minBottomPad;
        hostLp.leftMargin = 0;
        hostLp.rightMargin = 0;
        addView(imageHost, hostLp);

        // 图像：宽=容器宽（MATCH_PARENT），保持比例（WRAP_CONTENT+adjustViewBounds），居中
        image = new ImageView(ctx);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams imgLp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        imgLp.gravity = Gravity.CENTER; // 垂直/水平都居中
        imageHost.addView(image, imgLp);

        // 覆盖层（用于可点节点/可视化）
        nodeLayer = new NodeLayer(ctx, tapper);
        imageHost.addView(nodeLayer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 左上角 摘要（图像外部）
        speakBtn = new Button(ctx);
        speakBtn.setText("摘要");  // 只设置 text，避免与 CD 双读
        speakBtn.setAllCaps(false);
        speakBtn.setContentDescription("播报摘要");
        ViewCompat.setImportantForAccessibility(speakBtn, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
        LayoutParams tl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        tl.gravity = Gravity.TOP | Gravity.START;
        tl.topMargin = gap8dp;
        tl.setMarginStart(gap8dp);
        addView(speakBtn, tl);

        // 右下角 退出（图像外部）
        exitBtn = new Button(ctx);
        exitBtn.setText("退出");
        exitBtn.setContentDescription("退出当前图表视图");
        exitBtn.setAllCaps(false);
        ViewCompat.setImportantForAccessibility(exitBtn, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
        LayoutParams br = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        br.gravity = Gravity.BOTTOM | Gravity.END;
        br.bottomMargin = gap8dp;
        br.setMarginEnd(gap8dp);
        addView(exitBtn, br);

        // 摘要开/停
        speakBtn.setOnClickListener(v -> {
            if (summaryText == null || summaryText.length() == 0) {
                announceForAccessibility("没有可播报的摘要。");
                return;
            }
            if (isReading) {
                isReading = false;
                announceForAccessibility("已停止摘要。");
            } else {
                isReading = true;
                announceForAccessibility("开始播报摘要。");
                announceLong(summaryText.toString(), () -> isReading);
            }
        });

        // 退出
        exitBtn.setOnClickListener(v -> { if (onExit != null) onExit.run(); });

        // 根据按钮“真实高度”动态让出上下空间（左右为0 ⇒ 图表宽度=窗口宽度）
        ViewTreeObserver.OnGlobalLayoutListener adjustMarginsOnce = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                int topH = speakBtn.getMeasuredHeight();
                int bottomH = exitBtn.getMeasuredHeight();

                int needTop = Math.max(minTopPad, topH + gap8dp);
                int needBottom = Math.max(minBottomPad, bottomH + gap8dp);

                LayoutParams lp = (LayoutParams) imageHost.getLayoutParams();
                boolean changed = lp.topMargin != needTop || lp.bottomMargin != needBottom;
                if (changed) {
                    lp.topMargin = needTop;
                    lp.bottomMargin = needBottom;
                    imageHost.setLayoutParams(lp);
                }
                getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        };
        getViewTreeObserver().addOnGlobalLayoutListener(adjustMarginsOnce);

        setElevation(8 * ctx.getResources().getDisplayMetrics().density);
    }

    // ============ 新增：重写 dispatchTouchEvent 处理多指手势 ============
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int pointerCount = event.getPointerCount();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                // 新手势开始
                maxPointers = 1;
                gestureStartTime = event.getEventTime();
                isSwipeDetected = false;
                startXY[0][0] = event.getX(0);
                startXY[0][1] = event.getY(0);
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                // 记录新增的手指
                maxPointers = Math.max(maxPointers, pointerCount);
                int newPointerIndex = event.getActionIndex();
                if (newPointerIndex < startXY.length) {
                    startXY[newPointerIndex][0] = event.getX(newPointerIndex);
                    startXY[newPointerIndex][1] = event.getY(newPointerIndex);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                // 检测滑动（在移动过程中）
                if (!isSwipeDetected && maxPointers >= 2) {
                    isSwipeDetected = detectSwipe(event);
                    if (isSwipeDetected) {
                        return true; // 消费滑动事件
                    }
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                // 有手指抬起，但还有其他手指按下
                // 不在这里处理，等到所有手指都抬起
                break;

            case MotionEvent.ACTION_UP:
                // 所有手指都抬起，检测手势
                if (maxPointers >= 2) {
                    boolean handled = handleMultiFingerGesture(event);
                    maxPointers = 0;
                    if (handled) {
                        return true;
                    }
                }
                maxPointers = 0;
                break;

            case MotionEvent.ACTION_CANCEL:
                maxPointers = 0;
                isSwipeDetected = false;
                break;
        }

        // 单指事件正常传递给子 View（让 ExploreByTouchHelper 处理焦点）
        return super.dispatchTouchEvent(event);
    }

    /**
     * 在 ACTION_MOVE 时检测滑动手势
     */
    private boolean detectSwipe(MotionEvent event) {
        if (gestureCallback == null) return false;

        // 计算所有活跃手指的平均移动距离
        int activeCount = Math.min(event.getPointerCount(), maxPointers);
        float totalDx = 0, totalDy = 0;

        for (int i = 0; i < activeCount; i++) {
            if (i < startXY.length) {
                totalDx += event.getX(i) - startXY[i][0];
                totalDy += event.getY(i) - startXY[i][1];
            }
        }

        float avgDx = totalDx / activeCount;
        float avgDy = totalDy / activeCount;
        float distance = (float) Math.sqrt(avgDx * avgDx + avgDy * avgDy);

        // 如果移动距离超过阈值，触发滑动
        if (distance >= SWIPE_THRESHOLD) {
            int direction = getDirection(avgDx, avgDy);

            if (maxPointers == 2) {
                gestureCallback.onTwoFingerSwipe(direction);
                return true;
            } else if (maxPointers == 3) {
                gestureCallback.onThreeFingerSwipe(direction);
                return true;
            }
        }

        return false;
    }

    /**
     * 在 ACTION_UP 时处理多指手势（主要是双击）
     */
    private boolean handleMultiFingerGesture(MotionEvent event) {
        if (gestureCallback == null) return false;

        // 如果已经检测到滑动，不再处理双击
        if (isSwipeDetected) {
            return true;
        }

        long gestureDuration = event.getEventTime() - gestureStartTime;

        // 检查是否是轻触（短时间且没有大幅移动）
        if (gestureDuration < TAP_TIMEOUT) {
            // 检测双击
            long timeSinceLastTap = event.getEventTime() - lastTapUpTime;

            if (timeSinceLastTap < DOUBLE_TAP_TIMEOUT && lastTapFingerCount == maxPointers) {
                // 这是双击！
                if (maxPointers == 2) {
                    gestureCallback.onTwoFingerDoubleTap();
                } else if (maxPointers == 3) {
                    gestureCallback.onThreeFingerDoubleTap();
                }
                // 重置双击状态
                lastTapUpTime = 0;
                lastTapFingerCount = 0;
                return true;
            } else {
                // 这是第一次轻触，记录状态等待可能的第二次
                lastTapUpTime = event.getEventTime();
                lastTapFingerCount = maxPointers;
            }
        }

        return false;
    }

    private int getDirection(float dx, float dy) {
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? 1 : 0; // 右1 左0
        } else {
            return dy > 0 ? 3 : 2; // 下3 上2
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    public void bindData(Bitmap bmp, Rect chartRectOnScreen, List<NodeSpec> nodes) {
        bindData(bmp, chartRectOnScreen, nodes, null);
    }

    public void bindData(Bitmap bmp, Rect chartRectOnScreen, List<NodeSpec> nodes, CharSequence summary) {
        this.summaryText = summary;
        image.setImageBitmap(bmp);

        // 设置无障碍事件回调
        nodeLayer.setAccessibilityEventCallback(new NodeLayer.AccessibilityEventCallback() {
            @Override
            public void onDoubleTap(float x, float y) {
                // 双击事件（TalkBack双击触发）
                System.out.println("ChartPanelView: 双击事件 at (" + x + ", " + y + ")");
            }

            @Override
            public void onLongPress(float x, float y) {
                // 长按事件（TalkBack长按触发）
                System.out.println("ChartPanelView: 长按事件 at (" + x + ", " + y + ")");
            }

            @Override
            public void onScroll(int direction) {
                // 滚动事件（0=向前, 1=向后）- 不实际滚动视图
                System.out.println("ChartPanelView: 滚动事件 direction=" + (direction == 0 ? "向前" : "向后"));
            }
        });

        // 不改焦点；首帧抑制内容变更事件，避免开窗双播报
        nodeLayer.setChartBitmap(
                bmp, chartRectOnScreen, nodes
        );
    }

    private void announceLong(String text, java.util.function.Supplier<Boolean> keep) {
        final int CHUNK = 250;
        int i = 0;
        while (i < text.length() && keep.get()) {
            int end = Math.min(i + CHUNK, text.length());
            int best = -1;
            for (int j = end; j > i + 50; j--) {
                char c = text.charAt(j - 1);
                if (c == '。' || c == '！' || c == '？' || c == '.' || c == '\n' || c == ' ') { best = j; break; }
            }
            if (best == -1) best = end;
            String part = text.substring(i, best).trim();
            if (!part.isEmpty() && keep.get()) {
                announceForAccessibility(part);
                try { Thread.sleep(60); } catch (InterruptedException ignored) {}
            }
            i = best;
        }
        isReading = false;
    }
}
