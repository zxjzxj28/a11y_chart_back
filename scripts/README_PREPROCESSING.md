# YOLOv11 图像预处理优化说明

## 概述

根据ONNX模型导出配置，已优化图像预处理流程以确保完全兼容：

```python
export_path = model.export(
    format='onnx',
    imgsz=640,           # 输入尺寸: 640x640
    simplify=True,       # 简化模型
    opset=12,            # ONNX Runtime Android支持的opset版本
    dynamic=False,       # 移动端使用静态尺寸更高效
    half=False,          # 不使用FP16（某些设备可能不支持）
)
```

## 主要改进

### 1. Letterbox预处理

**改进前**：
- 使用简单的`Bitmap.createScaledBitmap()`直接缩放到640x640
- 会扭曲图像长宽比
- 可能影响检测精度

**改进后**：
- 使用letterbox方法保持长宽比
- 灰色填充（RGB: 114, 114, 114）
- 居中放置缩放后的图像
- 与YOLOv11训练时的预处理一致

### 2. 输入格式规范

确保符合模型要求：
- **输入尺寸**：640x640（静态）
- **数据格式**：NCHW [1, 3, 640, 640]
- **通道顺序**：RGB
- **数值类型**：FP32 (float32)
- **归一化**：0-1范围（像素值/255.0）

## 代码变更

### YOLOv11Utils.java

添加了`createLetterboxBitmap()`方法：

```java
private static Bitmap createLetterboxBitmap(Bitmap source, int targetWidth, int targetHeight) {
    // 计算缩放比例，保持长宽比
    float scale = Math.min(
        (float) targetWidth / sourceWidth,
        (float) targetHeight / sourceHeight
    );

    // 缩放并居中
    // 填充灰色背景 (114, 114, 114)
    // ...
}
```

## 测试验证

### 测试图像：smaple2.png

- **原始尺寸**：800x600
- **Letterbox后**：640x640
- **缩放比例**：0.8000
- **Padding**：上下各80像素
- **输入张量**：[1, 3, 640, 640], float32, 范围[0.14, 1.00]

### 运行测试

```bash
# 仅测试预处理
python3 scripts/test_image_preprocessing.py \
    --image app/src/main/res/drawable/smaple2.png \
    --output /tmp/result.png

# 使用ONNX模型进行完整测试
python3 scripts/test_image_preprocessing.py \
    --image app/src/main/res/drawable/smaple2.png \
    --model app/src/main/assets/yolov11n_chart.onnx \
    --output /tmp/result.png \
    --conf 0.25 \
    --iou 0.45
```

## 使用建议

### 1. 导出模型

使用提供的导出脚本，确保参数一致：

```bash
python3 scripts/export_yolov11_onnx.py \
    --weights path/to/best.pt \
    --output yolov11n_chart.onnx \
    --imgsz 640 \
    --simplify
```

### 2. 部署到Android

将导出的模型放置到：
```
app/src/main/assets/yolov11n_chart.onnx
```

### 3. 在代码中使用

YOLOv11Detector会自动使用优化后的预处理：

```java
YOLOv11Detector detector = new YOLOv11Detector(context);
detector.initialize();
ChartResult result = detector.detectSingleChart(bitmap);
```

## 性能优化

### Letterbox的优势

1. **保持长宽比**：避免图像扭曲
2. **提高精度**：与训练时的数据增强一致
3. **减少假阳性**：避免因形变导致的误检

### 内存优化

- 及时回收临时Bitmap
- 使用ARGB_8888格式平衡质量和性能

## 技术规格对照

| 配置项 | 导出配置 | Java实现 | Python测试 |
|-------|---------|---------|-----------|
| 输入尺寸 | 640x640 | INPUT_SIZE=640 | imgsz=640 |
| 动态尺寸 | False | 静态shape | 静态shape |
| 精度 | FP32 | float32 | float32 |
| 归一化 | 0-1 | /255.0f | /255.0 |
| 通道顺序 | RGB | RGB | RGB |
| 数据格式 | NCHW | NCHW | NCHW |
| Letterbox填充 | 灰色(114) | 0xFF727272 | (114,114,114) |

## 下一步

1. **训练模型**：使用YOLOv11在图表数据集上训练
2. **导出ONNX**：使用export_yolov11_onnx.py脚本
3. **测试验证**：使用test_image_preprocessing.py验证
4. **集成部署**：将模型部署到Android应用

## 故障排除

### 问题：检测结果不准确

- 检查输入图像预处理是否正确
- 验证模型输入输出shape
- 调整置信度阈值CONF_THRESHOLD

### 问题：推理速度慢

- 确保使用静态尺寸（dynamic=False）
- 检查线程数设置（setIntraOpNumThreads）
- 考虑使用量化模型（如果设备支持）

### 问题：模型加载失败

- 确认ONNX文件在assets目录
- 检查文件名是否为yolov11n_chart.onnx
- 验证ONNX Runtime版本兼容性
