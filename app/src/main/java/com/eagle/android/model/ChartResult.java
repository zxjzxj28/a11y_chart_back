package com.eagle.android.model;

import android.graphics.Bitmap;
import android.graphics.Rect;
import java.util.List;


public class ChartResult {
    public final Bitmap chartBitmap; // 裁剪出的图表图像（用于小窗展示）
    public final Rect chartRectOnScreen; // 图表在屏幕上的矩形
    public final List<NodeSpec> nodes; // 每个元素的屏幕坐标与朗读文案
    public ChartResult(Bitmap bmp, Rect rect, List<NodeSpec> nodes) {
        this.chartBitmap = bmp; this.chartRectOnScreen = rect; this.nodes = nodes;
    }
}