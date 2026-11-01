# Windows组件部署

本包提供已编译WPF客户端和Java后台；不含个人数据或全部第三方运行时。当前Windows实现使用 D:\tool\qita\SuperAutoStudy 作为工作目录，首次部署请使用该目录；如需改路径，需统一修改后台路径常量和桌面Root后重新编译。已经部署过的用户先停止服务并备份自己的数据后再替换组件。

依赖：Windows 10/11、.NET Framework 4.x WPF、Java 8、MariaDB/MySQL、Edge；浏览器适配还需要Node和Playwright。源码构建后台使用Maven，桌面使用desktop/build.ps1及.NET Framework自带csc。

1. 将组件包解压到工作目录；将Java8放到runtime/jdk8u504-b01，配置并启动自己的数据库，创建tihai数据库及用户。
2. 仅在**全新数据库**导入backend/schema.sql；不要覆盖已有数据。将backend/application-local.example.yml复制为config/application-local.yml，按你的数据库修改端口及用户名，设置STUDENTTONG_DB_PASSWORD环境变量；该变量仅填写自己的数据库密码。
3. 将bridges中的脚本复制到config。安装Node运行环境到runtime/vision-node，并在该目录执行npm install playwright；已有Edge可用于平台浏览器。
4. 在工作目录用Java启动target/SuperAuto-1.0-SNAPSHOT.jar，参数为 --spring.profiles.active=local --spring.config.additional-location=file:./config/ --spring.cloud.bootstrap.enabled=false。Windows证书库可使用 -Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE。
5. 服务仅绑定127.0.0.1:3030。运行client/StudyDesk.exe，通过界面添加你自己的平台账号。所有账号、密钥和浏览器会话在本机生成，发行包没有预置。

从源码构建：在backend运行mvn package；在desktop运行build.ps1。插件AssessmentProvider.cs需引用StudyDesk.Contracts.dll编译为Assessments.Provider.dll，放入client/plugins，按modules.json注册。移动版不依赖以上Windows环境。
