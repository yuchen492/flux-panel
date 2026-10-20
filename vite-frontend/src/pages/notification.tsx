import { useState, useEffect } from "react";
import { Card, CardBody, CardHeader } from "@heroui/card";
import { Button } from "@heroui/button";
import { Input } from "@heroui/input";
import { Switch } from "@heroui/switch";
import { Select, SelectItem } from "@heroui/select";
import { Checkbox, CheckboxGroup } from "@heroui/checkbox";
import { Chip } from "@heroui/chip";
import { Divider } from "@heroui/divider";
import toast from "react-hot-toast";

export default function NotificationPage() {
  const [loading, setLoading] = useState(false);
  const [testing, setTesting] = useState(false);
  const [sendingDaily, setSendingDaily] = useState(false);

  // 表单状态
  const [tgEnabled, setTgEnabled] = useState(false);
  const [tgToken, setTgToken] = useState("");
  const [tgChatId, setTgChatId] = useState("");
  
  // 每日简报设置
  const [dailyReportEnabled, setDailyReportEnabled] = useState(true);
  const [reportHour, setReportHour] = useState("20");
  const [reportItems, setReportItems] = useState<string[]>([
    "today_flow",
    "total_flow",
    "nodes_status",
    "user_rank"
  ]);

  // 节点监控设置
  const [nodeAlarmEnabled, setNodeAlarmEnabled] = useState(true);
  const [nodeRecoverNotify, setNodeRecoverNotify] = useState(true);
  const [dndEnabled, setDndEnabled] = useState(false); // 免打扰

  // 加载已有配置
  useEffect(() => {
    loadConfig();
  }, []);

  const loadConfig = async () => {
    try {
      const token = localStorage.getItem("token");
      const res = await fetch("/api/v1/config/list", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: token ? "Bearer " + token : ""
        }
      });
      const data = await res.json();
      if (data.code === 0 && data.data) {
        const cfg = data.data;
        setTgEnabled(cfg.alarm_tg_enabled === "true");
        setTgToken(cfg.alarm_tg_token || "");
        setTgChatId(cfg.alarm_tg_chat_id || "");
        setDailyReportEnabled(cfg.alarm_daily_report_enabled !== "false");
        setReportHour(cfg.alarm_report_hour || "20");
        setNodeAlarmEnabled(cfg.alarm_node_enabled !== "false");
        setNodeRecoverNotify(cfg.alarm_recover_enabled !== "false");
        if (cfg.alarm_report_items) {
          try {
            setReportItems(JSON.parse(cfg.alarm_report_items));
          } catch (e) {}
        }
      }
    } catch (e) {
      console.error(e);
    }
  };

  // 测试 Telegram 连通性
  const handleTest = async () => {
    if (!tgToken.trim() || !tgChatId.trim()) {
      toast.error("请先填写 Bot Token 和 Chat ID 喵！");
      return;
    }
    setTesting(true);
    try {
      const token = localStorage.getItem("token");
      const res = await fetch("/api/v1/alarm/test-telegram", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: token ? "Bearer " + token : ""
        },
        body: JSON.stringify({ token: tgToken, chatId: tgChatId })
      });
      const data = await res.json();
      if (data.code === 0) {
        toast.success(data.msg || "测试消息发送成功！");
      } else {
        toast.error(data.msg || "发送失败，请核对凭据");
      }
    } catch (e: any) {
      toast.error("测试异常: " + e.message);
    } finally {
      setTesting(false);
    }
  };

  // 立即触发一次每日报告
  const handleSendDailyNow = async () => {
    setSendingDaily(true);
    try {
      const token = localStorage.getItem("token");
      const res = await fetch("/api/v1/alarm/send-daily-now", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: token ? "Bearer " + token : ""
        }
      });
      const data = await res.json();
      if (data.code === 0) {
        toast.success("已即时推送每日晚报到您的 Telegram！");
      } else {
        toast.error(data.msg || "推送失败");
      }
    } catch (e: any) {
      toast.error("异常: " + e.message);
    } finally {
      setSendingDaily(false);
    }
  };

  // 保存设置
  const handleSave = async () => {
    setLoading(true);
    try {
      const token = localStorage.getItem("token");
      const payload = {
        alarm_tg_enabled: tgEnabled ? "true" : "false",
        alarm_tg_token: tgToken.trim(),
        alarm_tg_chat_id: tgChatId.trim(),
        alarm_daily_report_enabled: dailyReportEnabled ? "true" : "false",
        alarm_report_hour: reportHour,
        alarm_report_items: JSON.stringify(reportItems),
        alarm_node_enabled: nodeAlarmEnabled ? "true" : "false",
        alarm_recover_enabled: nodeRecoverNotify ? "true" : "false"
      };

      const res = await fetch("/api/v1/config/update", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: token ? "Bearer " + token : ""
        },
        body: JSON.stringify(payload)
      });
      const data = await res.json();
      if (data.code === 0) {
        toast.success("通知告警设置保存成功喵！");
      } else {
        toast.error(data.msg || "保存失败");
      }
    } catch (e: any) {
      toast.error("保存失败: " + e.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="p-4 lg:p-6 max-w-7xl mx-auto space-y-6">
      {/* 顶部标题栏 */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <span>🔔</span> 运维通知与告警中心
          </h1>
          <p className="text-sm text-default-500 mt-1">
            配置 Telegram 机器人的每日运营流量汇总、节点异常上下线即时通知
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Button
            color="primary"
            variant="shadow"
            isLoading={loading}
            onPress={handleSave}
            className="font-medium"
          >
            保存全部设置
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* 左侧主要配置区域 (7列) */}
        <div className="lg:col-span-7 space-y-6">
          {/* 卡片 1: Telegram 机器人连接设置 */}
          <Card className="border border-default-200 dark:border-default-100 shadow-sm">
            <CardHeader className="flex justify-between items-center px-5 pt-5 pb-3">
              <div className="flex items-center gap-3">
                <div className="p-2 rounded-lg bg-blue-500/10 text-blue-500 font-bold">
                  TG
                </div>
                <div>
                  <h3 className="text-base font-semibold text-foreground">
                    Telegram 机器人连接配置
                  </h3>
                  <p className="text-xs text-default-400">
                    绑定管理员 Telegram 机器人以接收推送
                  </p>
                </div>
              </div>
              <Switch
                isSelected={tgEnabled}
                onValueChange={setTgEnabled}
                color="primary"
                size="sm"
              />
            </CardHeader>
            <Divider />
            <CardBody className="p-5 space-y-4">
              <Input
                label="Telegram Bot Token"
                placeholder="例如: 123456789:AAFxxx_your_bot_token_here"
                value={tgToken}
                onValueChange={setTgToken}
                variant="bordered"
                size="sm"
                isDisabled={!tgEnabled}
                description="从 @BotFather 获取机器人 Token"
              />

              <Input
                label="管理员 Telegram Chat ID"
                placeholder="例如: 7876790495 或群组ID -100xxxxxxxx"
                value={tgChatId}
                onValueChange={setTgChatId}
                variant="bordered"
                size="sm"
                isDisabled={!tgEnabled}
                description="从 @userinfobot 或 @RawDataBot 获取你的个人数字 ID"
              />

              <div className="pt-2 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="relative flex h-2.5 w-2.5">
                    <span
                      className={`animate-ping absolute inline-flex h-full w-full rounded-full opacity-75 ${
                        tgEnabled && tgToken ? "bg-emerald-400" : "bg-zinc-400"
                      }`}
                    ></span>
                    <span
                      className={`relative inline-flex rounded-full h-2.5 w-2.5 ${
                        tgEnabled && tgToken ? "bg-emerald-500" : "bg-zinc-500"
                      }`}
                    ></span>
                  </span>
                  <span className="text-xs text-default-400">
                    {tgEnabled && tgToken ? "已启用推送链路" : "未启用 / 未配置"}
                  </span>
                </div>
                <Button
                  size="sm"
                  color="primary"
                  variant="flat"
                  isDisabled={!tgEnabled}
                  isLoading={testing}
                  onPress={handleTest}
                >
                  🚀 发送连通性测试
                </Button>
              </div>
            </CardBody>
          </Card>

          {/* 卡片 2: 定时流量汇总报告设置 */}
          <Card className="border border-default-200 dark:border-default-100 shadow-sm">
            <CardHeader className="flex justify-between items-center px-5 pt-5 pb-3">
              <div className="flex items-center gap-3">
                <div className="p-2 rounded-lg bg-emerald-500/10 text-emerald-500 font-bold">
                  📊
                </div>
                <div>
                  <h3 className="text-base font-semibold text-foreground">
                    定时流量汇总晚报
                  </h3>
                  <p className="text-xs text-default-400">
                    每日自选时间自动汇总入站、出站及平台总流量
                  </p>
                </div>
              </div>
              <Switch
                isSelected={dailyReportEnabled}
                onValueChange={setDailyReportEnabled}
                color="success"
                size="sm"
                isDisabled={!tgEnabled}
              />
            </CardHeader>
            <Divider />
            <CardBody className="p-5 space-y-4">
              <div>
                <label className="text-xs font-medium text-default-500 mb-1.5 block">
                  每日推送时间点（自选整点）
                </label>
                <Select
                  selectedKeys={[reportHour]}
                  onChange={(e) => setReportHour(e.target.value)}
                  variant="bordered"
                  size="sm"
                  isDisabled={!tgEnabled || !dailyReportEnabled}
                  aria-label="选择每日推送时间"
                >
                  <SelectItem key="8" textValue="每天 08:00 (早报汇总)">
                    ☀️ 每天 08:00 (早报汇总)
                  </SelectItem>
                  <SelectItem key="12" textValue="每天 12:00 (午间盘点)">
                    🥪 每天 12:00 (午间盘点)
                  </SelectItem>
                  <SelectItem key="17" textValue="每天 17:00 (傍晚运营报告)">
                    🌆 每天 17:00 (傍晚运营报告)
                  </SelectItem>
                  <SelectItem key="18" textValue="每天 18:00 (下班时段汇总)">
                    💼 每天 18:00 (下班时段汇总)
                  </SelectItem>
                  <SelectItem key="20" textValue="每天 20:00 (晚间总结)">
                    🌙 每天 20:00 (晚间总结)
                  </SelectItem>
                  <SelectItem key="22" textValue="每天 22:00 (深夜盘点)">
                    🌃 每天 22:00 (深夜盘点)
                  </SelectItem>
                  <SelectItem key="23" textValue="每天 23:00 (日终收盘统计)">
                    🕛 每天 23:00 (日终收盘统计)
                  </SelectItem>
                </Select>
              </div>

              <div>
                <label className="text-xs font-medium text-default-500 mb-2 block">
                  简报内容包含字段
                </label>
                <CheckboxGroup
                  value={reportItems}
                  onValueChange={setReportItems}
                  isDisabled={!tgEnabled || !dailyReportEnabled}
                  orientation="horizontal"
                  className="gap-4"
                >
                  <Checkbox value="today_flow" size="sm">
                    今日消耗流量
                  </Checkbox>
                  <Checkbox value="total_flow" size="sm">
                    历史累计总用量
                  </Checkbox>
                  <Checkbox value="nodes_status" size="sm">
                    在线节点数
                  </Checkbox>
                  <Checkbox value="user_rank" size="sm">
                    活跃用户统计
                  </Checkbox>
                </CheckboxGroup>
              </div>

              <div className="pt-2 flex justify-end">
                <Button
                  size="sm"
                  variant="bordered"
                  isDisabled={!tgEnabled || !dailyReportEnabled}
                  isLoading={sendingDaily}
                  onPress={handleSendDailyNow}
                >
                  ⚡ 立即执行一次推送测试
                </Button>
              </div>
            </CardBody>
          </Card>

          {/* 卡片 3: 节点状态监控与告警 */}
          <Card className="border border-default-200 dark:border-default-100 shadow-sm">
            <CardHeader className="flex justify-between items-center px-5 pt-5 pb-3">
              <div className="flex items-center gap-3">
                <div className="p-2 rounded-lg bg-rose-500/10 text-rose-500 font-bold">
                  🚨
                </div>
                <div>
                  <h3 className="text-base font-semibold text-foreground">
                    节点存活与健康监控告警
                  </h3>
                  <p className="text-xs text-default-400">
                    转发节点掉线超时探活与网络恢复自动推送
                  </p>
                </div>
              </div>
            </CardHeader>
            <Divider />
            <CardBody className="p-5 space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <div className="text-sm font-medium text-foreground">
                    节点离线即时告警
                  </div>
                  <div className="text-xs text-default-400">
                    当探活检测连续失败超时时，立刻推送红色警告
                  </div>
                </div>
                <Switch
                  isSelected={nodeAlarmEnabled}
                  onValueChange={setNodeAlarmEnabled}
                  color="danger"
                  size="sm"
                  isDisabled={!tgEnabled}
                />
              </div>

              <Divider className="my-1" />

              <div className="flex items-center justify-between">
                <div>
                  <div className="text-sm font-medium text-foreground">
                    节点网络恢复上线通知
                  </div>
                  <div className="text-xs text-default-400">
                    节点重新连接就绪后发送恢复通知
                  </div>
                </div>
                <Switch
                  isSelected={nodeRecoverNotify}
                  onValueChange={setNodeRecoverNotify}
                  color="success"
                  size="sm"
                  isDisabled={!tgEnabled}
                />
              </div>
            </CardBody>
          </Card>
        </div>

        {/* 右侧实时效果预览区域 (5列) */}
        <div className="lg:col-span-5 space-y-4">
          <div className="flex items-center justify-between">
            <span className="text-sm font-semibold text-foreground flex items-center gap-2">
              <span>📱</span> Telegram 消息排版实时预览
            </span>
            <Chip size="sm" variant="flat" color="primary">
              所见即所得
            </Chip>
          </div>

          {/* Telegram 聊天气泡仿真容器 */}
          <div className="rounded-2xl p-4 bg-[#0e1621] border border-[#242f3d] text-[#f5f5f5] shadow-lg font-sans">
            <div className="flex items-center gap-2 mb-3 pb-2 border-b border-[#242f3d]/60">
              <div className="w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center font-bold text-xs text-white">
                Flux
              </div>
              <div>
                <div className="text-xs font-bold text-white flex items-center gap-1.5">
                  Flux-Panel Bot <span className="text-[10px] bg-blue-500/20 text-blue-400 px-1 py-0.2 rounded">BOT</span>
                </div>
                <div className="text-[11px] text-zinc-400">刚刚</div>
              </div>
            </div>

            {/* 气泡 1: 每日晚报预览 */}
            <div className="bg-[#182533] p-3.5 rounded-xl border border-[#2b3c4e] mb-3 text-[13px] leading-relaxed shadow-sm">
              <div className="font-bold text-sky-400 text-sm mb-1.5 flex items-center gap-1.5">
                <span>📊</span> Flux-Panel 每日运营与流量晚报
              </div>
              <div className="text-zinc-300 space-y-1">
                <div>
                  <b className="text-zinc-100">📅 报告时间</b>: 2026-10-20 {reportHour.padStart(2, "0")}:00:00
                </div>
                {reportItems.includes("nodes_status") && (
                  <div>
                    <b className="text-zinc-100">🌐 节点概况</b>: 共 2 个转发节点已就绪
                  </div>
                )}
                {reportItems.includes("user_rank") && (
                  <div>
                    <b className="text-zinc-100">👥 用户统计</b>: 有效用户 3 / 总用户 3
                  </div>
                )}
                <div className="my-1.5 border-t border-zinc-700/60 pt-1.5"></div>
                {reportItems.includes("today_flow") && (
                  <div>
                    <b className="text-zinc-100">📈 今日消耗流量</b>: <span className="text-emerald-400 font-mono">14.28 GB</span>
                  </div>
                )}
                {reportItems.includes("total_flow") && (
                  <div>
                    <b className="text-zinc-100">🔥 平台累计总消耗</b>: <span className="text-amber-400 font-mono font-bold">84.23 GB</span>
                  </div>
                )}
                <div className="mt-2 text-[11px] text-zinc-400 italic">
                  💡 每日报告按每天 {reportHour.padStart(2, "0")}:00 准时送达。系统运行平稳喵~
                </div>
              </div>
            </div>

            {/* 气泡 2: 节点掉线告警预览 */}
            {nodeAlarmEnabled && (
              <div className="bg-[#2a1b1e] p-3 rounded-xl border border-red-900/50 text-[12px] leading-relaxed">
                <div className="font-bold text-rose-400 mb-1 flex items-center gap-1">
                  <span>⚠️</span> 节点离线告警: 香港CN2中转
                </div>
                <div className="text-zinc-300">
                  节点地址: 45.192.105.60:6365<br />
                  状态: 探活心跳超时 (丢包率 100%)<br />
                  告警时间: 14:35:12
                </div>
              </div>
            )}
          </div>

          <div className="text-xs text-default-400 px-1">
            * 真实消息将由你的 Telegram Bot 按照上方模板自动格式化发送至绑定 Chat ID。
          </div>
        </div>
      </div>
    </div>
  );
}
