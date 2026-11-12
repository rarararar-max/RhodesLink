# 2026-11-03 备份大记录与导入格式诊断

## 原因与处理

备份原先通过 SQLDelight 完整查询数据库记录。聊天存档 messagesJson/stateJson、知识库原文、长消息等可能导致 SQLiteBlobTooBigException，分页 ZIP 写入发生在查询之后，无法规避 CursorWindow 单行限制。

新增 ChunkedBackupDriver，仅用于 BackupSnapshotBuilder 的独立数据库读取句柄。普通查询继续原有路径；遇到 SQLiteBlobTooBigException 后，在事务中查询字段类型、字节长度、短值和 rowid，再以 16 KiB BLOB 子串读取大字段。按原始字节组装后解码 UTF-8，保留中文、非 BMP 字符、表情和 NUL。支持 SQLDelight 展开的字段投影、WHERE 参数和排序；不支持的表达式明确失败，不跳过或截断记录。独立句柄 finally 关闭，各备份模块记录开始/结束阶段，错误不含正文。

此修改不迁移数据库结构，不调整既有备份格式与校验规则。单页大小等既有备份安全上限仍保留，不能视为无限大小备份支持。普通聊天界面的查询不改动。恢复前安全备份和诊断备份同样经过安全读取入口。

## 导入提示

以前 ZipInputStream 对 JSON 或其他非 ZIP 文件可能返回零条目，最终只显示缺少 manifest.json。现在先识别空文件、JSON、SQLite 和 ZIP 特征，并区分 ZIP 内多包一层目录与根清单缺失。仍拒绝无清单备份，不自动编造清单或绕过完整性校验。旧版 JSON 不在本次修改中自动转换；具体用户文件需要原件检查，不能仅凭截图判定损坏或格式。

## 验证

- :app:compileDebugKotlin 成功。
- :app:testDebugUnitTest：340 tests，0 failures，0 errors。
- :app:assembleDebug 与 :app:assembleDebugAndroidTest 成功。
- 模拟器 BackupLargeRowIntegrationTest：3 tests 全部通过。
  - 大知识库、多行、空字符串、参数查询完整性。
  - 原始普通查询复现 CursorWindow 异常，安全查询、ZIP 写入读取、写回另一临时数据库及再次安全读取内容一致。
  - 同一行多个大字段、3 MiB BLOB、NULL、布尔、整数、浮点和 UTF-8 内容一致。
- 导入格式单元测试覆盖空文件、JSON/BOM、数据库、普通文本、多一层目录 ZIP、缺少清单 ZIP；既有备份完整性测试通过。

本轮未升级版本、未构建发布安装包。
