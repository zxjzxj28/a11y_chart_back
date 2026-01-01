#!/usr/bin/env python3
"""
测试YOLOv11图像预处理和推理脚本

此脚本用于验证图像预处理是否符合ONNX模型要求，并对测试图片进行检测
"""

import numpy as np
from PIL import Image, ImageDraw, ImageFont
import argparse
from pathlib import Path


def letterbox(im, new_shape=(640, 640), color=(114, 114, 114)):
    """
    使用letterbox方法调整图像大小，保持长宽比
    与Java代码中的createLetterboxBitmap方法对应

    Args:
        im: PIL Image对象
        new_shape: 目标尺寸 (width, height)
        color: 填充颜色 (R, G, B)

    Returns:
        调整后的图像和缩放信息
    """
    shape = im.size  # 当前尺寸 [width, height]
    if isinstance(new_shape, int):
        new_shape = (new_shape, new_shape)

    # 计算缩放比例（保持长宽比）
    r = min(new_shape[0] / shape[0], new_shape[1] / shape[1])

    # 计算缩放后的尺寸
    new_unpad = int(round(shape[0] * r)), int(round(shape[1] * r))

    # 计算padding
    dw, dh = new_shape[0] - new_unpad[0], new_shape[1] - new_unpad[1]
    dw /= 2  # 两边均分padding
    dh /= 2

    # 调整图像大小
    if shape != new_unpad:
        im = im.resize(new_unpad, Image.Resampling.BILINEAR)

    # 创建新图像并填充
    top, bottom = int(round(dh - 0.1)), int(round(dh + 0.1))
    left, right = int(round(dw - 0.1)), int(round(dw + 0.1))

    # 创建带padding的新图像
    new_im = Image.new('RGB', new_shape, color)
    new_im.paste(im, (left, top))

    return new_im, r, (dw, dh)


def preprocess_image(image_path, input_size=640):
    """
    预处理图像以符合YOLOv11 ONNX模型输入要求

    模型配置:
    - imgsz: 640x640 (静态尺寸)
    - dynamic: False
    - half: False (FP32)
    - opset: 12

    Args:
        image_path: 图像路径
        input_size: 模型输入尺寸

    Returns:
        input_tensor: NCHW格式的输入张量 [1, 3, 640, 640]
        original_image: 原始PIL图像
        letterbox_image: letterbox处理后的图像
    """
    # 加载图像
    original_image = Image.open(image_path).convert('RGB')
    print(f"原始图像尺寸: {original_image.size}")

    # Letterbox处理
    letterbox_image, scale, padding = letterbox(original_image, (input_size, input_size))
    print(f"Letterbox后尺寸: {letterbox_image.size}")
    print(f"缩放比例: {scale:.4f}")
    print(f"Padding (dw, dh): ({padding[0]:.1f}, {padding[1]:.1f})")

    # 转换为numpy数组
    img_array = np.array(letterbox_image, dtype=np.float32)

    # 归一化到0-1 (符合half=False的FP32要求)
    img_array = img_array / 255.0

    # 转换为CHW格式
    img_array = img_array.transpose(2, 0, 1)  # HWC -> CHW

    # 添加batch维度: CHW -> NCHW
    input_tensor = np.expand_dims(img_array, axis=0)

    print(f"输入张量形状: {input_tensor.shape}")
    print(f"输入张量数据类型: {input_tensor.dtype}")
    print(f"输入张量取值范围: [{input_tensor.min():.4f}, {input_tensor.max():.4f}]")

    return input_tensor, original_image, letterbox_image


def run_inference(model_path, input_tensor):
    """
    使用ONNX Runtime运行推理

    Args:
        model_path: ONNX模型路径
        input_tensor: 输入张量

    Returns:
        模型输出
    """
    try:
        import onnxruntime as ort
    except ImportError:
        print("警告: onnxruntime未安装，跳过推理")
        print("安装命令: pip install onnxruntime")
        return None

    # 创建推理会话
    session = ort.InferenceSession(model_path)

    # 获取输入输出信息
    input_name = session.get_inputs()[0].name
    output_names = [output.name for output in session.get_outputs()]

    print(f"\n模型输入名称: {input_name}")
    print(f"模型输出名称: {output_names}")

    # 运行推理
    outputs = session.run(output_names, {input_name: input_tensor})

    print(f"\n推理完成!")
    for i, output in enumerate(outputs):
        print(f"输出 {i} 形状: {output.shape}")

    return outputs


