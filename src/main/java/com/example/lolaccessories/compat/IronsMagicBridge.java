package com.example.lolaccessories.compat;

import com.example.lolaccessories.LOLAccessories;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;

/**
 * 铁魔法法力系统的运行时反射桥（与卢登的回声同策略：零编译期依赖）。
 *
 * <p>1.20.1 铁魔法把玩家法力放在
 * {@code io.redspace.ironsspellbooks.api.magic.MagicData}（早期版本同包下的
 * {@code PlayerMagicData}），通过静态方法 {@code getPlayerMagicData(LivingEntity)} 获取，
 * 实例方法 {@code getMana()} / {@code addMana(float)} / {@code getMaxMana()}（后者版本间
 * 不一定存在）读写法力。这里按类名/方法名在运行时探测，两个候选类都试一遍；任何一步失败
 * 都只告警，绝不阻断正常游戏。最大法力 {@link #getMaxMana} 优先反射 {@code getMaxMana()}，
 * 反射不可用时回退到铁魔法注册的原版属性 {@code irons_spellbooks:max_mana} 读取当前值。</p>
 */
public final class IronsMagicBridge {

    private static final String[] MAGIC_DATA_CANDIDATES = {
            "io.redspace.ironsspellbooks.api.magic.MagicData",
            "io.redspace.ironsspellbooks.api.magic.PlayerMagicData"
    };

    private static boolean attempted;
    private static boolean available;
    private static Method getPlayerMagicData;
    private static Method getMana;
    private static Method addMana;
    /** 可选的 {@code getMaxMana()} 反射句柄；探测不到时为 null（回退属性读取）。 */
    private static Method getMaxMana;
    private static boolean maxManaAttrTried;
    private static Attribute maxManaAttr;

    private IronsMagicBridge() {
    }

    /** 是否可用（已装铁魔法且反射探测成功）。 */
    public static boolean isAvailable() {
        resolve();
        return available;
    }

    /** 读取玩家当前法力值；不可用时返回 0。 */
    public static float getMana(LivingEntity entity) {
        if (!isAvailable()) {
            return 0.0F;
        }
        try {
            Object data = getPlayerMagicData.invoke(null, entity);
            if (data == null) {
                return 0.0F;
            }
            Object mana = getMana.invoke(data);
            return mana instanceof Number number ? number.floatValue() : 0.0F;
        } catch (ReflectiveOperationException e) {
            LOLAccessories.LOGGER.warn("[IronsMagicBridge] 读取法力失败：{}", e.getMessage());
            return 0.0F;
        }
    }

    /** 增加法力（铁魔法服务端会自行按最大法力封顶）。 */
    public static boolean addMana(LivingEntity entity, float amount) {
        if (!isAvailable() || amount <= 0.0F) {
            return false;
        }
        try {
            Object data = getPlayerMagicData.invoke(null, entity);
            if (data == null) {
                return false;
            }
            addMana.invoke(data, amount);
            return true;
        } catch (ReflectiveOperationException e) {
            LOLAccessories.LOGGER.warn("[IronsMagicBridge] 增加法力失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 读取玩家最大法力值。
     *
     * <p>优先反射 {@code MagicData#getMaxMana()}；旧版本没有该方法时回退读取铁魔法注册的原版
     * 属性 {@code irons_spellbooks:max_mana} 的当前值。两者都不可用返回 0（调用方自行忽略）。</p>
     */
    public static float getMaxMana(LivingEntity entity) {
        if (!isAvailable()) {
            return 0.0F;
        }
        if (getMaxMana != null) {
            try {
                Object data = getPlayerMagicData.invoke(null, entity);
                if (data != null) {
                    Object value = getMaxMana.invoke(data);
                    if (value instanceof Number number) {
                        return number.floatValue();
                    }
                }
            } catch (ReflectiveOperationException e) {
                LOLAccessories.LOGGER.warn("[IronsMagicBridge] 反射读取最大法力失败：{}", e.getMessage());
            }
        }
        Attribute attribute = maxManaAttribute();
        if (attribute == null || entity.getAttribute(attribute) == null) {
            return 0.0F;
        }
        return (float) entity.getAttributeValue(attribute);
    }

    /** 惰性解析铁魔法 max_mana 属性注册项；未注册时返回 null。 */
    private static Attribute maxManaAttribute() {
        if (!maxManaAttrTried) {
            maxManaAttrTried = true;
            ResourceLocation id = ResourceLocation.tryParse(IronsCompat.MAX_MANA);
            if (id != null) {
                maxManaAttr = BuiltInRegistries.ATTRIBUTE.get(id);
            }
        }
        return maxManaAttr;
    }

    private static void resolve() {
        if (attempted) {
            return;
        }
        attempted = true;
        if (!IronsCompat.isLoaded()) {
            return;
        }
        for (String className : MAGIC_DATA_CANDIDATES) {
            for (Class<?> paramType : new Class<?>[]{LivingEntity.class, Player.class}) {
                try {
                    Class<?> clazz = Class.forName(className);
                    Method getter = clazz.getMethod("getPlayerMagicData", paramType);
                    Method manaGetter = clazz.getMethod("getMana");
                    Method manaAdder = clazz.getMethod("addMana", float.class);
                    getPlayerMagicData = getter;
                    getMana = manaGetter;
                    addMana = manaAdder;
                    // getMaxMana() 在部分版本不存在，探测不到不视为失败
                    Method maxGetter = null;
                    try {
                        maxGetter = clazz.getMethod("getMaxMana");
                    } catch (NoSuchMethodException ignored) {
                        // 旧版本无该方法：用属性读取兜底
                    }
                    getMaxMana = maxGetter;
                    available = true;
                    LOLAccessories.LOGGER.info("[IronsMagicBridge] 已接入铁魔法法力系统：{} @ {}",
                            className, paramType.getSimpleName());
                    return;
                } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                    // 继续尝试下一个候选类 / 参数形态
                }
            }
        }
        LOLAccessories.LOGGER.warn("[IronsMagicBridge] 未能识别已安装铁魔法的法力 API，法力类被动（回复力/法力流）不会生效");
    }
}
