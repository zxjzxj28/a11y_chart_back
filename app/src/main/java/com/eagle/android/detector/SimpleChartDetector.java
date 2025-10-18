package com.eagle.android.detector;

import android.content.res.Resources;
import android.graphics.*;
import com.eagle.android.model.*;
import java.util.*;


public class SimpleChartDetector implements ChartDetector {
    @Override public ChartResult detectSingleChart(Bitmap screenshot) {
        if (screenshot == null) return null;
        int W = screenshot.getWidth(), H = screenshot.getHeight();
// 取屏幕中部 60%×36% 作为“图表区域”（演示用）
        int cw = (int)(W * 0.60f), ch = (int)(H * 0.36f);
        int cx = (W - cw)/2, cy = (int)(H*0.30f);
        Rect chartRect = new Rect(cx, cy, cx+cw, cy+ch);
        Bitmap chartBmp = Bitmap.createBitmap(screenshot, chartRect.left, chartRect.top, chartRect.width(), chartRect.height());
// 造3个示例数据点（24dp 触达框）
        float density = Resources.getSystem().getDisplayMetrics().density;
        int touch = (int)(24 * density);
        List<NodeSpec> list = new ArrayList<>();
        for (int i=0;i<3;i++) {
            int px = chartRect.left + (i+1)*chartRect.width()/4;
            int py = chartRect.bottom - (i+2)*chartRect.height()/5;
            Rect r = new Rect(px - touch/2, py - touch/2, px + touch/2, py + touch/2);
            list.add(new NodeSpec(100+i, r, String.format(Locale.getDefault(), "示例点 %d：%d", i+1, (i+1)*100)));
        }
        return new ChartResult(chartBmp, chartRect, list);
    }
}