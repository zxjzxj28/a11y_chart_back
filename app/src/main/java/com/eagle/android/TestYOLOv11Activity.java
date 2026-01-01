package com.eagle.android;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.eagle.android.detector.YOLOv11Detector;
import com.eagle.android.model.ChartResult;
import com.eagle.android.model.NodeSpec;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * YOLOv11 模型测试界面
 * 用于测试 drawable/smaple2.png 的检测功能
 */
public class TestYOLOv11Activity extends AppCompatActivity {
    private static final String TAG = "TestYOLOv11Activity";

    private TextView tvLog;
    private ScrollView scrollView;
    private Button btnTest;
    private android.widget.ImageView ivResult;
    private ExecutorService executor;
    private Handler mainHandler;

    private StringBuilder logBuilder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_test_yolov11);

        tvLog = findViewById(R.id.tvLog);
        scrollView = findViewById(R.id.scrollView);
        btnTest = findViewById(R.id.btnTest);
        ivResult = findViewById(R.id.ivResult);
        Button btnClose = findViewById(R.id.btnClose);

        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        logBuilder = new StringBuilder();

        btnTest.setOnClickListener(v -> runDetectionTest());
        btnClose.setOnClickListener(v -> finish());

        addLog("=== YOLOv11 模型检测测试 ===\n");
        addLog("点击'开始测试'按钮运行检测\n");
    }

    /**
     * 运行检测测试
     */
    private void runDetectionTest() {
        btnTest.setEnabled(false);
        logBuilder.setLength(0);
        tvLog.setText("");

        addLog("=== 开始测试检测 smaple2.png ===\n\n");

        executor.execute(() -> {
            try {
                // 1. 加载测试图片
                addLog("📷 正在加载测试图片...\n");
                Bitmap testImage = BitmapFactory.decodeResource(
                    getResources(),
                    R.drawable.smaple2
                );

                if (testImage == null) {
                    addLog("❌ 错误: 无法加载测试图片\n");
                    enableTestButton();
                    return;
                }

                addLog("✓ 图片加载成功\n");
                addLog("  尺寸: " + testImage.getWidth() + "x" + testImage.getHeight() + "\n\n");

                // 2. 创建并初始化检测器
                addLog("🔧 正在初始化 YOLOv11 检测器...\n");
                YOLOv11Detector detector = new YOLOv11Detector(TestYOLOv11Activity.this);

                long initStartTime = System.currentTimeMillis();
                boolean initialized = detector.initialize();
                long initTime = System.currentTimeMillis() - initStartTime;

                if (!initialized) {
                    addLog("❌ 模型初始化失败 (耗时: " + initTime + "ms)\n");
                    addLog("请检查:\n");
                    addLog("  1. app/src/main/assets/yolov11n_chart.onnx 是否存在\n");
                    addLog("  2. ONNX Runtime 依赖是否正确配置\n");
                    addLog("  3. 查看 Logcat 获取详细错误信息\n");
                    testImage.recycle();
                    enableTestButton();
                    return;
                }

                addLog("✓ 模型初始化成功 (耗时: " + initTime + "ms)\n\n");

                // 3. 运行检测并绘制边界框
                addLog("🔍 开始运行检测...\n");
                long detectStartTime = System.currentTimeMillis();
                Bitmap resultImage = detector.detectWithBoundingBoxes(testImage);
                long detectTime = System.currentTimeMillis() - detectStartTime;

                addLog("✓ 检测完成 (耗时: " + detectTime + "ms)\n\n");

                // 显示检测结果图片
                if (resultImage != null) {
                    mainHandler.post(() -> ivResult.setImageBitmap(resultImage));
                    addLog("✓ 检测结果已显示在上方图片中\n\n");
                }

                // 4. 也运行标准检测来获取详细信息
                ChartResult result = detector.detectSingleChart(testImage);

                // 4. 分析结果
                addLog("📊 检测结果分析:\n");
                addLog("─────────────────────────────\n");

                if (result == null) {
                    addLog("❌ 检测失败: 返回结果为 null\n");
                    addLog("\n可能原因:\n");
                    addLog("  • 未检测到任何内容（置信度都低于25%）\n");
                    addLog("  • 推理过程出错\n");
                    addLog("  • 查看 Logcat 中的 YOLOv11Detector 标签获取详细信息\n");
                } else if (result.chartRectOnScreen == null) {
                    addLog("⚠️ 检测失败: 未找到图表区域\n");
                } else if (result.nodes == null || result.nodes.isEmpty()) {
                    addLog("⚠️ 检测失败: 未检测到任何图表\n\n");
                    addLog("\n可能原因:\n");
                    addLog("  • 图表置信度低于25%阈值\n");
                    addLog("  • 图片中没有bar/line/pie类型的图表\n");
                } else {
                    // 成功！
                    addLog("✅ 检测成功！\n\n");
                    addLog("图表区域: " + result.chartRectOnScreen + "\n");
                    addLog("检测到 " + result.nodes.size() + " 个图表:\n\n");

                    for (NodeSpec node : result.nodes) {
                        addLog(String.format(
                            "  [%d] %s\n      位置: %s\n\n",
                            node.id,
                            node.label,
                            node.rectScreen.toShortString()
                        ));
                    }

                    // 统计各类型图表数量
                    int barCount = 0;
                    int lineCount = 0;
                    int pieCount = 0;

                    for (NodeSpec node : result.nodes) {
                        String label = node.label;
                        if (label.contains("柱状图")) barCount++;
                        else if (label.contains("折线图")) lineCount++;
                        else if (label.contains("饼图")) pieCount++;
                    }

                    addLog("图表类型统计:\n");
                    if (barCount > 0) addLog("  • 柱状图: " + barCount + "\n");
                    if (lineCount > 0) addLog("  • 折线图: " + lineCount + "\n");
                    if (pieCount > 0) addLog("  • 饼图: " + pieCount + "\n");
                }

                addLog("\n─────────────────────────────\n");
                addLog("\n性能统计:\n");
                addLog("  • 模型初始化: " + initTime + "ms\n");
                addLog("  • 图表检测: " + detectTime + "ms\n");
                addLog("  • 总耗时: " + (initTime + detectTime) + "ms\n");

                addLog("\n💡 提示:\n");
                addLog("  • 使用 'adb logcat -s YOLOv11Detector' 查看详细日志\n");
                addLog("  • 置信度阈值: 25%\n");
                addLog("  • NMS IoU阈值: 45%\n");

                // 5. 清理资源
                detector.release();
                testImage.recycle();

                addLog("\n✓ 测试完成\n");

            } catch (Exception e) {
                Log.e(TAG, "测试过程出错", e);
                addLog("\n❌ 测试过程出错:\n");
                addLog(e.getMessage() + "\n");
                addLog("\n查看 Logcat 获取完整堆栈信息\n");
            } finally {
                enableTestButton();
            }
        });
    }

    /**
     * 添加日志到界面
     */
    private void addLog(String text) {
        Log.d(TAG, text.trim());
        logBuilder.append(text);

        mainHandler.post(() -> {
            tvLog.setText(logBuilder.toString());
            // 自动滚动到底部
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    /**
     * 启用测试按钮
     */
    private void enableTestButton() {
        mainHandler.post(() -> btnTest.setEnabled(true));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }
}
