#!/usr/bin/env python3
"""
YOLOv11-nano 模型导出脚本

此脚本用于将YOLOv11-nano模型导出为ONNX格式，以便在Android端使用ONNX Runtime运行。

使用方法:
    1. 安装依赖: pip install ultralytics onnx onnxruntime
    2. 运行脚本: python export_yolov11_onnx.py --weights path/to/best.pt --output yolov11n_chart.onnx

参数说明:
    --weights: 训练好的YOLOv11权重文件路径 (.pt)
    --output: 输出的ONNX文件路径
    --imgsz: 输入图像尺寸 (默认640)
    --simplify: 是否简化ONNX模型 (推荐开启)
"""

import argparse
from pathlib import Path


def export_to_onnx(weights_path: str, output_path: str, imgsz: int = 640, simplify: bool = True):
    """
    将YOLOv11模型导出为ONNX格式

    Args:
        weights_path: PyTorch权重文件路径
        output_path: 输出ONNX文件路径
        imgsz: 输入图像尺寸
        simplify: 是否简化模型
    """
    try:
        from ultralytics import YOLO
    except ImportError:
        print("错误: 请先安装ultralytics库")
        print("运行: pip install ultralytics")
        return False

    print(f"正在加载模型: {weights_path}")
    model = YOLO(weights_path)

    print(f"正在导出ONNX模型...")
    print(f"  - 输入尺寸: {imgsz}x{imgsz}")
    print(f"  - 简化模型: {simplify}")

    # 导出为ONNX格式
    export_path = model.export(
        format='onnx',
        imgsz=imgsz,
        simplify=simplify,
        opset=12,  # ONNX Runtime Android支持的opset版本
        dynamic=False,  # 移动端使用静态尺寸更高效
        half=False,  # 不使用FP16（某些设备可能不支持）
    )

    # 移动到指定输出路径
    if export_path and Path(export_path).exists():
        import shutil
        shutil.move(export_path, output_path)
        print(f"模型已导出到: {output_path}")

        # 打印模型信息
        import os
        size_mb = os.path.getsize(output_path) / (1024 * 1024)
        print(f"模型大小: {size_mb:.2f} MB")

        return True

    return False


def verify_onnx_model(onnx_path: str):
    """验证导出的ONNX模型"""
    try:
        import onnx
        import onnxruntime as ort
        import numpy as np
    except ImportError:
        print("警告: 无法验证模型，请安装 onnx 和 onnxruntime")
        return

    print(f"\n正在验证模型: {onnx_path}")

    # 加载并检查模型
    model = onnx.load(onnx_path)
    onnx.checker.check_model(model)
    print("ONNX模型格式验证通过")

    # 打印输入输出信息
    print("\n模型输入:")
    for input in model.graph.input:
        print(f"  - {input.name}: {[d.dim_value for d in input.type.tensor_type.shape.dim]}")

    print("\n模型输出:")
    for output in model.graph.output:
        print(f"  - {output.name}: {[d.dim_value for d in output.type.tensor_type.shape.dim]}")

    # 测试推理
    print("\n测试推理...")
    session = ort.InferenceSession(onnx_path)
    input_name = session.get_inputs()[0].name
    input_shape = session.get_inputs()[0].shape

    # 创建随机输入
    dummy_input = np.random.randn(*[1 if isinstance(d, str) else d for d in input_shape]).astype(np.float32)
    output = session.run(None, {input_name: dummy_input})

    print(f"推理成功! 输出形状: {[o.shape for o in output]}")


def main():
    parser = argparse.ArgumentParser(description='导出YOLOv11模型为ONNX格式')
    parser.add_argument('--weights', type=str, required=True, help='YOLOv11权重文件路径 (.pt)')
    parser.add_argument('--output', type=str, default='yolov11n_chart.onnx', help='输出ONNX文件路径')
    parser.add_argument('--imgsz', type=int, default=640, help='输入图像尺寸')
    parser.add_argument('--simplify', action='store_true', default=True, help='简化ONNX模型')
    parser.add_argument('--verify', action='store_true', default=True, help='验证导出的模型')

    args = parser.parse_args()

    success = export_to_onnx(
        weights_path=args.weights,
        output_path=args.output,
        imgsz=args.imgsz,
        simplify=args.simplify
    )

    if success and args.verify:
        verify_onnx_model(args.output)

    if success:
        print("\n" + "=" * 50)
        print("导出完成!")
        print("=" * 50)
        print(f"\n请将 {args.output} 复制到:")
        print("  app/src/main/assets/yolov11n_chart.onnx")
        print("\n然后重新构建Android应用。")


if __name__ == '__main__':
    main()
