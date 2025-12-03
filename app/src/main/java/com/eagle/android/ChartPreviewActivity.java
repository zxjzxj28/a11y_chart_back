package com.eagle.android;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ChartPreviewActivity extends AppCompatActivity {

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

        Button summaryButton = findViewById(R.id.btnShowSummary);
        summaryButton.setOnClickListener(v -> showSummaryDialog());

        Button closeButton = findViewById(R.id.btnCloseChart);
        closeButton.setOnClickListener(v -> finish());
    }

    private void showSummaryDialog() {
        String summaryText = getString(R.string.chart_preview_summary);
        new AlertDialog.Builder(this)
                .setTitle("摘要")
                .setMessage(summaryText)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
