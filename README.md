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

---

## 本地开发与源码部署指南

本项目采用前后端分离架构，如需在本地进行二次开发或直接基于源码构建部署，请参考以下流程：

### 目录结构
```text
flux-panel/
├── vite-frontend/       # 前端工程 (Vite + React + TailwindCSS + HeroUI)
├── springboot-backend/  # 后端工程 (Spring Boot 2.7 / 3.x + MyBatis-Plus + MySQL)
├── go-gost/             # 底层转发引擎源码 (基于 GOST 二次开发)
├── android-app/         # Android 客户端源码
├── ios-app/             # iOS 客户端源码
├── docker-compose-v4.yml# IPv4 容器编排文件
└── gost.sql             # 数据库初始化结构与种子数据
```

---

### 一、准备环境
- **Node.js**: >= 18.x (推荐 20.x 或 22.x) 及 `pnpm` / `npm`
- **JDK**: Java 17+ (推荐 OpenJDK 17 或 21)
- **Maven**: >= 3.8
- **MySQL**: 5.7 或 8.0+
- **Docker & Docker Compose** (可选，容器化部署必备)

---

### 二、数据库初始化
1. 本地创建 MySQL 数据库，例如 `gost`，字符集选择 `utf8mb4`：
   ```sql
   CREATE DATABASE gost CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
2. 导入项目根目录下的 SQL 脚本：
   ```bash
   mysql -u root -p gost < gost.sql
   ```

---

### 三、后端开发与运行 (`springboot-backend`)
1. **修改配置**：
   进入 `springboot-backend/src/main/resources/application.yml`，核对或修改你的本地 MySQL 数据库连接、账号密码以及 JWT 密钥：
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/gost?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
       username: root
       password: your_db_password
   ```
2. **本地启动**：
   ```bash
   cd springboot-backend
   mvn clean spring-boot:run
   ```
   后端服务默认监听在端口 `6365`。
3. **打包构建**：
   ```bash
   mvn clean package -DskipTests
   # 打包产物位于 target/admin-0.0.1-SNAPSHOT.jar
   ```

---

### 四、前端开发与构建 (`vite-frontend`)
1. **安装依赖**：
   ```bash
   cd vite-frontend
   npm install --legacy-peer-deps
   # 或使用 pnpm install
   ```
2. **配置后端接口代理**：
   检查或配置 `.env.development`：
   ```env
   VITE_API_BASE=http://localhost:6365
   ```
3. **启动本地热重载调试**：
   ```bash
   npm run dev
   ```
   启动后访问 `http://localhost:3000` 即可开始调试。
4. **编译构建静态资源**：
   ```bash
   npm run build
   ```
   构建完成后输出到 `dist/` 目录，将产物丢入 Nginx 或任何静态 Web 服务器即可上线运行。

---

### 五、Docker 本地镜像自建与编排 (可选)
如果需要把自己的修改构建为本地 Docker 镜像运行：
1. **构建前端与后端镜像**：
   ```bash
   # 构建前端
   docker build -t my-vite-frontend:latest ./vite-frontend

   # 构建后端
   docker build -t my-springboot-backend:latest ./springboot-backend
   ```
2. **结合 docker-compose 运行**：
   修改 `docker-compose-v4.yml` 中的镜像名称为 `my-vite-frontend:latest` 和 `my-springboot-backend:latest`，然后执行：
   ```bash
   docker compose -f docker-compose-v4.yml up -d
   ```

---

## 免责声明

本项目仅供个人学习与网络技术研究使用，基于开源项目进行二次开发。

使用本项目所带来的任何风险均由使用者自行承担，包括但不限于：
- 配置不当或使用错误导致的服务异常或不可用；
- 使用本项目引发的网络滥用或违规行为；
- 服务器因使用者自身安全配置问题导致的数据泄露、资源消耗或损失；
- 因违反当地法律法规所产生的任何责任。

使用者必须确保其使用行为完全符合所在国家或地区的法律法规，请在合法、合规的前提下使用。
