# LiveFlashAssistant V2.0

面向 iQOO Z9 Turbo+ / Android 16 / OriginOS 6 的本地直播画面 OCR 提醒项目。

## 功能
- MediaProjection 用户授权后捕获屏幕
- ML Kit 中文 OCR
- 本地关键词匹配、通知与提示音
- 不保存截图或 OCR 文本
- 检测到支付/结算关键词时暂停匹配
- 不自动点击购买、不提交订单、不发起支付

## 构建
1. 使用 Android Studio 打开项目根目录。
2. 安装 Android SDK Platform 36、Build Tools 和 JDK 17。
3. 等待 Gradle Sync 完成。
4. Build > Build Bundle(s) / APK(s) > Build APK(s)。

## 使用
1. 安装 APK 并打开。
2. 先填写目标商品关键词，多个关键词用逗号分隔。
3. 点击“授权并开始屏幕识别”，在系统弹窗中确认屏幕共享。
4. 切换到抖音直播间。识别到关键词时会发出通知。
5. 结束后回到应用或通知栏停止识别。

## 重要限制
- MediaProjection 必须由用户显式授权；Android 版本和厂商后台策略可能影响持续运行。
- OCR 只做文字匹配，不代表确认商品、价格、库存或秒杀状态。
- 不包含 Root 调用。该功能不需要 Root；不要授予不必要的超级用户权限。
- 银行卡付款必须在抖音官方支付页面由用户完成授权。
- 这是源代码项目，不是已编译、已在该型号实机验证的 APK。
