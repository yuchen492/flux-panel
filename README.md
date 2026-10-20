# flux-panel 转发面板 (哆啦A梦转发面板)

基于 [go-gost/gost](https://github.com/go-gost/gost) 和 [go-gost/x](https://github.com/go-gost/x) 开源库构建的高性能流量转发管理面板。

---

## 优化与改进说明
- **侧边栏折叠/展开优化**：新增顶部一键折叠/展开侧边栏菜单功能，支持自适应窄栏模式（72px）与本地状态持久化存储。
- **UI 体验提升**：优化高分辨率屏与宽屏下的图表自适应展示，操作更加清爽流畅。
- **纯源码归档**：保留完整前后端未编译源码工程，方便二次定制与自建镜像。

---

## 特性

- 支持按 **隧道账号级别** 管理流量转发数量，可用于用户/隧道配额控制
- 支持 **TCP** 和 **UDP** 协议的转发
- 支持两种转发模式：**端口转发** 与 **隧道转发**
- 可针对 **指定用户的指定隧道进行限速** 设置
- 支持配置 **单向或双向流量计费方式**，灵活适配不同计费模型
- 提供灵活的转发策略配置，适用于多种网络场景

---

## 部署流程

### Docker Compose 快速部署

面板端一键部署：
```bash
curl -L https://raw.githubusercontent.com/yuchen492/flux-panel/refs/heads/main/panel_install.sh -o panel_install.sh && chmod +x panel_install.sh && ./panel_install.sh
```

节点端一键部署：
```bash
curl -L https://raw.githubusercontent.com/yuchen492/flux-panel/refs/heads/main/install.sh -o install.sh && chmod +x install.sh && ./install.sh
```

#### 默认管理员账号
- **账号**: `admin_user`
- **密码**: `admin_user`

> ⚠️ 首次登录后请立即修改默认密码！

---

## 免责声明

本项目仅供个人学习与网络技术研究使用，基于开源项目进行二次开发。

使用本项目所带来的任何风险均由使用者自行承担，包括但不限于：
- 配置不当或使用错误导致的服务异常或不可用；
- 使用本项目引发的网络滥用或违规行为；
- 服务器因使用者自身安全配置问题导致的数据泄露、资源消耗或损失；
- 因违反当地法律法规所产生的任何责任。

使用者必须确保其使用行为完全符合所在国家或地区的法律法规，请在合法、合规的前提下使用。
