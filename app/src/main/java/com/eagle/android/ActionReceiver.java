package com.eagle.android;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONObject;

public class ActionReceiver extends BroadcastReceiver {
    private MyAccessibilityService accessibilityService;
    public ActionReceiver(MyAccessibilityService accessibilityService){
        this.accessibilityService = accessibilityService;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        try{
            String action = intent.getExtras().getString("action");
            String extra = intent.getExtras().getString("extra");
            if(action.equals("1")){//查找聚焦框
                this.accessibilityService.findAllFoucs();
            }
            if(action.equals("2")) { //节点布局
                this.accessibilityService.findAllNodes();
            }
            if(action.equals("3")) { //右滑
                this.accessibilityService.performSwipeGesture();
            }
            if(action.equals("4")) { //下滚动
                this.accessibilityService.performTwoFingerScrollDownGesture();
            }
            if(action.equals("5")) { //第一个focus节点
                this.accessibilityService.findFirstFocus(null);
            }
            if(action.equals("6")) { //找app入口
                this.accessibilityService.getMainActivity(extra);
            }
            if(action.equals("7")) { //点击进入
                this.accessibilityService.clickNode(extra);
            }
            if(action.equals("8")) { //回到顶部
                this.accessibilityService.backTop();
            }
            Log.i("action",">>>>>>>receive action");
        }catch (Exception e){
            Log.e("",e.toString());
        }
    }
}
