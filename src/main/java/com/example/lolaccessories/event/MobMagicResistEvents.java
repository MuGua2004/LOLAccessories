package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.init.ModAttributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

/**
 * 原版与铁魔法生物「默认传说魔法抗性」的入世补值。
 *
 * <p>取代旧的「所有 MONSTER 敌怪魔抗 = 护甲一半」自动机制（旧的会以固定 UUID 修正器强加到
 * 任何模组的怪身上，整合包作者难以自行配置）。现在只对 <b>{@code minecraft:} 与
 * {@code irons_spellbooks:} 两个命名空间</b>下的敌对生物生效，且只写属性<b>基础值</b>
 * （不挂修正器）：默认 = 最大生命值的 10%，铁魔法的 Boss（dead_king / fire_boss，本体带
 * Boss 血条）翻倍为 20%。第三方模组生物不注入任何东西，交给整合包作者自己管。</p>
 *
 * <p>入世时若该属性基础值已被其它来源预设为非 0（作者自己加的默认值），一律不覆盖，
 * 以此保留最大可魔改空间。属性槽本身见
 * {@code ModAttributes#addMagicResistToHostileMobs}（同样只补原版 + 铁魔法 MONSTER）。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MobMagicResistEvents {

    /** 铁魔法命名空间。 */
    private static final String IRONS_SPELLBOOKS = "irons_spellbooks";

    /** 默认魔抗 = 最大生命值的 10%。 */
    public static final double DEFAULT_MR_RATIO = 0.10D;

    /** 铁魔法 Boss 默认魔抗 = 最大生命值的 20%（翻倍）。 */
    public static final double BOSS_MR_RATIO = 0.20D;

    /** 铁魔法中自带 Boss 血条（BossbarManager）的实体注册路径；后续新 Boss 在此追加即可。 */
    public static final Set<String> IRON_BOSS_PATHS = Set.of("dead_king", "fire_boss");

    private MobMagicResistEvents() {
    }

    @SubscribeEvent
    public static void onDefaultMagicResistMobJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(living.getType());
        if (key == null) {
            return;
        }
        String namespace = key.getNamespace();
        if (!namespace.equals("minecraft") && !namespace.equals(IRONS_SPELLBOOKS)) {
            return;
        }
        AttributeInstance mrAttr = living.getAttribute(ModAttributes.LOL_MAGIC_RESIST.get());
        if (mrAttr == null) {
            return;
        }
        // 已被整合包/其它模组预设过基础值则不覆盖
        if (mrAttr.getBaseValue() != 0.0D) {
            return;
        }
        double maxHealth = living.getAttributeValue(Attributes.MAX_HEALTH);
        if (maxHealth <= 0.0D) {
            return;
        }
        boolean ironBoss = namespace.equals(IRONS_SPELLBOOKS) && IRON_BOSS_PATHS.contains(key.getPath());
        double ratio = ironBoss ? BOSS_MR_RATIO : DEFAULT_MR_RATIO;
        double base = Math.floor(maxHealth * ratio);
        if (base > 1000.0D) {
            base = 1000.0D; // 属性上限
        }
        mrAttr.setBaseValue(base);
    }
}
