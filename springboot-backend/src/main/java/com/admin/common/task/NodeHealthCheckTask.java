package com.admin.common.task;

import com.admin.common.utils.AlarmNotifyUtil;
import com.admin.entity.Node;
import com.admin.service.NodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.Resource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 节点健康监控与异常自动告警定时任务
 * 定期检查各转发节点的心跳与连通性，离线时即时推送告警
 */
@Slf4j
@Configuration
@EnableScheduling
public class NodeHealthCheckTask {

    @Resource
    private NodeService nodeService;

    @Resource
    private AlarmNotifyUtil alarmNotifyUtil;

    // 记录节点上次状态（避免重复告警刷屏）
    private static final ConcurrentHashMap<Long, Boolean> NODE_STATUS_MAP = new ConcurrentHashMap<>();

    // 每 2 分钟巡检一次节点连通性
    @Scheduled(fixedRate = 120000)
    public void checkNodesHealth() {
        try {
            List<Node> nodeList = nodeService.list();
            if (nodeList == null || nodeList.isEmpty()) {
                return;
            }

            for (Node node : nodeList) {
                Long nodeId = node.getId();
                String ip = node.getServerIp();
                if (ip == null || ip.trim().isEmpty()) {
                    ip = node.getIp();
                }

                if (ip == null || ip.trim().isEmpty()) {
                    continue;
                }

                int checkPort = (node.getPortSta() != null && node.getPortSta() > 0) ? node.getPortSta() : 6365;
                boolean isOnline = pingHost(ip, checkPort, 3000);

                Boolean lastStatus = NODE_STATUS_MAP.get(nodeId);

                // 首次记录状态
                if (lastStatus == null) {
                    NODE_STATUS_MAP.put(nodeId, isOnline);
                    continue;
                }

                // 状态从 在线 -> 离线：触发紧急告警
                if (lastStatus && !isOnline) {
                    NODE_STATUS_MAP.put(nodeId, false);
                    String title = "⚠️ 节点离线告警: " + node.getName();
                    String msg = String.format("节点名称: %s\n节点地址: %s:%d\n状态: 无法连通 / 心跳超时\n告警时间: %s",
                            node.getName(), ip, checkPort, new java.util.Date().toString());
                    log.warn("检测到节点离线: {} ({}:{})", node.getName(), ip, checkPort);
                    alarmNotifyUtil.sendAlarm(title, msg);
                }
                // 状态从 离线 -> 恢复在线：触发恢复通知
                else if (!lastStatus && isOnline) {
                    NODE_STATUS_MAP.put(nodeId, true);
                    String title = "✅ 节点已恢复正常: " + node.getName();
                    String msg = String.format("节点名称: %s\n节点地址: %s:%d\n状态: 重新连接成功\n恢复时间: %s",
                            node.getName(), ip, checkPort, new java.util.Date().toString());
                    log.info("节点恢复在线: {} ({}:{})", node.getName(), ip, checkPort);
                    alarmNotifyUtil.sendAlarm(title, msg);
                }
            }
        } catch (Exception e) {
            log.error("节点巡检健康检查异常: ", e);
        }
    }

    private boolean pingHost(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
