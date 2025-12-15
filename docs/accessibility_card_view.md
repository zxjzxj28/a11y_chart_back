# 图表无障碍访问视图 - 开发文档

## 概述

本次更新为图表无障碍服务添加了全新的**卡片式访问视图**（Summary Card View），提供更加优化的 TalkBack 无障碍体验。该视图采用全屏悬浮窗口设计，包含摘要节点、图表图像、数据点列表和退出按钮四个区域，支持完整的焦点导航和无障碍播报。

## 新增文件清单

### 数据模型 (model/)

| 文件 | 说明 |
|------|------|
| `ChartInfo.java` | 图表信息数据模型，包含摘要、标题、图片和数据点列表 |
| `DataPoint.java` | 数据点数据模型，包含标签、值、描述和无障碍播报方法 |

### 视图组件 (overlay/)

| 文件 | 说明 |
|------|------|
| `ChartAccessOverlayManager.java` | 全屏悬浮窗口管理器，负责视图的创建、显示、隐藏和焦点导航 |
| `DataPointAdapter.java` | RecyclerView 适配器，用于展示数据点列表并监听焦点变化 |

### 布局文件 (res/layout/)

| 文件 | 说明 |
|------|------|
| `overlay_chart_access.xml` | 主布局文件，定义四个功能区域的结构 |
| `item_data_point_card.xml` | 数据点卡片布局，用于 RecyclerView 中的每个数据项 |

### Drawable 资源 (res/drawable/)

| 文件 | 说明 |
|------|------|
| `card_bg_selector.xml` | 普通卡片背景选择器（支持聚焦状态变化） |
| `card_bg_exit_selector.xml` | 退出按钮背景选择器（红色聚焦高亮） |
| `card_bg_data_point_selector.xml` | 数据点卡片背景选择器 |
| `bg_white_rounded.xml` | 图表图像区域白色圆角背景 |

### 资源文件 (res/values/)

| 文件 | 更新内容 |
|------|----------|
| `colors.xml` | 添加无障碍视图专用颜色资源 |
| `dimens.xml` | 添加布局尺寸、间距、圆角等资源 |

## 视图结构

```
┌─────────────────────────────────────┐
│                                     │
│  ① 摘要节点区域 (SummaryCard)       │  ← 可聚焦，展示图表整体描述
│                                     │
├─────────────────────────────────────┤
│                                     │
│  ② 图表图像区域 (ChartImageView)    │  ← 不可聚焦，仅展示图表图片
│                                     │
├─────────────────────────────────────┤
│                                     │
│  ③ 数据点列表区域 (DataPointList)   │  ← 可滑动 RecyclerView
│     ┌─────────────────────────┐     │     每个 Item 可聚焦
│     │  数据点卡片1            │     │
│     └─────────────────────────┘     │
│     ┌─────────────────────────┐     │
│     │  数据点卡片2            │     │
│     └─────────────────────────┘     │
│     │  ...更多数据点          │     │
│                                     │
├─────────────────────────────────────┤
│                                     │
│  ④ 退出节点区域 (ExitButton)        │  ← 可聚焦，点击退出视图
│                                     │
└─────────────────────────────────────┘
```

## 焦点导航顺序

焦点导航按以下顺序进行：

```
摘要节点 → 数据点1 → 数据点2 → ... → 数据点N → 退出节点
```

支持循环导航：从退出节点向下滑动可回到摘要节点。

## UI 规格

### 窗口容器

| 属性 | 值 |
|------|-----|
| 窗口类型 | `TYPE_ACCESSIBILITY_OVERLAY` |
| 窗口标志 | `FLAG_NOT_TOUCH_MODAL | FLAG_WATCH_OUTSIDE_TOUCH | FLAG_LAYOUT_IN_SCREEN` |
| 宽度 | `MATCH_PARENT` |
| 高度 | `MATCH_PARENT` |
| 背景色 | `#F5121212`（半透明深灰，约96%不透明度）|
| 内边距 | 左右 12dp，上 48dp，下 56dp |

### 聚焦状态视觉反馈

**普通卡片聚焦状态：**
- 默认背景色：`#1E1E1E`
- 聚焦背景色：`#1E3A5F`
- 默认边框：`2dp solid #333333`
- 聚焦边框：`2dp solid #4A9EFF`

**退出按钮聚焦状态：**
- 默认背景色：`#1E1E1E`
- 聚焦背景色：`#5C1A1A`
- 默认边框：`2dp solid #333333`
- 聚焦边框：`2dp solid #FF6B6B`

## 集成到 ChartA11yService

### 新增偏好设置

| 设置键 | 默认值 | 说明 |
|--------|--------|------|
| `feature_use_card_view_enabled` | `true` | 是否使用卡片视图（true=卡片视图，false=原面板视图）|

### 使用方法

