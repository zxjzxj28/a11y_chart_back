package com.eagle.android.voice;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.VoiceWakeuper;
import com.iflytek.cloud.WakeuperListener;
import com.iflytek.cloud.WakeuperResult;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * 科大讯飞语音管理类
 * 封装唤醒词检测和语音识别功能
 */
public class VoiceManager {

    private static final String TAG = "VoiceManager";

    private Context context;
    private VoiceWakeuper wakeuper;           // 唤醒器
    private SpeechRecognizer recognizer;       // 语音识别器

    private VoiceCallback callback;
    private boolean isWakeupListening = false;
    private boolean isRecognizing = false;

    // 语音识别结果缓存（多次回调拼接）
    private StringBuilder recognitionResult = new StringBuilder();

    /**
     * 语音回调接口
     */
    public interface VoiceCallback {
        /** 唤醒成功 */
        void onWakeup();

        /** 语音识别结果（最终结果） */
        void onRecognitionResult(String text);

        /** 错误回调 */
        void onError(String message);

        /** 状态变化 */
        void onStatusChange(VoiceStatus status);
    }

    public enum VoiceStatus {
        IDLE,           // 空闲
        WAKEUP_LISTENING, // 等待唤醒
        RECOGNIZING     // 正在识别
    }

    public VoiceManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 设置回调
     */
    public void setCallback(VoiceCallback callback) {
        this.callback = callback;
    }

    /**
     * 初始化唤醒器
     */
    public boolean initWakeuper() {
        if (!IflytekConfig.isConfigured()) {
            Log.e(TAG, "请先配置IflytekConfig.APPID");
            return false;
        }

        wakeuper = VoiceWakeuper.createWakeuper(context, initListener);
        return wakeuper != null;
    }

    /**
     * 初始化语音识别器
     */
    public boolean initRecognizer() {
        if (!IflytekConfig.isConfigured()) {
            Log.e(TAG, "请先配置IflytekConfig.APPID");
            return false;
        }

        recognizer = SpeechRecognizer.createRecognizer(context, initListener);
        return recognizer != null;
    }

    /**
     * 开始唤醒监听
     * 持续监听唤醒词，检测到后回调onWakeup
     */
    public boolean startWakeupListening() {
        if (wakeuper == null) {
            if (!initWakeuper()) {
                notifyError("唤醒器初始化失败");
                return false;
            }
        }

        // 设置唤醒参数
        String resPath = "fo|" + getResourcePath();
        wakeuper.setParameter(SpeechConstant.IVW_RES_PATH, resPath);
        wakeuper.setParameter(SpeechConstant.IVW_THRESHOLD, "0:" + IflytekConfig.WAKE_THRESHOLD);
        wakeuper.setParameter(SpeechConstant.KEEP_ALIVE, IflytekConfig.KEEP_ALIVE ? "1" : "0");

        int ret = wakeuper.startListening(wakeuperListener);
        if (ret == 0) {
            isWakeupListening = true;
            notifyStatus(VoiceStatus.WAKEUP_LISTENING);
            Log.i(TAG, "开始唤醒监听");
            return true;
        } else {
            notifyError("启动唤醒失败，错误码: " + ret);
            return false;
        }
    }

    /**
     * 停止唤醒监听
     */
    public void stopWakeupListening() {
        if (wakeuper != null && isWakeupListening) {
            wakeuper.stopListening();
            isWakeupListening = false;
            notifyStatus(VoiceStatus.IDLE);
            Log.i(TAG, "停止唤醒监听");
        }
    }

    /**
     * 开始语音识别
     * 识别用户语音并转为文字
     */
    public boolean startRecognition() {
        if (recognizer == null) {
            if (!initRecognizer()) {
                notifyError("识别器初始化失败");
                return false;
            }
        }

        // 先停止唤醒（可选，避免冲突）
        // stopWakeupListening();

        // 清空之前的结果
        recognitionResult.setLength(0);

        // 设置识别参数
        recognizer.setParameter(SpeechConstant.ENGINE_TYPE, IflytekConfig.ENGINE_TYPE);
        recognizer.setParameter(SpeechConstant.LANGUAGE, IflytekConfig.LANGUAGE);
        recognizer.setParameter(SpeechConstant.ACCENT, "mandarin"); // 普通话
        recognizer.setParameter(SpeechConstant.RESULT_TYPE, "json");
        recognizer.setParameter(SpeechConstant.VAD_BOS, String.valueOf(IflytekConfig.VAD_BOS));
        recognizer.setParameter(SpeechConstant.VAD_EOS, String.valueOf(IflytekConfig.VAD_EOS));

        int ret = recognizer.startListening(recognizerListener);
        if (ret == 0) {
            isRecognizing = true;
            notifyStatus(VoiceStatus.RECOGNIZING);
            Log.i(TAG, "开始语音识别");
            return true;
        } else {
            notifyError("启动识别失败，错误码: " + ret);
            return false;
        }
    }

