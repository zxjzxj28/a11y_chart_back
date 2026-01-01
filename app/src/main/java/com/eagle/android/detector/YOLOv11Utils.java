package com.eagle.android.detector;

import android.graphics.Bitmap;
import android.graphics.RectF;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * YOLOv11预处理和后处理工具类
 */
public class YOLOv11Utils {

    /**
     * 将Bitmap预处理为模型输入张量
     * @param bitmap 输入图像
     * @param inputSize 模型输入尺寸（例如640）
     * @return 归一化后的浮点数组 [1, 3, inputSize, inputSize]
     */
    public static float[] preprocessImage(Bitmap bitmap, int inputSize) {
        // 缩放图像到模型输入尺寸
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true);

        int[] pixels = new int[inputSize * inputSize];
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize);

        // NCHW格式：[1, 3, H, W]
        float[] inputData = new float[3 * inputSize * inputSize];

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            // RGB通道，归一化到0-1
            inputData[i] = ((pixel >> 16) & 0xFF) / 255.0f;                    // R
            inputData[inputSize * inputSize + i] = ((pixel >> 8) & 0xFF) / 255.0f;  // G
            inputData[2 * inputSize * inputSize + i] = (pixel & 0xFF) / 255.0f;      // B
        }

        if (resized != bitmap) {
            resized.recycle();
        }

        return inputData;
    }

    /**
     * 后处理YOLOv11输出
     * YOLOv11输出格式: [1, 4 + num_classes, num_boxes] 或转置后 [1, num_boxes, 4 + num_classes]
     *
     * @param output 模型输出数组
     * @param numClasses 类别数量
     * @param inputSize 模型输入尺寸
     * @param originalWidth 原始图像宽度
     * @param originalHeight 原始图像高度
     * @param confThreshold 置信度阈值
     * @param iouThreshold NMS的IoU阈值
     * @param classNames 类别名称数组
     * @return 检测结果列表
     */
    public static List<Detection> postprocess(
            float[] output,
            int numClasses,
            int inputSize,
            int originalWidth,
            int originalHeight,
            float confThreshold,
            float iouThreshold,
            String[] classNames) {

        List<Detection> detections = new ArrayList<>();

        // YOLOv11-nano典型输出形状: [1, 84, 8400] for COCO (80 classes + 4 box coords)
        // 或者对于自定义模型可能是 [1, numClasses+4, numBoxes]
        int numBoxes = output.length / (numClasses + 4);
        int stride = numClasses + 4;

        // 计算缩放因子
        float scaleX = (float) originalWidth / inputSize;
        float scaleY = (float) originalHeight / inputSize;

        for (int i = 0; i < numBoxes; i++) {
            // 提取边界框 (cx, cy, w, h) - YOLOv11使用中心点格式
            float cx = output[i * stride];
            float cy = output[i * stride + 1];
            float w = output[i * stride + 2];
            float h = output[i * stride + 3];

            // 找到最大类别得分
            int bestClassId = 0;
            float maxScore = 0;
            for (int c = 0; c < numClasses; c++) {
                float score = output[i * stride + 4 + c];
                if (score > maxScore) {
                    maxScore = score;
                    bestClassId = c;
                }
            }

            // 置信度过滤
            if (maxScore < confThreshold) {
                continue;
            }

            // 转换为角点格式 (x1, y1, x2, y2) 并缩放回原始尺寸
            float x1 = (cx - w / 2) * scaleX;
            float y1 = (cy - h / 2) * scaleY;
            float x2 = (cx + w / 2) * scaleX;
            float y2 = (cy + h / 2) * scaleY;

            // 裁剪到图像边界
            x1 = Math.max(0, Math.min(x1, originalWidth));
            y1 = Math.max(0, Math.min(y1, originalHeight));
            x2 = Math.max(0, Math.min(x2, originalWidth));
            y2 = Math.max(0, Math.min(y2, originalHeight));

            String className = (classNames != null && bestClassId < classNames.length)
                    ? classNames[bestClassId]
                    : "class_" + bestClassId;

            detections.add(new Detection(
                    bestClassId,
                    className,
                    maxScore,
                    new RectF(x1, y1, x2, y2)
            ));
        }

        // 应用非极大值抑制 (NMS)
        return applyNMS(detections, iouThreshold);
    }

    /**
     * 非极大值抑制 (NMS)
     */
    private static List<Detection> applyNMS(List<Detection> detections, float iouThreshold) {
        // 按置信度降序排序
        Collections.sort(detections, (a, b) -> Float.compare(b.confidence, a.confidence));

        List<Detection> result = new ArrayList<>();
        boolean[] suppressed = new boolean[detections.size()];

        for (int i = 0; i < detections.size(); i++) {
            if (suppressed[i]) continue;

            Detection det = detections.get(i);
            result.add(det);

            for (int j = i + 1; j < detections.size(); j++) {
                if (suppressed[j]) continue;

                Detection other = detections.get(j);
                // 只抑制同类别的检测框
                if (det.classId == other.classId) {
                    float iou = calculateIoU(det.boundingBox, other.boundingBox);
                    if (iou > iouThreshold) {
                        suppressed[j] = true;
                    }
                }
            }
        }

        return result;
    }

    /**
     * 计算两个边界框的IoU
     */
    private static float calculateIoU(RectF a, RectF b) {
        float x1 = Math.max(a.left, b.left);
        float y1 = Math.max(a.top, b.top);
        float x2 = Math.min(a.right, b.right);
        float y2 = Math.min(a.bottom, b.bottom);

        float intersection = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        float areaA = (a.right - a.left) * (a.bottom - a.top);
        float areaB = (b.right - b.left) * (b.bottom - b.top);
        float union = areaA + areaB - intersection;

        return union > 0 ? intersection / union : 0;
    }

    /**
     * 处理转置后的输出格式
     * 某些ONNX导出的YOLOv11模型输出格式为 [1, 4+numClasses, numBoxes]
     * 需要转置为 [1, numBoxes, 4+numClasses]
     */
    public static float[] transposeOutput(float[] output, int rows, int cols) {
        float[] transposed = new float[output.length];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                transposed[j * rows + i] = output[i * cols + j];
            }
        }
        return transposed;
    }
}