1. **通过音量键触发**：按照配置的音量键组合（如 UP+DOWN）即可打开/关闭图表访问视图

2. **通过无障碍按钮触发**：点击系统无障碍按钮即可打开/关闭图表访问视图

3. **切换视图模式**：
   ```java
   SharedPreferences sp = getSharedPreferences("a11y_prefs", MODE_PRIVATE);
   sp.edit().putBoolean("feature_use_card_view_enabled", true).apply(); // 启用卡片视图
   sp.edit().putBoolean("feature_use_card_view_enabled", false).apply(); // 使用原面板视图
   ```

### 代码调用示例

```java
// 在 ChartA11yService 中，图表访问视图的显示由 togglePanel() 方法控制
// 该方法会根据 feature_use_card_view_enabled 偏好设置自动选择视图类型

// 手动显示卡片视图
ChartInfo chartInfo = new ChartInfo();
chartInfo.setSummary("图表摘要文本");
chartInfo.setChartTitle("图表标题");
chartInfo.setChartImage(bitmap);
chartInfo.setDataPoints(dataPointList);
accessOverlayManager.showAccessView(chartInfo);

// 关闭卡片视图
accessOverlayManager.dismissAccessView();
```

## 无障碍特性

### ContentDescription 设置

每个可聚焦节点都设置了有意义的 contentDescription：

- **摘要节点**：`"图表摘要：" + 摘要文本`
- **数据点卡片**：`标签 + "，" + 值 + "，" + 描述`
- **退出节点**：`"退出视图，双击退出"`

### TalkBack 支持

1. 开启 TalkBack 后，焦点可以按顺序在摘要→数据点→退出之间切换
2. 每个可聚焦节点的 contentDescription 被正确播报
3. 数据点列表可以滑动，且每个 Item 都可以获得焦点
4. 双击退出节点可以关闭视图
5. 图表图像不参与焦点导航

## 颜色资源

```xml
<!-- 背景色 -->
<color name="overlay_background">#F5121212</color>
<color name="card_bg_normal">#1E1E1E</color>
<color name="card_bg_focused">#1E3A5F</color>
<color name="card_bg_exit_focused">#5C1A1A</color>
<color name="chart_image_bg">#FFFFFF</color>

<!-- 边框色 -->
<color name="border_normal">#333333</color>
<color name="border_focused">#4A9EFF</color>
<color name="border_exit_focused">#FF6B6B</color>

<!-- 文字色 -->
<color name="text_primary">#FFFFFF</color>
<color name="text_secondary">#E0E0E0</color>
<color name="text_tertiary">#999999</color>
<color name="text_hint">#888888</color>
<color name="text_accent">#4A9EFF</color>
<color name="text_chart_title">#666666</color>
```

## 尺寸资源

```xml
<!-- 内边距 -->
<dimen name="overlay_padding_horizontal">12dp</dimen>
<dimen name="overlay_padding_top">48dp</dimen>
<dimen name="overlay_padding_bottom">56dp</dimen>
<dimen name="card_padding">12dp</dimen>
<dimen name="card_padding_horizontal">14dp</dimen>

<!-- 外边距 -->
<dimen name="card_margin_bottom">8dp</dimen>
<dimen name="section_margin_vertical">4dp</dimen>

<!-- 圆角 -->
<dimen name="card_corner_radius">12dp</dimen>
<dimen name="card_corner_radius_small">10dp</dimen>

<!-- 字号 -->
<dimen name="text_size_title">13sp</dimen>
<dimen name="text_size_value">14sp</dimen>
<dimen name="text_size_body">13sp</dimen>
<dimen name="text_size_label">11sp</dimen>
<dimen name="text_size_hint">10sp</dimen>

<!-- 图表图像 -->
<dimen name="chart_image_max_height">120dp</dimen>
```

## 测试验证清单

实现完成后，请验证以下功能：

- [ ] 开启 TalkBack 后，焦点可以按顺序在摘要→数据点→退出之间切换
- [ ] 每个可聚焦节点的 contentDescription 被正确播报
- [ ] 聚焦状态的视觉变化（背景色、边框色）正常显示
- [ ] 数据点列表可以滑动，且每个 Item 都可以获得焦点
- [ ] 双击退出节点可以关闭视图
- [ ] 视图正确避开状态栏和导航栏
- [ ] 图表图像不参与焦点导航
- [ ] 音量键组合可以正常打开/关闭视图
- [ ] 切换 `feature_use_card_view_enabled` 偏好设置可以在两种视图模式之间切换

## 更新日志

### v1.0.0 (2024-XX-XX)

- 新增全屏悬浮卡片视图
- 支持摘要播报
- 支持数据点列表焦点导航
- 支持聚焦状态视觉反馈
- 集成到 ChartA11yService，可通过音量键或无障碍按钮触发
