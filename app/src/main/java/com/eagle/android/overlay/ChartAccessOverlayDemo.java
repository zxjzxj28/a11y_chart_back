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
        chartInfo.setSummary("这是一个标题为支出构成的饼图，其中服务类占比最高，达到70.65%，交通占比最低，仅为0.14%，最高与最低相差约70个百分点，整体呈现高度集中的分布特征，服务类独占七成以上，餐饮和转账分别占11.07%和10.28%，发红包占6.64%，而娱乐和交通合计不足2%。");
        chartInfo.setChartTitle("支出构成");
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
                new DataPoint("交通", "0.14%", "交通的支出占比为0.14%"),
                new DataPoint("娱乐", "1.23%", "娱乐的支出占比为1.23%"),
                new DataPoint("发红包", "6.64%", "发红包的支出占比为6.64%"),
                new DataPoint("转账", "10.28%", "转账的支出占比为10.28%"),
                new DataPoint("餐饮", "11.07%", "餐饮的支出占比为11.07%"),
                new DataPoint("服务", "70.65%", "服务的支出占比为70.65%")
        ));
    }

    /**
     * 从 drawable 资源加载图表图片
     */
    private static Bitmap loadChartBitmap(Context context) {
        return BitmapFactory.decodeResource(context.getResources(), R.drawable.chart);
    }
}
