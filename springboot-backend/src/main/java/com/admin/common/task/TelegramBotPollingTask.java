package com.admin.common.task;

import com.admin.entity.Node;
import com.admin.entity.User;
import com.admin.service.NodeService;
import com.admin.service.UserService;
import com.admin.service.ViteConfigService;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Telegram 机器人交互菜单与长轮询监听服务
 * 支持管理员在 Telegram 对话中直接调出交互菜单：
 * - 实时流量统计
 * - 节点健康状态与在线概况
 * - 用户活跃统计
 * - 立即触发晚报推送
 * - 快捷修改每日晚报时间 (08:00/12:00/18:00/20:00/22:00)
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
    private DailyReportTask dailyReportTask;

    @Resource
    private RestTemplate restTemplate;

    private long lastUpdateId = 0;
    private boolean isPolling = false;

    // 每 2 秒轮询一次 Telegram updates
    @Scheduled(fixedDelay = 2000)
    public void pollTelegramUpdates() {
        if (isPolling) return;

        String tgEnabled = getConfigValue("alarm_tg_enabled", "false");
        String botToken = getConfigValue("alarm_tg_token", "");
        String adminChatId = getConfigValue("alarm_tg_chat_id", "");

        if (!"true".equalsIgnoreCase(tgEnabled) || botToken.isEmpty() || adminChatId.isEmpty()) {
            return;
        }

        isPolling = true;
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
            // 忽略一般网络超时
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

            // 严格鉴权：只有配置的管理员 Chat ID 允许交互
            if (!adminChatId.equals(chatId)) {
                sendTelegramHtml(botToken, chatId, "⚠️ <b>权限不足</b>\n您不是该 Flux-Panel 面板绑定的管理员，无权执行操作。");
                return;
            }

            text = text.trim();
            if (text.startsWith("/start") || text.startsWith("/menu") || "菜单".equals(text)) {
                sendMainMenu(botToken, chatId);
            } else if (text.startsWith("/flow") || "流量".equals(text)) {
                sendFlowSummary(botToken, chatId, false, 0);
            } else if (text.startsWith("/nodes") || "节点".equals(text)) {
                sendNodesSummary(botToken, chatId, false, 0);
            } else if (text.startsWith("/daily") || "晚报".equals(text)) {
                dailyReportTask.sendDailySummaryReport();
                sendTelegramHtml(botToken, chatId, "⚡ <b>已即时触发并推送每日流量晚报汇总！</b>");
            } else {
                sendMainMenu(botToken, chatId);
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

            // 回应 callback
            answerCallbackQuery(botToken, callbackId);

            if (!adminChatId.equals(fromId)) {
                return;
            }

            if (data == null) return;

            switch (data) {
                case "menu_main":
                    sendMainMenu(botToken, adminChatId, messageId);
                    break;
                case "action_flow":
                    sendFlowSummary(botToken, adminChatId, true, messageId);
                    break;
                case "action_nodes":
                    sendNodesSummary(botToken, adminChatId, true, messageId);
                    break;
                case "action_users":
                    sendUsersSummary(botToken, adminChatId, true, messageId);
                    break;
                case "action_send_daily":
                    dailyReportTask.sendDailySummaryReport();
                    sendTelegramHtml(botToken, adminChatId, "⚡ <b>已即时推送每日流量晚报汇总！请查收。</b>");
                    break;
                case "action_time_menu":
                    sendTimeSettingsMenu(botToken, adminChatId, messageId);
                    break;
                default:
                    if (data.startsWith("set_hour_")) {
                        String hourStr = data.replace("set_hour_", "");
                        viteConfigService.updateConfig("alarm_report_hour", hourStr);
                        sendTelegramHtml(botToken, adminChatId, "✅ <b>每日晚报推送时间已修改为: " + hourStr + ":00</b>");
                        sendMainMenu(botToken, adminChatId, messageId);
                    }
                    break;
            }
        } catch (Exception e) {
            log.error("处理 TG 按钮回调异常: ", e);
        }
    }

    private void sendMainMenu(String botToken, String chatId) {
        sendMainMenu(botToken, chatId, 0);
    }

    private void sendMainMenu(String botToken, String chatId, int editMessageId) {
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String text = String.format(
                "🐾 <b>Flux-Panel 快捷运维控制台</b>\n" +
                "--------------------\n" +
                "当前服务器时间: <code>%s</code>\n" +
                "请点击下方快捷按钮进行交互操作喵：",
                timeStr
        );

        List<List<Map<String, String>>> keyboard = new ArrayList<>();

        // 第一排
        List<Map<String, String>> row1 = new ArrayList<>();
        row1.add(Map.of("text", "📊 流量与用量", "callback_data", "action_flow"));
        row1.add(Map.of("text", "🌐 节点状态", "callback_data", "action_nodes"));
        keyboard.add(row1);

        // 第二排
        List<Map<String, String>> row2 = new ArrayList<>();
        row2.add(Map.of("text", "👥 用户概况", "callback_data", "action_users"));
        row2.add(Map.of("text", "⚡ 立即触发晚报", "callback_data", "action_send_daily"));
        keyboard.add(row2);

        // 第三排
        List<Map<String, String>> row3 = new ArrayList<>();
        row3.add(Map.of("text", "⏰ 修改晚报推送时间", "callback_data", "action_time_menu"));
        keyboard.add(row3);

        sendOrEditMessage(botToken, chatId, editMessageId, text, keyboard);
    }

    private void sendFlowSummary(String botToken, String chatId, boolean isEdit, int messageId) {
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
                    "📊 <b>Flux-Panel 流量数据统计</b>\n" +
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
                    Map.of("text", "🔄 刷新流量", "callback_data", "action_flow"),
                    Map.of("text", "🔙 返回菜单", "callback_data", "menu_main")
            ));

            sendOrEditMessage(botToken, chatId, isEdit ? messageId : 0, text, keyboard);
        } catch (Exception e) {
            log.error("查询流量概况异常: ", e);
        }
    }

    private void sendNodesSummary(String botToken, String chatId, boolean isEdit, int messageId) {
        try {
            List<Node> nodeList = nodeService.list();
            StringBuilder sb = new StringBuilder();
            sb.append("🌐 <b>转发节点在线状态</b>\n--------------------\n");

            int onlineCount = 0;
            if (nodeList != null && !nodeList.isEmpty()) {
                for (Node n : nodeList) {
                    boolean isUp = (n.getStatus() != null && n.getStatus() == 1);
                    if (isUp) onlineCount++;
                    sb.append(isUp ? "🟢 " : "🔴 ")
                      .append("<b>").append(n.getName()).append("</b>")
                      .append(" (").append(n.getIp() != null ? n.getIp() : "未知地址").append(")\n");
                }
                sb.append("--------------------\n");
                sb.append("统计: 共 ").append(nodeList.size()).append(" 个节点，在线 ").append(onlineCount).append(" 个\n");
            } else {
                sb.append("暂无接入的转发节点\n");
            }

            List<List<Map<String, String>>> keyboard = new ArrayList<>();
            keyboard.add(List.of(
                    Map.of("text", "🔄 刷新节点", "callback_data", "action_nodes"),
                    Map.of("text", "🔙 返回菜单", "callback_data", "menu_main")
            ));

            sendOrEditMessage(botToken, chatId, isEdit ? messageId : 0, sb.toString(), keyboard);
        } catch (Exception e) {
            log.error("查询节点概况异常: ", e);
        }
    }

    private void sendUsersSummary(String botToken, String chatId, boolean isEdit, int messageId) {
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
                    "👥 <b>Flux-Panel 用户统计</b>\n" +
                    "--------------------\n" +
                    "平台总注册用户: <b>%d</b> 人\n" +
                    "有效活跃用户数: <b>%d</b> 人\n" +
                    "系统管理员权限: 正常接入中\n" +
                    "--------------------\n" +
                    "<i>更新时间: %s</i>",
                    total, active,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );

            List<List<Map<String, String>>> keyboard = new ArrayList<>();
            keyboard.add(List.of(
                    Map.of("text", "🔄 刷新", "callback_data", "action_users"),
                    Map.of("text", "🔙 返回菜单", "callback_data", "menu_main")
            ));

            sendOrEditMessage(botToken, chatId, isEdit ? messageId : 0, text, keyboard);
        } catch (Exception e) {
            log.error("查询用户概况异常: ", e);
        }
    }

    private void sendTimeSettingsMenu(String botToken, String chatId, int messageId) {
        String currentHour = getConfigValue("alarm_report_hour", "20");
        String text = String.format(
                "⏰ <b>修改每日流量晚报推送时间</b>\n" +
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
                Map.of("text", "🔙 返回菜单", "callback_data", "menu_main")
        ));

        sendOrEditMessage(botToken, chatId, messageId, text, keyboard);
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
            // 如果编辑失败则 fallback 直接新发一条
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
                return r.getData().toString().trim();
            }
        } catch (Exception ignored) {}
        return defaultVal;
    }
}
