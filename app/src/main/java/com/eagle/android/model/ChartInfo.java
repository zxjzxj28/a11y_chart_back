package com.eagle.android.model;

import android.graphics.Bitmap;

import java.util.List;

/**
 * 图表信息数据模型
 * 包含图表摘要、标题、图片和数据点列表
 */
public class ChartInfo {
    private String summary;           // 图表摘要描述
    private String chartTitle;        // 图表标题
    private Bitmap chartImage;        // 图表图片
    private List<DataPoint> dataPoints; // 数据点列表

    public ChartInfo() {
    }

    public ChartInfo(String summary, String chartTitle, Bitmap chartImage, List<DataPoint> dataPoints) {
        this.summary = summary;
        this.chartTitle = chartTitle;
        this.chartImage = chartImage;
        this.dataPoints = dataPoints;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getChartTitle() {
        return chartTitle;
    }

    public void setChartTitle(String chartTitle) {
        this.chartTitle = chartTitle;
    }

    public Bitmap getChartImage() {
        return chartImage;
    }

    public void setChartImage(Bitmap chartImage) {
        this.chartImage = chartImage;
    }

    public List<DataPoint> getDataPoints() {
        return dataPoints;
    }

    public void setDataPoints(List<DataPoint> dataPoints) {
        this.dataPoints = dataPoints;
    }

    /**
     * 从 ChartResult 和 NodeSpec 列表创建 ChartInfo
     */
    public static ChartInfo fromChartResult(ChartResult result, String summary, String chartTitle) {
        ChartInfo info = new ChartInfo();
        info.setSummary(summary);
        info.setChartTitle(chartTitle);
        info.setChartImage(result.chartBitmap);

        if (result.nodes != null) {
            java.util.ArrayList<DataPoint> dataPoints = new java.util.ArrayList<>();
            for (NodeSpec node : result.nodes) {
                DataPoint dp = new DataPoint();
                dp.setLabel(node.label);
                dp.setValue("");
                dp.setDescription(node.label);
                dataPoints.add(dp);
            }
            info.setDataPoints(dataPoints);
        }

        return info;
    }
}
