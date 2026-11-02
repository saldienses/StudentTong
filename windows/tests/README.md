# 原生归档安全测试

此测试只在生成的build/tests目录创建离线tar.gz夹具，不访问平台账号，不进入安装包。

构建windows/build/payload/学生通.exe后，用Python标准库生成USTAR+gzip夹具：valid包含package/cli.js与package/package.json（version=1.62.1），traversal包含package/../../escape.txt，link包含链接条目，checksum在合法tar的头部修改一个字节而不更新校验字段。

使用Windows .NET Framework csc编译ArchiveContracts.cs为测试目录Tests.exe。将四个夹具按valid.tgz、traversal.tgz、link.tgz、checksum.tgz放在Tests.exe同目录，执行Tests.exe <学生通.exe绝对路径>。测试通过反射调用同一原生归档实现，不启动下载或平台任务，检查正常解析、越界拒绝、链接拒绝、损坏拒绝。当前验收4项通过。
