# 1.14.22 私聊进入位置修复

- 移除两条私聊首次加载路径将最新原始消息ID写入“指定消息跳转”的旧逻辑。避免列表先回到底部，又跳到最新一轮回复第一个气泡并关闭跟随。
- 普通打开私聊由共享MessageList定位真正底部。搜索/聊天历史点击仍使用指定消息跳转，定位目标后允许阅读历史。
- 私聊、群聊共享按钮改为“↓ 回到底部”，有新消息时为“↓ 新消息 · 回到底部”，明确这是操作入口。
- 不调整历史加载数量、气泡排序、人设、记忆或用户设置。不修改数据库结构、SQL或迁移，不执行升级数据清理。
- applicationId保持不变，versionCode69/versionName1.14.22。

检查脚本verify_chat_entry_1_14_22.py针对两个加载入口、非空跳转目标来源、历史导航保留和底部按钮进行静态回归检查。它不替代真机UI测试。设备未连接，尚未完成多气泡、长回复和真实存档覆盖安装验收。

## 检查结果

- 266项单元测试通过，0失败、0错误、0跳过。
- 4项静态回归检查通过，结果保存到chat-entry-verification-1.14.22.json。
- shared模块和数据库文件无改动，git diff --check通过。
- 本版仅删除加载完成后的多余跳转，没有改搜索目标、列表排序与历史分页策略。
- Release构建BUILD SUCCESSFUL，日志build-1.14.22.log。APK包名com.buzidongshouhuoji.privatechat、versionCode69、versionName1.14.22已核对。
- 与1.14.21 APK均通过apksigner验证，签名证书SHA-256一致：7f0c044642f66ae92417cc6bd1163e51e58a42ff6046310cac4e64d372a52a5c。
- 安装包apk-output/app-universal-release-1.14.22.apk；SHA-256：7F7B3274DA24C165C6CB63FCAEDBB6F816452EE1A7F7FBC6E62BEEB7706FF050。
