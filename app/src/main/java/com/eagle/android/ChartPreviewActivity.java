package com.eagle.android;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.eagle.android.view.ChartDataOverlay;

import java.util.ArrayList;
import java.util.List;

public class ChartPreviewActivity extends AppCompatActivity {

    private static final int IMAGE_WIDTH = 800;
    private static final int IMAGE_HEIGHT = 600;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chart_preview);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.chartPreviewRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageView chartImage = findViewById(R.id.chartImage);
        chartImage.setImageResource(R.drawable.smaple2);
        chartImage.setContentDescription(getString(R.string.chart_preview_content_desc));

        ChartDataOverlay overlay = findViewById(R.id.chartDataOverlay);
        List<ChartDataOverlay.DataPoint> points = buildDataPoints();
        overlay.setData(points, IMAGE_WIDTH, IMAGE_HEIGHT);

        TextView summary = findViewById(R.id.chartSummaryText);
        summary.setText(getString(R.string.chart_preview_summary));
        summary.setContentDescription(getString(R.string.chart_preview_summary));

        LinearLayout dataList = findViewById(R.id.dataList);
        renderDataList(dataList, points);

        Button closeButton = findViewById(R.id.btnCloseChart);
        closeButton.setOnClickListener(v -> finish());
    }

    private List<ChartDataOverlay.DataPoint> buildDataPoints() {
        List<ChartDataOverlay.DataPoint> points = new ArrayList<>();
        points.add(new ChartDataOverlay.DataPoint(1, "1980", 114.0, 115.125f, 495.875f, ""));
        points.add(new ChartDataOverlay.DataPoint(2, "1982", 131.0, 178.43055555555557f, 483.48333333333335f, ""));
        points.add(new ChartDataOverlay.DataPoint(3, "1984", 141.0, 241.73611111111114f, 476.1941176470589f, ""));
        points.add(new ChartDataOverlay.DataPoint(4, "1986", 127.0, 305.0416666666667f, 486.3990196078432f, ""));
        points.add(new ChartDataOverlay.DataPoint(5, "1988", 135.0, 368.3472222222223f, 480.5676470588236f, ""));
        points.add(new ChartDataOverlay.DataPoint(6, "1990", 576.0, 431.6527777777778f, 159.11323529411763f, ""));
        points.add(new ChartDataOverlay.DataPoint(7, "1992", 622.0, 494.95833333333337f, 125.58284313725488f, ""));
        points.add(new ChartDataOverlay.DataPoint(8, "1994", 311.0, 558.2638888888889f, 352.27745098039213f, ""));
        points.add(new ChartDataOverlay.DataPoint(9, "1996", 565.0, 621.5694444444445f, 167.1313725490196f, ""));
        points.add(new ChartDataOverlay.DataPoint(10, "1998", 624.0, 684.875f, 124.125f, ""));
        return points;
    }

    private void renderDataList(LinearLayout container, List<ChartDataOverlay.DataPoint> points) {
        LayoutInflater inflater = LayoutInflater.from(this);
        container.removeAllViews();
        for (ChartDataOverlay.DataPoint point : points) {
            TextView item = (TextView) inflater.inflate(R.layout.item_chart_data_point, container, false);
            item.setText(point.getDisplayLabel());
            item.setContentDescription(point.getContentDescription());
            container.addView(item);
        }
    }
}
