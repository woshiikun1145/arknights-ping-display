package com.arknightsendfieldpingandserverpacketratedisplay.hud;

import com.arknightsendfieldpingandserverpacketratedisplay.PacketRateManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.text.DecimalFormat;

public class InfoHud implements HudRenderCallback {
    private static InfoHud instance;
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.#");

    // 按键状态检测
    private boolean wasControlDown = false;
    private boolean wasLPressed = false;
    private boolean wasJPressed = false;

    public static void initialize() {
        instance = new InfoHud();
        HudRenderCallback.EVENT.register(instance);
        PacketRateManager.initialize(); // 启动统计管理器
        instance.registerKeyListener();
    }

    private void registerKeyListener() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long window = client.getWindow().getHandle();
            boolean controlDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
            boolean lPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_L) == GLFW.GLFW_PRESS;
            boolean jPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_J) == GLFW.GLFW_PRESS;

            // Ctrl+L 组合键（按下瞬间触发）
            if (controlDown && lPressed && !(wasControlDown && wasLPressed)) {
                copyPingInfo();
            }
            // Ctrl+J 组合键
            if (controlDown && jPressed && !(wasControlDown && wasJPressed)) {
                copyAllInfo();
            }

            wasControlDown = controlDown;
            wasLPressed = lPressed;
            wasJPressed = jPressed;
        });
    }

    @Override
    public void onHudRender(DrawContext drawContext, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return; // 不在服务器中时不显示

        PacketRateManager stats = PacketRateManager.getInstance();

        // 构建带颜色的文本行（每行是一个 Text 对象）
        Text[] lines = {
                buildPingLine(stats),
                buildPingMinuteLine(stats),
                buildPingTotalLine(stats),
                buildRateLine(stats),
                buildRateMinuteLine(stats),
                buildRateTotalLine(stats)
        };

        // 计算位置（右上角）
        int screenWidth = client.getWindow().getScaledWidth();
        int x = screenWidth - 10; // 右对齐基准
        int y = 10;

        MatrixStack matrices = drawContext.getMatrices();
        matrices.push();
        matrices.translate(0, 0, 100); // 确保在最上层

        for (Text line : lines) {
            int textWidth = client.textRenderer.getWidth(line);
            int drawX = x - textWidth; // 右对齐
            drawContext.drawText(client.textRenderer, line, drawX, y, 0xFFFFFF, false);
            y += 10;
        }

        matrices.pop();
    }

    // 构建第一行：Ping: Now [数值]ms
    private Text buildPingLine(PacketRateManager stats) {
        int ping = stats.getCurrentPing();
        int color = getPingColor(ping);
        return Text.literal("Ping: Now ")
                .append(Text.literal(ping + "ms").styled(style -> style.withColor(color)));
    }

    // 构建第二行：In the last minute Max [max]ms Min [min]ms Avg [avg]ms
    private Text buildPingMinuteLine(PacketRateManager stats) {
        int max = stats.getMaxPingMinute();
        int min = stats.getMinPingMinute();
        int avg = (int) stats.getAvgPingMinute();

        MutableText text = Text.literal("         In the last minute Max ");
        text.append(Text.literal(max + "ms").styled(style -> style.withColor(getPingColor(max))));
        text.append(Text.literal(" Min "));
        text.append(Text.literal(min + "ms").styled(style -> style.withColor(getPingColor(min))));
        text.append(Text.literal(" Avg "));
        text.append(Text.literal(avg + "ms").styled(style -> style.withColor(getPingColor(avg))));
        return text;
    }

    // 构建第三行：Since I joined the server Max [max]ms Min [min]ms Avg [avg]ms
    private Text buildPingTotalLine(PacketRateManager stats) {
        int max = stats.getMaxPingTotal();
        int min = stats.getMinPingTotal();
        int avg = (int) stats.getAvgPingTotal();

        MutableText text = Text.literal("         Since I joined the server Max ");
        text.append(Text.literal(max + "ms").styled(style -> style.withColor(getPingColor(max))));
        text.append(Text.literal(" Min "));
        text.append(Text.literal(min + "ms").styled(style -> style.withColor(getPingColor(min))));
        text.append(Text.literal(" Avg "));
        text.append(Text.literal(avg + "ms").styled(style -> style.withColor(getPingColor(avg))));
        return text;
    }

    // 构建第四行：Server packet rate: Now [rate] Packet per sec
    private Text buildRateLine(PacketRateManager stats) {
        double rate = stats.getCurrentPacketsPerSec();
        int color = getRateColor(rate);
        String formatted = DECIMAL_FORMAT.format(rate);
        return Text.literal("Server packet rate: Now ")
                .append(Text.literal(formatted + " Packet per sec").styled(style -> style.withColor(color)));
    }

    // 构建第五行：In the last minute: Max [max] Packet per sec Min [min] Packet per sec Avg [avg] Packet per sec
    private Text buildRateMinuteLine(PacketRateManager stats) {
        double max = stats.getMaxPacketsMinute();
        double min = stats.getMinPacketsMinute();
        double avg = stats.getAvgPacketsMinute();

        MutableText text = Text.literal("         In the last minute: Max ");
        text.append(Text.literal(DECIMAL_FORMAT.format(max) + " Packet per sec").styled(style -> style.withColor(getRateColor(max))));
        text.append(Text.literal(" Min "));
        text.append(Text.literal(DECIMAL_FORMAT.format(min) + " Packet per sec").styled(style -> style.withColor(getRateColor(min))));
        text.append(Text.literal(" Avg "));
        text.append(Text.literal(DECIMAL_FORMAT.format(avg) + " Packet per sec").styled(style -> style.withColor(getRateColor(avg))));
        return text;
    }

    // 构建第六行：Since I joined the server: Max [max] Packet per sec Min [min] Packet per sec Avg [avg] Packet per sec
    private Text buildRateTotalLine(PacketRateManager stats) {
        double max = stats.getMaxPacketsTotal();
        double min = stats.getMinPacketsTotal();
        double avg = stats.getAvgPacketsTotal();

        MutableText text = Text.literal("         Since I joined the server: Max ");
        text.append(Text.literal(DECIMAL_FORMAT.format(max) + " Packet per sec").styled(style -> style.withColor(getRateColor(max))));
        text.append(Text.literal(" Min "));
        text.append(Text.literal(DECIMAL_FORMAT.format(min) + " Packet per sec").styled(style -> style.withColor(getRateColor(min))));
        text.append(Text.literal(" Avg "));
        text.append(Text.literal(DECIMAL_FORMAT.format(avg) + " Packet per sec").styled(style -> style.withColor(getRateColor(avg))));
        return text;
    }

    // 获取 Ping 颜色
    private int getPingColor(int ping) {
        if (ping >= 1000) return 0x8B0000; // 深红色
        if (ping >= 300) return 0xFF0000;  // 红色
        if (ping >= 100) return 0xFFFF00;  // 黄色
        return 0xFFFFFF;                    // 白色
    }

    // 获取包速率颜色
    private int getRateColor(double rate) {
        if (rate <= 1.0) return 0xFF0000;   // 红色
        if (rate <= 5.0) return 0xFFFF00;   // 黄色
        return 0xFFFFFF;                      // 白色
    }

    // 复制 Ping 信息（Ctrl+L）- 纯文本，无颜色
    private void copyPingInfo() {
        PacketRateManager stats = PacketRateManager.getInstance();
        String content = String.format("Ping: Now %dms     In the last minute: Max %dms Min %dms Avg %.1fms",
                stats.getCurrentPing(),
                stats.getMaxPingMinute(),
                stats.getMinPingMinute(),
                stats.getAvgPingMinute());
        MinecraftClient.getInstance().keyboard.setClipboard(content);
    }

    // 复制全部信息（Ctrl+J）- 纯文本，无颜色
    private void copyAllInfo() {
        PacketRateManager stats = PacketRateManager.getInstance();
        String content = String.format(
                "Ping: Now %dms\n" +
                "         In the last minute Max %dms Min %dms Avg %.1fms\n" +
                "         Since I joined the server Max %dms Min %dms Avg %.1fms\n" +
                "Server packet rate: Now %.1f Packet per sec\n" +
                "         In the last minute: Max %.1f Packet per sec Min %.1f Packet per sec Avg %.1f Packet per sec\n" +
                "         Since I joined the server: Max %.1f Packet per sec Min %.1f Packet per sec Avg %.1f Packet per sec",
                stats.getCurrentPing(),
                stats.getMaxPingMinute(), stats.getMinPingMinute(), stats.getAvgPingMinute(),
                stats.getMaxPingTotal(), stats.getMinPingTotal(), stats.getAvgPingTotal(),
                stats.getCurrentPacketsPerSec(),
                stats.getMaxPacketsMinute(), stats.getMinPacketsMinute(), stats.getAvgPacketsMinute(),
                stats.getMaxPacketsTotal(), stats.getMinPacketsTotal(), stats.getAvgPacketsTotal()
        );
        MinecraftClient.getInstance().keyboard.setClipboard(content);
    }
}