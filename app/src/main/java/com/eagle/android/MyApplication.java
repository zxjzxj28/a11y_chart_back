package com.eagle.android;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MyApplication extends Application {
    private static String currentActivity;

    @Override
    public void onCreate() {
        super.onCreate();

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
}
