# 科大讯飞语音SDK配置指南

## 你需要做的配置

### 1. 获取APPID（必须）

1. 访问 [讯飞开放平台](https://www.xfyun.cn/) 注册账号
2. 进入控制台 → 创建新应用
3. 开通以下服务：
   - **语音听写**（在线语音识别）
   - **语音唤醒**（离线唤醒词）
4. 复制应用的 **APPID**

### 2. 配置APPID

编辑 `IflytekConfig.java` 文件：

```java
public static final String APPID = "12345678";  // 替换为你的APPID
```

### 3. 制作唤醒词（必须）

1. 在讯飞控制台 → 你的应用 → 语音唤醒
2. 点击"制作唤醒词资源"
3. 输入唤醒词（如"图表助手"，需要4-6个汉字）
4. 等待制作完成，下载 `.jet` 资源文件

### 4. 放置唤醒词资源

将下载的 `.jet` 文件放到：
```
app/src/main/assets/ivw_res.jet
```

如果文件名不同，修改 `IflytekConfig.java`：
```java
public static final String WAKE_WORD_RES_FILE = "你的文件名.jet";
```

### 5. 下载SDK并放置文件

从 [讯飞SDK下载页面](https://www.xfyun.cn/sdk/dispatcher) 下载Android SDK。

解压后，将以下文件放到对应目录：

| SDK文件 | 放置位置 |
|---------|----------|
| `libs/Msc.jar` | `app/libs/Msc.jar` |
| `libs/armeabi-v7a/libmsc.so` | `app/src/main/jniLibs/armeabi-v7a/libmsc.so` |
| `libs/arm64-v8a/libmsc.so` | `app/src/main/jniLibs/arm64-v8a/libmsc.so` |

### 6. 在应用设置中启用语音功能

在应用的设置页面中，开启"语音指令"功能开关。

## 目录结构

配置完成后，目录结构应该是：

```
app/
├── libs/
│   └── Msc.jar                          ← 讯飞SDK JAR包
├── src/main/
│   ├── assets/
│   │   └── ivw_res.jet                  ← 唤醒词资源
│   ├── jniLibs/
│   │   ├── armeabi-v7a/
│   │   │   └── libmsc.so                ← SO库(32位)
│   │   └── arm64-v8a/
│   │       └── libmsc.so                ← SO库(64位)
│   └── java/.../voice/
│       ├── IflytekConfig.java           ← 配置APPID
│       └── VoiceManager.java            ← 语音管理类
```

## 使用方式

1. 启动应用并开启无障碍服务
2. 在设置中开启"语音指令"功能
3. 说出唤醒词（如"图表助手"）
4. 听到"我在听"后，说出指令：
   - "上一个" - 切换到上一个焦点
   - "下一个" - 切换到下一个焦点
   - "重复朗读" - 重复当前内容
   - "播放摘要" - 播放图表摘要
   - "自动播报" - 开始自动播报
   - "退出" - 退出图表模式

## 常见问题

### 错误码 20006 - 录音失败
- 检查 RECORD_AUDIO 权限是否授予
- 检查是否有其他应用占用麦克风

### 错误码 10102 - 无效APPID
- 确认 APPID 配置正确
- 确认已在讯飞平台开通相关服务

### 唤醒不灵敏
- 调整 `IflytekConfig.WAKE_THRESHOLD` 值（降低数值更灵敏）
- 推荐范围：1000-2000
