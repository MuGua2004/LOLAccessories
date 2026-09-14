package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.init.ModMobEffects;
import com.example.lolaccessories.item.GearItem;
import com.example.lolaccessories.networking.LOLNetworking;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.List;
import java.util.function.Supplier;

/**
 * Forge 总线事件：装备被动触发 + 配置重载命令。
 *
 * <p>「切割 / 热烈」的触发点统一挂在 {@link LivingHurtEvent} 上：
 * 只要造成伤害的一方身上戴着带被动效果的装备，就会按配置施加对应药水效果。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModForgeEvents {

    private ModForgeEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lolaccessories")
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            GearConfigManager.reload();
                            Supplier<Component> message =
                                    () -> Component.translatable("commands.lolaccessories.reload.success");
                            context.getSource().sendSuccess(message, true);
                            return 1;
                        }))
                // 调试：同时触发 4 种装备的冷却条（无视佩戴条件强制显示），便于观察 HUD 观感
                .then(Commands.literal("cdtest")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> runCooldownTest(context.getSource(), 15))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
                                .executes(context -> runCooldownTest(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "seconds"))))));
    }

    /**
     * 调试冷却条：同时点亮 4 个不同主题色的冷却条
     * （时间停止·琥珀金 / 回声·紫 / 净化·银蓝 / 魔法弹突进·橙红）。
     * 走调试通道，不要求玩家实际佩戴对应装备。
     */
    private static int runCooldownTest(CommandSourceStack source, int seconds) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("该指令只能由玩家执行"));
            return 0;
        }
        int ticks = seconds * 20;
        String[] skills = {"time_stop", "echo", "quicksilver", "rocketbelt"};
        for (String skillId : skills) {
            LOLNetworking.sendSkillCooldown(player, skillId, ticks, true);
        }
        source.sendSuccess(() -> Component.literal(
                "[LOLAccessories] 已同时触发 " + skills.length + " 条调试冷却（" + seconds + " 秒），"
                        + "无需佩戴对应装备即可在快捷栏左上方观察"), false);
        return 1;
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getSource() == null) {
            return;
        }
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof LivingEntity attacker)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim == attacker || !victim.isAlive()) {
            return;
        }

        // 找到攻击方身上所有佩戴的配置型饰品（黑色切割者等）
        List<SlotResult> equipped = CuriosApi.getCuriosInventory(attacker)
                .resolve()
                .map(handler -> handler.findCurios(stack -> stack.getItem() instanceof GearItem))
                .orElse(List.of());

        for (SlotResult result : equipped) {
            ItemStack stack = result.stack();
            if (!(stack.getItem() instanceof GearItem gear)) {
                continue;
            }
            GearConfig config = GearConfigManager.get(gear.getGearId());
            if (config == null) {
                continue;
            }
            for (GearConfig.OnHitEffect effect : config.on_hit_effects) {
                if (effect.enabled) {
                    applyOnHitEffect(effect, attacker, victim);
                }
            }
        }
    }

    private static void applyOnHitEffect(GearConfig.OnHitEffect effect, LivingEntity attacker, LivingEntity victim) {
        LivingEntity bearer = "self".equals(effect.target) ? attacker : victim;
        if (bearer == null || !bearer.isAlive()) {
            return;
        }
        int duration = Math.round((float) (effect.duration_seconds * 20.0F));
        if (duration <= 0) {
            return;
        }
        switch (effect.id) {
            case "cleaver_shred" -> applyShred(effect, bearer, duration);
            case "cleaver_rush" -> applyRush(effect, bearer, duration);
            // 其它被动（重伤/灼烧/法盾/咒刃/磐石等）由各自的专职事件类结算，这里静默跳过，
            // 不要当作“未知类型”告警刷屏
            default -> {
            }
        }
    }

    /** 切割：对目标叠加护甲削减层数（至多 max_stacks / 总上限 max_total），每次命中刷新 6 秒。 */
    private static void applyShred(GearConfig.OnHitEffect effect, LivingEntity target, int duration) {
        MobEffect shredEffect = ModMobEffects.CLEAVER_SHRED.get();

        int currentStacks = 0;
        MobEffectInstance existing = target.getEffect(shredEffect);
        if (existing != null) {
            currentStacks = existing.getAmplifier() + 1;
        }

        // 层数同时受「最多层数」与「总削减上限」约束，二者取小
        int stacksByTotal = effect.max_total > 0
                ? (int) Math.floor(effect.max_total / Math.max(effect.per_stack, 1.0E-6) + 1.0E-6)
                : effect.max_stacks;
        int maxStacks = Math.max(1, Math.min(effect.max_stacks, stacksByTotal));
        int newStacks = Math.min(currentStacks + 1, maxStacks);

        refreshEffect(target, shredEffect, duration, newStacks - 1);
    }

    /** 热烈：为自己刷新一段移速加成。 */
    private static void applyRush(GearConfig.OnHitEffect effect, LivingEntity bearer, int duration) {
        MobEffect rushEffect = ModMobEffects.CLEAVER_RUSH.get();
        refreshEffect(bearer, rushEffect, duration, 0);
    }

    /**
     * 用新的层数/时长替换已有效果。先移除再添加，确保药水效果叠加层数变化时，
     * 其携带的属性修正器能够被正确撤销并重新应用。
     */
    private static void refreshEffect(LivingEntity target, MobEffect effect, int duration, int amplifier) {
        target.removeEffect(effect);
        target.addEffect(new MobEffectInstance(effect, duration, amplifier, false, false, false));
    }
}
