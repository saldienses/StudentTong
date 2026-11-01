# 学生通 iOS 工程

本工程为不依赖电脑后台的原生 iPhone/iPad 学习工作台，最低 iOS 17。

包含：学习通/雨课堂切换、独立账号持久化 WebKit 数据目录、平台内学习页面、任务与前台媒体进度记录、当前网页题目收录、DeepSeek 参考讲解、JSON 题库导入导出、自测及错题记录。Key 存入 iOS 钥匙串，题库不包含 Key。作业和考试在平台页面人工确认提交。

没有移植 Windows 数据库、全天后台刷课、自动考试交卷或模拟定位。网页结构不匹配、图片题或跨域页面可能读不到完整题目，可导入 Windows 导出的题库 JSON。平台公布答案优先于 AI 参考答案。

**当前交付的是源工程，不是 IPA，尚未在 macOS/Xcode 编译或 iPhone/iPad 实测。Windows 上的结构检查不代表苹果端编译、签名或安装成功。**

## 在 Mac 构建

1. 在 Mac 安装带 iOS 17 或更新 SDK 的 Xcode。解压并打开 StudentTong.xcodeproj。
2. Scheme 选择 StudentTong。先选择一个 iPhone 模拟器，执行 Product → Build / Run，修复可能出现的 SDK 适配或编译问题。
3. 真机安装：Xcode → Settings → Accounts 登录你自己的 Apple Account；在 Target → Signing & Capabilities 勾选 Automatically manage signing，选择 Team。Bundle Identifier 改成你可使用的唯一标识。
4. 连接并信任 iPhone，在设备中按 Xcode 提示启用开发者模式，选择设备后 Run。不要将 Apple 密码或证书私钥发到聊天里。
5. 如果后续需要 TestFlight、Ad Hoc 或 App Store 分发，按 Apple Developer 的当前分发要求完成开发者计划、证书和签名。没有 Mac 时可先保留本工程，等具备构建环境后再执行；不能把 APK 改名成 IPA 安装。

模拟器命令（须在 Mac 执行，本次未执行）：

```sh
xcodebuild -project StudentTong.xcodeproj -scheme StudentTong \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath Build CODE_SIGNING_ALLOWED=NO build
```

## 文件

- Models.swift：账号、任务、题库、错题、导入导出和 Keychain。
- Browser.swift：每账号独立 WKWebsiteDataStore、页面访问与媒体读取。
- StudentTongApp.swift：原生 SwiftUI 界面、AI讲解与自测。
- reader.js / media.js：与 Android 版共享的网页读取脚本，不伪造平台完成记录。
- Assets.xcassets：无外部白边图标。iOS AppIcon 为1024×1024不透明PNG，外侧填充蓝色，交给系统处理图标圆角。

资料默认保存在 App Documents/StudentTong/library.json；平台登录资料由对应账号的 WebKit 数据目录管理。不同设备不会自动同步，可手动导入导出题库 JSON。

官方参考：

- [WebKit 多账号持久化数据目录](https://webkit.org/blog/14423/building-profiles-with-new-webkit-api/)
- [Apple 个人设备开发账号说明](https://developer.apple.com/help/account/basics/about-your-developer-account)
- [Xcode 签名与设备分发](https://developer.apple.com/documentation/xcode/distributing-your-app-to-registered-devices)
