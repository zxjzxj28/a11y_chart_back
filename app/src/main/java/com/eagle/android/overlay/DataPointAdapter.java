package com.eagle.android.overlay;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.eagle.android.R;
import com.eagle.android.model.DataPoint;

import java.util.List;

/**
 * 数据点列表适配器
 * 用于在无障碍视图中展示图表数据点
 */
public class DataPointAdapter extends RecyclerView.Adapter<DataPointAdapter.ViewHolder> {

    private List<DataPoint> dataPoints;
    private int focusedPosition = -1;
    private OnDataPointFocusListener focusListener;

    /**
     * 数据点焦点变化监听器
     */
    public interface OnDataPointFocusListener {
        /**
         * 当数据点获得焦点时回调
         * @param position 当前焦点位置（从1开始）
         * @param total 数据点总数
         */
        void onDataPointFocused(int position, int total);
    }

    public DataPointAdapter() {
    }

    public DataPointAdapter(List<DataPoint> dataPoints) {
        this.dataPoints = dataPoints;
    }

    public void setDataPoints(List<DataPoint> dataPoints) {
        this.dataPoints = dataPoints;
        notifyDataSetChanged();
    }

    public void setOnDataPointFocusListener(OnDataPointFocusListener listener) {
        this.focusListener = listener;
    }

    public int getFocusedPosition() {
        return focusedPosition;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_data_point_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DataPoint dataPoint = dataPoints.get(position);

        // 设置数据标签
        String label = dataPoint.getLabel();
        holder.labelText.setText(label != null ? label : "");

        // 设置数据值
        String value = dataPoint.getValue();
        holder.valueText.setText(value != null ? value : "");
        holder.valueText.setVisibility(value != null && !value.isEmpty() ? View.VISIBLE : View.GONE);

        // 设置数据描述
        String description = dataPoint.getDescription();
        holder.descText.setText(description != null ? description : "");
        holder.descText.setVisibility(description != null && !description.isEmpty() ? View.VISIBLE : View.GONE);

        // 设置无障碍描述
        holder.itemView.setContentDescription(dataPoint.getAccessibilityDescription());

        // 焦点变化监听
        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            int adapterPosition = holder.getAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION) return;

            if (hasFocus) {
                focusedPosition = adapterPosition;
                if (focusListener != null) {
                    focusListener.onDataPointFocused(adapterPosition + 1, getItemCount());
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return dataPoints != null ? dataPoints.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView labelText;
        final TextView valueText;
        final TextView descText;

        ViewHolder(View itemView) {
            super(itemView);
            labelText = itemView.findViewById(R.id.data_label);
            valueText = itemView.findViewById(R.id.data_value);
            descText = itemView.findViewById(R.id.data_description);
        }
    }
}