    /**
     * 停止语音识别
     */
    public void stopRecognition() {
        if (recognizer != null && isRecognizing) {
            recognizer.stopListening();
            isRecognizing = false;
            notifyStatus(VoiceStatus.IDLE);
            Log.i(TAG, "停止语音识别");
        }
    }

    /**
     * 释放资源
     */
    public void release() {
        stopWakeupListening();
        stopRecognition();

        if (wakeuper != null) {
            wakeuper.destroy();
            wakeuper = null;
        }
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        Log.i(TAG, "VoiceManager已释放");
    }

    /**
     * 获取唤醒资源路径
     */
    private String getResourcePath() {
        return context.getFilesDir().getAbsolutePath() + "/" + IflytekConfig.WAKE_WORD_RES_FILE;
    }

    // ================= 监听器实现 =================

    private final InitListener initListener = code -> {
        if (code != 0) {
            Log.e(TAG, "初始化失败，错误码: " + code);
            notifyError("SDK初始化失败: " + code);
        } else {
            Log.i(TAG, "SDK初始化成功");
        }
    };

    private final WakeuperListener wakeuperListener = new WakeuperListener() {
        @Override
        public void onBeginOfSpeech() {
            Log.d(TAG, "唤醒-检测到语音");
        }

        @Override
        public void onResult(WakeuperResult result) {
            Log.i(TAG, "唤醒成功: " + result.getResultString());
            if (callback != null) {
                callback.onWakeup();
            }
        }

        @Override
        public void onError(SpeechError error) {
            Log.e(TAG, "唤醒错误: " + error.getErrorDescription());
            // 某些错误不中断监听
            if (error.getErrorCode() != 0) {
                notifyError("唤醒错误: " + error.getErrorDescription());
            }
        }

        @Override
        public void onEvent(int eventType, int isLast, int arg2, Bundle obj) {
            Log.d(TAG, "唤醒事件: " + eventType);
        }

        @Override
        public void onVolumeChanged(int volume) {
            // 音量变化，可用于UI反馈
        }
    };

    private final RecognizerListener recognizerListener = new RecognizerListener() {
        @Override
        public void onBeginOfSpeech() {
            Log.d(TAG, "识别-开始说话");
        }

        @Override
        public void onEndOfSpeech() {
            Log.d(TAG, "识别-结束说话");
        }

        @Override
        public void onResult(RecognizerResult result, boolean isLast) {
            String text = parseRecognitionResult(result.getResultString());
            recognitionResult.append(text);

            if (isLast) {
                isRecognizing = false;
                String finalResult = recognitionResult.toString();
                Log.i(TAG, "识别结果: " + finalResult);

                if (callback != null) {
                    callback.onRecognitionResult(finalResult);
                }
                notifyStatus(VoiceStatus.IDLE);
            }
        }

        @Override
        public void onError(SpeechError error) {
            isRecognizing = false;
            Log.e(TAG, "识别错误: " + error.getErrorDescription());
            notifyError("识别错误: " + error.getErrorDescription());
            notifyStatus(VoiceStatus.IDLE);
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            Log.d(TAG, "识别事件: " + eventType);
        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            // 音量变化
        }
    };

    /**
     * 解析讯飞返回的JSON结果
     */
    private String parseRecognitionResult(String json) {
        try {
            JSONTokener tokener = new JSONTokener(json);
            JSONObject joResult = new JSONObject(tokener);
            JSONArray words = joResult.getJSONArray("ws");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < words.length(); i++) {
                JSONArray items = words.getJSONObject(i).getJSONArray("cw");
                JSONObject obj = items.getJSONObject(0);
                sb.append(obj.getString("w"));
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "解析识别结果失败", e);
            return "";
        }
    }

    private void notifyError(String message) {
        if (callback != null) {
            callback.onError(message);
        }
    }

    private void notifyStatus(VoiceStatus status) {
        if (callback != null) {
            callback.onStatusChange(status);
        }
    }

    // ================= 状态查询 =================

    public boolean isWakeupListening() {
        return isWakeupListening;
    }

    public boolean isRecognizing() {
        return isRecognizing;
    }
}
