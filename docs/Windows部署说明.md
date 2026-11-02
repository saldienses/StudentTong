# 学生通 Windows 0.3.1

双击 StudentTong-Windows-0.3.1-Setup.exe，推荐安装到 D:\tool\qita\StudentTong 的新目录。安装器只安装应用文件，不自动运行配置脚本、不更改防护设置。

安装完成后从桌面“学生通”或安装目录“学生通.exe”打开可见的环境配置窗口，点击“配置环境并启动”。首次需要联网，从官方来源下载并校验 Java、MariaDB、Node、Playwright Core 和对应Chromium。数据库与缓存、下载、日志均放在所选目录，不修改系统PATH，不注册后台服务。

以后仍使用桌面快捷方式启动。已有旧版“学生通”快捷方式时，新版将使用“学生通0.3.1”，不会覆盖旧版入口。关闭界面不等于停止后台，停止请重新打开环境配置窗口，点击“停止本版本后台”。

账号、Cookie、API Key和旧版历史数据不在安装包中；本版不自动迁移旧数据库。旧版请保留，并在新版本登录自己的账号。升级现有0.3.1目录前请先停止该版本后台并关闭界面；不要在运行中移动目录。

如果缺少微软VC++运行库，配置窗口会给出微软官方下载链接，需要用户自行确认安装该系统组件。本程序不静默修改系统组件；当前电脑已具备运行库，该分支未在全新Windows虚拟机验证。

官方访问域名包括 GitHub、archive.mariadb.org、nodejs.org、registry.npmjs.org 与 Playwright浏览器CDN。下载断开后保留完整已校验文件和诊断日志，可重试。问题查看logs/environment-setup.log、environment-error.log、application.log与database.err。不要公开上传config、data或完整个人日志。

本版使用可见原生.NET配置程序，移除旧版PowerShell引导和执行策略Bypass调用。运行时下载均校验固定SHA256；浏览器依赖档案同时与npm官方SHA512来源核对。依赖来源不等于绝对安全保证，保持安全软件开启。

上游、许可证及验收范围见仓库：https://github.com/saldienses/StudentTong 。此安装包未购买代码签名证书，测试结果只代表这台电脑和记录时的检测库，不能承诺所有安全产品永不报警。
