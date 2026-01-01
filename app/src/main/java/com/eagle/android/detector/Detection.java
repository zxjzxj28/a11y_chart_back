package com.eagle.android.detector;

import android.graphics.RectF;

/**
 * 表示YOLOv11检测到的单个目标
 */
public class Detection {
    public final int classId;        // 类别ID
    public final String className;    // 类别名称
    public final float confidence;    // 置信度 (0-1)
    public final RectF boundingBox;   // 边界框 (像素坐标)

    public Detection(int classId, String className, float confidence, RectF boundingBox) {
        this.classId = classId;
        this.className = className;
        this.confidence = confidence;
        this.boundingBox = boundingBox;
    }

    @Override
    public String toString() {
        return String.format("%s (%.2f): [%.1f, %.1f, %.1f, %.1f]",
                className, confidence,
                boundingBox.left, boundingBox.top,
                boundingBox.right, boundingBox.bottom);
    }
}
