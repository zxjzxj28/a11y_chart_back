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
        String action = intent.getExtras().getString("action");
        if(action.equals("1")){//查找聚焦框
            this.accessibilityService.findAllFoucs();
        }else{//滚动屏幕
            this.accessibilityService.scoll();
        }
        Log.i("action",">>>>>>>receive action");

    }
}
