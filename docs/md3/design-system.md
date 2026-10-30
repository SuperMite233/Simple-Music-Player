# SMP Material Design 3

本次改造保留原生 Android Views、所有音乐来源和现有设置/播放/下载逻辑。使用官方 Material Components 1.14.0 的 Material 3 主题、按钮、导航栏、复选框、文本输入与对话框，统一其他自绘列表及容器。

## 视觉契约

- 用户保存的 RGB 是种子，不直接充当所有表面的颜色。官方 `SchemeTonalSpot` 生成浅/深色颜色角色；主色/容器与对应 on-color 成对使用。
- 主背景为 surface，列表卡片为 surfaceContainerLow，导航与迷你播放器为 surfaceContainer，对话框为 surfaceContainerHigh，输入区为 surfaceContainerHighest。背景图和透明度保持原设置。
- 页面标题 24sp，应用栏 22sp，条目标题/正文 16sp，辅助文字 14sp，按钮 14sp，导航标签 12sp。正文采用常规字重，标题使用 medium，保留系统字体缩放。
- 页面间距采用 4/8dp 节奏，手机内容边距 16dp。卡片 12dp、迷你播放器 16dp、对话框 28dp；按钮使用胶囊形状。宽度达到 600dp 使用侧边导航轨；低高度窗口采用紧凑播放条，宽屏播放页为双栏布局。取消原来的彩色渐变横幅和缩放按压。
- Android 可操作目标至少 48dp；图标 24dp。按钮、卡片、导航提供 ripple，选中状态同时提供语义状态。播放主动作使用 primary，其余常规操作使用 tonal。
- 表单保留可见标签；长表单在对话框中滚动。输入和按钮高度随字体增长，不以固定 38/42dp 裁切文字。内容避让状态栏、导航栏、屏幕缺口和键盘。
- 保留浅色/深色/跟随系统、自定义 RGB/预设主题色、背景图/透明度、通知及悬浮歌词设置。主题色编辑取消不会应用预览色。遵循系统减少动画设置。

## 覆盖范围与证据

验收覆盖 8 类顶层页面、来源详情和设置/编辑/播放对话框，使用隔离调试包与合成音乐/账户数据。视觉截图、配置矩阵和发现的问题记录在验收报告中。网站内容、Android 文件选择器及系统权限界面由其提供方控制，MD3 改造覆盖播放器及 WebView 的原生外壳。

## 参考

- [Material 3 Android](https://m3.material.io/develop/android/mdc-android)
- [颜色角色](https://m3.material.io/styles/color/roles)
- [字体](https://m3.material.io/styles/typography/applying-type)
- [Material Components 入门](https://github.com/material-components/material-components-android/blob/master/docs/getting-started.md)
- [UI UX Pro Max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill)：采用触控、对比度、可读性和验收清单；不采用其检索生成的营销网站布局和固定深色配色。
