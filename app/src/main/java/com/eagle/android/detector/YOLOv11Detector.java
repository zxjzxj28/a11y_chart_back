package com.eagle.android.detector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;

import com.eagle.android.model.ChartResult;
import com.eagle.android.model.NodeSpec;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

/**
 * 基于YOLOv11-nano的图表检测器
 *
 * 使用ONNX Runtime在移动端本地运行YOLOv11-nano模型进行图表元素检测
 */
public class YOLOv11Detector implements ChartDetector {

    private static final String TAG = "YOLOv11Detector";

    // 模型配置
    private static final String MODEL_FILE = "yolov11n_chart.onnx"; // 模型文件名
    private static final int INPUT_SIZE = 640;                       // 模型输入尺寸
    private static final float CONF_THRESHOLD = 0.25f;               // 置信度阈值
    private static final float IOU_THRESHOLD = 0.45f;                // NMS IoU阈值

    // 图表类别定义 - 图表类型检测
    private static final String[] CLASS_NAMES = {
            "bar",   // 0: 柱状图
            "line",  // 1: 折线图
            "pie"    // 2: 饼图
    };

    private final Context context;
    private OrtEnvironment ortEnv;
    private OrtSession ortSession;
    private boolean isInitialized = false;

    private int numClasses;
    private boolean needsTranspose = true; // YOLOv11输出通常需要转置

    public YOLOv11Detector(Context context) {
        this.context = context.getApplicationContext();
        this.numClasses = CLASS_NAMES.length;
    }

