# 上游与修改说明

本项目为学生通社区改版，与上游作者、学校及学习平台无隶属关系。

| 来源 | 使用方式 | 许可证 |
|---|---|---|
| [Dainoar/SuperAutoStudy](https://github.com/Dainoar/SuperAutoStudy) | Windows本地Java后台基础，保留原作者注释及Apache许可证；新增本机工作台、任务状态、复习、自测、账号与平台隔离等 | Apache-2.0 |
| [Niuwh/yuketang-jiaoben](https://github.com/Niuwh/yuketang-jiaoben) | 雨课堂脚本适配基础，原脚本保留在third-party；修改版在bridges/yuketang-adapter.js，增加桌面通信、账号隔离及状态接口 | GPL-3.0，完整许可证随附 |
| [ZCShou/GoGoGo](https://github.com/ZCShou/GoGoGo) | 用户指定的定位与项目展示参考，未嵌入该应用、未复制其模拟定位实现；本版安卓从系统LocationManager读取位置 | 参考项目GPL-3.0 |
| [wandergis/coordtransform](https://github.com/wandergis/coordtransform) | WGS84→GCJ02→BD09数学转换移植，见SignLogic.java及随附MIT声明 | MIT |

新增Android原生Java和iOS SwiftUI工程为独立移动端实现，不包含Windows数据库或用户账号。整体集成发行按GPL-3.0提供；各原有文件的版权及许可证仍保留，Apache/MIT文本见licenses目录。当前公开改版日期：2026-11-02。

## Android 0.3.0 新增组件

- 地图交互为本项目实现，使用Leaflet1.9.4（BSD-2-Clause）；源码及许可证位于android/app/assets/map，JS/CSS与Leaflet官方SRI值一致。
- 默认地图数据及地理编码来自OpenStreetMap/Nominatim，遵循可见署名、用户主动查询、缓存与限速规则；支持更换有权使用的HTTPS服务。没有嵌入GoGoGo源码。
- 系统模拟定位使用Android官方TestProvider接口，不替换系统GPS/网络Provider，不隐藏Mock标记，不绕过平台验证。

## Windows 0.3.0 分发

Windows自动配置脚本、目录隔离与打包流程为本项目修改。Playwright1.62.1按Apache-2.0及其ThirdPartyNotices随包保留声明；仅保留所需Chromium运行路径，省略可选的Chrome/Edge重装脚本。Java、MariaDB和Node运行时不随配置ZIP分发，由首次启动从清单列出的官方来源下载并校验，各自许可证随官方档案保留。Inno Setup为可选构建工具，未随发布ZIP分发；项目源码包含安装脚本，当前发布以已验证ZIP为准。
