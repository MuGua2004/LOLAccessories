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

    private ModKeyBindings() {
    }

    @SubscribeEvent
    public static void onRegisterKeyBindings(RegisterKeyMappingsEvent event) {
        event.register(TIME_STOP);
        event.register(QUICKSILVER);
        event.register(CRESCENT);
        event.register(INSPIRING_SPEECH);
        event.register(REALIZE);
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
}
