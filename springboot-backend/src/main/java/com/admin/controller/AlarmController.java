package com.admin.controller;

import com.admin.common.annotation.RequireRole;
import com.admin.common.aop.LogAnnotation;
import com.admin.common.lang.R;
import com.admin.common.task.DailyReportTask;
import com.admin.common.utils.AlarmNotifyUtil;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 告警通知控制器
 * 提供 Telegram 连接测试、即时发送测试报告以及保存配置
 */
@RestController
@CrossOrigin
@RequestMapping("/api/v1/alarm")
public class AlarmController extends BaseController {

    @Resource
    private AlarmNotifyUtil alarmNotifyUtil;

    @Resource
    private DailyReportTask dailyReportTask;

    /**
     * 测试 Telegram Bot 连通性
     */
    @LogAnnotation
    @RequireRole
    @PostMapping("/test-telegram")
    public R testTelegram(@RequestBody Map<String, String> params) {
        String token = params.get("token");
        String chatId = params.get("chatId");

        if (token == null || token.trim().isEmpty() || chatId == null || chatId.trim().isEmpty()) {
            return R.err("Token 和 Chat ID 不能为空");
        }

        try {
            String testTitle = "🔔 Flux-Panel 连通性测试成功";
            String testContent = "这是一条来自 <b>Flux-Panel</b> 的 Telegram 通知测试消息！\n当前机器人与管理员已成功绑定，后续告警与每日流量报告将准时送达喵🐾";
            boolean ok = alarmNotifyUtil.sendDirectTelegram(token.trim(), chatId.trim(), testTitle, testContent);
            return ok ? R.ok("测试消息发送成功，请查看您的 Telegram！") : R.err("测试消息发送失败，请检查 Token 或 Chat ID 是否正确，并确认已在私聊中给机器人发送过 /start");
        } catch (Exception e) {
            return R.err("测试异常: " + e.getMessage());
        }
    }

    /**
     * 立即发送一次每日流量晚报汇总（供管理员预览与调试）
     */
    @LogAnnotation
    @RequireRole
    @PostMapping("/send-daily-now")
    public R sendDailyNow() {
        try {
            dailyReportTask.sendDailySummaryReport();
            return R.ok("每日报告已即时触发发送，请查看 Telegram 对话！");
        } catch (Exception e) {
            return R.err("发送失败: " + e.getMessage());
        }
    }
}
