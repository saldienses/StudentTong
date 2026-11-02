<div align="center">
<img src="docs/images/icon.png" width="120" alt="学生通图标" />

# 学生通 · 学习通与雨课堂学习助手（99%集成开源项目和ai）

Windows 本机工作台 · Android 独立手机版 · iOS 工程

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Android 9+](https://img.shields.io/badge/Android-9%2B-green.svg)](https://github.com/saldienses/StudentTong/releases)
[下载安装](https://github.com/saldienses/StudentTong/releases) · [手机版指南](docs/手机版使用指南.md) · [上游与许可证](UPSTREAM.md)
</div>

学生通将学习通、雨课堂的学习页面、任务、复习与账号管理放进一个本机工作台。账号资料保存在各自的本机空间；Android 可独立使用，不需要电脑提供后台服务。

## 功能

| 功能 | Windows | Android 0.2 |
|---|---|---|
| 学习通／雨课堂平台切换、账号独立空间 | 支持 | 支持，最多16个账号空间 |
| 章节／作业／考试分类与学习页面 | 支持 | 支持打开平台页面、保存任务 |
| 任务进度、停止、删除 | 支持任务执行进度 | 支持前台可读取媒体的真实进度与任务管理 |
| 题目收录、错题、自测与答案来源标注 | 支持课程归纳、Word导出 | 当前可读取页面收录、JSON导入导出、HTML复习导出 |
| DeepSeek | 本机配置与适配流程 | 独立Key配置、选题后确认AI讲解 |
| 签到 | 普通签到、位置管理、原平台处理入口 | 新增签到中心、普通签到确认、系统定位、位置确认、官方回执核对 |
| iOS | — | 提供独立SwiftUI工程，尚未编译、签名或真机测试 |

**各端能力不同。** Android不运行Windows数据库，也不提供全天后台刷课、自动作业或自动考试交卷。雨课堂保留适配入口及离线验证，当前没有实际账号用于验证完整登录与提交流程。读取不了的图片题、跨域页面和平台专有能力不会被标为已经支持。

## 下载安装

打开 [Releases](https://github.com/saldienses/StudentTong/releases)，按设备选择：

- `StudentTong-Android-0.2.0.apk`：Android 9及以上；同一发布签名可覆盖更新。APK签名私钥不在仓库中。
- `StudentTong-Windows-components-0.2.0.zip`：Windows本机工作台与Java后台组件；需要自行配置Java、MariaDB/MySQL和相关浏览器运行环境，**不是已打包运行时的一键安装器**。见[Windows部署说明](docs/Windows部署说明.md)。
- `StudentTong-iOS-source-0.2.0.zip`：iOS工程；需要Mac、Xcode与自己的签名Team构建。ZIP不能直接安装到iPhone；本版本原生签到中心仅适配Android。

## Android签到

1. 在当前账号空间登录，打开课程页面。
2. 底部进入“签到”，从当前页读取课程或粘贴课程/活动链接，刷新活动。
3. 普通签到：打开活动并确认签到。位置签到：先获取系统位置，核对精度、来源和坐标，再提供位置并确认签到。
4. 等待官方回执。未确认结果时使用“核对结果”，软件不会因坐标准备完成而显示签到成功。

位置来自Android系统LocationManager，显示原始WGS84与转换后的平台BD09坐标。定位过期或精度无效时拒绝提交。模拟器测试可使用模拟器自身的定位工具；软件如实展示系统提供的模拟标记，不替代平台安全验证。验证码、人脸和官方客户端专有验证需要用户按平台要求处理，提醒可以在设置中选择开启。

## 验证范围

2026-11-02：Android正式包完成签名及对齐校验，在MuMu Android12完成43项回归检查与原生界面自测／账号隔离检查。另在自建测试课程完成一次位置签到，官方返回success，发布端显示1/1人签到。该活动没有开启指定地点范围，尚不能据此确认所有学校的范围限制或专有验证兼容性。测试账号、Cookie与活动ID未打入正式包。

Windows本地功能的现有实现和扩展接口一并提供；本次主要更新Android签到。iOS仅提供工程与后续构建方案。

## 开发与目录

- `android/`：原生Java移动端，构建步骤见[Android开发构建](android/Android开发构建.md)。
- `ios/`：SwiftUI、WKWebView及本机存储，见[iOS说明](ios/README.md)。
- `backend/`：基于SuperAutoStudy修改的Java8 / Spring Boot后台。
- `desktop/`：Windows WPF本机界面、模块合同与插件示例。
- `bridges/`：平台浏览器适配脚本；配置放在各自的本机运行目录。
- `third-party/`、`licenses/`：原始脚本与上游许可证。

后续可沿平台适配、任务状态、题目归档与本机存储边界扩展。

## 上游与许可

Windows后台基于 [Dainoar/SuperAutoStudy](https://github.com/Dainoar/SuperAutoStudy)；雨课堂脚本基于 [Niuwh/yuketang-jiaoben](https://github.com/Niuwh/yuketang-jiaoben)；定位与展示参考 [ZCShou/GoGoGo](https://github.com/ZCShou/GoGoGo)；坐标转换移植自 [wandergis/coordtransform](https://github.com/wandergis/coordtransform)。详细修改范围、原始许可证与发行方式见 [UPSTREAM.md](UPSTREAM.md)。

本项目不是学习通、雨课堂、学校或上游作者的官方客户端。请在你有权访问的课程和允许的活动中使用。
