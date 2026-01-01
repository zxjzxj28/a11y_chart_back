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

    // 图表类别定义 - 根据你训练的模型进行调整
    private static final String[] CLASS_NAMES = {
            "chart",        // 0: 整个图表区域
            "bar",          // 1: 柱状图的柱子
            "line_point",   // 2: 折线图的数据点
            "pie_slice",    // 3: 饼图的扇区
            "axis_label",   // 4: 坐标轴标签
            "legend",       // 5: 图例
            "title",        // 6: 标题
            "data_label"    // 7: 数据标签
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

            // 4. 获取输出
            float[] outputData = ((float[][][][]) results.get(0).getValue())[0][0][0];

            // 尝试获取正确的输出形状
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

            // 8. 转换为ChartResult
            return convertToChartResult(screenshot, detections, originalWidth, originalHeight);

        } catch (Exception e) {
            Log.e(TAG, "Error during inference", e);
            // 出错时回退到Demo检测器
            return new DemoChartDetector().detectSingleChart(screenshot);
        }
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

        // 找到主图表区域（classId == 0）
        Detection chartDetection = null;
        for (Detection det : detections) {
            if (det.classId == 0) { // "chart" 类别
                chartDetection = det;
                break;
            }
        }

        // 如果没有检测到图表区域，使用所有检测的边界框
        Rect chartRect;
        if (chartDetection != null) {
            chartRect = rectFToRect(chartDetection.boundingBox);
        } else {
            // 计算所有检测的包围盒
            float minX = width, minY = height, maxX = 0, maxY = 0;
            for (Detection det : detections) {
                minX = Math.min(minX, det.boundingBox.left);
                minY = Math.min(minY, det.boundingBox.top);
                maxX = Math.max(maxX, det.boundingBox.right);
                maxY = Math.max(maxY, det.boundingBox.bottom);
            }
            chartRect = new Rect((int) minX, (int) minY, (int) maxX, (int) maxY);
        }

        // 裁剪图表区域
        Bitmap chartBitmap = cropBitmap(screenshot, chartRect);

        // 转换检测结果为NodeSpec列表
        List<NodeSpec> nodes = new ArrayList<>();
        int nodeId = 100;

        for (Detection det : detections) {
            // 跳过整体图表框
            if (det.classId == 0) continue;

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
            case 1: // bar
                return String.format(Locale.getDefault(),
                        "柱状图柱子，置信度%.0f%%", det.confidence * 100);
            case 2: // line_point
                return String.format(Locale.getDefault(),
                        "折线图数据点，置信度%.0f%%", det.confidence * 100);
            case 3: // pie_slice
                return String.format(Locale.getDefault(),
                        "饼图扇区，置信度%.0f%%", det.confidence * 100);
            case 4: // axis_label
                return String.format(Locale.getDefault(),
                        "坐标轴标签，置信度%.0f%%", det.confidence * 100);
            case 5: // legend
                return String.format(Locale.getDefault(),
                        "图例，置信度%.0f%%", det.confidence * 100);
            case 6: // title
                return String.format(Locale.getDefault(),
                        "图表标题，置信度%.0f%%", det.confidence * 100);
            case 7: // data_label
                return String.format(Locale.getDefault(),
                        "数据标签，置信度%.0f%%", det.confidence * 100);
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
}