def postprocess_detections(outputs, conf_threshold=0.25, iou_threshold=0.45):
    """
    后处理检测结果

    Args:
        outputs: 模型输出
        conf_threshold: 置信度阈值
        iou_threshold: NMS IoU阈值

    Returns:
        检测框列表
    """
    if outputs is None or len(outputs) == 0:
        return []

    # YOLOv11输出格式: [1, 4+num_classes, num_boxes]
    output = outputs[0]
    print(f"\n原始输出形状: {output.shape}")

    # 转置为 [num_boxes, 4+num_classes]
    if len(output.shape) == 3:
        output = output[0].T  # [1, features, boxes] -> [boxes, features]

    print(f"转置后形状: {output.shape}")

    # 提取边界框和类别分数
    boxes = output[:, :4]  # cx, cy, w, h
    scores = output[:, 4:]  # 类别分数

    # 找到最大类别和分数
    class_ids = np.argmax(scores, axis=1)
    confidences = np.max(scores, axis=1)

    # 置信度过滤
    mask = confidences > conf_threshold
    boxes = boxes[mask]
    class_ids = class_ids[mask]
    confidences = confidences[mask]

    print(f"\n置信度阈值过滤后: {len(boxes)} 个检测框")

    detections = []
    for i in range(len(boxes)):
        box = boxes[i]
        detections.append({
            'box': box,  # [cx, cy, w, h]
            'class_id': int(class_ids[i]),
            'confidence': float(confidences[i])
        })

    return detections


def visualize_detections(image, detections, input_size=640, class_names=None):
    """
    可视化检测结果

    Args:
        image: PIL图像
        detections: 检测结果列表
        input_size: 模型输入尺寸
        class_names: 类别名称列表
    """
    if class_names is None:
        class_names = [
            "chart", "bar", "line_point", "pie_slice",
            "axis_label", "legend", "title", "data_label"
        ]

    # 创建绘图对象
    draw = ImageDraw.Draw(image)

    # 绘制检测框
    for det in detections:
        box = det['box']  # [cx, cy, w, h]
        class_id = det['class_id']
        confidence = det['confidence']

        # 转换为 [x1, y1, x2, y2]
        x1 = int(box[0] - box[2] / 2)
        y1 = int(box[1] - box[3] / 2)
        x2 = int(box[0] + box[2] / 2)
        y2 = int(box[1] + box[3] / 2)

        # 绘制矩形
        draw.rectangle([x1, y1, x2, y2], outline='red', width=2)

        # 绘制标签
        class_name = class_names[class_id] if class_id < len(class_names) else f"class_{class_id}"
        label = f"{class_name}: {confidence:.2f}"
        draw.text((x1, y1 - 10), label, fill='red')

    return image


def main():
    parser = argparse.ArgumentParser(description='测试YOLOv11图像预处理和推理')
    parser.add_argument('--image', type=str, required=True, help='测试图像路径')
    parser.add_argument('--model', type=str, help='ONNX模型路径（可选）')
    parser.add_argument('--output', type=str, help='输出图像路径（可选）')
    parser.add_argument('--imgsz', type=int, default=640, help='输入尺寸')
    parser.add_argument('--conf', type=float, default=0.25, help='置信度阈值')
    parser.add_argument('--iou', type=float, default=0.45, help='NMS IoU阈值')

    args = parser.parse_args()

    print("=" * 60)
    print("YOLOv11 图像预处理测试")
    print("=" * 60)
    print(f"\n配置信息:")
    print(f"  - 输入图像: {args.image}")
    print(f"  - ONNX模型: {args.model if args.model else '未指定（仅测试预处理）'}")
    print(f"  - 输入尺寸: {args.imgsz}x{args.imgsz}")
    print(f"  - 静态尺寸: True (dynamic=False)")
    print(f"  - 精度: FP32 (half=False)")
    print(f"  - ONNX opset: 12")
    print()

    # 1. 预处理图像
    print("步骤 1: 图像预处理")
    print("-" * 60)
    input_tensor, original_image, letterbox_image = preprocess_image(args.image, args.imgsz)

    # 保存letterbox处理后的图像
    if args.output:
        letterbox_output = args.output.replace('.', '_letterbox.')
        letterbox_image.save(letterbox_output)
        print(f"\nLetterbox图像已保存: {letterbox_output}")

    # 2. 运行推理（如果提供了模型）
    if args.model and Path(args.model).exists():
        print("\n步骤 2: 模型推理")
        print("-" * 60)
        outputs = run_inference(args.model, input_tensor)

        # 3. 后处理
        if outputs is not None:
            print("\n步骤 3: 后处理")
            print("-" * 60)
            detections = postprocess_detections(outputs, args.conf, args.iou)

            print(f"\n检测结果:")
            if len(detections) == 0:
                print("  未检测到任何对象")
            else:
                class_names = [
                    "chart", "bar", "line_point", "pie_slice",
                    "axis_label", "legend", "title", "data_label"
                ]
                for i, det in enumerate(detections):
                    class_name = class_names[det['class_id']] if det['class_id'] < len(class_names) else f"class_{det['class_id']}"
                    print(f"  {i+1}. {class_name}: {det['confidence']:.2%}")
                    print(f"     位置: cx={det['box'][0]:.1f}, cy={det['box'][1]:.1f}, w={det['box'][2]:.1f}, h={det['box'][3]:.1f}")

            # 可视化
            if args.output and len(detections) > 0:
                result_image = visualize_detections(letterbox_image.copy(), detections, args.imgsz)
                result_image.save(args.output)
                print(f"\n检测结果已保存: {args.output}")

    elif args.model:
        print(f"\n警告: 模型文件不存在: {args.model}")
        print("仅执行预处理验证")

    print("\n" + "=" * 60)
    print("测试完成!")
    print("=" * 60)


if __name__ == '__main__':
    main()
