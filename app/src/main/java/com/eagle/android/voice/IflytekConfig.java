package com.eagle.android.voice;

/**
 * 科大讯飞SDK配置类
 *
 * 【用户需要配置的内容】
 * 1. APPID - 在讯飞开放平台创建应用后获取
 * 2. 唤醒词资源文件 - 在讯飞平台制作唤醒词后下载，放到 assets 目录
 */
public class IflytekConfig {

    // ==================== 必须配置项 ====================

    /**
     * 【必须配置】科大讯飞APPID
     *
     * 获取方式：
     * 1. 访问 https://www.xfyun.cn/ 注册并登录
     * 2. 进入控制台 -> 创建新应用
     * 3. 复制应用的 APPID 到这里
     */
    public static final String APPID = "YOUR_APPID_HERE";  // TODO: 替换为你的APPID

    /**
     * 【必须配置】唤醒词资源文件名
     *
     * 获取方式：
     * 1. 在讯飞控制台进入你的应用 -> 语音唤醒
     * 2. 制作唤醒词（如"图表助手"，4-6个汉字）
     * 3. 下载唤醒词资源文件（.jet格式）
     * 4. 将 .jet 文件放到 app/src/main/assets/ 目录
     * 5. 修改下面的文件名
     */
    public static final String WAKE_WORD_RES_FILE = "ivw_res.jet";  // TODO: 替换为你的唤醒词资源文件名

    // ==================== 可选配置项 ====================

    /**
     * 唤醒词门限值（0-3000）
     * 值越大，越不容易被唤醒（误唤醒率低，但可能漏唤醒）
     * 值越小，越容易被唤醒（响应灵敏，但可能误唤醒）
     * 推荐值：1450
     */
    public static final int WAKE_THRESHOLD = 1450;

    /**
     * 是否持续唤醒
     * true: 唤醒后继续监听，可多次唤醒
     * false: 唤醒一次后停止
     */
    public static final boolean KEEP_ALIVE = true;

    /**
     * 语音识别语言
     * zh_cn: 中文
     * en_us: 英文
     */
    public static final String LANGUAGE = "zh_cn";

    /**
     * 语音识别引擎类型
     * cloud: 在线识别（需要网络，准确率高）
     * local: 离线识别（无需网络，需要额外资源）
     * mixed: 混合模式
     */
    public static final String ENGINE_TYPE = "cloud";

    /**
     * 语音前端点超时（毫秒）
     * 用户开始说话前的静音时长超过此值，认为无语音输入
     */
    public static final int VAD_BOS = 5000;

    /**
     * 语音后端点超时（毫秒）
     * 用户说话后的静音时长超过此值，认为说话结束
     */
    public static final int VAD_EOS = 1800;

    /**
     * 检查APPID是否已配置
     */
    public static boolean isConfigured() {
        return APPID != null
            && !APPID.isEmpty()
            && !APPID.equals("YOUR_APPID_HERE");
    }
}
