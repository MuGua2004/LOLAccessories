package com.example.lolaccessories.client;

import com.example.lolaccessories.LOLAccessories;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 本模组所有按键绑定（仅客户端）。
 *
 * <p>主动技能约定：每个主动技能对应一个按键，且默认不绑定——玩家在按键设置的
 * 「LOL 饰品」分类中自行分配（按键名称即明确标注是哪个技能，如「探索者的护臂：时间停止」）。
 * 触发校验 / 冷却一律在服务端完成，客户端按键只是“转发意图”。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModKeyBindings {

    /** 探索者的护臂——时间停止（默认不绑定）。 */
    public static final KeyMapping TIME_STOP = new KeyMapping(
            "key." + LOLAccessories.MOD_ID + ".time_stop",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories." + LOLAccessories.MOD_ID);

    /** 水银饰带——净化（默认不绑定）。 */
    public static final KeyMapping QUICKSILVER = new KeyMapping(
            "key." + LOLAccessories.MOD_ID + ".quicksilver",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories." + LOLAccessories.MOD_ID);

    /** 提亚马特——新月（默认不绑定）。 */
    public static final KeyMapping CRESCENT = new KeyMapping(
            "key." + LOLAccessories.MOD_ID + ".crescent",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories." + LOLAccessories.MOD_ID);

    /** 舒瑞娅的战歌——鼓舞（默认不绑定）。 */
    public static final KeyMapping INSPIRING_SPEECH = new KeyMapping(
            "key." + LOLAccessories.MOD_ID + ".inspiring_speech",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories." + LOLAccessories.MOD_ID);

    /** 实现器——法力成真（默认不绑定）。 */
    public static final KeyMapping REALIZE = new KeyMapping(
            "key." + LOLAccessories.MOD_ID + ".realize",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories." + LOLAccessories.MOD_ID);

    /** 命运十面骰——嘲弄命运（默认不绑定）。 */
    public static final KeyMapping MOCK_FATE = new KeyMapping(
            "key." + LOLAccessories.MOD_ID + ".mock_fate",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories." + LOLAccessories.MOD_ID);

    /** 骑士之誓——誓约（默认不绑定）。 */
    public static final KeyMapping PLEDGE = new KeyMapping(
            "key." + LOLAccessories.MOD_ID + ".pledge",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories." + LOLAccessories.MOD_ID);

    /** 救赎——降临（默认不绑定）。 */
    public static final KeyMapping REDEMPTION = new KeyMapping(
            "key." + LOLAccessories.MOD_ID + ".intervention",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories." + LOLAccessories.MOD_ID);

    /** 水银弯刀——水银（默认不绑定）。 */
    public static final KeyMapping MERCURIAL = new KeyMapping(
            "key." + LOLAccessories.MOD_ID + ".mercurial",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories." + LOLAccessories.MOD_ID);

    /** 幽梦之灵——鬼步（默认不绑定）。 */
    public static final KeyMapping YOUMUUS = new KeyMapping(
            "key." + LOLAccessories.MOD_ID + ".wraith_step",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories." + LOLAccessories.MOD_ID);

    /** 海克斯科技枪刃——闪电弹球（默认不绑定）。 */
    public static final KeyMapping GUNBLADE = new KeyMapping(
            "key." + LOLAccessories.MOD_ID + ".gunblade",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories." + LOLAccessories.MOD_ID);

    /** 海克斯科技火箭腰带——魔法弹突进（默认不绑定）。 */
    public static final KeyMapping ROCKETBELT = new KeyMapping(
            "key." + LOLAccessories.MOD_ID + ".rocketbelt",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories." + LOLAccessories.MOD_ID);

    /** 兰顿之兆——减速光环（默认不绑定）。 */
    public static final KeyMapping RANDUINS = new KeyMapping(
            "key." + LOLAccessories.MOD_ID + ".randuins_active",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories." + LOLAccessories.MOD_ID);

    /** 翡翠城——再见桃花源（默认不绑定）。 */
    public static final KeyMapping FAREWELL_PARADISE = new KeyMapping(
            "key." + LOLAccessories.MOD_ID + ".farewell_paradise",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories." + LOLAccessories.MOD_ID);

    /** 灵恸——此恨无绝（默认不绑定）。 */
    public static final KeyMapping ENDLESS_GRIEF = new KeyMapping(
            "key." + LOLAccessories.MOD_ID + ".endless_grief",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories." + LOLAccessories.MOD_ID);

    /** 挺进破坏者——破阵冲击波（默认不绑定）。 */
    public static final KeyMapping STRIDEBREAKER = new KeyMapping(
            "key." + LOLAccessories.MOD_ID + ".shockwave",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories." + LOLAccessories.MOD_ID);

    private ModKeyBindings() {
    }

    @SubscribeEvent
    public static void onRegisterKeyBindings(RegisterKeyMappingsEvent event) {
        event.register(TIME_STOP);
        event.register(QUICKSILVER);
        event.register(CRESCENT);
        event.register(INSPIRING_SPEECH);
        event.register(REALIZE);
        event.register(MOCK_FATE);
        event.register(PLEDGE);
        event.register(REDEMPTION);
        event.register(MERCURIAL);
        event.register(YOUMUUS);
        event.register(GUNBLADE);
        event.register(ROCKETBELT);
        event.register(RANDUINS);
        event.register(FAREWELL_PARADISE);
        event.register(STRIDEBREAKER);
        event.register(ENDLESS_GRIEF);
    }

    /** 若当前未在聊天/输入框中，且“时间停止”键刚被按下，则返回 true。 */
    public static boolean isTimeStopPressed() {
        return TIME_STOP.consumeClick();
    }

    /** 若“净化”键刚被按下，则返回 true。 */
    public static boolean isQuicksilverPressed() {
        return QUICKSILVER.consumeClick();
    }

    /** 若“新月”键刚被按下，则返回 true。 */
    public static boolean isCrescentPressed() {
        return CRESCENT.consumeClick();
    }

    /** 若“鼓舞”键刚被按下，则返回 true。 */
    public static boolean isInspiringSpeechPressed() {
        return INSPIRING_SPEECH.consumeClick();
    }

    /** 若“法力成真”键刚被按下，则返回 true。 */
    public static boolean isRealizePressed() {
        return REALIZE.consumeClick();
    }

    /** 若“嘲弄命运”键刚被按下，则返回 true。 */
    public static boolean isMockFatePressed() {
        return MOCK_FATE.consumeClick();
    }

    /** 若“誓约”键刚被按下，则返回 true。 */
    public static boolean isPledgePressed() {
        return PLEDGE.consumeClick();
    }

    /** 若“降临”键刚被按下，则返回 true。 */
    public static boolean isRedemptionPressed() {
        return REDEMPTION.consumeClick();
    }

    /** 若“水银”键刚被按下，则返回 true。 */
    public static boolean isMercurialPressed() {
        return MERCURIAL.consumeClick();
    }

    /** 若“鬼步”键刚被按下，则返回 true。 */
    public static boolean isYoumuusPressed() {
        return YOUMUUS.consumeClick();
    }

    /** 若“闪电弹球”键刚被按下，则返回 true。 */
    public static boolean isGunbladePressed() {
        return GUNBLADE.consumeClick();
    }

    /** 若“魔法弹突进”键刚被按下，则返回 true。 */
    public static boolean isRocketbeltPressed() {
        return ROCKETBELT.consumeClick();
    }

    /** 若“减速光环”键刚被按下，则返回 true。 */
    public static boolean isRanduinsPressed() {
        return RANDUINS.consumeClick();
    }

    /** 若“再见桃花源”键刚被按下，则返回 true。 */
    public static boolean isFarewellParadisePressed() {
        return FAREWELL_PARADISE.consumeClick();
    }

    /** 灵恸——此恨无绝：本次客户端 tick 是否刚被按下。 */
    public static boolean isEndlessGriefPressed() {
        return ENDLESS_GRIEF.consumeClick();
    }

    /** 若“破阵冲击波”键刚被按下，则返回 true。 */
    public static boolean isStridebreakerPressed() {
        return STRIDEBREAKER.consumeClick();
    }
}
