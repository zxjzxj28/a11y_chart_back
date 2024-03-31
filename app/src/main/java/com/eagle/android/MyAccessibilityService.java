package com.eagle.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

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

    public void getMainActivity(String appName){
        PackageManager packageManager = getPackageManager();
        List<ApplicationInfo> installedApplications = packageManager.getInstalledApplications(PackageManager.MATCH_ALL);
        installedApplications.forEach(e->{
            String applicationName = packageManager.getApplicationLabel(e).toString();
            Log.d("AccessibilityService", "当前应用名称：" + applicationName);

            if(appName.equals(applicationName)){
                String packageName = e.packageName;

                // 创建一个 Intent 对象，用于查询应用程序的 Activity 信息
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setPackage(packageName);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);


                // 查询应用程序的 Activity 信息
                List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(intent, 0);
                if(resolveInfos.size() == 0){
                    return;
                }else{
                    String activityName = resolveInfos.get(0).activityInfo.name;
                    // 打印 Activity 的名称
                    Log.d("ActivityInfo", "ActivityName: " + activityName);
//                        String mainActivity = "." + activityName.substring(packageName.length()+1);
                    Intent msg = new Intent("ACTION_RESULT");
                    JSONObject res = new JSONObject();
                    try {
                        res.put("action","6");
                        //聚焦框信息
                        res.put("packageName", packageName);
                        res.put("mainActivity", activityName);
                    } catch (JSONException ex) {
                        throw new RuntimeException(ex);
                    }
//        msg.getExtras().putString("res", res.toString());
                    msg.putExtra("res", res.toString());
                    msg.setPackage("com.eagle.android");
                    sendBroadcast(msg);
                }
            }
        });
    }

    public void focusNext(){
        AccessibilityNodeInfo parent = getRootInActiveWindow();
        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = parent.getChild(i);
            AccessibilityNodeInfoCompat node = AccessibilityNodeInfoCompat.wrap(child); // yourNode 是要设置为无障碍焦点的节点

// 设置节点为无障碍焦点
//            node.setAccessibilityFocused(true); // 设置为 true 表示节点具有无障碍焦点

// 触发 TalkBack 聚焦到节点
            boolean result = node.performAction(AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS);
        }
    }

    public void getActivity(){
//        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
//            // 获取当前应用程序所在的包名
//            String packageName = event.getPackageName().toString();
//
//            // 获取当前应用程序所在的 Activity 类名
//            String className = event.getClassName().toString();
//
//            // 输出当前应用程序所在的包名和 Activity 类名
//            Log.d(TAG, "当前应用程序包名：" + packageName);
//            Log.d(TAG, "当前应用程序 Activity 类名：" + className);
//        }
    }

    public Boolean findFirstFocus(AccessibilityNodeInfo parent){
        if(parent == null){
            parent = getRootInActiveWindow();
        }

        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = parent.getChild(i);
            if(isFocusable(child) || (isTopLevelScrollItem(child) && isSpeakingNode(child))){
                AccessibilityNodeInfoCompat node = AccessibilityNodeInfoCompat.wrap(child); // yourNode 是要设置为无障碍焦点的节点

                boolean result = node.performAction(AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS);
                return true;
            }
            if(findFirstFocus(child)){
                return true;
            }

        }
        return false;
    }

    public void performTwoFingerScrollDownGesture() {
        // 创建手势描述对象
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();

        // 创建第一个手指的滑动路径
        Path firstFingerPath = new Path();
        firstFingerPath.moveTo(800, 800); // 第一个手指起点的坐标
        firstFingerPath.lineTo(800, 400); // 第一个手指终点的坐标

        // 创建第二个手指的滑动路径
        Path secondFingerPath = new Path();
        secondFingerPath.moveTo(800, 1200); // 第二个手指起点的坐标
        secondFingerPath.lineTo(800, 800); // 第二个手指终点的坐标

        // 创建两个手指的手势描述对象
        GestureDescription.StrokeDescription firstFingerStroke = new GestureDescription.StrokeDescription(firstFingerPath, 0, 1500);
        GestureDescription.StrokeDescription secondFingerStroke = new GestureDescription.StrokeDescription(secondFingerPath, 0, 1500);

        // 添加手势操作到手势描述对象中
        gestureBuilder.addStroke(firstFingerStroke);
        gestureBuilder.addStroke(secondFingerStroke);

        // 发送手势操作
        boolean result = dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                Log.d("TAG", "两个手指向下滑动手势模拟成功");
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                Log.d("TAG", "两个手指向下滑动手势模拟取消");
            }
        }, null);

        // 检查结果
        if (!result) {
            Log.d("", "两个手指向下滑动手势模拟失败");
        }
    }



    public void performSwipeGesture() {
        // 创建手势路径，模拟右滑动作
        Path swipePath = new Path();
        swipePath.moveTo(100, 500); // 滑动起点的坐标
        swipePath.lineTo(600, 500); // 滑动终点的坐标

        // 创建手势描述对象
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 100));

        // 发送手势操作
        boolean result = dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                Log.d("", "右滑手势模拟成功");
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                Log.d("", "右滑手势模拟取消");
            }
        }, null);

        // 检查结果
        if (!result) {
            Log.d("", "右滑手势模拟失败");
        }
    }

    public void clickNode(String id){
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            List<AccessibilityNodeInfo> targetNodes = rootNode.findAccessibilityNodeInfosByViewId(id);
            for (AccessibilityNodeInfo node : targetNodes) {
                if (node.isEnabled() && node.isClickable()) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    Intent msg = new Intent("ACTION_RESULT");
                    JSONObject res = new JSONObject();
                    try {
                        res.put("action","7");
                        //聚焦框信息
                    } catch (JSONException ex) {
                        throw new RuntimeException(ex);
                    }
//        msg.getExtras().putString("res", res.toString());
                    msg.putExtra("res", res.toString());
                    msg.setPackage("com.eagle.android");
                    sendBroadcast(msg);
                    return;
                }
            }
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int eventType = event.getEventType();
//        Log.i("type",">>>type: "+ eventType);
//        Log.i("type",">>>type: "+ Integer.toHexString(eventType));
//        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED || event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
//        || event.getWindowChanges() == AccessibilityEvent.WINDOWS_CHANGE_FOCUSED || event.getWindowChanges() == AccessibilityEvent.WINDOWS_CHANGE_ACCESSIBILITY_FOCUSED) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Intent msg = new Intent("ACTION_RESULT");
            JSONObject res = new JSONObject();
            try {
                res.put("action","5");
                //聚焦框信息
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
//        msg.getExtras().putString("res", res.toString());
            msg.putExtra("res", res.toString());
            msg.setPackage("com.eagle.android");
            sendBroadcast(msg);
        }
        if(event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED){
            AccessibilityNodeInfo nodeInfo = event.getSource();
            if (nodeInfo != null) {

                // 获取焦点控件的相关信息
//                CharSequence text = nodeInfo.getText();
//                CharSequence contentDescription = nodeInfo.getContentDescription();


                Rect boundsInScreen = new Rect();
                nodeInfo.getBoundsInScreen(boundsInScreen);
                int left = boundsInScreen.left;
                int top = boundsInScreen.top;
                int bottom = boundsInScreen.bottom;
                int right = boundsInScreen.right;
                try{
                    String id = nodeInfo.getViewIdResourceName();
                    String text = "";
                    if(nodeInfo.getText() != null){
                        text = nodeInfo.getText().toString();
                    }
                    if(nodeInfo.getContentDescription() != null){
                        text = nodeInfo.getContentDescription().toString();
                    }
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("className", nodeInfo.getClassName());
                    jsonObject.put("id", id != null ? id.toString() : "");
                    jsonObject.put("text", text);
                    jsonObject.put("left", left);
                    jsonObject.put("top", top);
                    jsonObject.put("right", right);
                    jsonObject.put("bottom", bottom);
                    Intent msg = new Intent("ACTION_RESULT");
                    JSONObject res = new JSONObject();
                    try {
                        res.put("action","5");
                        //聚焦框信息
                        res.put("node", jsonObject);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
//        msg.getExtras().putString("res", res.toString());
                    msg.putExtra("res", res.toString());
                    msg.setPackage("com.eagle.android");
                    sendBroadcast(msg);
                    Log.d("AccessibilityService", "Focused control: " + text);
                }catch (Exception ex) {
                    System.out.println(1);
                }

                // 可以根据需要获取其他信息，如控件的ID、类名等
                // 处理获取到的信息，例如打印
                // 到日志中或者发送到远程服务器
                nodeInfo.recycle(); // 释放 AccessibilityNodeInfo 对象
            }
        }
        if(1==1){
            return;
        }


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
//        findFocusableNodes(null, rootNode, focusableNodes,count);
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

    public void findAllNodes(){
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        findNodes(nodes, rootNode);

        JSONArray jsonArray = new JSONArray();
        nodes.forEach(e->{
            Rect boundsInScreen = new Rect();
            e.getBoundsInScreen(boundsInScreen);
            int left = boundsInScreen.left;
            int top = boundsInScreen.top;
            int bottom = boundsInScreen.bottom;
            int right = boundsInScreen.right;
            try{
                String id = e.getViewIdResourceName();
                String text = "";
                if(e.getText() != null){
                    text = e.getText().toString();
                }
                if(e.getContentDescription() != null){
                    text = e.getContentDescription().toString();
                }
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("className", e.getClassName());
                jsonObject.put("id", id != null ? id.toString() : "");
                jsonObject.put("text", text);
                jsonObject.put("left", left);
                jsonObject.put("top", top);
                jsonObject.put("right", right);
                jsonObject.put("bottom", bottom);
                jsonArray.put(jsonObject);
                Log.d("AccessibilityService", "className: "+ e.getClassName() + "text:"+ text +", 控件坐标：left=" + left + ", top=" + top + ", right=" + right + ", bottom=" + bottom);
            }catch (Exception ex) {
                System.out.println(1);
            }

        });


        Intent msg = new Intent("ACTION_RESULT");
        JSONObject res = new JSONObject();
        try {
            res.put("action","2");
            //聚焦框信息
            res.put("list", jsonArray);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
//        msg.getExtras().putString("res", res.toString());
        msg.putExtra("res", res.toString());
        msg.setPackage("com.eagle.android");
        sendBroadcast(msg);
    }

    public void findNodes(List<AccessibilityNodeInfo> nodes, AccessibilityNodeInfo parent){
        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = parent.getChild(i);
            nodes.add(child);


            findNodes(nodes, child);
        }
    }

    public void findAllFoucs(){
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if(rootNode == null){
            return;
        }

        // 遍历子节点
        int childCount = rootNode.getChildCount();
        int recyclerViewIndex = 0;
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo childNode = rootNode.getChild(i);
            if (childNode == null){
                continue;
            }
            Log.i("root",">>>>" + childNode.getClassName());
            if(childNode.getText() != null){
                Log.i("root",">>>>" + childNode.getText().toString());
            }
            if(childNode.getClassName().equals("androidx.recyclerview.widget.RecyclerView")){
                recyclerViewIndex = i;
            }
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
            list.add(new FocusBox(top,bottom,left,right,e != null && e.getText() != null ? e.getText().toString() : e.getClassName().toString(), e.getExtras().getString("id")));
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
                jsonObject.put("id", focusBox.id);
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

    public boolean supportsAnyAction(
            @Nullable AccessibilityNodeInfoCompat node, int... actions) {
        if (node != null) {
            final int supportedActions = node.getActions();

            for (int action : actions) {
                if ((supportedActions & action) == action) {
                    return true;
                }
            }
        }

        return false;
    }

    //是否可聚焦
    private Boolean isFocusable(AccessibilityNodeInfo node){
        if(node == null || !node.isVisibleToUser()){
            return false;
        }
        if(node.getClassName().equals("androidx.recyclerview.widget.RecyclerView")){
            return false;
        }
        if(node.getParent().getClassName().equals("android.widget.FrameLayout") && node.getText() != null){
            return true;
        }
        if(node.isFocusable() || node.isClickable() || node.isLongClickable()){
            return true;
        }
        AccessibilityNodeInfoCompat wrap = AccessibilityNodeInfoCompat.wrap(node);

        if(wrap.isScreenReaderFocusable()){
            return true;
        }
        if(supportsAnyAction(wrap, AccessibilityNodeInfoCompat.ACTION_FOCUS,AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT,
                AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT,AccessibilityNodeInfoCompat.ACTION_CLICK,AccessibilityNodeInfoCompat.ACTION_LONG_CLICK)){
            return true;
        }

//        List<AccessibilityNodeInfoCompat.AccessibilityActionCompat> actionList = wrap.getActionList();
//        List<AccessibilityNodeInfoCompat.AccessibilityActionCompat> actions = new ArrayList();
//        actions.add(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_ACCESSIBILITY_FOCUS);
//        for (AccessibilityNodeInfoCompat.AccessibilityActionCompat action : actions) {
//            if (actionList.contains(action)) {
//                return true;
//            }
//        }

        return false;
    }
    public Boolean isTopLevelScrollItem(AccessibilityNodeInfo node){
        AccessibilityNodeInfo parent = node.getParent();

        if(parent.getClassName().equals("androidx.recyclerview.widget.RecyclerView")){
            return true;
        }


        AccessibilityNodeInfoCompat wrap = AccessibilityNodeInfoCompat.wrap(parent);
        List<AccessibilityNodeInfoCompat.AccessibilityActionCompat> actionList = wrap.getActionList();
        List<AccessibilityNodeInfoCompat.AccessibilityActionCompat> actions = new ArrayList();
        actions.add(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_FORWARD);
        actions.add(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_BACKWARD);
        actions.add(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_DOWN);
        actions.add(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_UP);
        actions.add(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_RIGHT);
        actions.add(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SCROLL_LEFT);

        for (AccessibilityNodeInfoCompat.AccessibilityActionCompat action : actions) {
            if (actionList.contains(action)) {
                return true;
            }
        }
//
//        final int supportedActions = wrap.getActions();
//        List<Integer> list = new ArrayList();
//        list.add(AccessibilityNodeInfoCompat.ACTION_FOCUS);
//        list.add(AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT);
//        list.add(AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT);
//        for (int action : list) {
//            if ((supportedActions & action) == action) {
//                return true;
//            }
//        }


        CharSequence className = parent.getClassName();
        if(className.equals("")){
            return true;
        }
        return false;
    }

    public Boolean isSpeakingNode(AccessibilityNodeInfo node){
        if(node == null){
            return false;
        }
        if(node.getCollectionInfo() == null && (node.getText() != "" || node.getContentDescription() != null || node.getHintText() != null)){
            return true;
        }
        if(node.getStateDescription() != null || node.isCheckable()){
            return true;
        }
        //特殊情况，即使没焦点，威了用户体验，也需要聚焦朗读
//        if(){
//
//        }
        return false;
    }

    // 遍历节点，找到所有可聚焦的控件
    private void findFocusableNodes(AccessibilityNodeInfo parent,AccessibilityNodeInfo node, List<AccessibilityNodeInfo> focusableNodes,Integer count) {

        if (node == null) {
            return;
        }

        // 遍历子节点
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo childNode = node.getChild(i);
            if (childNode == null){
                continue;
            }
            String childNodeId = childNode.getViewIdResourceName();
            if(childNodeId == null){
                childNodeId = "";
            }
            Log.i("nodeclass",">>>>>>>>" + childNode.getClassName() + ",focusable:" + childNode.isFocusable() + ", visable: " + childNode.isVisibleToUser()
            + "isClickable:" + childNode.isClickable() + "isLongClickable:" + childNode.isLongClickable() + "");

            //是否可被聚焦
            if(isFocusable(childNode) || (isTopLevelScrollItem(childNode) && isSpeakingNode(childNode))){
                Log.i(">>>>>",">>>>>需要被聚焦的node：" + childNode.getClassName() + ", Id:" + childNodeId);
                if(childNode.getText() != null){
                    Log.i(">>",">>>text: " + childNode.getText().toString());
                }
                //是否被收集
                if (collectable(childNode)){
                    Rect boundsInScreen = new Rect();
                    childNode.getBoundsInScreen(boundsInScreen);
                    String text = "";
                    if(childNode.getText() != null){
                        text = childNode.getText().toString();
                    }
                    if(childNode.getContentDescription() != null){
                        text = childNode.getContentDescription().toString();
                    }
                    childNode.getExtras().putString("text", text);
                    childNode.getExtras().putBoolean("collect", true);
                    childNode.getExtras().putString("id", "");
                    AccessibilityNodeInfo newNode = new AccessibilityNodeInfo();
                    newNode.setBoundsInScreen(boundsInScreen);
                    focusableNodes.add(newNode);
                    if(childNode.getContentDescription() == null){
                        findFocusableNodes(node, childNode, focusableNodes,count);
                    }
                    if(!"".equals(childNodeId)){
                        newNode.getExtras().putString("id", childNodeId);
                    }else{
                        newNode.getExtras().putString("id", childNode.getExtras().getString("id"));
                    }
                    newNode.setText(childNode.getExtras().getString("text"));
                    String tt = newNode.getText().toString();

                    Log.i("1111",">>>>>>>>>>end " + newNode.getText());
                    continue;
                }else{
                    processNode(node, childNode, focusableNodes);

                }


            }else{ //不可被聚焦
                if(node.getExtras().getBoolean("collect")){
                    processNode(node, childNode, focusableNodes);
                }
            }

            findFocusableNodes(node, childNode, focusableNodes,count);


//            if(node.getExtras().getBoolean("collect")){
//                childNode.getExtras().putBoolean("collect", true);
//                childNode.getExtras().putString("text", node.getExtras().getString("text"));
//                childNode.getExtras().putString("id", "");
//            }

//            if(!childNode.getClassName().equals("androidx.recyclerview.widget.RecyclerView")
//                    && !childNode.getClassName().equals("androidx.viewpager.widget.ViewPager")
//                    && (childNode.isFocusable() || (childNode.getClassName().equals("android.widget.LinearLayout") && childNode.getChildCount() != 0))
//                    && (!node.isFocusable() || (node.getClassName().equals("androidx.recyclerview.widget.RecyclerView") || node.getClassName().equals("androidx.viewpager.widget.ViewPager")))){
//            if(childNode.getClassName().equals("android.widget.LinearLayout") && collectable(childNode) || childNode.getClassName().equals("android.view.ViewGroup")){
//                Rect boundsInScreen = new Rect();
//                childNode.getBoundsInScreen(boundsInScreen);
//                int left = boundsInScreen.left;
//                int top = boundsInScreen.top;
//                int right = boundsInScreen.right;
//                int bottom = boundsInScreen.bottom;
//                String text = "";
//                if(childNode.getText() != null){
//                    text = childNode.getText().toString();
//                }
//                if(childNode.getContentDescription() != null){
//                    text = childNode.getContentDescription().toString();
//                }
//                childNode.getExtras().putString("text", text);
//                childNode.getExtras().putBoolean("collect", true);
//                childNode.getExtras().putString("id", "");
//                AccessibilityNodeInfo newNode = new AccessibilityNodeInfo();
//                newNode.setBoundsInScreen(boundsInScreen);
//                findFocusableNodes(node, childNode, focusableNodes,count);
//                if(!"".equals(childNodeId)){
//                    newNode.getExtras().putString("id", childNodeId);
//                }else{
//                    newNode.getExtras().putString("id", childNode.getExtras().getString("id"));
//                }
//                newNode.setText(childNode.getExtras().getString("text"));
//                String tt = newNode.getText().toString();
//                focusableNodes.add(newNode);
//                Log.i("1111",">>>>>>>>>>end " + newNode.getText());
//                continue;
//            }
//
//            AccessibilityNodeInfo scrollView = findScrollView(childNode);
//            Boolean hasScroll = false;
////            if(scrollView != null){
////                Log.i("aa",">>>>>> find scroll");
////                hasScroll = true;
//////                Bundle args = new Bundle();
//////                args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVE_WINDOW_Y, 100); // 向上移动 100 个像素
//////                boolean success = scrollView.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_MOVE_WINDOW.getId(), args);
//////                Log.i("aa",">>>>>> find scroll" + success);
////                findFocusableNodes(scrollView, focusableNodes,count);
////                boolean success = scrollView.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
////                Log.i("aa",">>>>>> find scroll" + success);
//////                if(!success){
//////                    break;
//////                }
////            }
//
//            if (childNode.getClassName() != null && childNode.getClassName().equals("android.widget.Switch")) {
//                // 检查节点类名是否为 android.widget.Switch，确保是 Switch 控件
//                boolean isChecked = childNode.isChecked(); // 获取 Switch 控件的状态
//                String text = isChecked ? "开启" : "关闭";
//                Log.d("AccessibilityService", ">>>>>>>  Switch state: " + isChecked);
//                count++;
//                if(node.getExtras().getBoolean("collect")){
//                    node.getExtras().putString("text", node.getExtras().getString("text") + "," + text);
//                    node.getExtras().putString("id", node.getExtras().getString("id") + "," + childNodeId);
//                    continue;
//                }else{
//                    //                focusableNodes.add(childNode);
//                    Rect boundsInScreen = new Rect();
//                    childNode.getBoundsInScreen(boundsInScreen);
//                    int left = boundsInScreen.left;
//                    int top = boundsInScreen.top;
//                    int right = boundsInScreen.right;
//                    int bottom = boundsInScreen.bottom;
//                    AccessibilityNodeInfo newNode = new AccessibilityNodeInfo();
//                    newNode.setBoundsInScreen(boundsInScreen);
//                    newNode.setText(text);
//                    newNode.getExtras().putString("id", childNodeId);
//                    focusableNodes.add(newNode);
//                    int x = boundsInScreen.left;
//                    int y = boundsInScreen.top;
//                    Log.d("AccessibilityService", "控件在屏幕上的坐标：x=" + x + ", y=" + y);
//                    continue;
//                }
//            }
//
////            if(childNode.getText() != null && (node.getExtras().getBoolean("collect") || childNode.isFocusable())){
//            if(childNode.getText() != null){
//                String text = childNode.getText().toString();
//                if(isErrStr(text)){
//                    continue;
//                }
//                Boolean focusable = false;
//                if (childNode.isFocusable()) {
//                    focusable = true;
//                }
////                String text = new String(childNode.getText().toString().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
//                Log.d("AccessibilityService", ">>>>>>>  text: " + text + ", focusable: " + focusable + ", " + childNode.isVisibleToUser() + "  " + childNode.isImportantForAccessibility());
//                count++;
//                if(node.getExtras().getBoolean("collect")){
//                    node.getExtras().putString("text", node.getExtras().getString("text") + "," + text);
//                    node.getExtras().putString("id", node.getExtras().getString("id") + "," + childNodeId);
//                    continue;
//                }else{
//                    if(childNode.isFocusable()){
//
//                    }
//                    if((childNode.isFocusable() && node.getClassName().equals("android.widget.LinearLayout"))
//                            || !node.getClassName().equals("android.widget.LinearLayout")){
//
//                    }
//                    childNode.getExtras().putString("id", childNodeId);
//                    focusableNodes.add(childNode);
//                    continue;
//                }
//            }
//
//            CharSequence contentDescription = childNode.getContentDescription();
//            if(contentDescription != null){
////                Log.d("AccessibilityService", ">>>>>>>  text: " + contentDescription.toString());
////                count++;
////                focusableNodes.add(childNode);
////                continue;
//                String text = contentDescription.toString();
//                Log.d("AccessibilityService", ">>>>>>>  text: " + text);
//                if(node.getExtras().getBoolean("collect")){
//                    node.getExtras().putString("text", node.getExtras().getString("text") + "," + contentDescription.toString());
//                    node.getExtras().putString("id", node.getExtras().getString("id") + "," + childNodeId);
//                    continue;
//                }else{
//                    //                focusableNodes.add(childNode);
//                    Rect boundsInScreen = new Rect();
//                    childNode.getBoundsInScreen(boundsInScreen);
//                    int left = boundsInScreen.left;
//                    int top = boundsInScreen.top;
//                    int right = boundsInScreen.right;
//                    int bottom = boundsInScreen.bottom;
//                    AccessibilityNodeInfo newNode = new AccessibilityNodeInfo();
//                    newNode.setBoundsInScreen(boundsInScreen);
//                    newNode.setText(text);
//                    newNode.getExtras().putString("id", childNodeId);
//                    focusableNodes.add(newNode);
//                    int x = boundsInScreen.left;
//                    int y = boundsInScreen.top;
//                    Log.d("AccessibilityService", "控件在屏幕上的坐标：x=" + x + ", y=" + y);
////                    continue;
//                }
//            }
//            findFocusableNodes(node, childNode, focusableNodes,count);
//            if(node.getExtras().getBoolean("collect") && childNode.getExtras().getString("text") != null){
//                node.getExtras().putString("text", node.getExtras().getString("text") + childNode.getExtras().getString("text"));
//                node.getExtras().putString("id", node.getExtras().getString("id") + childNode.getExtras().getString("id"));
//            }
        }
    }

    public void processNode(AccessibilityNodeInfo node, AccessibilityNodeInfo childNode, List<AccessibilityNodeInfo> focusableNodes){
        String childNodeId = childNode.getViewIdResourceName();
        if(childNodeId == null){
            childNodeId = "";
        }
        if (childNode.getClassName() != null && childNode.getClassName().equals("android.widget.Switch")) {
            // 检查节点类名是否为 android.widget.Switch，确保是 Switch 控件
            boolean isChecked = childNode.isChecked(); // 获取 Switch 控件的状态
            String text = isChecked ? "开启" : "关闭";
            Log.d("AccessibilityService", ">>>>>>>  Switch state: " + isChecked);
            if(node.getExtras().getBoolean("coll11ect")){
                node.getExtras().putString("text", node.getExtras().getString("text") + "," + text);
                node.getExtras().putString("id", node.getExtras().getString("id") + "," + childNodeId);
            }else{
                //                focusableNodes.add(childNode);
                Rect boundsInScreen = new Rect();
                childNode.getBoundsInScreen(boundsInScreen);
                AccessibilityNodeInfo newNode = new AccessibilityNodeInfo();
                newNode.setBoundsInScreen(boundsInScreen);
                newNode.setText(text);
                newNode.getExtras().putString("id", childNodeId);
                focusableNodes.add(newNode);
                int x = boundsInScreen.left;
                int y = boundsInScreen.top;
                Log.d("AccessibilityService", "控件在屏幕上的坐标：x=" + x + ", y=" + y);
            }
        }

        if(childNode.getText() != null){
            String text = childNode.getText().toString();
            Boolean focusable = false;
            if (childNode.isFocusable()) {
                focusable = true;
            }
            //                String text = new String(childNode.getText().toString().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            Log.d("AccessibilityService", ">>>>>>>  text: " + text + ", focusable: " + focusable + ", " + childNode.isVisibleToUser() + "  " + childNode.isImportantForAccessibility());
            if(node.getExtras().getBoolean("collect")){
                node.getExtras().putString("text", node.getExtras().getString("text") + "," + text);
                node.getExtras().putString("id", node.getExtras().getString("id") + "," + childNodeId);
            }else{
                if(childNode.isFocusable()){

                }
                if((childNode.isFocusable() && node.getClassName().equals("android.widget.LinearLayout"))
                        || !node.getClassName().equals("android.widget.LinearLayout")){

                }
                childNode.getExtras().putString("id", childNodeId);
                focusableNodes.add(childNode);
            }
        }

        CharSequence contentDescription = childNode.getContentDescription();
        if(contentDescription != null){
            String text = contentDescription.toString();
            Log.d("AccessibilityService", ">>>>>>>  text: " + text);
            if(node.getExtras().getBoolean("collect")){
                node.getExtras().putString("text", node.getExtras().getString("text") + "," + contentDescription.toString());
                node.getExtras().putString("id", node.getExtras().getString("id") + "," + childNodeId);
            }else{
                Rect boundsInScreen = new Rect();
                childNode.getBoundsInScreen(boundsInScreen);
                AccessibilityNodeInfo newNode = new AccessibilityNodeInfo();
                newNode.setBoundsInScreen(boundsInScreen);
                newNode.setText(text);
                newNode.getExtras().putString("id", childNodeId);
                focusableNodes.add(newNode);
                int x = boundsInScreen.left;
                int y = boundsInScreen.top;
                Log.d("AccessibilityService", "控件在屏幕上的坐标：x=" + x + ", y=" + y);
                //                    continue;
            }
        }
    }

    public Boolean isErrStr(String str){
        Boolean res = false;
//        try{
//            CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
//            decoder.decode(ByteBuffer.wrap(str.getBytes()));
//        }catch (Exception e){
//            res = true;
//        }
        return res;
    }

    public Boolean collectable(AccessibilityNodeInfo node){
        int childCount = node.getChildCount();
        if(childCount < 1){
            return false;
        }
        Boolean res = true;
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo childNode = node.getChild(i);
            if (childNode == null){
                continue;
            }
//            if(childNode.isFocusable() || childNode.isClickable() ||childNode.isCheckable()){
            if(childNode.isFocusable()){
                res=false;
            }
        }
        return res;
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
        private String id;

        public FocusBox(Integer top,Integer bottom,Integer left,Integer right,String text,String id){
            this.top = top;
            this.bottom = bottom;
            this.left = left;
            this.right = right;
            this.text = text;
            this.id = id;
        }

        @NonNull
        @Override
        public String toString() {
            return "id:"+ this.id + ", text:" + this.text + ", top:" + this.top + ", bottom:" + this.bottom + ", left:" + this.left + ", right:" + this.right;
        }
    }

}
