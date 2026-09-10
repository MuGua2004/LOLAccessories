package com.example.lolaccessories;

import com.example.lolaccessories.compat.IronsCompat;
import com.example.lolaccessories.config.ClientFxConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.init.ModAttributes;
import com.example.lolaccessories.init.ModCreativeModeTabs;
import com.example.lolaccessories.init.ModEntityTypes;
import com.example.lolaccessories.init.ModItems;
import com.example.lolaccessories.init.ModMobEffects;
import com.example.lolaccessories.init.ModRecipeSerializers;
import com.example.lolaccessories.init.ModSounds;
import com.example.lolaccessories.networking.LOLNetworking;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * LOLAccessories 模组主类。
 *
 * <p>职责：把各注册器挂到 mod 事件总线上，其余逻辑分散在 init / data 等包中。</p>
 */
@Mod(LOLAccessories.MOD_ID)
public class LOLAccessories {

    /** 模组 ID，必须与 gradle.properties 中的 mod_id 一致。 */
    public static final String MOD_ID = "lolaccessories";

    /** 模组日志器，统一通过它输出日志。 */
    public static final Logger LOGGER = LogUtils.getLogger();

    public LOLAccessories(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        // 初始化装备配置目录（config/lolaccessories/gear/），缺失时从模板生成
        GearConfigManager.init();

        // 注册网络消息（必须在模组加载早期完成）
        LOLNetworking.register();

        // 将两个暴击属性注册进玩家默认属性表（ModAttributes#addAttributesToPlayer）
        modEventBus.addListener(ModAttributes::addAttributesToPlayer);
        // 将传说魔法抗性注册进所有敌怪（MONSTER 类别）的默认属性表（适配任何模组怪物）
        modEventBus.addListener(ModAttributes::addMagicResistToHostileMobs);

        // 注册所有 DeferredRegister
        ModAttributes.ATTRIBUTES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeModeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModMobEffects.MOB_EFFECTS.register(modEventBus);
        ModEntityTypes.ENTITY_TYPES.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);

        // 引用铁魔法事件 API 的订阅器统一在此延迟注册（内部先探测铁魔法是否加载再反射加载）
        IronsCompat.registerDynamicSubscribers();

        // 客户端特效质量配置（CLIENT 型，仅客户端读写；服务端加载时不会创建/读取该文件）
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientFxConfig.SPEC);
    }
}
