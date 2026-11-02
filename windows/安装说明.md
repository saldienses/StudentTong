# 学生通 Windows 0.3.0 安装说明

适用 Windows 10/11 64位。下载 StudentTong-Windows-0.3.0-AutoSetup.zip，完整解压到新目录，双击“启动学生通.cmd”。推荐 D:\tool\qita\StudentTong。首次完成后会创建桌面快捷方式。首次需要联网；配置程序将自动校验并配置 Java、MariaDB、Node 和 Chromium，无需手动配置数据库或环境变量。

1. 等待下载、文件校验、空数据库创建和工作台启动。
2. 以后双击桌面的“学生通”或安装目录“启动学生通.cmd”。
3. 关闭界面后后台可能继续运行。停止本目录全部后台：双击“停止学生通.cmd”。
4. 安装程序只包含程序与空表结构，没有账号、密码、Cookie、API Key和历史任务。请在软件中登录、配置自己的API。
5. 每个安装目录使用独立数据库和端口。不要在程序运行时移动文件夹或覆盖安装；移动目录前先停止后台，整体保留 config、data、runtime。移动后双击“启动学生通.cmd”会重新检查路径。
6. 安装器不迁移旧版账号与数据库。旧版不会被自动删除或覆盖，已有旧版时请选择不同目录；可在新版重新登录。

## 文件与网络

下载、缓存、日志、数据库和浏览器都位于所选目录，不修改系统 PATH、不注册 Windows 服务。安装在D盘即可将这些应用文件留在D盘。Windows自身的临时记录、桌面快捷方式和系统组件由Windows管理。

若系统缺少微软VC++运行库，会下载并验证微软签名，再请求Windows授权安装该系统组件。需要管理员同意的仅是该系统运行库，不能保证系统组件文件不写入系统盘。当前电脑已具备该运行库，此分支未在全新Windows虚拟机测试。

首次配置需要访问 GitHub、archive.mariadb.org、nodejs.org、Playwright 浏览器CDN。网络失败后保留已下载的完整文件，重新运行即可重试；不关闭TLS或文件校验。该发布包未购买代码签名证书。

启动故障查看 logs/startup-error.log、application.out.log、database.err 和 download-*.log，不要将含账号资料的完整 config 或 data 文件夹公开上传。

## 上游及运行时

项目源代码：https://github.com/saldienses/StudentTong 。Windows后台基于 Dainoar/SuperAutoStudy。详细许可证见 UPSTREAM.md、licenses 和各运行时随附的 LICENSE。
Java 使用 Eclipse Temurin8 JRE；数据库 MariaDB11.4.5；Node24.22.1；Playwright1.62.1 和对应 Chromium。准确官方下载地址及SHA256保存在 runtime-manifest.json。运行时首次从官方来源下载；安装器内不含Java/MariaDB运行时二进制。
