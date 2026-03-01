package com.arknightsendfieldpingandserverpacketratedisplay;

import com.arknightsendfieldpingandserverpacketratedisplay.hud.InfoHud;
import net.fabricmc.api.ClientModInitializer;

public class ArknightsEndfieldPingDisplayClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 初始化 HUD（内部也会启动统计管理器）
        InfoHud.initialize();
    }
}