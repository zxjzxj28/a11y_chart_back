package com.eagle.android.model;

import android.graphics.Rect;

public class NodeSpec {
    public final int id;
    public final Rect rectScreen; // 屏幕绝对坐标（px）
    public final String label;
    public NodeSpec(int id, Rect rectScreen, String label) {
        this.id = id; this.rectScreen = rectScreen; this.label = label;
    }
}