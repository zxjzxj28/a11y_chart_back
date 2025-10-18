package com.eagle.android.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.core.view.ViewCompat;

import com.eagle.android.model.NodeSpec;

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

    // 额外留白
    private final int gap8dp;
    // 兜底上下留白（按钮未测量前）
    private final int minTopPad, minBottomPad;

    public ChartPanelView(Context ctx, ChartPanelWindow.Tapper tapper, Runnable onExit) {
        super(ctx);
        this.onExit = onExit;

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

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    public void bindData(Bitmap bmp, Rect chartRectOnScreen, List<NodeSpec> nodes) {
        bindData(bmp, chartRectOnScreen, nodes, null);
    }

    public void bindData(Bitmap bmp, Rect chartRectOnScreen, List<NodeSpec> nodes, CharSequence summary) {
        this.summaryText = summary;
        image.setImageBitmap(bmp);

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
