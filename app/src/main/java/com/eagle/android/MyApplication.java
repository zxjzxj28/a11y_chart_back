package com.eagle.android;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eagle.android.voice.IflytekConfig;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechUtility;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MyApplication extends Application {
    private static final String TAG = "MyApplication";
    private static String currentActivity;

    @Override
    public void onCreate() {
        super.onCreate();

        // 初始化科大讯飞SDK
        initIflytekSdk();

        // 注册 ActivityLifecycleCallbacks
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                // Activity 创建时更新当前活动
                setCurrentActivity(activity);
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                // Activity 恢复时更新当前活动
                setCurrentActivity(activity);
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
            }
        });
    }

    public static String getCurrentActivity() {
        return currentActivity;
    }

    private static void setCurrentActivity(Activity activity) {
        currentActivity = activity.getClass().getSimpleName();
    }

    /**
     * 初始化科大讯飞SDK
     */
    private void initIflytekSdk() {
        if (!IflytekConfig.isConfigured()) {
            Log.w(TAG, "========================================");
            Log.w(TAG, "科大讯飞SDK未配置！");
            Log.w(TAG, "请修改 IflytekConfig.java 中的 APPID");
            Log.w(TAG, "========================================");
            return;
        }

        // 初始化讯飞SDK
        SpeechUtility.createUtility(this, SpeechConstant.APPID + "=" + IflytekConfig.APPID);
        Log.i(TAG, "科大讯飞SDK初始化完成");

        // 复制唤醒资源到应用目录
        copyWakeupResourceIfNeeded();
    }

    /**
     * 复制唤醒词资源文件到应用目录
     * 讯飞SDK需要从文件系统路径读取唤醒资源
     */
    private void copyWakeupResourceIfNeeded() {
        String resFileName = IflytekConfig.WAKE_WORD_RES_FILE;
        File destFile = new File(getFilesDir(), resFileName);

        // 如果已存在则跳过
        if (destFile.exists()) {
            Log.d(TAG, "唤醒资源已存在: " + destFile.getAbsolutePath());
            return;
        }

        try {
            InputStream is = getAssets().open(resFileName);
            FileOutputStream fos = new FileOutputStream(destFile);
            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            is.close();
            Log.i(TAG, "唤醒资源复制完成: " + destFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "复制唤醒资源失败，可能尚未放置资源文件到assets目录: " + e.getMessage());
        }
    }
}
