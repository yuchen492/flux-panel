package com.admin.common.utils;

import com.admin.service.ViteConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 运维与监控告警通知工具类
 * 支持 Telegram Bot 和自定义 Webhook 告警推送
 */
@Slf4j
@Component
public class AlarmNotifyUtil {

    @Resource
    private ViteConfigService viteConfigService;

    @Resource
    private RestTemplate restTemplate;

    public void sendAlarm(String title, String message) {
        new Thread(() -> {
            try {
                String tgEnabled = getConfigValue("alarm_tg_enabled", "false");
                String tgToken = getConfigValue("alarm_tg_token", "");
                String tgChatId = getConfigValue("alarm_tg_chat_id", "");

                String webhookEnabled = getConfigValue("alarm_webhook_enabled", "false");
                String webhookUrl = getConfigValue("alarm_webhook_url", "");

                String fullText = "<b>" + title + "</b>\n--------------------\n" + message;

                // 1. 发送 Telegram 通知
                if ("true".equalsIgnoreCase(tgEnabled) && !tgToken.isEmpty() && !tgChatId.isEmpty()) {
                    sendDirectTelegram(tgToken, tgChatId, title, message);
                }

                // 2. 发送 Webhook 通知
                if ("true".equalsIgnoreCase(webhookEnabled) && !webhookUrl.isEmpty()) {
                    sendWebhook(webhookUrl, title, message);
                }
            } catch (Exception e) {
                log.error("发送监控告警失败: ", e);
            }
        }).start();
    }

    public boolean sendDirectTelegram(String token, String chatId, String title, String message) {
        try {
            String url = "https://api.telegram.org/bot" + token + "/sendMessage";
            String fullText = "<b>" + title + "</b>\n--------------------\n" + message;

            Map<String, String> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", fullText);
            body.put("parse_mode", "HTML");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> res = restTemplate.postForEntity(url, request, String.class);
            log.info("Telegram 消息发送响应: {}", res.getStatusCode());
            return res.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Telegram 消息推送失败: {}", e.getMessage());
            return false;
        }
    }

    private void sendWebhook(String webhookUrl, String title, String content) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "text");
            body.put("title", title);
            body.put("content", content);
            body.put("text", Map.of("content", title + "\n" + content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            restTemplate.postForEntity(webhookUrl, request, String.class);
            log.info("Webhook 告警发送成功: {}", webhookUrl);
        } catch (Exception e) {
            log.warn("Webhook 告警推送失败: {}", e.getMessage());
        }
    }

    private String getConfigValue(String key, String defaultVal) {
        try {
            var r = viteConfigService.getConfigByName(key);
            if (r != null && r.getData() != null) {
                Object data = r.getData();
                if (data instanceof com.admin.entity.ViteConfig) {
                    String val = ((com.admin.entity.ViteConfig) data).getValue();
                    return val != null ? val.trim() : defaultVal;
                }
                return data.toString().trim();
            }
        } catch (Exception ignored) {}
        return defaultVal;
    }
}
