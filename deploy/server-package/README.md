# VibeRemote 服务端部署包

本部署包用于在公司内网或无法直连发布环境中，手工完成 VibeRemote 服务端部署。

## 使用方式

1. 将本压缩包上传到目标 Linux 服务器
2. 解压部署包
3. 复制 `deploy.env.example` 为 `deploy.env`
4. 按目标环境填写 `deploy.env`
5. 执行:

```bash
bash install-server.sh
```

6. 部署完成后执行:

```bash
bash verify-deploy.sh
```

## 重要说明

- 目标服务器需要预装 Java 8、Nginx
- 数据库只需要提前建库，不需要手工建表
- 首次启动后，服务端会自动创建业务表、默认管理员和系统参数
- 部署完成后，门户的“服务端部署”页面会同步提供当前环境的部署包下载入口，便于后续在其他环境复用
- 默认管理员账号:
  - 用户名: `admin`
  - 密码: `admin123`
- `AGENT_PACKAGE_BASE_URL` 留空时，会自动按 `PORTAL_PUBLIC_BASE_URL/agent/release` 推导
- `JWT_SECRET` 留空时，会自动生成并保存到 `${APP_HOME}/deploy-generated.env`

## 主要文件

- `deploy.env.example`: 配置模板
- `install-server.sh`: 主部署脚本
- `verify-deploy.sh`: 部署验证脚本
- `rollback.sh`: 回滚脚本
- `backend/server.jar`: 后端程序
- `frontend/dist/`: 控制台前端静态资源
- `portal/site/`: 门户静态站点
- `agent/release/`: Agent 安装包
- `agent/installer-templates/`: Agent 一键安装脚本模板

## 部署完成后的后端控制

- 启动脚本：`${APP_HOME}/bin/start-backend.sh`
- 停止脚本：`${APP_HOME}/bin/stop-backend.sh`
- 状态脚本：`${APP_HOME}/bin/status-backend.sh`

## 回滚

部署脚本会在 `${APP_HOME}/backups/<timestamp>` 下保留历史备份。

如需回滚：

```bash
bash rollback.sh
```

或指定某个备份目录：

```bash
bash rollback.sh /opt/viberemote/backups/20260331103000
```
