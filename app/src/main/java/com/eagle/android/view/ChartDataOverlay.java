package com.eagle.android.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Overlay for rendering chart data points with accessibility support.
 */
public class ChartDataOverlay extends View {

    public static class DataPoint {
        private final int id;
        private final String xLabel;
        private final double yValue;
        private final float pixelX;
        private final float pixelY;
        private final String classify;

        public DataPoint(int id, String xLabel, double yValue, float pixelX, float pixelY, String classify) {
            this.id = id;
            this.xLabel = xLabel;
            this.yValue = yValue;
            this.pixelX = pixelX;
            this.pixelY = pixelY;
            this.classify = classify == null ? "" : classify;
        }

        public int getId() {
            return id;
        }

        public float getPixelX() {
            return pixelX;
        }

        public float getPixelY() {
            return pixelY;
        }

        public String getDisplayLabel() {
            return String.format("%s / %.0f", xLabel, yValue);
        }

        public String getContentDescription() {
            String prefix = classify.isEmpty() ? "" : classify + "，";
            return prefix + xLabel + " 年，数值 " + yValue;
        }
    }

    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<DataPoint> points = new ArrayList<>();
    private int baseWidth = 1;
    private int baseHeight = 1;
    private int focusedId = ExploreByTouchHelper.INVALID_ID;

    private final ExploreByTouchHelper touchHelper = new ExploreByTouchHelper(this) {
        @Override
        protected int getVirtualViewAt(float x, float y) {
            for (DataPoint point : points) {
                if (getTouchBounds(point).contains(Math.round(x), Math.round(y))) {
                    return point.getId();
                }
            }
            return ExploreByTouchHelper.INVALID_ID;
        }

        @Override
        protected void getVisibleVirtualViews(@NonNull List<Integer> virtualViewIds) {
            virtualViewIds.clear();
            for (DataPoint point : points) {
                virtualViewIds.add(point.getId());
            }
        }

        @Override
        protected void onPopulateNodeForVirtualView(int virtualViewId, @NonNull AccessibilityNodeInfoCompat node) {
            DataPoint point = findPointById(virtualViewId);
            if (point == null) return;
            Rect bounds = getTouchBounds(point);
            node.setBoundsInParent(bounds);
            node.setContentDescription(point.getContentDescription());
            node.setClassName("android.view.View");
            node.setPackageName(getContext().getPackageName());
            node.setFocusable(true);
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
        }

        @Override
        protected boolean onPerformActionForVirtualView(int virtualViewId, int action, @Nullable android.os.Bundle args) {
            DataPoint point = findPointById(virtualViewId);
            if (point == null) return false;
            if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
                announceForAccessibility(point.getContentDescription());
                sendEventForVirtualView(virtualViewId, android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED);
                return true;
            } else if (action == AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS) {
                focusedId = virtualViewId;
                invalidate();
                return true;
            } else if (action == AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS) {
                focusedId = ExploreByTouchHelper.INVALID_ID;
                invalidate();
                return true;
            }
            return false;
        }
    };

    public ChartDataOverlay(Context context) {
        this(context, null);
    }

    public ChartDataOverlay(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ChartDataOverlay(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setColor(Color.parseColor("#6750A4"));

        labelPaint.setColor(Color.parseColor("#1F2937"));
        labelPaint.setTextSize(dp(11));

        focusPaint.setStyle(Paint.Style.STROKE);
        focusPaint.setStrokeWidth(dp(2));
        focusPaint.setColor(Color.parseColor("#FF8A00"));

        setFocusable(true);
        setFocusableInTouchMode(true);
        ViewCompat.setImportantForAccessibility(this, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
        ViewCompat.setAccessibilityDelegate(this, touchHelper);
    }

    public void setData(List<DataPoint> dataPoints, int imageWidth, int imageHeight) {
        points.clear();
        if (dataPoints != null) {
            points.addAll(dataPoints);
            Collections.sort(points, (a, b) -> a.getId() - b.getId());
        }
        baseWidth = Math.max(1, imageWidth);
        baseHeight = Math.max(1, imageHeight);
        focusedId = ExploreByTouchHelper.INVALID_ID;
        touchHelper.invalidateRoot();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (points.isEmpty()) return;

        for (DataPoint point : points) {
            float[] mapped = mapToView(point);
            float cx = mapped[0];
            float cy = mapped[1];
            canvas.drawCircle(cx, cy, dp(6), pointPaint);
            canvas.drawText(point.getDisplayLabel(), cx + dp(8), cy - dp(8), labelPaint);

            if (point.getId() == focusedId) {
                canvas.drawCircle(cx, cy, dp(12), focusPaint);
            }
        }
    }

    @Override
    public boolean dispatchHoverEvent(@NonNull MotionEvent event) {
        return touchHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    private float[] mapToView(DataPoint point) {
        float sx = getWidth() / (float) baseWidth;
        float sy = getHeight() / (float) baseHeight;
        return new float[]{point.getPixelX() * sx, point.getPixelY() * sy};
    }

    private Rect getTouchBounds(DataPoint point) {
        float[] mapped = mapToView(point);
        int r = (int) dp(16);
        int cx = Math.round(mapped[0]);
        int cy = Math.round(mapped[1]);
        return new Rect(cx - r, cy - r, cx + r, cy + r);
    }

    @Nullable
    private DataPoint findPointById(int id) {
        for (DataPoint point : points) {
            if (point.getId() == id) return point;
        }
        return null;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
