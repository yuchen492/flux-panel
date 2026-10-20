package com.admin.common.task;

import com.admin.common.utils.AlarmNotifyUtil;
import com.admin.entity.Node;
import com.admin.entity.User;
import com.admin.service.NodeService;
import com.admin.service.UserService;
import com.admin.service.ViteConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.Resource;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 管理员与系统每日流量报告定时任务
 * 支持自定义每日几点钟自动推送 Telegram 汇总，包含总流量使用情况、用户总消耗以及节点状态
 */
@Slf4j
@Configuration
@EnableScheduling
public class DailyReportTask {

    @Resource
    private UserService userService;

    @Resource
    private NodeService nodeService;

    @Resource
    private ViteConfigService viteConfigService;

    @Resource
    private AlarmNotifyUtil alarmNotifyUtil;

    private static String lastSentDate = "";

    // 每 10 分钟检查一次是否到达设定的发送小时
    @Scheduled(cron = "0 */10 * * * ?")
    public void checkAndSendDailyReport() {
        try {
            String tgEnabled = getConfigValue("alarm_tg_enabled", "false");
            if (!"true".equalsIgnoreCase(tgEnabled)) {
                return;
            }

            String dailyReportEnabled = getConfigValue("alarm_daily_report_enabled", "true");
            if (!"true".equalsIgnoreCase(dailyReportEnabled)) {
                return;
            }

            // 获取配置的小时（默认 20 点）
            int targetHour = 20;
            try {
                targetHour = Integer.parseInt(getConfigValue("alarm_report_hour", "20"));
            } catch (Exception ignored) {}

            LocalTime now = LocalTime.now();
            String today = java.time.LocalDate.now().toString();

            if (now.getHour() == targetHour && !today.equals(lastSentDate)) {
                lastSentDate = today;
                sendDailySummaryReport();
            }
        } catch (Exception e) {
            log.error("检查每日定时报告任务异常: ", e);
        }
    }

    public void sendDailySummaryReport() {
        try {
            List<User> userList = userService.list();
            List<Node> nodeList = nodeService.list();

            long totalInFlow = 0;
            long totalOutFlow = 0;
            int totalUsers = (userList != null) ? userList.size() : 0;
            int activeUsers = 0;

            if (userList != null) {
                for (User u : userList) {
                    if (u.getStatus() != null && u.getStatus() == 1) {
                        activeUsers++;
                    }
                    if (u.getInFlow() != null) totalInFlow += u.getInFlow();
                    if (u.getOutFlow() != null) totalOutFlow += u.getOutFlow();
                }
            }

            long totalFlow = totalInFlow + totalOutFlow;
            int totalNodes = (nodeList != null) ? nodeList.size() : 0;

            String timeStr = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            String title = "📊 Flux-Panel 每日运营与流量晚报";
            String content = String.format(
                    "<b>📅 报告时间</b>: %s\n" +
                    "<b>🌐 节点概况</b>: 共 %d 个转发节点已就绪\n" +
                    "<b>👥 用户统计</b>: 有效用户 %d / 总用户 %d\n" +
                    "--------------------\n" +
                    "<b>📈 累计入站流量</b>: %s\n" +
                    "<b>📉 累计出站流量</b>: %s\n" +
                    "<b>🔥 平台总消耗流量</b>: <b>%s</b>\n" +
                    "--------------------\n" +
                    "<i>💡 每日报告已按设定准时送达。系统运行平稳喵~</i>",
                    timeStr, totalNodes, activeUsers, totalUsers,
                    formatBytes(totalInFlow), formatBytes(totalOutFlow), formatBytes(totalFlow)
            );

            log.info("触发每日流量报告推送至 Telegram: 总流量 {}", formatBytes(totalFlow));
            alarmNotifyUtil.sendAlarm(title, content);

        } catch (Exception e) {
            log.error("发送每日流量晚报异常: ", e);
        }
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
