package com.eagle.android.a11y;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class ReadingOrderHelper {

    public static final class Neighbors {
        public AccessibilityNodeInfo prev;
        public AccessibilityNodeInfo next;
    }

    /** 计算图表矩形的前驱/后继；完全落在 chartRect 内部的节点视为“图表内部”并被过滤；部分相交者也视为外部 */
    public static Neighbors computeNeighbors(AccessibilityNodeInfo root, Rect chartRect, float density) {
        Neighbors ng = new Neighbors();
        if (root == null || chartRect == null) return ng;

        List<AccessibilityNodeInfo> all = new ArrayList<>();
        collect(root, all);

        // 过滤掉：不可见、既不focusable也不clickable的；以及“完全包含在图表矩形内”的
        List<AccessibilityNodeInfo> cand = new ArrayList<>();
        for (AccessibilityNodeInfo n : all) {
            if (!n.isVisibleToUser()) { n.recycle(); continue; }
            if (!(n.isFocusable() || n.isClickable())) { n.recycle(); continue; }
            Rect b = new Rect(); n.getBoundsInScreen(b);
            if (isFullyInside(b, chartRect)) { n.recycle(); continue; } // 完全在图表内 → 排除
            cand.add(n);
        }

        sortReadingOrder(cand, density);

        // 把 chartRect 当成“虚拟项”插入排序，找出邻居
        int insert = 0;
        for (; insert < cand.size(); insert++) {
            Rect r = new Rect(); cand.get(insert).getBoundsInScreen(r);
            if (goesAfter(r, chartRect, density)) break;
        }
        if (insert - 1 >= 0) ng.prev = AccessibilityNodeInfo.obtain(cand.get(insert - 1));
        if (insert < cand.size()) ng.next = AccessibilityNodeInfo.obtain(cand.get(insert));

        // 清理临时
        for (AccessibilityNodeInfo n : cand) n.recycle();

        return ng;
    }

    private static void collect(AccessibilityNodeInfo n, List<AccessibilityNodeInfo> out) {
        out.add(AccessibilityNodeInfo.obtain(n));
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c != null) { collect(c, out); c.recycle(); }
        }
    }

    private static boolean isFullyInside(Rect a, Rect container) {
        return container.contains(a);
    }

    private static void sortReadingOrder(List<AccessibilityNodeInfo> list, float density) {
        final int rowEps = Math.round(8 * density);
        Collections.sort(list, new Comparator<AccessibilityNodeInfo>() {
            @Override public int compare(AccessibilityNodeInfo a, AccessibilityNodeInfo b) {
                Rect ra = new Rect(), rb = new Rect();
                a.getBoundsInScreen(ra); b.getBoundsInScreen(rb);
                int dy = ra.top - rb.top;
                if (Math.abs(dy) > rowEps) return dy; // 上到下
                int dx = ra.left - rb.left;
                if (dx != 0) return dx;               // 左到右
                return (ra.right - rb.right);         // 次级
            }
        });
    }

    private static boolean goesAfter(Rect r, Rect chart, float density) {
        int rowEps = Math.round(8 * density);
        if (r.top > chart.top + rowEps) return true;
        if (Math.abs(r.top - chart.top) <= rowEps && r.left >= chart.left) return true;
        return false;
    }
}
