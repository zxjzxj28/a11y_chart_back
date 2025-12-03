// app/src/main/java/com/eagle/android/detector/DemoChartDetector.java
package com.eagle.android.detector;

import android.graphics.*;
import com.eagle.android.model.*;
import java.util.*;

public class DemoChartDetector implements ChartDetector {

    @Override
    public ChartResult detectSingleChart(Bitmap screenshot) {
        if (screenshot == null) return null;

        int W = screenshot.getWidth(), H = screenshot.getHeight();

        // 让“图表”位于屏幕中间（左右留 6%，上下留 24%）
        int marginX = (int)(W * 0.06f);
        int marginTop = (int)(H * 0.24f);
        int marginBottom = (int)(H * 0.26f);
        Rect chartRect = new Rect(
                marginX,
                marginTop,
                W - marginX,
                H - marginBottom
        );

        int cw = chartRect.width(), ch = chartRect.height();

        // 1) 生成一张“假的柱状图”位图（画坐标轴+网格+五根柱子）
        Bitmap chartBmp = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(chartBmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 背景
        c.drawColor(Color.rgb(248, 250, 252));

        // 坐标轴
        p.setColor(Color.rgb(180, 190, 200));
        p.setStrokeWidth(3f);
        c.drawLine(dp(48), ch - dp(36), cw - dp(20), ch - dp(36), p); // x 轴
        c.drawLine(dp(48), dp(20), dp(48), ch - dp(36), p);           // y 轴

        // 网格
        p.setColor(Color.argb(90, 200, 210, 220));
        p.setStrokeWidth(1.5f);
        for (int i=1; i<=4; i++) {
            float y = dp(20) + (ch - dp(56)) * i / 5f;
            c.drawLine(dp(48), y, cw - dp(20), y, p);
        }

        // 标题
        p.setColor(Color.rgb(60, 72, 88));
        p.setTextSize(dp(16));
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        c.drawText("Demo 柱状图（模拟数据）", dp(56), dp(28), p);

        // 柱子数据（五根柱，对应示例图表的时间轴）
        // 目标：让无障碍能够按像素坐标命中“1980 年，数量为 114”这一数据点
        int[] years = new int[]{1980, 1988, 1990, 1994, 1998};
        int[] values = new int[]{114, 220, 540, 360, 624};
        String[] labels = new String[]{"1980","1988","1990","1994","1998"};
        int n = values.length;

        // y 方向最大值
        int vmax = 400;
        float plotLeft = dp(64), plotRight = cw - dp(28);
        float plotBottom = ch - dp(36), plotTop = dp(36);

        float plotW = plotRight - plotLeft;
        float plotH = plotBottom - plotTop;

        // 柱宽 & 间距
        float barW = plotW / (n * 2f);
        float gap = barW;

        // 画柱子
        Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        bar.setColor(Color.rgb(99, 132, 255)); // 视觉上好看；只是演示

        Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        txt.setColor(Color.rgb(60,72,88));
        txt.setTextSize(dp(12));

        List<NodeSpec> nodes = new ArrayList<>();

        for (int i=0; i<n; i++) {
            float cx = plotLeft + (i * (barW + gap)) + gap + barW/2f;
            float val = values[i];
            float h = (val / (float)vmax) * plotH;
            float left = cx - barW/2f;
            float right = cx + barW/2f;
            float top = plotBottom - h;

            // 柱子
            c.drawRoundRect(new RectF(left, top, right, plotBottom), dp(6), dp(6), bar);

            // x 轴标签
            float tw = txt.measureText(labels[i]);
            c.drawText(labels[i], cx - tw/2f, plotBottom + dp(16), txt);

            // 为无障碍节点准备“屏幕坐标”的触达框：以柱子的顶部为中心，给 32dp 方块
            // —— 注意：NodeSpec 需要 SCREEN 坐标，所以 + chartRect 偏移
            int touch = dp(32);
            int sx = chartRect.left + Math.round(cx);
            int sy = chartRect.top + Math.round(top);
            Rect hit = new Rect(sx - touch/2, sy - touch/2, sx + touch/2, sy + touch/2);

            String speak = String.format(Locale.getDefault(),
                    "在%d年，数量为%d", years[i], values[i]);
            nodes.add(new NodeSpec(100 + i, hit, speak));
        }

        return new ChartResult(chartBmp, chartRect, nodes);
    }

    private static int dp(float v) {
        // 因为我们在“图表位图”里用的是 px，这里选择一个固定 dp≈px 的基准，足够演示
        return Math.round(v * 2f); // 约等于在 xxhdpi 机型的视觉大小
    }
}
