package com.eagle.android.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.eagle.android.model.ChartInfo;
import com.eagle.android.model.DataPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 提供用于展示 ChartAccessOverlay 的模拟数据示例，便于在接入阶段快速验证视图效果。
 */
public final class ChartAccessOverlayDemo {

    private ChartAccessOverlayDemo() {
    }

    /**
     * 使用一组模拟数据创建 ChartInfo，用于渲染无障碍图表视图。
     */
    public static ChartInfo createMockChartInfo() {
        ChartInfo chartInfo = new ChartInfo();
        chartInfo.setSummary("本季度销售额整体上升，Q4 明显高于前三季度，预计继续保持增长趋势。");
        chartInfo.setChartTitle("2024 年度季度销售额（万元）");
        chartInfo.setDataPoints(createMockDataPoints());
        chartInfo.setChartImage(createMockChartBitmap());
        return chartInfo;
    }

    /**
     * 在指定 Context 下直接弹出带有模拟数据的 ChartAccessOverlay。
     * 适合在无障碍服务的 onServiceConnected/onAccessibilityEvent 中快速验证效果。
     *
     * @return 已展示视图的 ChartAccessOverlayManager，便于后续关闭或调焦点。
     */
    public static ChartAccessOverlayManager showMockOverlay(Context context) {
        ChartAccessOverlayManager overlayManager = new ChartAccessOverlayManager(context);
        overlayManager.showAccessView(createMockChartInfo());
        return overlayManager;
    }

    private static List<DataPoint> createMockDataPoints() {
        List<DataPoint> dataPoints = new ArrayList<>(Arrays.asList(
                new DataPoint("Q1", "120", "第一季度销售额 120 万元"),
                new DataPoint("Q2", "156", "第二季度销售额 156 万元，同比增长 12%"),
                new DataPoint("Q3", "132", "第三季度销售额 132 万元，环比回落"),
                new DataPoint("Q4", "180", "第四季度销售额 180 万元，创全年最高")
        ));

        for (DataPoint point : dataPoints) {
            point.setValue(point.getValue() + " 万元");
        }
        return dataPoints;
    }

    private static Bitmap createMockChartBitmap() {
        int width = 1080;
        int height = 600;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 背景
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        canvas.drawRect(0, 0, width, height, paint);

        // 网格与坐标轴
        float leftMargin = 140f;
        float bottomMargin = 100f;
        float topMargin = 80f;
        float chartHeight = height - topMargin - bottomMargin;

        paint.setColor(Color.parseColor("#EEEEEE"));
        float[] gridValues = {0f, 50f, 100f, 150f, 200f};
        for (float value : gridValues) {
            float y = height - bottomMargin - (value / 200f) * chartHeight;
            canvas.drawLine(leftMargin, y, width - 80f, y, paint);
        }

        paint.setColor(Color.DKGRAY);
        paint.setStrokeWidth(4f);
        canvas.drawLine(leftMargin, topMargin, leftMargin, height - bottomMargin, paint);
        canvas.drawLine(leftMargin, height - bottomMargin, width - 80f, height - bottomMargin, paint);

        // 柱状图数据
        float[] values = {120f, 156f, 132f, 180f};
        String[] labels = {"Q1", "Q2", "Q3", "Q4"};
        float barWidth = 120f;
        float spacing = 80f;
        float startX = leftMargin + 40f;

        Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setColor(Color.parseColor("#4CAF50"));

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.DKGRAY);
        textPaint.setTextSize(36f);

        for (int i = 0; i < values.length; i++) {
            float value = values[i];
            float left = startX + i * (barWidth + spacing);
            float right = left + barWidth;
            float top = height - bottomMargin - (value / 200f) * chartHeight;
            float bottom = height - bottomMargin;
            canvas.drawRoundRect(new RectF(left, top, right, bottom), 16f, 16f, barPaint);

            // 数值
            String valueText = String.valueOf((int) value);
            float valueWidth = textPaint.measureText(valueText);
            canvas.drawText(valueText, left + (barWidth - valueWidth) / 2f, top - 12f, textPaint);

            // 标签
            float labelWidth = textPaint.measureText(labels[i]);
            canvas.drawText(labels[i], left + (barWidth - labelWidth) / 2f, bottom + 40f, textPaint);
        }

        // 标题
        textPaint.setTextSize(42f);
        String title = "模拟柱状图";
        float titleWidth = textPaint.measureText(title);
        canvas.drawText(title, (width - titleWidth) / 2f, topMargin - 20f, textPaint);

        return bitmap;
    }
}
