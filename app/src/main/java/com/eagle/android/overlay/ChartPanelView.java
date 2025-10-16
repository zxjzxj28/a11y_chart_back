package com.eagle.android.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.eagle.android.model.NodeSpec;
import java.util.List;


public class ChartPanelView extends FrameLayout {
    private final ImageView image;
    private final NodeLayer nodeLayer; //处理数据点的无障碍操作逻辑


    public ChartPanelView(Context ctx, ChartPanelWindow.Tapper tapper) {
        super(ctx);
        image = new ImageView(ctx);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER); /// 保持宽高比，居中显示
        image.setAdjustViewBounds(true); // 根据图像调整视图边界
        nodeLayer = new NodeLayer(ctx, tapper);
        //将image和NodeLayer加入到当前布局中
        addView(image, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(nodeLayer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        // 4. 设置阴影效果（8dp的阴影高度）
        setElevation(8 * ctx.getResources().getDisplayMetrics().density);
    }


    public void bindData(Bitmap bmp, Rect chartRectOnScreen, List<NodeSpec> nodes) {
        image.setImageBitmap(bmp);
        nodeLayer.setChartBitmap(bmp, chartRectOnScreen, nodes);
    }
}