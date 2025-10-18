package com.eagle.android.detector;

import android.graphics.Bitmap;
import com.eagle.android.model.ChartResult;


public interface ChartDetector {
    ChartResult detectSingleChart(Bitmap screenshot);
}