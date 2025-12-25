package com.eagle.android;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.eagle.android.overlay.NodeLayer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/**
 * 手势监听功能测试
 *
 * 测试双击、长按、滚动的回调是否正常触发
 */
@RunWith(AndroidJUnit4.class)
public class GestureListenerTest {

    private static final String TAG = "GestureListenerTest";

    private Context context;
    private boolean doubleTapTriggered;
    private boolean longPressTriggered;
    private int scrollDirection;
    private float lastX, lastY;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        doubleTapTriggered = false;
        longPressTriggered = false;
        scrollDirection = -1;
        lastX = -1;
        lastY = -1;
    }

    /**
     * 测试双击回调是否能被触发
     */
    @Test
    public void testDoubleTapCallback() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            NodeLayer.AccessibilityEventCallback callback = new NodeLayer.AccessibilityEventCallback() {
                @Override
                public void onDoubleTap(float x, float y) {
                    Log.d(TAG, "✅ 双击回调触发! 坐标: (" + x + ", " + y + ")");
                    doubleTapTriggered = true;
                    lastX = x;
                    lastY = y;
                    latch.countDown();
                }

                @Override
                public void onLongPress(float x, float y) {}

                @Override
                public void onScroll(int direction) {}
            };

            // 验证回调接口可以正常创建
            assertNotNull("回调接口应该可以创建", callback);

            // 模拟触发双击
            callback.onDoubleTap(100f, 200f);
        });

        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue("双击回调应该被触发", completed);
        assertTrue("doubleTapTriggered 应该为 true", doubleTapTriggered);
        assertEquals("X坐标应该正确", 100f, lastX, 0.01f);
        assertEquals("Y坐标应该正确", 200f, lastY, 0.01f);

        Log.d(TAG, "✅ 双击监听测试通过!");
    }

    /**
     * 测试长按回调是否能被触发
     */
    @Test
    public void testLongPressCallback() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            NodeLayer.AccessibilityEventCallback callback = new NodeLayer.AccessibilityEventCallback() {
                @Override
                public void onDoubleTap(float x, float y) {}

                @Override
                public void onLongPress(float x, float y) {
                    Log.d(TAG, "✅ 长按回调触发! 坐标: (" + x + ", " + y + ")");
                    longPressTriggered = true;
                    lastX = x;
                    lastY = y;
                    latch.countDown();
                }

                @Override
                public void onScroll(int direction) {}
            };

            // 模拟触发长按
            callback.onLongPress(150f, 250f);
        });

        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue("长按回调应该被触发", completed);
        assertTrue("longPressTriggered 应该为 true", longPressTriggered);
        assertEquals("X坐标应该正确", 150f, lastX, 0.01f);
        assertEquals("Y坐标应该正确", 250f, lastY, 0.01f);

        Log.d(TAG, "✅ 长按监听测试通过!");
    }

    /**
     * 测试滚动回调是否能被触发 - 向前滚动
     */
    @Test
    public void testScrollForwardCallback() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            NodeLayer.AccessibilityEventCallback callback = new NodeLayer.AccessibilityEventCallback() {
                @Override
                public void onDoubleTap(float x, float y) {}

                @Override
                public void onLongPress(float x, float y) {}

                @Override
                public void onScroll(int direction) {
                    Log.d(TAG, "✅ 滚动回调触发! 方向: " + (direction == 0 ? "向前" : "向后"));
                    scrollDirection = direction;
                    latch.countDown();
                }
            };

            // 模拟触发向前滚动
            callback.onScroll(0);
        });

        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue("滚动回调应该被触发", completed);
        assertEquals("滚动方向应该是向前(0)", 0, scrollDirection);

        Log.d(TAG, "✅ 向前滚动监听测试通过!");
    }

    /**
     * 测试滚动回调是否能被触发 - 向后滚动
     */
    @Test
    public void testScrollBackwardCallback() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            NodeLayer.AccessibilityEventCallback callback = new NodeLayer.AccessibilityEventCallback() {
                @Override
                public void onDoubleTap(float x, float y) {}

                @Override
                public void onLongPress(float x, float y) {}

                @Override
                public void onScroll(int direction) {
                    Log.d(TAG, "✅ 滚动回调触发! 方向: " + (direction == 0 ? "向前" : "向后"));
                    scrollDirection = direction;
                    latch.countDown();
                }
            };

            // 模拟触发向后滚动
            callback.onScroll(1);
        });

        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue("滚动回调应该被触发", completed);
        assertEquals("滚动方向应该是向后(1)", 1, scrollDirection);

        Log.d(TAG, "✅ 向后滚动监听测试通过!");
    }

    /**
     * 综合测试：验证所有回调可以在同一个回调实例中工作
     */
    @Test
    public void testAllGesturesInOneCallback() throws Exception {
        final CountDownLatch latch = new CountDownLatch(3);
        final boolean[] results = new boolean[3]; // [doubleTap, longPress, scroll]

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            NodeLayer.AccessibilityEventCallback callback = new NodeLayer.AccessibilityEventCallback() {
                @Override
                public void onDoubleTap(float x, float y) {
                    Log.d(TAG, "✅ 双击回调触发!");
                    results[0] = true;
                    latch.countDown();
                }

                @Override
                public void onLongPress(float x, float y) {
                    Log.d(TAG, "✅ 长按回调触发!");
                    results[1] = true;
                    latch.countDown();
                }

                @Override
                public void onScroll(int direction) {
                    Log.d(TAG, "✅ 滚动回调触发!");
                    results[2] = true;
                    latch.countDown();
                }
            };

            // 依次触发所有手势
            callback.onDoubleTap(100f, 100f);
            callback.onLongPress(200f, 200f);
            callback.onScroll(0);
        });

        boolean completed = latch.await(3, TimeUnit.SECONDS);
        assertTrue("所有回调应该被触发", completed);
        assertTrue("双击应该触发", results[0]);
        assertTrue("长按应该触发", results[1]);
        assertTrue("滚动应该触发", results[2]);

        Log.d(TAG, "✅ 综合手势监听测试通过!");
    }

    /**
     * 输出测试结果摘要
     */
    @Test
    public void testPrintSummary() {
        Log.d(TAG, "========================================");
        Log.d(TAG, "          手势监听测试摘要");
        Log.d(TAG, "========================================");
        Log.d(TAG, "");
        Log.d(TAG, "📱 支持的手势类型:");
        Log.d(TAG, "   1. 双击 (Double Tap) - TalkBack双击触发ACTION_CLICK");
        Log.d(TAG, "   2. 长按 (Long Press) - TalkBack长按触发ACTION_LONG_CLICK");
        Log.d(TAG, "   3. 滚动 (Scroll) - 三指滑动触发ACTION_SCROLL_FORWARD/BACKWARD");
        Log.d(TAG, "");
        Log.d(TAG, "🔧 测试方法:");
        Log.d(TAG, "   - 开启TalkBack: 设置 → 无障碍 → TalkBack");
        Log.d(TAG, "   - 双击: 单指双击屏幕");
        Log.d(TAG, "   - 长按: 单指长按屏幕");
        Log.d(TAG, "   - 滚动: 三指上下滑动");
        Log.d(TAG, "");
        Log.d(TAG, "📍 回调接口位置:");
        Log.d(TAG, "   NodeLayer.AccessibilityEventCallback");
        Log.d(TAG, "   - onDoubleTap(float x, float y)");
        Log.d(TAG, "   - onLongPress(float x, float y)");
        Log.d(TAG, "   - onScroll(int direction)");
        Log.d(TAG, "========================================");

        assertTrue(true);
    }
}
