package com.eagle.android.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.eagle.android.R;
import com.eagle.android.model.ChartInfo;
import com.eagle.android.model.DataPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 提供用于展示 ChartAccessOverlay 的模拟数据示例，便于在接入阶段快速验证视图效果。
 */
public final class ChartAccessOverlayDemo {

    private ChartAccessOverlayDemo() {
    }

    /**
     * 使用一组模拟数据创建 ChartInfo，用于渲染无障碍图表视图。
     *
     * @param context 用于加载 drawable 资源的 Context
     */
    public static ChartInfo createMockChartInfo(Context context) {
        ChartInfo chartInfo = new ChartInfo();
        chartInfo.setSummary("这张饼图展示了不同支出项目的占比情况。整体平均占比约16.7%，其中服务占比最高达69.94%，交通占比最低仅0.13%，两者相差约69.7个百分点。分布呈现明显集中特征，主要集中于服务项目，其他项目占比相对较小。");
        chartInfo.setChartTitle("图表");
        chartInfo.setDataPoints(createMockDataPoints());
        chartInfo.setChartImage(loadChartBitmap(context));
        return chartInfo;
    }

    /**
     * 在指定 Context 下直接弹出带有模拟数据的 ChartAccessOverlay。
     * 适合在无障碍服务的 onServiceConnected/onAccessibilityEvent 中快速验证效果。
     *
     * @return 已展示视图的 ChartAccessOverlayManager，便于后续关闭或调焦点。
     */
    public static ChartAccessOverlayManager showMockOverlay(Context context) {
        ChartAccessOverlayManager overlayManager = new ChartAccessOverlayManager(context);
        overlayManager.showAccessView(createMockChartInfo(context));
        return overlayManager;
    }

    private static List<DataPoint> createMockDataPoints() {
        return new ArrayList<>(Arrays.asList(
                new DataPoint("交通", "0.13%", "交通的支出占比为0.13%"),
                new DataPoint("娱乐", "2.22%", "娱乐的支出占比为2.22%"),
                new DataPoint("发红包", "6.57%", "发红包的支出占比为6.57%"),
                new DataPoint("转账", "10.17%", "转账的支出占比为10.17%"),
                new DataPoint("餐饮", "10.96%", "餐饮的支出占比为10.96%"),
                new DataPoint("服务", "69.94%", "服务的支出占比为69.94%")
        ));
    }

    /**
     * 从 drawable 资源加载图表图片
     */
    private static Bitmap loadChartBitmap(Context context) {
        return BitmapFactory.decodeResource(context.getResources(), R.drawable.chart);
    }
}
