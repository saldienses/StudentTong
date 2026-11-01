# 上游与修改说明

本项目为学生通社区改版，与上游作者、学校及学习平台无隶属关系。

| 来源 | 使用方式 | 许可证 |
|---|---|---|
| [Dainoar/SuperAutoStudy](https://github.com/Dainoar/SuperAutoStudy) | Windows本地Java后台基础，保留原作者注释及Apache许可证；新增本机工作台、任务状态、复习、自测、账号与平台隔离等 | Apache-2.0 |
| [Niuwh/yuketang-jiaoben](https://github.com/Niuwh/yuketang-jiaoben) | 雨课堂脚本适配基础，原脚本保留在third-party；修改版在bridges/yuketang-adapter.js，增加桌面通信、账号隔离及状态接口 | GPL-3.0，完整许可证随附 |
| [ZCShou/GoGoGo](https://github.com/ZCShou/GoGoGo) | 用户指定的定位与项目展示参考，未嵌入该应用、未复制其模拟定位实现；本版安卓从系统LocationManager读取位置 | 参考项目GPL-3.0 |
| [wandergis/coordtransform](https://github.com/wandergis/coordtransform) | WGS84→GCJ02→BD09数学转换移植，见SignLogic.java及随附MIT声明 | MIT |

新增Android原生Java和iOS SwiftUI工程为独立移动端实现，不包含Windows数据库或用户账号。整体集成发行按GPL-3.0提供；各原有文件的版权及许可证仍保留，Apache/MIT文本见licenses目录。当前公开改版日期：2026-11-02。
