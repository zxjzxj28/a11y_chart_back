package com.eagle.android.model;

/**
 * 数据点数据模型
 * 表示图表中单个数据点的信息
 */
public class DataPoint {
    private String label;       // 数据标签，如"1月"
    private String value;       // 数据值，如"45万元"
    private String description; // 完整描述，如"销售额为45万元，环比下降12%"

    public DataPoint() {
    }

    public DataPoint(String label, String value, String description) {
        this.label = label;
        this.value = value;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 用于无障碍播报的完整描述
     */
    public String getAccessibilityDescription() {
        StringBuilder sb = new StringBuilder();
        if (label != null && !label.isEmpty()) {
            sb.append(label);
        }
        if (value != null && !value.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("，");
            }
            sb.append(value);
        }
        if (description != null && !description.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("，");
            }
            sb.append(description);
        }
        return sb.toString();
    }
}
