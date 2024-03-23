package com.eagle.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

public class ActionResultReceiver extends BroadcastReceiver {
    private static SocketServerService socketServerService;
    public ActionResultReceiver(){
        System.out.println(1);
    }
    public ActionResultReceiver(SocketServerService socketServerService){
        this.socketServerService = socketServerService;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String resString = intent.getExtras().getString("res");
        try {
            JSONObject res = new JSONObject(resString);
            this.socketServerService.sendRes2Client(res);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        Log.i("action",">>>>>>>receive action result");

    }
}
