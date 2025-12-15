package com.eagle.android.overlay;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.eagle.android.R;
import com.eagle.android.model.ChartInfo;
import com.eagle.android.model.DataPoint;

import java.util.List;

/**
 * 图表无障碍访问视图管理器
 * 管理全屏悬浮窗口的显示和隐藏
 */
public class ChartAccessOverlayManager {

    private final Context context;
    private WindowManager windowManager;
    private View overlayView;
    private DataPointAdapter dataPointAdapter;
    private OnExitListener exitListener;

    // 视图组件引用
    private LinearLayout summaryCard;
    private TextView summaryText;
    private TextView chartTitle;
    private ImageView chartImage;
    private TextView dataListTitle;
    private RecyclerView dataPointList;
    private LinearLayout exitButton;

    /**
     * 退出监听器
     */
    public interface OnExitListener {
        void onExit();
    }

    public ChartAccessOverlayManager(Context context) {
        this.context = context;
    }

    public void setOnExitListener(OnExitListener listener) {
        this.exitListener = listener;
    }

    /**
     * 检查视图是否正在显示
     */
    public boolean isShowing() {
        return overlayView != null;
    }

    /**
     * 显示无障碍访问视图
     * @param chartInfo 图表信息数据
     */
    public void showAccessView(ChartInfo chartInfo) {
        if (overlayView != null) {
            // 已经显示，更新内容
            updateContent(chartInfo);
            return;
        }

        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        // 创建窗口参数
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;

        // 加载布局
        LayoutInflater inflater = LayoutInflater.from(context);
        overlayView = inflater.inflate(R.layout.overlay_chart_access, null);

        // 初始化视图组件
        initViews();

        // 绑定数据
        bindData(chartInfo);

        // 设置事件监听
        setupListeners();

        // 设置焦点导航顺序
        setupFocusOrder(chartInfo);

        // 添加到窗口
        windowManager.addView(overlayView, params);

        // 请求焦点到摘要节点
        summaryCard.requestFocus();
        summaryCard.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }

    /**
     * 初始化视图组件引用
     */
    private void initViews() {
        summaryCard = overlayView.findViewById(R.id.summary_card);
        summaryText = overlayView.findViewById(R.id.summary_text);
        chartTitle = overlayView.findViewById(R.id.chart_title);
        chartImage = overlayView.findViewById(R.id.chart_image);
        dataListTitle = overlayView.findViewById(R.id.data_list_title);
        dataPointList = overlayView.findViewById(R.id.data_point_list);
        exitButton = overlayView.findViewById(R.id.exit_button);

        // 设置RecyclerView
        dataPointList.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        dataPointAdapter = new DataPointAdapter();
        dataPointList.setAdapter(dataPointAdapter);

        // 监听数据点焦点变化，更新标题
        dataPointAdapter.setOnDataPointFocusListener((position, total) -> {
            dataListTitle.setText(String.format("数据详情（%d/%d）", position, total));
        });
    }

    /**
     * 绑定图表数据
     */
    private void bindData(ChartInfo chartInfo) {
        // 摘要
        String summary = chartInfo.getSummary();
        if (summary != null && !summary.isEmpty()) {
            summaryText.setText(summary);
            summaryCard.setContentDescription("图表摘要：" + summary);
        } else {
            summaryText.setText("暂无摘要信息");
            summaryCard.setContentDescription("图表摘要：暂无摘要信息");
        }

        // 图表标题
        String title = chartInfo.getChartTitle();
        if (title != null && !title.isEmpty()) {
            chartTitle.setText(title);
            chartTitle.setVisibility(View.VISIBLE);
        } else {
            chartTitle.setVisibility(View.GONE);
        }

        // 图表图片
        if (chartInfo.getChartImage() != null) {
            chartImage.setImageBitmap(chartInfo.getChartImage());
        }

        // 数据点列表
        List<DataPoint> dataPoints = chartInfo.getDataPoints();
        if (dataPoints != null && !dataPoints.isEmpty()) {
            dataPointAdapter.setDataPoints(dataPoints);
            dataListTitle.setText(String.format("数据详情（1/%d）", dataPoints.size()));
        } else {
            dataListTitle.setText("数据详情（0/0）");
        }
    }

    /**
     * 更新视图内容（视图已显示时调用）
     */
    private void updateContent(ChartInfo chartInfo) {
        bindData(chartInfo);
        setupFocusOrder(chartInfo);
    }

    /**
     * 设置事件监听
     */
    private void setupListeners() {
        // 退出按钮点击事件
        exitButton.setOnClickListener(v -> dismissAccessView());

        // 摘要卡片点击事件（可用于播报摘要）
        summaryCard.setOnClickListener(v -> {
            String summary = summaryText.getText().toString();
            if (!summary.isEmpty()) {
                summaryCard.announceForAccessibility(summary);
            }
        });
    }

    /**
     * 设置焦点导航顺序
     * 顺序：摘要节点 → 数据点1 → 数据点2 → ... → 数据点N → 退出节点
     */
    private void setupFocusOrder(ChartInfo chartInfo) {
        List<DataPoint> dataPoints = chartInfo.getDataPoints();

        if (dataPoints == null || dataPoints.isEmpty()) {
            // 没有数据点时，摘要直接导航到退出
            summaryCard.setNextFocusDownId(R.id.exit_button);
            exitButton.setNextFocusUpId(R.id.summary_card);
        } else {
            // 摘要导航到数据点列表
            summaryCard.setNextFocusDownId(R.id.data_point_list);

            // 退出按钮的向上焦点导航到数据点列表
            exitButton.setNextFocusUpId(R.id.data_point_list);
        }

        // 设置退出按钮的向下焦点循环到摘要
        exitButton.setNextFocusDownId(R.id.summary_card);

        // 设置摘要的向上焦点循环到退出
        summaryCard.setNextFocusUpId(R.id.exit_button);
    }

    /**
     * 关闭无障碍访问视图
     */
    public void dismissAccessView() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (IllegalArgumentException e) {
                // 视图可能已经被移除
            }
            overlayView = null;

            if (exitListener != null) {
                exitListener.onExit();
            }
        }
    }

    /**
     * 获取当前焦点所在的数据点位置
     * @return 焦点位置（0-based），如果没有焦点则返回-1
     */
    public int getCurrentFocusedDataPointPosition() {
        if (dataPointAdapter != null) {
            return dataPointAdapter.getFocusedPosition();
        }
        return -1;
    }

    /**
     * 请求焦点到摘要卡片
     */
    public void focusSummaryCard() {
        if (summaryCard != null) {
            summaryCard.requestFocus();
            summaryCard.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        }
    }

    /**
     * 请求焦点到退出按钮
     */
    public void focusExitButton() {
        if (exitButton != null) {
            exitButton.requestFocus();
            exitButton.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        }
    }

    /**
     * 请求焦点到指定数据点
     * @param position 数据点位置（0-based）
     */
    public void focusDataPoint(int position) {
        if (dataPointList != null && dataPointAdapter != null) {
            if (position >= 0 && position < dataPointAdapter.getItemCount()) {
                dataPointList.scrollToPosition(position);
                dataPointList.post(() -> {
                    RecyclerView.ViewHolder holder = dataPointList.findViewHolderForAdapterPosition(position);
                    if (holder != null && holder.itemView != null) {
                        holder.itemView.requestFocus();
                        holder.itemView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
                    }
                });
            }
        }
    }
}
