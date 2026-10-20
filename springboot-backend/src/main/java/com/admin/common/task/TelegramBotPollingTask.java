package com.admin.common.task;

import com.admin.entity.Forward;
import com.admin.entity.Node;
import com.admin.entity.Tunnel;
import com.admin.entity.User;
import com.admin.entity.ViteConfig;
import com.admin.service.ForwardService;
import com.admin.service.NodeService;
import com.admin.service.TunnelService;
import com.admin.service.UserService;
import com.admin.service.ViteConfigService;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.DigestUtils;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Telegram 机器人交互菜单与长轮询监听服务
 * 支持管理员与普通用户双模交互：
 * - 管理员：全量节点监控、全量转发规则、平台总流量、用户统计、晚报设置 (菜单标注管理员)
 * - 普通用户：绑定个人账号、只查自己名下的流量与配额、只查自己的专属晚报 (严格按用户隔离)
 */
@Slf4j
@Configuration
@EnableScheduling
public class TelegramBotPollingTask {

    @Resource
    private ViteConfigService viteConfigService;

    @Resource
    private UserService userService;

    @Resource
    private NodeService nodeService;

    @Resource
    private ForwardService forwardService;

    @Resource
    private TunnelService tunnelService;

    @Resource
    private DailyReportTask dailyReportTask;

    @Resource
    private RestTemplate restTemplate;

    private long lastUpdateId = 0;
    private boolean isPolling = false;
    private boolean commandsRegistered = false;

