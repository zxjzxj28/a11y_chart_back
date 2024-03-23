package com.eagle.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MyAccessibilityService extends AccessibilityService {
    private Handler handler;
    private Boolean run = false;
    private Boolean scrollFlag = true;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d("4444","Service Connected");
        IntentFilter filter = new IntentFilter("FIND_ALL_FOCUS_INFO");
        ActionReceiver accessibilityReceiver = new ActionReceiver(this);
        registerReceiver(accessibilityReceiver, filter,RECEIVER_NOT_EXPORTED);
        // 创建并启动监听套接字连接的线程
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try{
//                    Thread.sleep(5_000);
//                }catch (Exception e){
//                    System.out.println(1);
//                }
//                performScrollGesture();
//            }
//        }).start();
//        for (int i = 0; i < 4; i++) {
//            Log.i("8888","8888");
//            // 模拟向下滚动一定距离（例如50dp）
//            performScrollGesture();
//            // 延迟一段时间以等待滚动完成，可以根据需要调整延迟时间
//            try {
//                Thread.sleep(2000); // 暂停200毫秒
//            } catch (InterruptedException e) {
//                System.out.println(">>>>>>>>>>>.error");
//            }
//        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int eventType = event.getEventType();
        if(1==1){
            return;
        }

        Log.i("type",">>>type: "+ eventType);
//        if(eventType != AccessibilityEvent.TYPE_VIEW_LONG_CLICKED){
//            return;
//        }
//        if(run){
//            return;
//        }
        run = true;
        Log.i("3333","333333");
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if(rootNode == null){
            return;
        }
//        int childCount = rootNode.getChildCount();
//        for (int i = 0; i < childCount; i++) {
//            AccessibilityNodeInfo childNode = rootNode.getChild(i);
//            if(childNode != null && childNode.getClassName().equals(TextView.class.getName())){
//                String text = childNode.getText().toString();
//                Log.d("AccessibilityService", ">>>>>>>  text: " + text);
//            }
//        }
//
//        AccessibilityNodeInfo focus = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
//        if(focus != null && focus.getClassName().equals(TextView.class.getName())){
//            String text = focus.getText().toString();
//            Log.d("AccessibilityService", ">>>>>>>  text: " + text);
//        }
//        CharSequence t = rootNode.getText();
//        Log.d("AccessibilityService", ">>>>>>>root  text: " + t);
//        CharSequence contentDescription = rootNode.getContentDescription();
//        Log.d("AccessibilityService", ">>>>>>>root2222  text: " + contentDescription);
//        // 获取当前活动页面的所有可聚焦控件
        List<AccessibilityNodeInfo> focusableNodes = new ArrayList<>();
        Integer count = 0;
        findFocusableNodes(null, rootNode, focusableNodes,count);
        Log.i("aa", ">>>>>>>focus size:" + focusableNodes.size());
//        // 处理可聚焦控件
//        for (AccessibilityNodeInfo node : focusableNodes) {
//            // 处理每个可聚焦控件
//            // 例如，输出控件的文本内容
//            CharSequence text = node.getText();
//            if (text != null) {
//                Log.d("AccessibilityService", "Focusable control text: " + text);
//            }
//        }
//        AccessibilityNodeInfo scrollViewNode = findScrollView(rootNode);
//        if (scrollViewNode != null) {
//            scrollViewNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
//        }
//        AccessibilityNodeInfo rootNode2 = getRootInActiveWindow();
//        if(rootNode2 == null){
//            return;
//        }
//
//        List<AccessibilityNodeInfo> focusableNodes2 = new ArrayList<>();
//        Integer count2 = 0;
//        findFocusableNodes(rootNode2, focusableNodes2,count2);
//        Log.i("aa", ">>>>>>>22222focus size:" + focusableNodes.size());
        run = false;
    }

    public void findAllFoucs(){
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if(rootNode == null){
            return;
        }
        List<AccessibilityNodeInfo> focusableNodes = new ArrayList<>();
        Integer count = 0;
        findFocusableNodes(null, rootNode, focusableNodes,count);
        Log.i("aa", ">>>>>>>focus size:" + focusableNodes.size());
        List<FocusBox> list = new ArrayList<>();
        focusableNodes.forEach(e->{
            Rect boundsInScreen = new Rect();
            e.getBoundsInScreen(boundsInScreen);
            int left = boundsInScreen.left;
            int top = boundsInScreen.top;
            int bottom = boundsInScreen.bottom;
            int right = boundsInScreen.right;
            Log.d("AccessibilityService", "text:"+ e.getText() +", 控件坐标：left=" + left + ", top=" + top + ", right=" + right + ", bottom=" + bottom);
            list.add(new FocusBox(top,bottom,left,right,e != null && e.getText() != null ? e.getText().toString() : e.getClassName().toString()));
        });
        Intent msg = new Intent("ACTION_RESULT");
        JSONObject res = new JSONObject();
        try {
            res.put("action","1");
            //聚焦框信息
            JSONArray jsonArray = new JSONArray();
            for (FocusBox focusBox : list) {
                JSONObject jsonObject = new JSONObject();
                // 将 obj 对象的属性放入 jsonObject 中
                // 示例：jsonObject.put("key", obj.getValue());
                jsonObject.put("text", focusBox.text);
                jsonObject.put("left", focusBox.left);
                jsonObject.put("top", focusBox.top);
                jsonObject.put("right", focusBox.right);
                jsonObject.put("bottom", focusBox.bottom);
                jsonArray.put(jsonObject);
            }
            res.put("list", jsonArray);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
//        msg.getExtras().putString("res", res.toString());
        msg.putExtra("res", res.toString());
        msg.setPackage("com.eagle.android");
        sendBroadcast(msg);
//        handler.post(new Runnable() {
//            @Override
//            public void run() {
//                sendBroadcast(msg);
//            }
//        });

    }

    public void scoll(){
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        Boolean scrollRes = false;
        if(1==1){
//            Path path = new Path();
//            path.moveTo(500, 500); // 设置起始点坐标
//            path.lineTo(500, 100); // 设置终点坐标
//            GestureDescription gestureDescription = new GestureDescription.Builder()
//                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 500))
//                    .build();
//            scrollRes = dispatchGesture(gestureDescription, null, null);
//            if (scrollRes) {
//                Log.d("sss", "Scroll down gesture dispatched successfully.");
//            } else {
//                Log.e("sss", "Failed to dispatch scroll down gesture.");
//            }
            try {
                Process process = Runtime.getRuntime().exec("adb shell input swipe 500 1000 500 960");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
//        scroll(rootNode, scrollRes);
//        findFocusableNodes(rootNode, new ArrayList<>(),1);

        Intent msg = new Intent("ACTION_RESULT");
        JSONObject res = new JSONObject();
        try {
            res.put("action", "2");
            //滚动结果
            res.put("res", scrollRes);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        msg.putExtra("res", res.toString());
        msg.setPackage("com.eagle.android");
        sendBroadcast(msg);
    }

    public Boolean scroll(AccessibilityNodeInfo rootNode, Boolean scrollRes){
        // 遍历子节点
        int childCount = rootNode.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo childNode = rootNode.getChild(i);
            if(childNode == null){
                continue;
            }
            AccessibilityNodeInfo scrollView = findScrollView(childNode);
            if(scrollView != null){
                scrollRes = scrollView.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                return scrollRes;
            }
            scroll(childNode, scrollRes);
        }
        return scrollRes;
    }

    // 遍历节点，找到所有可聚焦的控件
    private void findFocusableNodes(AccessibilityNodeInfo parent,AccessibilityNodeInfo node, List<AccessibilityNodeInfo> focusableNodes,Integer count) {
        Log.i("nodeclass",">>>>>>>>" + node.getClassName() + ",focusable:" + node.isFocusable());

        if (node == null) {
            return;
        }

        // 判断节点是否可聚焦
//        if (node.isFocusable() || node.isClickable() ||node.isCheckable()
//                ||node.isScreenReaderFocusable()||node.isContextClickable()
//                ||node.isScreenReaderFocusable()||node.isScrollable()||node.isEnabled()) {
//            info(node);
//            focusableNodes.add(node);
//
//        }

        // 遍历子节点
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo childNode = node.getChild(i);
//            if(1==1){
//                continue;
//            }
            if (childNode == null){
                continue;
            }

            if(node.getExtras().getBoolean("collect")){
                childNode.getExtras().putBoolean("collect", true);
                childNode.getExtras().putString("text", "");
            }

            if(childNode.isFocusable() && !node.isFocusable()){
                Rect boundsInScreen = new Rect();
                childNode.getBoundsInScreen(boundsInScreen);
                int left = boundsInScreen.left;
                int top = boundsInScreen.top;
                int right = boundsInScreen.right;
                int bottom = boundsInScreen.bottom;
                String text = "";
                if(childNode.getText() != null){
                    text = childNode.getText().toString();
                }
                if(childNode.getContentDescription() != null){
                    text = childNode.getContentDescription().toString();
                }
                childNode.getExtras().putString("text", text);
                childNode.getExtras().putBoolean("collect", true);
                AccessibilityNodeInfo newNode = new AccessibilityNodeInfo();
                newNode.setBoundsInScreen(boundsInScreen);
                findFocusableNodes(node, childNode, focusableNodes,count);
                newNode.setText(childNode.getExtras().getString("text"));
                String tt = newNode.getText().toString();
                focusableNodes.add(newNode);
                continue;
            }

            if (childNode.getClassName() != null && childNode.getClassName().equals("android.widget.Linear1Layout")) {
                Rect boundsInScreen = new Rect();
                childNode.getBoundsInScreen(boundsInScreen);
                int left = boundsInScreen.left;
                int top = boundsInScreen.top;
                int right = boundsInScreen.right;
                int bottom = boundsInScreen.bottom;
                childNode.getExtras().putString("text", "");
                childNode.getExtras().putBoolean("collect", true);
                AccessibilityNodeInfo newNode = new AccessibilityNodeInfo();
                newNode.setBoundsInScreen(boundsInScreen);
                findFocusableNodes(node, childNode, focusableNodes,count);
                newNode.setText(childNode.getExtras().getString("text"));
                focusableNodes.add(newNode);
                continue;

//                Log.d("AccessibilityService", "text:"+ childNode.getExtras().getString("text") +"控件坐标：left=" + left + ", top=" + top + ", right=" + right + ", bottom=" + bottom);
            }

            if (childNode.getClassName() != null && childNode.getClassName().equals("android.widget.Image1View")) {
                Rect boundsInScreen = new Rect();
                childNode.getBoundsInScreen(boundsInScreen);
                int left = boundsInScreen.left;
                int top = boundsInScreen.top;
                int right = boundsInScreen.right;
                int bottom = boundsInScreen.bottom;
                JSONObject textObj = new JSONObject();
                childNode.getExtras().putString("text", "");
                childNode.getExtras().putBoolean("collect", true);

                AccessibilityNodeInfo newNode = new AccessibilityNodeInfo();
                newNode.setBoundsInScreen(boundsInScreen);

                findFocusableNodes(node, childNode, focusableNodes,count);

                newNode.setText(childNode.getExtras().getString("text"));
                focusableNodes.add(newNode);
                continue;

//                Log.d("AccessibilityService", "text:"+ childNode.getExtras().getString("text") +"控件坐标：left=" + left + ", top=" + top + ", right=" + right + ", bottom=" + bottom);
            }


            AccessibilityNodeInfo scrollView = findScrollView(childNode);
            Boolean hasScroll = false;
//            if(scrollView != null){
//                Log.i("aa",">>>>>> find scroll");
//                hasScroll = true;
////                Bundle args = new Bundle();
////                args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVE_WINDOW_Y, 100); // 向上移动 100 个像素
////                boolean success = scrollView.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_MOVE_WINDOW.getId(), args);
////                Log.i("aa",">>>>>> find scroll" + success);
//                findFocusableNodes(scrollView, focusableNodes,count);
//                boolean success = scrollView.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
//                Log.i("aa",">>>>>> find scroll" + success);
////                if(!success){
////                    break;
////                }
//            }


            if (childNode.getClassName() != null && childNode.getClassName().equals("android.widget.Switch")) {
                // 检查节点类名是否为 android.widget.Switch，确保是 Switch 控件
                boolean isChecked = childNode.isChecked(); // 获取 Switch 控件的状态
                Log.d("AccessibilityService", ">>>>>>>  Switch state: " + isChecked);
                count++;
                focusableNodes.add(childNode);
                Rect boundsInScreen = new Rect();
                childNode.getBoundsInScreen(boundsInScreen);
                int x = boundsInScreen.left;
                int y = boundsInScreen.top;
                Log.d("AccessibilityService", "控件在屏幕上的坐标：x=" + x + ", y=" + y);
                continue;
            }

            if(childNode.getText() != null){
                String text = childNode.getText().toString();
//                String text = new String(childNode.getText().toString().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
                Log.d("AccessibilityService", ">>>>>>>  text: " + text);
                count++;
                if(node.getExtras().getBoolean("collect")){
                    node.getExtras().putString("text", node.getExtras().getString("text") + "," + text);
                    continue;
                }else{
                    focusableNodes.add(childNode);

                    Rect boundsInScreen = new Rect();
                    childNode.getBoundsInScreen(boundsInScreen);
                    int left = boundsInScreen.left;
                    int top = boundsInScreen.top;
                    int right = boundsInScreen.right;
                    int bottom = boundsInScreen.bottom;
                    Boolean focusable = false;
//                if (childNode.isFocusable() ||
//                        childNode.isClickable() ||
//                        childNode.isCheckable() ||
//                        childNode.isScreenReaderFocusable() ||
//                        childNode.isContextClickable() ||
//                        childNode.isScrollable()) {
//                    focusable = true;
//                }
                    if (childNode.isFocusable() || node.isFocusable()) {
                        focusable = true;
                    }
                    Log.d("AccessibilityService", "控件坐标：left=" + left + ", top=" + top + ", right=" + right + ", bottom=" + bottom + ", focusAble=" + focusable );
                }


//                Rect boundsInScreen = new Rect();
//                childNode.getBoundsInScreen(boundsInScreen);
//                int x = boundsInScreen.left;
//                int y = boundsInScreen.top;
//                Log.d("AccessibilityService", "控件在屏幕上的坐标：x=" + x + ", y=" + y);
//                    continue;
                // 如果 decodedString 与原始字符串不同，说明可能存在乱码

            }
//            if(childNode.getHintText() != null){
//                String text = childNode.getHintText().toString();
//                Log.d("AccessibilityService", ">>>>>>>  text: " + text);
//                count++;
//                focusableNodes.add(childNode);
//                continue;
//            }
            CharSequence contentDescription = childNode.getContentDescription();
            if(contentDescription != null){
//                Log.d("AccessibilityService", ">>>>>>>  text: " + contentDescription.toString());
//                count++;
//                focusableNodes.add(childNode);
//                continue;

                if(node.getExtras().getBoolean("collect")){
                    node.getExtras().putString("text", node.getExtras().getString("text") + "," + contentDescription.toString());
                    continue;
                }
            }
//            CharSequence paneTitle = childNode.getPaneTitle();
//            if(paneTitle != null){
//                Log.d("AccessibilityService", ">>>>>>>  text: " + paneTitle.toString());
//                count++;
//                focusableNodes.add(childNode);
//                continue;
//            }
//
//            CharSequence stateDescription = childNode.getStateDescription();
//            if(stateDescription != null){
//                Log.d("AccessibilityService", ">>>>>>>  text: " + stateDescription.toString());
//                count++;
//                focusableNodes.add(childNode);
//                continue;
//            }
//
//            CharSequence tooltipText = childNode.getTooltipText();
//            if(tooltipText != null){
//                Log.d("AccessibilityService", ">>>>>>>  text: " + tooltipText.toString());
//                count++;
//                focusableNodes.add(childNode);
//                continue;
//            }


//            if (childNode.isFocusable()) {
//                CharSequence text = node.getText();
//                Log.d("AccessibilityService", ">>>>>>>Focusable control text: " + text.toString());
//            }
            if(!hasScroll){
                findFocusableNodes(node, childNode, focusableNodes,count);
                if(node.getExtras().getBoolean("collect")){
                    node.getExtras().putString("text", childNode.getExtras().getString("text"));
                }
            }
        }
    }

    public void info(AccessibilityNodeInfo childNode){
        if (childNode.getClassName() != null && childNode.getClassName().equals("android.widget.Switch")) {
            // 检查节点类名是否为 android.widget.Switch，确保是 Switch 控件
            boolean isChecked = childNode.isChecked(); // 获取 Switch 控件的状态
            Log.d("AccessibilityService", ">>>>>>>22222  Switch state: " + isChecked);
        }

        if(childNode.getText() != null){
            String text = childNode.getText().toString();
            Log.d("AccessibilityService", ">>>>>>>2222  text: " + text);
        }
        if(childNode.getHintText() != null){
            String text = childNode.getHintText().toString();
            Log.d("AccessibilityService", ">>>>>>> 22222 text: " + text);
        }
        CharSequence contentDescription = childNode.getContentDescription();
        if(contentDescription != null){
            Log.d("AccessibilityService", ">>>>>>> 222 text: " + contentDescription.toString());
        }
        CharSequence paneTitle = childNode.getPaneTitle();
        if(paneTitle != null){
            Log.d("AccessibilityService", ">>>>>>> 2222 text: " + paneTitle.toString());
        }
    }
    @Override
    public void onInterrupt() {
        Log.i("2222","22222");
        // 在中断服务时执行操作
    }

    // 执行向下滚动操作
    private void performScrollDown() {
        Log.i("1111","1111");
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        Log.i("6666","66666");
        if (rootNode != null) {
            // 找到滚动视图
            Log.i("77777","777777");
            AccessibilityNodeInfo scrollViewNode = findScrollView(rootNode);
            if (scrollViewNode != null) {
                Log.i("555555","555555");
                for (int i = 0; i < 4; i++) {
                    Log.i("8888","8888");
                    // 模拟向下滚动一定距离（例如50dp）
                    scrollViewNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                    // 延迟一段时间以等待滚动完成，可以根据需要调整延迟时间
                    try {
                        Thread.sleep(1000); // 暂停200毫秒
                    } catch (InterruptedException e) {
                        System.out.println(">>>>>>>>>>>.error");
                    }
                }
            }
        }
    }

    private void performScrollGesture() {
        Log.i(">>>",">>>>111");
        // 创建一个路径，表示手势路径
        Path path = new Path();
        // 设置起始点为屏幕中心
        int centerX = getDisplayWidth() / 2;
        int centerY = getDisplayHeight() / 2;
        path.moveTo(centerX, centerY);
        // 设置终止点为右侧一定距离的位置（这里假设向右滑动 200px）
        int endPointX = centerX + 250;
        path.lineTo(endPointX, centerY);
        Log.i(">>>",String.valueOf(centerX));
        Log.i(">>>",String.valueOf(centerY));
        Log.i(">>>",String.valueOf(endPointX));


        // 创建手势描述对象
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50)); // 100 毫秒的滑动时间
        Log.i(">>>",">>>>222");
        // 发送手势事件
        Boolean res = dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                Log.i(">>>",">>>>3333");
                // 手势完成后执行操作
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                Log.i(">>>",">>>>4444");
                // 手势取消后执行操作
            }
        }, null);
        Log.i(">>>", res.toString());
    }

    private int getDisplayWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private int getDisplayHeight() {
        return getResources().getDisplayMetrics().heightPixels;
    }

    private AccessibilityNodeInfo findScrollView(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return null;

        // 遍历根节点的子节点
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            AccessibilityNodeInfo childNode = rootNode.getChild(i);
            if (childNode == null) continue;

            // 判断子节点是否为滚动视图类型，例如 ScrollView、RecyclerView、ListView 等
            if (isScrollView(childNode)) {
                return childNode; // 找到滚动视图，返回节点
            }

            // 递归查找子节点中的滚动视图
            AccessibilityNodeInfo scrollViewNode = findScrollView(childNode);
            if (scrollViewNode != null) {
                return scrollViewNode; // 找到滚动视图，返回节点
            }

            // 回收子节点资源
//            childNode.recycle();
        }

        return null; // 没有找到滚动视图，返回null
    }

    // 判断节点是否为滚动视图类型
    private boolean isScrollView(AccessibilityNodeInfo node) {
//        Log.i("nodeclass",">>>>>>>>" + node.getClassName());
        // 在这里添加判断条件，判断节点是否为滚动视图类型
        // 例如，可以判断节点的类名、资源 ID、文本内容等
        // 这里只是一个简单的示例，需要根据实际情况进行适当的判断逻辑
        return node.getClassName().equals("android.widget.ScrollView")
                || node.getClassName().equals("androidx.recyclerview.widget.RecyclerView")
                || node.getClassName().equals("android.widget.ListView");
    }

    public static class FocusBox{
        private Integer top;
        private Integer bottom;
        private Integer left;
        private Integer right;

        private String text;

        public FocusBox(Integer top,Integer bottom,Integer left,Integer right,String text){
            this.top = top;
            this.bottom = bottom;
            this.left = left;
            this.right = right;
            this.text = text;
        }

        @NonNull
        @Override
        public String toString() {
            return "text:" + this.text + ", top:" + this.top + ", bottom:" + this.bottom + ", left:" + this.left + ", right:" + this.right;
        }
    }

}