    /**
     * 初始化ONNX Runtime会话
     * 需要在使用前调用
     */
    public synchronized boolean initialize() {
        if (isInitialized) {
            return true;
        }

        try {
            ortEnv = OrtEnvironment.getEnvironment();

            // 配置会话选项
            OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);

            // 设置线程数（根据设备性能调整）
            sessionOptions.setIntraOpNumThreads(4);

            // 从assets加载模型
            byte[] modelBytes = loadModelFromAssets(MODEL_FILE);
            if (modelBytes == null) {
                Log.e(TAG, "Failed to load model from assets: " + MODEL_FILE);
                return false;
            }

            ortSession = ortEnv.createSession(modelBytes, sessionOptions);
            isInitialized = true;

            Log.i(TAG, "YOLOv11 model loaded successfully");
            logModelInfo();

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize ONNX Runtime", e);
            return false;
        }
    }

    /**
     * 从assets目录加载模型文件
     */
    private byte[] loadModelFromAssets(String modelName) {
        try (InputStream is = context.getAssets().open(modelName)) {
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            return buffer;
        } catch (Exception e) {
            Log.e(TAG, "Error loading model from assets", e);
            return null;
        }
    }

    /**
     * 打印模型信息用于调试
     */
    private void logModelInfo() {
        if (ortSession == null) return;

        try {
            Log.d(TAG, "=== Model Input Info ===");
            for (String name : ortSession.getInputNames()) {
                Log.d(TAG, "Input: " + name);
            }

            Log.d(TAG, "=== Model Output Info ===");
            for (String name : ortSession.getOutputNames()) {
                Log.d(TAG, "Output: " + name);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error logging model info", e);
        }
    }

    @Override
    public ChartResult detectSingleChart(Bitmap screenshot) {
        if (screenshot == null) {
            Log.w(TAG, "Screenshot is null");
            return null;
        }

        // 确保模型已初始化
        if (!isInitialized && !initialize()) {
            Log.e(TAG, "Model not initialized, falling back to demo detector");
            return new DemoChartDetector().detectSingleChart(screenshot);
        }

        try {
            List<Detection> detections = runInference(screenshot);

            // 转换为ChartResult
            return convertToChartResult(screenshot, detections,
                    screenshot.getWidth(), screenshot.getHeight());

        } catch (Exception e) {
            Log.e(TAG, "Error during inference", e);
            // 出错时回退到Demo检测器
            return new DemoChartDetector().detectSingleChart(screenshot);
        }
    }

    /**
     * 进行检测并返回带边界框的图片
     *
     * @param screenshot 输入图片
     * @return 带边界框的图片，如果检测失败返回null
     */
    public Bitmap detectWithBoundingBoxes(Bitmap screenshot) {
        if (screenshot == null) {
            Log.w(TAG, "Screenshot is null");
            return null;
        }

        // 确保模型已初始化
        if (!isInitialized && !initialize()) {
            Log.e(TAG, "Model not initialized");
            return null;
        }

        try {
            List<Detection> detections = runInference(screenshot);

            if (detections.isEmpty()) {
                Log.d(TAG, "No detections found");
                return screenshot;
            }

            // 绘制边界框
            return drawBoundingBoxes(screenshot, detections);

        } catch (Exception e) {
            Log.e(TAG, "Error during inference", e);
            return null;
        }
    }

    /**
     * 运行模型推理，返回检测结果
     */
    private List<Detection> runInference(Bitmap screenshot) throws Exception {
        int originalWidth = screenshot.getWidth();
        int originalHeight = screenshot.getHeight();

        // 1. 预处理图像
        float[] inputData = YOLOv11Utils.preprocessImage(screenshot, INPUT_SIZE);

        // 2. 创建输入张量
        long[] inputShape = {1, 3, INPUT_SIZE, INPUT_SIZE};
        OnnxTensor inputTensor = OnnxTensor.createTensor(ortEnv,
                FloatBuffer.wrap(inputData), inputShape);

        // 3. 运行推理
        Map<String, OnnxTensor> inputs = new HashMap<>();
        String inputName = ortSession.getInputNames().iterator().next();
        inputs.put(inputName, inputTensor);

        OrtSession.Result results = ortSession.run(inputs);

        // 4. 获取输出 - 直接使用rawOutput避免错误的类型转换
        Object rawOutput = results.get(0).getValue();
        float[] flatOutput = flattenOutput(rawOutput);

        // 5. 如果需要，转置输出
        // YOLOv11输出格式: [1, numClasses+4, numBoxes] -> 需要转置为 [numBoxes, numClasses+4]
        int numBoxes = flatOutput.length / (numClasses + 4);
        if (needsTranspose && numBoxes > 0) {
            flatOutput = YOLOv11Utils.transposeOutput(flatOutput, numClasses + 4, numBoxes);
        }

        // 6. 后处理
        List<Detection> detections = YOLOv11Utils.postprocess(
                flatOutput,
                numClasses,
                INPUT_SIZE,
                originalWidth,
                originalHeight,
                CONF_THRESHOLD,
                IOU_THRESHOLD,
                CLASS_NAMES
        );

        // 7. 释放资源
        inputTensor.close();
        results.close();

        return detections;
    }

    /**
     * 将输出展平为一维数组
     */
    private float[] flattenOutput(Object output) {
        if (output instanceof float[]) {
            return (float[]) output;
        } else if (output instanceof float[][]) {
            float[][] arr = (float[][]) output;
            int total = 0;
            for (float[] row : arr) total += row.length;
            float[] result = new float[total];
            int idx = 0;
            for (float[] row : arr) {
                System.arraycopy(row, 0, result, idx, row.length);
                idx += row.length;
            }
            return result;
        } else if (output instanceof float[][][]) {
            float[][][] arr = (float[][][]) output;
            List<Float> list = new ArrayList<>();
            for (float[][] plane : arr) {
                for (float[] row : plane) {
                    for (float val : row) {
                        list.add(val);
                    }
                }
            }
            float[] result = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                result[i] = list.get(i);
            }
            return result;
        }
        // 默认返回空数组
        return new float[0];
    }

    /**
     * 将检测结果转换为ChartResult
     */
    private ChartResult convertToChartResult(Bitmap screenshot, List<Detection> detections,
                                             int width, int height) {
        if (detections.isEmpty()) {
            Log.d(TAG, "No detections found");
            return null;
        }

        // 找到置信度最高的图表检测
        Detection mainChartDetection = detections.get(0); // 已经按置信度排序
        for (Detection det : detections) {
            if (det.confidence > mainChartDetection.confidence) {
                mainChartDetection = det;
            }
        }

        // 使用主图表的边界框作为图表区域
        Rect chartRect = rectFToRect(mainChartDetection.boundingBox);

        // 裁剪图表区域
        Bitmap chartBitmap = cropBitmap(screenshot, chartRect);

        // 转换检测结果为NodeSpec列表（包含所有检测的图表）
        List<NodeSpec> nodes = new ArrayList<>();
        int nodeId = 100;

        for (Detection det : detections) {
            Rect hitRect = rectFToRect(det.boundingBox);
            String label = generateLabel(det);

            nodes.add(new NodeSpec(nodeId++, hitRect, label));
        }

        return new ChartResult(chartBitmap, chartRect, nodes);
    }

    /**
     * 生成无障碍朗读标签
     */
    private String generateLabel(Detection det) {
        switch (det.classId) {
            case 0: // bar
                return String.format(Locale.getDefault(),
                        "柱状图，置信度%.0f%%", det.confidence * 100);
            case 1: // line
                return String.format(Locale.getDefault(),
                        "折线图，置信度%.0f%%", det.confidence * 100);
            case 2: // pie
                return String.format(Locale.getDefault(),
                        "饼图，置信度%.0f%%", det.confidence * 100);
            default:
                return String.format(Locale.getDefault(),
                        "%s，置信度%.0f%%", det.className, det.confidence * 100);
        }
    }

    /**
     * RectF转Rect
     */
    private Rect rectFToRect(RectF rectF) {
        return new Rect(
                (int) rectF.left,
                (int) rectF.top,
                (int) rectF.right,
                (int) rectF.bottom
        );
    }

    /**
     * 裁剪Bitmap
     */
    private Bitmap cropBitmap(Bitmap source, Rect rect) {
        // 确保裁剪区域在图像范围内
        int x = Math.max(0, rect.left);
        int y = Math.max(0, rect.top);
        int width = Math.min(rect.width(), source.getWidth() - x);
        int height = Math.min(rect.height(), source.getHeight() - y);

        if (width <= 0 || height <= 0) {
            return source;
        }

        return Bitmap.createBitmap(source, x, y, width, height);
    }

    /**
     * 释放资源
     */
    public void release() {
        try {
            if (ortSession != null) {
                ortSession.close();
                ortSession = null;
            }
            if (ortEnv != null) {
                ortEnv.close();
                ortEnv = null;
            }
            isInitialized = false;
            Log.i(TAG, "YOLOv11 detector released");
        } catch (Exception e) {
            Log.e(TAG, "Error releasing resources", e);
        }
    }

    /**
     * 获取当前类别名称列表
     */
    public String[] getClassNames() {
        return CLASS_NAMES.clone();
    }

    /**
     * 设置是否需要转置输出
     */
    public void setNeedsTranspose(boolean needsTranspose) {
        this.needsTranspose = needsTranspose;
    }

    /**
     * 检查模型是否已初始化
     */
    public boolean isModelLoaded() {
        return isInitialized;
    }

    /**
     * 在图片上绘制检测边界框和标签
     *
     * @param bitmap 原始图片
     * @param detections 检测结果列表
     * @return 带边界框的新图片
     */
    public static Bitmap drawBoundingBoxes(Bitmap bitmap, List<Detection> detections) {
        // 创建可变的bitmap副本
        Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);

        // 定义不同类别的颜色
        int[] colors = {
            0xFFFF5722,  // 柱状图 - 深橙色
            0xFF2196F3,  // 折线图 - 蓝色
            0xFF4CAF50   // 饼图 - 绿色
        };

        // 绘制每个检测框
        for (Detection det : detections) {
            // 选择颜色
            int color = colors[det.classId % colors.length];

            // 创建画笔
            android.graphics.Paint boxPaint = new android.graphics.Paint();
            boxPaint.setColor(color);
            boxPaint.setStyle(android.graphics.Paint.Style.STROKE);
            boxPaint.setStrokeWidth(5.0f);

            // 绘制边界框
            canvas.drawRect(det.boundingBox, boxPaint);

            // 创建标签背景画笔
            android.graphics.Paint labelBgPaint = new android.graphics.Paint();
            labelBgPaint.setColor(color);
            labelBgPaint.setStyle(android.graphics.Paint.Style.FILL);

            // 创建文本画笔
            android.graphics.Paint textPaint = new android.graphics.Paint();
            textPaint.setColor(0xFFFFFFFF);  // 白色文字
            textPaint.setTextSize(40.0f);
            textPaint.setAntiAlias(true);
            textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

            // 准备标签文本
            String label = String.format(Locale.getDefault(),
                    "%s %.0f%%", det.className, det.confidence * 100);

            // 测量文本大小
            android.graphics.Rect textBounds = new android.graphics.Rect();
            textPaint.getTextBounds(label, 0, label.length(), textBounds);

            // 计算标签位置（边界框左上角）
            float labelX = det.boundingBox.left;
            float labelY = det.boundingBox.top - 10;

            // 确保标签不超出图片边界
            if (labelY < textBounds.height()) {
                labelY = det.boundingBox.top + textBounds.height() + 10;
            }

            // 绘制标签背景
            float padding = 10;
            canvas.drawRect(
                    labelX,
                    labelY - textBounds.height() - padding,
                    labelX + textBounds.width() + 2 * padding,
                    labelY + padding,
                    labelBgPaint
            );

            // 绘制标签文本
            canvas.drawText(label, labelX + padding, labelY, textPaint);
        }

        return mutableBitmap;
    }
}