    // 每 2 秒轮询一次 Telegram updates
    @Scheduled(fixedDelay = 2000)
    public void pollTelegramUpdates() {
        if (isPolling) return;

        String tgEnabled = getConfigValue("alarm_tg_enabled", "false");
        String botToken = getConfigValue("alarm_tg_token", "");
        String adminChatId = getConfigValue("alarm_tg_chat_id", "");

        if (!"true".equalsIgnoreCase(tgEnabled) || botToken.isEmpty()) {
            return;
        }

        isPolling = true;
        if (!commandsRegistered) {
            registerBotMenuCommands(botToken);
            commandsRegistered = true;
        }

        try {
            String url = "https://api.telegram.org/bot" + botToken + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=1";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return;
            }

            JSONObject json = JSONObject.parseObject(response.getBody());
            if (!json.getBooleanValue("ok")) {
                return;
            }

            JSONArray results = json.getJSONArray("result");
            if (results == null || results.isEmpty()) {
                return;
            }

            for (int i = 0; i < results.size(); i++) {
                JSONObject item = results.getJSONObject(i);
                long updateId = item.getLongValue("update_id");
                if (updateId > lastUpdateId) {
                    lastUpdateId = updateId;
                }

                // 处理私聊消息
                if (item.containsKey("message")) {
                    handleIncomingMessage(botToken, adminChatId, item.getJSONObject("message"));
                }
                // 处理按钮点击回调
                else if (item.containsKey("callback_query")) {
                    handleCallbackQuery(botToken, adminChatId, item.getJSONObject("callback_query"));
                }
            }
        } catch (Exception e) {
            // 忽略网络抖动
        } finally {
            isPolling = false;
        }
    }

    private void handleIncomingMessage(String botToken, String adminChatId, JSONObject message) {
        try {
            JSONObject chat = message.getJSONObject("chat");
            if (chat == null) return;
            String chatId = chat.getString("id");
            String text = message.getString("text");
            if (text == null) return;

            text = text.trim();
            boolean isAdmin = !adminChatId.isEmpty() && adminChatId.equals(chatId);

            // 1. 账号绑定指令: /bind <用户名> <密码>
            if (text.startsWith("/bind")) {
                handleUserBind(botToken, chatId, text);
                return;
            }

            // 2. 解除绑定指令: /unbind
            if (text.startsWith("/unbind")) {
                handleUserUnbind(botToken, chatId);
                return;
            }

            // 3. 管理员分支
            if (isAdmin) {
                if (text.startsWith("/start") || text.startsWith("/menu") || "菜单".equals(text)) {
                    sendAdminMainMenu(botToken, chatId, 0);
                } else if (text.startsWith("/flow") || "流量".equals(text)) {
                    sendAdminFlowSummary(botToken, chatId, false, 0);
                } else if (text.startsWith("/nodes") || "节点".equals(text)) {
                    sendAdminNodesSummary(botToken, chatId, false, 0);
                } else if (text.startsWith("/users") || "用户".equals(text)) {
                    sendAdminUsersSummary(botToken, chatId, false, 0);
                } else if (text.startsWith("/daily") || "晚报".equals(text)) {
                    dailyReportTask.sendDailySummaryReport();
                    sendTelegramHtml(botToken, chatId, "⚡ <b>已即时推送每日流量晚报汇总！请查收。</b>");
                } else if (text.startsWith("/myflow")) {
                    sendUserPersonalFlow(botToken, chatId, null, false, 0);
                } else {
                    sendAdminMainMenu(botToken, chatId, 0);
                }
                return;
            }

            // 4. 普通用户分支：根据 Chat ID 查找对应用户
            User matchedUser = findUserByChatId(chatId);
            if (matchedUser == null) {
                String welcome = "👋 <b>欢迎使用 Flux-Panel 个人通知助手</b>\n" +
                        "--------------------\n" +
                        "您尚未绑定面板账号，请发送下方指令完成绑定：\n" +
                        "👉 <code>/bind 您的用户名 您的密码</code>\n\n" +
                        "<i>绑定后即可随时在 Telegram 查看您的专属个人流量消耗与每日晚报喵🐾</i>";
                sendTelegramHtml(botToken, chatId, welcome);
                return;
            }

            // 已绑定的普通用户响应
            if (text.startsWith("/start") || text.startsWith("/menu") || "菜单".equals(text)) {
                sendUserMainMenu(botToken, chatId, matchedUser, 0);
            } else if (text.startsWith("/myflow") || "我的流量".equals(text) || "流量".equals(text)) {
                sendUserPersonalFlow(botToken, chatId, matchedUser, false, 0);
            } else if (text.startsWith("/daily") || text.startsWith("/myreport") || "晚报".equals(text)) {
                sendUserPersonalDaily(botToken, chatId, matchedUser);
            } else {
                sendUserMainMenu(botToken, chatId, matchedUser, 0);
            }

        } catch (Exception e) {
            log.error("处理 TG 消息异常: ", e);
        }
    }

    private void handleCallbackQuery(String botToken, String adminChatId, JSONObject callbackQuery) {
        try {
            String callbackId = callbackQuery.getString("id");
            JSONObject from = callbackQuery.getJSONObject("from");
            String fromId = from != null ? from.getString("id") : "";
            JSONObject message = callbackQuery.getJSONObject("message");
            int messageId = message != null ? message.getIntValue("message_id") : 0;
            String data = callbackQuery.getString("data");

            answerCallbackQuery(botToken, callbackId);

            if (data == null) return;
            boolean isAdmin = !adminChatId.isEmpty() && adminChatId.equals(fromId);

            // 管理员专属回调
            if (isAdmin) {
                switch (data) {
                    case "menu_admin_main":
                        sendAdminMainMenu(botToken, adminChatId, messageId);
                        break;
                    case "action_admin_flow":
                        sendAdminFlowSummary(botToken, adminChatId, true, messageId);
                        break;
                    case "action_admin_nodes":
                        sendAdminNodesSummary(botToken, adminChatId, true, messageId);
                        break;
                    case "action_admin_users":
                        sendAdminUsersSummary(botToken, adminChatId, true, messageId);
                        break;
                    case "action_admin_daily":
                        dailyReportTask.sendDailySummaryReport();
                        sendTelegramHtml(botToken, adminChatId, "⚡ <b>已即时推送每日流量晚报汇总！请查收。</b>");
                        break;
                    case "action_admin_time_menu":
                        sendAdminTimeSettingsMenu(botToken, adminChatId, messageId);
                        break;
                    default:
                        if (data.startsWith("set_hour_")) {
                            String hourStr = data.replace("set_hour_", "");
                            viteConfigService.updateConfig("alarm_report_hour", hourStr);
                            sendTelegramHtml(botToken, adminChatId, "✅ <b>每日晚报推送时间已修改为: " + hourStr + ":00</b>");
                            sendAdminMainMenu(botToken, adminChatId, messageId);
                        }
                        break;
                }
                return;
            }

            // 普通用户回调
            User matchedUser = findUserByChatId(fromId);
            if (matchedUser == null) {
                sendTelegramHtml(botToken, fromId, "⚠️ 账号未绑定，请发送 <code>/bind 用户名 密码</code> 绑定。");
                return;
            }

            switch (data) {
                case "menu_user_main":
                    sendUserMainMenu(botToken, fromId, matchedUser, messageId);
                    break;
                case "action_user_flow":
                    sendUserPersonalFlow(botToken, fromId, matchedUser, true, messageId);
                    break;
                case "action_user_daily":
                    sendUserPersonalDaily(botToken, fromId, matchedUser);
                    break;
                case "action_user_unbind":
                    handleUserUnbind(botToken, fromId);
                    break;
                default:
                    break;
            }

        } catch (Exception e) {
            log.error("处理 TG 回调异常: ", e);
        }
    }

    // 绑定账号
    private void handleUserBind(String botToken, String chatId, String text) {
        String[] parts = text.split("\\s+");
        if (parts.length < 3) {
            sendTelegramHtml(botToken, chatId, "❌ <b>格式错误</b>\n请按如下格式发送：\n<code>/bind 您的用户名 您的密码</code>");
            return;
        }

        String username = parts[1].trim();
        String password = parts[2].trim();
        String md5Pwd = DigestUtils.md5DigestAsHex(password.getBytes());

        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("user", username);
        User user = userService.getOne(qw);

        if (user == null || !md5Pwd.equalsIgnoreCase(user.getPwd())) {
            sendTelegramHtml(botToken, chatId, "❌ <b>验证失败</b>\n用户名或密码错误，请核对后重试。");
            return;
        }

        // 保存绑定关系到 vite_config 表
        String bindKey = "user_tg_chat_id_" + user.getId();
        viteConfigService.updateConfig(bindKey, chatId);

        String successMsg = String.format(
                "🎉 <b>账号绑定成功！</b>\n" +
                "--------------------\n" +
                "👤 绑定用户: <b>%s</b>\n" +
                "💬 Telegram ID: <code>%s</code>\n" +
                "--------------------\n" +
                "现在您可以发送 /menu 调出您的专属个人控制面板喵🐾",
                user.getUser(), chatId
        );
        sendTelegramHtml(botToken, chatId, successMsg);
        sendUserMainMenu(botToken, chatId, user, 0);
    }

    // 解除绑定
    private void handleUserUnbind(String botToken, String chatId) {
        User user = findUserByChatId(chatId);
        if (user != null) {
            viteConfigService.updateConfig("user_tg_chat_id_" + user.getId(), "");
        }
        sendTelegramHtml(botToken, chatId, "👋 <b>已成功解除 Telegram 绑定。</b>\n后续若需使用可随时发送 /bind 重新绑定。");
    }

    // 根据 Chat ID 寻找对应用户
    private User findUserByChatId(String chatId) {
        try {
            List<User> list = userService.list();
            if (list == null) return null;
            for (User u : list) {
                String val = getConfigValue("user_tg_chat_id_" + u.getId(), "");
                if (chatId.equals(val)) {
                    return u;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ==================== 管理员界面 (标明管理员) ====================

    private void sendAdminMainMenu(String botToken, String chatId, int editMessageId) {
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String text = String.format(
                "👑 <b>Flux-Panel 快捷运维控制台 (管理员)</b>\n" +
                "--------------------\n" +
                "当前服务器时间: <code>%s</code>\n" +
                "身份标识: <b>平台总管理员</b>\n" +
                "请点击下方快捷按钮进行交互操作喵：",
                timeStr
        );

        List<List<Map<String, String>>> keyboard = new ArrayList<>();

        List<Map<String, String>> row1 = new ArrayList<>();
        row1.add(Map.of("text", "📊 平台总流量 (管理员)", "callback_data", "action_admin_flow"));
        row1.add(Map.of("text", "🌐 全量节点与转发 (管理员)", "callback_data", "action_admin_nodes"));
        keyboard.add(row1);

        List<Map<String, String>> row2 = new ArrayList<>();
        row2.add(Map.of("text", "👥 用户概况 (管理员)", "callback_data", "action_admin_users"));
        row2.add(Map.of("text", "⚡ 立即触发晚报 (管理员)", "callback_data", "action_admin_daily"));
        keyboard.add(row2);

        List<Map<String, String>> row3 = new ArrayList<>();
        row3.add(Map.of("text", "⏰ 修改晚报推送时间 (管理员)", "callback_data", "action_admin_time_menu"));
        keyboard.add(row3);

        sendOrEditMessage(botToken, chatId, editMessageId, text, keyboard);
    }

    private void sendAdminFlowSummary(String botToken, String chatId, boolean isEdit, int messageId) {
        try {
            List<User> userList = userService.list();
            long totalInFlow = 0;
            long totalOutFlow = 0;
            if (userList != null) {
                for (User u : userList) {
                    if (u.getInFlow() != null) totalInFlow += u.getInFlow();
                    if (u.getOutFlow() != null) totalOutFlow += u.getOutFlow();
                }
            }
            long totalFlow = totalInFlow + totalOutFlow;

            String text = String.format(
                    "📊 <b>Flux-Panel 全平台流量总计 (管理员)</b>\n" +
                    "--------------------\n" +
                    "📈 <b>累计入站流量</b>: <code>%s</code>\n" +
                    "📉 <b>累计出站流量</b>: <code>%s</code>\n" +
                    "🔥 <b>平台总消耗流量</b>: <b>%s</b>\n" +
                    "--------------------\n" +
                    "<i>统计时间: %s</i>",
                    formatBytes(totalInFlow), formatBytes(totalOutFlow), formatBytes(totalFlow),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );

            List<List<Map<String, String>>> keyboard = new ArrayList<>();
            keyboard.add(List.of(
                    Map.of("text", "🔄 刷新数据", "callback_data", "action_admin_flow"),
                    Map.of("text", "🔙 返回菜单", "callback_data", "menu_admin_main")
            ));

            sendOrEditMessage(botToken, chatId, isEdit ? messageId : 0, text, keyboard);
        } catch (Exception e) {
            log.error("查询平台流量异常: ", e);
        }
    }

    private void sendAdminNodesSummary(String botToken, String chatId, boolean isEdit, int messageId) {
        try {
            List<Node> nodeList = nodeService.list();
            List<Tunnel> tunnelList = tunnelService != null ? tunnelService.list() : null;
            List<Forward> forwardList = forwardService != null ? forwardService.list() : null;

            StringBuilder sb = new StringBuilder();
            sb.append("🌐 <b>Flux-Panel 全景节点与转发状态 (管理员)</b>\n");
            sb.append("--------------------\n");

            // 1. 物理节点
            int onlineCount = 0;
            sb.append("<b>【物理节点】</b>\n");
            if (nodeList != null && !nodeList.isEmpty()) {
                for (Node n : nodeList) {
                    boolean isUp = (n.getStatus() != null && n.getStatus() == 1);
                    if (isUp) onlineCount++;
                    sb.append(isUp ? "🟢 " : "🔴 ")
                      .append("<b>").append(n.getName()).append("</b>")
                      .append(" (").append(n.getIp() != null ? n.getIp() : "未知IP").append(")\n");
                }
                sb.append(String.format("<i>物理节点在线: %d / %d 台</i>\n", onlineCount, nodeList.size()));
            } else {
                sb.append("暂无接入节点\n");
            }

            sb.append("--------------------\n");

            // 2. 隧道
            sb.append("<b>【转发隧道】</b>: ");
            if (tunnelList != null && !tunnelList.isEmpty()) {
                sb.append(tunnelList.size()).append(" 条运行中\n");
            } else {
                sb.append("暂无\n");
            }

            sb.append("--------------------\n");

            // 3. 所有用户的端口转发明细
            sb.append("<b>【全部用户端口转发明细】</b>\n");
            if (forwardList != null && !forwardList.isEmpty()) {
                int fwdRunning = 0;
                for (Forward f : forwardList) {
                    boolean isFwdUp = (f.getStatus() != null && f.getStatus() == 1);
                    if (isFwdUp) fwdRunning++;
                    sb.append(isFwdUp ? "🟢 " : "⚪ ")
                      .append("<b>[").append(f.getUserName() != null ? f.getUserName() : "用户" + f.getUserId()).append("]</b> ")
                      .append(f.getName()).append(" | 端口: <code>").append(f.getInPort()).append("</code>")
                      .append(" -> <code>").append(f.getRemoteAddr() != null ? f.getRemoteAddr() : "未设目标").append("</code>\n");
                }
                sb.append(String.format("<i>运行状态: %d / %d 个转发就绪</i>\n", fwdRunning, forwardList.size()));
            } else {
                sb.append("暂无生效的转发规则\n");
            }

            sb.append("--------------------\n");
            sb.append("<i>更新时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))).append("</i>");

            List<List<Map<String, String>>> keyboard = new ArrayList<>();
            keyboard.add(List.of(
                    Map.of("text", "🔄 刷新状态", "callback_data", "action_admin_nodes"),
                    Map.of("text", "🔙 返回菜单", "callback_data", "menu_admin_main")
            ));

            sendOrEditMessage(botToken, chatId, isEdit ? messageId : 0, sb.toString(), keyboard);
        } catch (Exception e) {
            log.error("查询全量节点异常: ", e);
        }
    }

    private void sendAdminUsersSummary(String botToken, String chatId, boolean isEdit, int messageId) {
        try {
            List<User> userList = userService.list();
            int total = (userList != null) ? userList.size() : 0;
            int active = 0;
            if (userList != null) {
                for (User u : userList) {
                    if (u.getStatus() != null && u.getStatus() == 1) active++;
                }
            }

            String text = String.format(
                    "👥 <b>Flux-Panel 用户统计 (管理员)</b>\n" +
                    "--------------------\n" +
                    "平台总注册用户: <b>%d</b> 人\n" +
                    "有效活跃用户数: <b>%d</b> 人\n" +
                    "--------------------\n" +
                    "<i>更新时间: %s</i>",
                    total, active,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );

            List<List<Map<String, String>>> keyboard = new ArrayList<>();
            keyboard.add(List.of(
                    Map.of("text", "🔄 刷新", "callback_data", "action_admin_users"),
                    Map.of("text", "🔙 返回菜单", "callback_data", "menu_admin_main")
            ));

            sendOrEditMessage(botToken, chatId, isEdit ? messageId : 0, text, keyboard);
        } catch (Exception e) {
            log.error("查询用户概况异常: ", e);
        }
    }

    private void sendAdminTimeSettingsMenu(String botToken, String chatId, int messageId) {
        String currentHour = getConfigValue("alarm_report_hour", "20");
        String text = String.format(
                "⏰ <b>修改每日晚报推送时间 (管理员)</b>\n" +
                "--------------------\n" +
                "当前设定推送时间: <b>每天 %s:00</b>\n" +
                "请选择你需要调整的新推送整点：",
                currentHour
        );

        List<List<Map<String, String>>> keyboard = new ArrayList<>();
        keyboard.add(List.of(
                Map.of("text", "08:00 (早报)", "callback_data", "set_hour_8"),
                Map.of("text", "12:00 (午间)", "callback_data", "set_hour_12")
        ));
        keyboard.add(List.of(
                Map.of("text", "18:00 (下班)", "callback_data", "set_hour_18"),
                Map.of("text", "20:00 (推荐)", "callback_data", "set_hour_20")
        ));
        keyboard.add(List.of(
                Map.of("text", "22:00 (深夜)", "callback_data", "set_hour_22"),
                Map.of("text", "23:00 (日终)", "callback_data", "set_hour_23")
        ));
        keyboard.add(List.of(
                Map.of("text", "🔙 返回菜单", "callback_data", "menu_admin_main")
        ));

        sendOrEditMessage(botToken, chatId, messageId, text, keyboard);
    }

    // ==================== 普通用户专属界面 (严格数据隔离) ====================

    private void sendUserMainMenu(String botToken, String chatId, User user, int editMessageId) {
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String text = String.format(
                "👤 <b>Flux-Panel 用户中心</b>\n" +
                "--------------------\n" +
                "尊敬的 <b>%s</b> 您好！\n" +
                "系统时间: <code>%s</code>\n" +
                "您可以点击下方按钮查询个人用量或晚报喵：",
                user.getUser(), timeStr
        );

        List<List<Map<String, String>>> keyboard = new ArrayList<>();

        List<Map<String, String>> row1 = new ArrayList<>();
        row1.add(Map.of("text", "📊 我的流量消耗", "callback_data", "action_user_flow"));
        row1.add(Map.of("text", "⚡ 查看我的晚报", "callback_data", "action_user_daily"));
        keyboard.add(row1);

        List<Map<String, String>> row2 = new ArrayList<>();
        row2.add(Map.of("text", "🔓 解除账号绑定", "callback_data", "action_user_unbind"));
        keyboard.add(row2);

        sendOrEditMessage(botToken, chatId, editMessageId, text, keyboard);
    }

    private void sendUserPersonalFlow(String botToken, String chatId, User user, boolean isEdit, int messageId) {
        try {
            if (user == null) {
                user = findUserByChatId(chatId);
            }
            if (user == null) return;

            User fresh = userService.getById(user.getId());
            if (fresh != null) user = fresh;

            long inFlow = user.getInFlow() != null ? user.getInFlow() : 0;
            long outFlow = user.getOutFlow() != null ? user.getOutFlow() : 0;
            long totalUsed = inFlow + outFlow;
            long quotaGb = user.getFlow() != null ? user.getFlow() : 0;

            QueryWrapper<Forward> fqw = new QueryWrapper<>();
            fqw.eq("user_id", user.getId());
            int fwdCount = forwardService != null ? (int) forwardService.count(fqw) : 0;

            String expDateStr = "永久有效";
            if (user.getExpTime() != null && user.getExpTime() > 0) {
                expDateStr = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(user.getExpTime()), java.time.ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            }

            String text = String.format(
                    "📊 <b>%s 的个人流量消耗明细</b>\n" +
                    "--------------------\n" +
                    "📈 <b>已用入站流量</b>: <code>%s</code>\n" +
                    "📉 <b>已用出站流量</b>: <code>%s</code>\n" +
                    "🔥 <b>累计总消耗</b>: <b>%s</b>\n" +
                    "--------------------\n" +
                    "📦 <b>账号总配额</b>: %d GB\n" +
                    "⏳ <b>到期时间</b>: %s\n" +
                    "🔗 <b>生效转发规则</b>: %d 个\n" +
                    "--------------------\n" +
                    "<i>更新时间: %s</i>",
                    user.getUser(),
                    formatBytes(inFlow), formatBytes(outFlow), formatBytes(totalUsed),
                    quotaGb, expDateStr, fwdCount,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );

            List<List<Map<String, String>>> keyboard = new ArrayList<>();
            keyboard.add(List.of(
                    Map.of("text", "🔄 刷新用量", "callback_data", "action_user_flow"),
                    Map.of("text", "🔙 返回菜单", "callback_data", "menu_user_main")
            ));

            sendOrEditMessage(botToken, chatId, isEdit ? messageId : 0, text, keyboard);
        } catch (Exception e) {
            log.error("查询个人流量异常: ", e);
        }
    }

    private void sendUserPersonalDaily(String botToken, String chatId, User user) {
        try {
            if (user == null) {
                user = findUserByChatId(chatId);
            }
            if (user == null) return;

            User fresh = userService.getById(user.getId());
            if (fresh != null) user = fresh;

            long inFlow = user.getInFlow() != null ? user.getInFlow() : 0;
            long outFlow = user.getOutFlow() != null ? user.getOutFlow() : 0;
            long totalUsed = inFlow + outFlow;

            QueryWrapper<Forward> fqw = new QueryWrapper<>();
            fqw.eq("user_id", user.getId());
            List<Forward> myForwards = forwardService != null ? forwardService.list(fqw) : null;
            int fwdCount = (myForwards != null) ? myForwards.size() : 0;

            String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            StringBuilder sb = new StringBuilder();
            sb.append("📋 <b>Flux-Panel 个人运营流量晚报</b>\n");
            sb.append("--------------------\n");
            sb.append("👤 <b>用户账号</b>: <code>").append(user.getUser()).append("</code>\n");
            sb.append("📅 <b>报告时间</b>: ").append(timeStr).append("\n");
            sb.append("--------------------\n");
            sb.append("📈 <b>入站流量</b>: ").append(formatBytes(inFlow)).append("\n");
            sb.append("📉 <b>出站流量</b>: ").append(formatBytes(outFlow)).append("\n");
            sb.append("🔥 <b>累计总消耗</b>: <b>").append(formatBytes(totalUsed)).append("</b>\n");
            sb.append("--------------------\n");
            sb.append("🔗 <b>名下转发规则 (").append(fwdCount).append("个)</b>:\n");

            if (myForwards != null && !myForwards.isEmpty()) {
                for (Forward f : myForwards) {
                    boolean isUp = (f.getStatus() != null && f.getStatus() == 1);
                    sb.append(isUp ? "🟢 " : "⚪ ")
                      .append(f.getName()).append(" (端口: <code>").append(f.getInPort()).append("</code>)\n");
                }
            } else {
                sb.append("暂无端口转发\n");
            }

            sb.append("--------------------\n");
            sb.append("<i>💡 这是您的专属个人流量日报，数据仅您本人可见喵🐾</i>");

            sendTelegramHtml(botToken, chatId, sb.toString());
        } catch (Exception e) {
            log.error("发送个人晚报异常: ", e);
        }
    }

    // ==================== 底层通讯工具 ====================

    private void registerBotMenuCommands(String botToken) {
        try {
            String url = "https://api.telegram.org/bot" + botToken + "/setMyCommands";
            List<Map<String, String>> cmds = new ArrayList<>();
            cmds.add(Map.of("command", "menu", "description", "打开控制台快捷交互菜单"));
            cmds.add(Map.of("command", "myflow", "description", "查看我的个人流量明细"));
            cmds.add(Map.of("command", "myreport", "description", "查看我的专属每日晚报"));
            cmds.add(Map.of("command", "bind", "description", "绑定面板账号 (格式: /bind 用户名 密码)"));
            cmds.add(Map.of("command", "unbind", "description", "解除当前绑定的面板账号"));
            cmds.add(Map.of("command", "flow", "description", "查看平台总流量 (管理员)"));
            cmds.add(Map.of("command", "nodes", "description", "查看所有节点与转发状态 (管理员)"));
            cmds.add(Map.of("command", "users", "description", "查看用户统计数据 (管理员)"));
            cmds.add(Map.of("command", "daily", "description", "立即生成并推送平台晚报 (管理员)"));

            Map<String, Object> body = Map.of("commands", cmds);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(url, request, String.class);
            log.info("Telegram 官方快捷指令菜单注册成功！(带管理员与个人区分)");
        } catch (Exception e) {
            log.warn("注册 Telegram 菜单失败: {}", e.getMessage());
        }
    }

    private void sendOrEditMessage(String botToken, String chatId, int messageId, String text, List<List<Map<String, String>>> keyboard) {
        try {
            String url;
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");
            body.put("reply_markup", Map.of("inline_keyboard", keyboard));

            if (messageId > 0) {
                url = "https://api.telegram.org/bot" + botToken + "/editMessageText";
                body.put("message_id", messageId);
            } else {
                url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(url, entity, String.class);
        } catch (Exception e) {
            if (messageId > 0) {
                sendOrEditMessage(botToken, chatId, 0, text, keyboard);
            }
        }
    }

    private void answerCallbackQuery(String botToken, String callbackQueryId) {
        try {
            String url = "https://api.telegram.org/bot" + botToken + "/answerCallbackQuery";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("callback_query_id", callbackQueryId), headers);
            restTemplate.postForEntity(url, entity, String.class);
        } catch (Exception ignored) {}
    }

    private void sendTelegramHtml(String botToken, String chatId, String text) {
        try {
            String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            Map<String, String> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(url, request, String.class);
        } catch (Exception ignored) {}
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private String getConfigValue(String key, String defaultVal) {
        try {
            var r = viteConfigService.getConfigByName(key);
            if (r != null && r.getData() != null) {
                Object data = r.getData();
                if (data instanceof ViteConfig) {
                    String val = ((ViteConfig) data).getValue();
                    return val != null ? val.trim() : defaultVal;
                }
                return data.toString().trim();
            }
        } catch (Exception ignored) {}
        return defaultVal;
    }
}
