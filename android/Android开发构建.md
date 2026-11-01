# Android 源代码与更新

app/ 为原生Java工程，最低Android 9，目标API34；不依赖电脑后台，不包含测试账号。测试包qa/未放进此源代码压缩包，也不会打进正式APK。

在android目录构建，发布APK输出到release目录。工具需从Android官方渠道自行安装。

源代码压缩包不重复携带SDK。独立重新构建需提供：Java8 javac、可运行Android工具的Java17及以上、Android API34的android.jar、Android Build Tools34.0.0。请按 build-android.ps1 顶部变量配置路径；也可把构建工具放入 tools/android-14、平台放入 tools/android-34-ext12，保持当前目录布局。随后用PowerShell执行 build-android.ps1。

构建依次执行javac、D8、AAPT2、zipalign和apksigner。脚本默认在private中读取或创建本机签名材料，签名密码不写入源代码，也不打入APK。

要升级已经安装的APK，必须使用同一签名密钥。设置 STUDENTTONG_SIGNING_HOME 指向原签名目录；本机保留副本为 D:\tool\qita\SuperAutoStudy\config\mobile-signing。不要把该目录放入公开分享的源代码压缩包。没有原密钥时生成的新签名包不能覆盖旧签名安装，需另行处理已有数据迁移。

主要文件：Store.java 管理SQLite账号归属、任务和题库；Secret.java 使用Android密钥库；MainActivity.java 为原生界面与平台页面；BrowserSlots.java 使用独立进程和WebView数据目录隔离账号；assets/reader.js和media.js读取当前可访问的网页内容。后续可在这些边界扩展平台适配。

官方工具说明：https://developer.android.com/tools/aapt2
