package com.example.lolaccessories.client;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.event.LolNewActiveSkillEvents;
import com.example.lolaccessories.networking.LOLNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 「净化 / 新月」等新主动技按键的客户端转发（仅客户端）。
 *
 * <p>按键被按下且不在聊天输入框时，把技能 id 发往服务端；是否可用（穿戴/冷却）由服务端判定
 * 并回执。新增主动技能只需在此追加一次按键判定即可。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NewActiveSkillKeyHandler {

    private NewActiveSkillKeyHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        // 聊天/输入框打开时忽略按键，避免打字误触
        if (mc.screen instanceof ChatScreen) {
            return;
        }
        if (ModKeyBindings.isQuicksilverPressed()) {
            LOLNetworking.sendActiveSkillTrigger(LolNewActiveSkillEvents.SKILL_QUICKSILVER);
        }
        if (ModKeyBindings.isCrescentPressed()) {
            LOLNetworking.sendActiveSkillTrigger(LolNewActiveSkillEvents.SKILL_CRESCENT);
        }
        if (ModKeyBindings.isInspiringSpeechPressed()) {
            LOLNetworking.sendActiveSkillTrigger(LolNewActiveSkillEvents.SKILL_INSPIRING_SPEECH);
        }
        if (ModKeyBindings.isRealizePressed()) {
            LOLNetworking.sendActiveSkillTrigger(LolNewActiveSkillEvents.SKILL_REALIZE);
        }
        if (ModKeyBindings.isMockFatePressed()) {
            LOLNetworking.sendActiveSkillTrigger(LolNewActiveSkillEvents.SKILL_MOCK_FATE);
        }
        if (ModKeyBindings.isPledgePressed()) {
            LOLNetworking.sendActiveSkillTrigger(LolNewActiveSkillEvents.SKILL_PLEDGE);
        }
        if (ModKeyBindings.isRedemptionPressed()) {
            LOLNetworking.sendActiveSkillTrigger(LolNewActiveSkillEvents.SKILL_INTERVENTION);
        }
        if (ModKeyBindings.isMercurialPressed()) {
            LOLNetworking.sendActiveSkillTrigger(LolNewActiveSkillEvents.SKILL_MERCURIAL);
        }
        if (ModKeyBindings.isYoumuusPressed()) {
            LOLNetworking.sendActiveSkillTrigger(LolNewActiveSkillEvents.SKILL_WRAITH_STEP);
        }
        if (ModKeyBindings.isGunbladePressed()) {
            LOLNetworking.sendActiveSkillTrigger(LolNewActiveSkillEvents.SKILL_GUNBLADE);
        }
        if (ModKeyBindings.isRocketbeltPressed()) {
            LOLNetworking.sendActiveSkillTrigger(LolNewActiveSkillEvents.SKILL_ROCKETBELT);
        }
        if (ModKeyBindings.isRanduinsPressed()) {
            LOLNetworking.sendActiveSkillTrigger(LolNewActiveSkillEvents.SKILL_RANDUINS);
        }
        if (ModKeyBindings.isFarewellParadisePressed()) {
            LOLNetworking.sendActiveSkillTrigger(LolNewActiveSkillEvents.SKILL_FAREWELL_PARADISE);
        }
        if (ModKeyBindings.isStridebreakerPressed()) {
            LOLNetworking.sendActiveSkillTrigger(LolNewActiveSkillEvents.SKILL_STRIDEBREAKER);
        }
        if (ModKeyBindings.isEndlessGriefPressed()) {
            LOLNetworking.sendActiveSkillTrigger(LolNewActiveSkillEvents.SKILL_ENDLESS_GRIEF);
        }
    }
}
