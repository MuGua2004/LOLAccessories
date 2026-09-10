package com.example.lolaccessories.init;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.effect.ArmorShredEffect;
import com.example.lolaccessories.effect.FanfareEffect;
import com.example.lolaccessories.effect.InspiringSpeechEffect;
import com.example.lolaccessories.effect.RageEffect;
import com.example.lolaccessories.effect.RegenBoostEffect;
import com.example.lolaccessories.effect.SpeedBurstEffect;
import com.example.lolaccessories.effect.WoundsEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 药水效果注册。
 *
 * <p>黑色切割者的两个被动都用「原版药水效果 + 属性修正器」实现：
 * <ul>
 *   <li>cleaver_shred：护甲削减（作用于受害方，按层数线性削减护甲）</li>
 *   <li>cleaver_rush：热烈（作用于攻击方，提供移动速度加成）</li>
 * </ul>
 * 各效果的强度/时长由对应装备的配置文件控制。</p>
 */
public final class ModMobEffects {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, LOLAccessories.MOD_ID);

    /** 切割-护甲削减：每层削减目标 X% 护甲，参数来自 black_cleaver 配置。 */
    public static final RegistryObject<MobEffect> CLEAVER_SHRED =
            MOB_EFFECTS.register("cleaver_shred", () -> new ArmorShredEffect("black_cleaver"));

    /** 热烈-移速加成：为佩戴者提供 X% 移动速度，参数来自 black_cleaver 配置。 */
    public static final RegistryObject<MobEffect> CLEAVER_RUSH =
            MOB_EFFECTS.register("cleaver_rush", () -> new SpeedBurstEffect("black_cleaver"));

    /** 耐久专注：多兰盾佩戴者受伤后窗口期内自然生命恢复翻倍，参数来自 doran_shield 配置。 */
    public static final RegistryObject<MobEffect> PERSEVERANCE =
            MOB_EFFECTS.register("perseverance", () -> new RegenBoostEffect("doran_shield"));

    /** 重伤（死刑宣告 / 湮灭宝珠 / 棘刺背心）：受害者受到治疗降低，参数来自对应装备配置。 */
    public static final RegistryObject<MobEffect> WOUNDS =
            MOB_EFFECTS.register("wounds", WoundsEffect::new);

    /** 狂暴（净蚀）：佩戴者攻击后获得短时移动速度加成，参数来自 phage 配置。 */
    public static final RegistryObject<MobEffect> RAGE =
            MOB_EFFECTS.register("rage", () -> new RageEffect("phage"));

    /** 鼓舞（舒瑞娅的战歌·主动）：佩戴者与附近友方玩家的移速加成，参数来自 shurelyas_battlesong 配置。 */
    public static final RegistryObject<MobEffect> INSPIRING_SPEECH =
            MOB_EFFECTS.register("inspiring_speech",
                    () -> new InspiringSpeechEffect("shurelyas_battlesong", "inspiring_speech"));

    /** 嘹亮旋律（班德尔音管被动触发）：佩戴者与附近友军玩家的移速 + 攻速加成，参数来自 bandlepipes 配置。 */
    public static final RegistryObject<MobEffect> FANFARE =
            MOB_EFFECTS.register("fanfare",
                    () -> new FanfareEffect("bandlepipes", "fanfare"));

    private ModMobEffects() {
    }
}
