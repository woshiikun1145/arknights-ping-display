package com.arknightsendfieldpingandserverpacketratedisplay;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 管理 Ping 和数据包速率的实时统计。
 * 使用反射获取 ClientConnection 中的 receivedPackets 字段。
 */
public class PacketRateManager {
    private static PacketRateManager instance;

    // 统计周期（0.5秒）
    private static final long UPDATE_INTERVAL_MS = 500;

    // 最近一分钟的样本数 (60秒 / 0.5秒 = 120)
    private static final int MAX_SAMPLES = 120;

    // 数据样本类
    private static class Sample {
        long time;
        int ping;
        double packetsPerSec; // 当前采样点的瞬时速率
    }

    private final Deque<Sample> samples = new ArrayDeque<>(MAX_SAMPLES + 1);
    private long lastUpdateTime = 0;
    private long lastPacketsReceived = 0;
    private Field receivedPacketsField;

    // 累计统计（从加入服务器至今）
    private long totalPingSum = 0;
    private int totalPingCount = 0;
    private int maxPingTotal = 0;
    private int minPingTotal = Integer.MAX_VALUE;

    private double totalPacketsSum = 0;
    private int totalPacketsCount = 0;
    private double maxPacketsTotal = 0;
    private double minPacketsTotal = Double.MAX_VALUE;

    // 当前实时值（用于显示）
    private int currentPing = 0;
    private double currentPacketsPerSec = 0;

    private boolean connected = false;

    private PacketRateManager() {
        try {
            // 反射获取 receivedPackets 字段
            Class<?> clazz = Class.forName("net.minecraft.network.ClientConnection");
            receivedPacketsField = clazz.getDeclaredField("receivedPackets");
            receivedPacketsField.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void initialize() {
        instance = new PacketRateManager();
        instance.registerEvents();
    }

    public static PacketRateManager getInstance() {
        return instance;
    }

    private void registerEvents() {
        // 加入服务器时重置统计
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            reset();
            connected = true;
        });

        // 断开连接时停止统计
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            connected = false;
            samples.clear();
        });

        // 每 tick 检查更新（每秒 20 tick，每 0.5 秒更新一次）
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!connected || client.world == null || client.player == null) return;

            long now = System.currentTimeMillis();
            if (now - lastUpdateTime >= UPDATE_INTERVAL_MS) {
                updateStats(client, now);
                lastUpdateTime = now;
            }
        });
    }

    private void updateStats(MinecraftClient client, long now) {
        // 获取当前 Ping
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        currentPing = (entry != null) ? entry.getLatency() : 0;

        // 获取当前接收包总数
        long packetsNow = getReceivedPackets(client);
        if (packetsNow < 0) return; // 获取失败

        // 计算包速率 (包数 / 秒)
        double packetsPerSec = 0;
        if (lastPacketsReceived != 0) {
            long timeDiff = now - lastUpdateTime; // 实际上等于 UPDATE_INTERVAL_MS
            packetsPerSec = (packetsNow - lastPacketsReceived) * 1000.0 / timeDiff;
        }
        lastPacketsReceived = packetsNow;
        currentPacketsPerSec = packetsPerSec;

        // 创建样本
        Sample sample = new Sample();
        sample.time = now;
        sample.ping = currentPing;
        sample.packetsPerSec = packetsPerSec;

        // 添加到队列，并移除旧样本
        samples.add(sample);
        while (samples.size() > MAX_SAMPLES) {
            Sample removed = samples.poll();
            // 从一分钟统计中移除被淘汰样本的影响（可选，但为了准确，我们重新计算一分钟统计）
            // 为了简单，我们每次直接从 samples 重新计算一分钟统计数据，因为 MAX_SAMPLES=120 计算量很小
        }

        // 更新累计统计
        totalPingSum += currentPing;
        totalPingCount++;
        maxPingTotal = Math.max(maxPingTotal, currentPing);
        if (currentPing > 0) minPingTotal = Math.min(minPingTotal, currentPing);

        totalPacketsSum += packetsPerSec;
        totalPacketsCount++;
        maxPacketsTotal = Math.max(maxPacketsTotal, packetsPerSec);
        minPacketsTotal = Math.min(minPacketsTotal, packetsPerSec);
    }

    private long getReceivedPackets(MinecraftClient client) {
        if (client.getNetworkHandler() == null) return -1;
        try {
            Object connection = client.getNetworkHandler().getConnection();
            return receivedPacketsField.getLong(connection);
        } catch (Exception e) {
            return -1;
        }
    }

    private void reset() {
        samples.clear();
        lastPacketsReceived = 0;
        lastUpdateTime = 0;
        totalPingSum = 0;
        totalPingCount = 0;
        maxPingTotal = 0;
        minPingTotal = Integer.MAX_VALUE;
        totalPacketsSum = 0;
        totalPacketsCount = 0;
        maxPacketsTotal = 0;
        minPacketsTotal = Double.MAX_VALUE;
        currentPing = 0;
        currentPacketsPerSec = 0;
    }

    // --- 公共获取方法 ---
    public int getCurrentPing() { return currentPing; }
    public double getCurrentPacketsPerSec() { return currentPacketsPerSec; }

    // 最近一分钟统计（从 samples 中计算）
    public int getMinPingMinute() {
        return samples.stream().mapToInt(s -> s.ping).min().orElse(0);
    }
    public int getMaxPingMinute() {
        return samples.stream().mapToInt(s -> s.ping).max().orElse(0);
    }
    public double getAvgPingMinute() {
        return samples.stream().mapToInt(s -> s.ping).average().orElse(0);
    }
    public double getMinPacketsMinute() {
        return samples.stream().mapToDouble(s -> s.packetsPerSec).min().orElse(0);
    }
    public double getMaxPacketsMinute() {
        return samples.stream().mapToDouble(s -> s.packetsPerSec).max().orElse(0);
    }
    public double getAvgPacketsMinute() {
        return samples.stream().mapToDouble(s -> s.packetsPerSec).average().orElse(0);
    }

    // 累计统计
    public int getMinPingTotal() { return minPingTotal == Integer.MAX_VALUE ? 0 : minPingTotal; }
    public int getMaxPingTotal() { return maxPingTotal; }
    public double getAvgPingTotal() {
        return totalPingCount == 0 ? 0 : (double) totalPingSum / totalPingCount;
    }
    public double getMinPacketsTotal() { return minPacketsTotal == Double.MAX_VALUE ? 0 : minPacketsTotal; }
    public double getMaxPacketsTotal() { return maxPacketsTotal; }
    public double getAvgPacketsTotal() {
        return totalPacketsCount == 0 ? 0 : totalPacketsSum / totalPacketsCount;
    }
}