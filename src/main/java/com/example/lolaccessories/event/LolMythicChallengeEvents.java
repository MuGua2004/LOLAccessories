package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.advancement.LolAdvancementService;
import com.example.lolaccessories.config.MythicChallengeConfig;
import com.example.lolaccessories.config.MythicChallengeManager;
import net.minecraft.advancements.Advancement;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 神话装备「挑战解锁」：完成挑战 → 解锁进度 → 装备直接发放到背包（满则掉落）。
 *
 * <p><b>流程与规则：</b></p>
 * <ol>
 *   <li>挑战判定（本类）：单次弹射物直击伤害 ≥ 配置阈值（默认澄空之愿 1,000,000）；
 *       达成且对应进度尚未完成 → 授予进度；</li>
 *   <li>进度授予瞬间（{@link AdvancementEvent}）：按配置把对应神话装备发放到玩家背包，
 *       背包满则掉落在脚下；</li>
 *   <li><b>终生一次以进度状态为准</b>：进度已完成时不重复发放；进度被重置
 *       （{@code /advancement revoke}）后 {@code isDone()} 回到 false，再次完成挑战
 *       即可再次解锁与获取——无额外 NBT 标记，逻辑天然闭环；</li>
 *   <li><b>魔改空间</b>：挑战类型/阈值/进度 id/总开关全部在
 *       {@code config/lolaccessories/mythic/<gear_id>.json}（首启从 jar 模板落地），
 *       整合包作者可改条件、换进度、或直接禁用后自行投放装备。</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolMythicChallengeEvents {

    /** 挑战类型：单次弹射物直击伤害 ≥ threshold。 */
    public static final String TYPE_PROJECTILE_SINGLE_DAMAGE = "projectile_single_damage";
    /** 挑战类型：最大生命 ≥ health_threshold 且收集 ≥ collection_count 种品阶装备。 */
    public static final String TYPE_MAX_HEALTH_AND_COLLECTION = "max_health_and_collection";

    private LolMythicChallengeEvents() {
    }

    // ================= 挑战判定（类型 2：tick 每 2 秒） =================

    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        if (player.tickCount % 40 != 0) {
            return;
        }
        MythicChallengeConfig config = MythicChallengeManager.get("demon_heart");
        if (!config.enabled || config.challenge == null
                || !TYPE_MAX_HEALTH_AND_COLLECTION.equals(config.challenge.type)
                || config.advancement_id == null || config.advancement_id.isEmpty()) {
            return;
        }
        if (player.getMaxHealth() < config.challenge.health_threshold) {
            return;
        }
        int seen = LolAdvancementService.countSeenInTier(player, config.challenge.collection_tag);
        if (seen < config.challenge.collection_count) {
            return;
        }
        tryUnlock(player, "demon_heart", config.advancement_id);
    }

    /** 已达成的挑战直接解锁（跳过阈值判断，供 tick 双条件检测复用）。 */
    private static void tryUnlock(ServerPlayer player, String gearId, String advancementId) {
        Advancement advancement = player.server.getAdvancements()
                .getAdvancement(new ResourceLocation(LOLAccessories.MOD_ID, advancementId));
        if (advancement == null) {
            return;
        }
        if (player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            return;
        }
        LolAdvancementService.grantAdvancement(player, advancementId);
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0F
                || event.getEntity().level().isClientSide) {
            return;
        }
        // 只认玩家本体击出的弹射物直击伤害（getEntity=归属玩家，getDirectEntity=弹射物）
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || !(event.getSource().getDirectEntity() instanceof Projectile)) {
            return;
        }
        evaluate(player, event.getAmount());
    }

    /** 澄空之愿：当前唯一注册的神话挑战（后续神话装备在此追加或改为遍历已注册配置）。 */
    private static void evaluate(ServerPlayer player, double projectileDamage) {
        tryUnlock(player, "clear_skys_wish", TYPE_PROJECTILE_SINGLE_DAMAGE, projectileDamage);
    }

    /**
     * 挑战判定：配置启用、类型匹配、阈值达成且进度尚未完成 → 授予进度。
     * （发放由 {@link #onAdvancement} 在进度授予事件里统一处理。）
     */
    public static void tryUnlock(ServerPlayer player, String gearId, String type, double value) {
        MythicChallengeConfig config = MythicChallengeManager.get(gearId);
        if (!config.enabled
                || config.challenge == null
                || !type.equals(config.challenge.type)
                || config.challenge.threshold <= 0.0D
                || value < config.challenge.threshold
                || config.advancement_id == null
                || config.advancement_id.isEmpty()) {
            return;
        }
        unlock(player, config.advancement_id);
    }

    /** 终生一次：进度已达成则跳过（进度重置后 isDone 变 false，可再次解锁）。 */
    private static void unlock(ServerPlayer player, String advancementId) {
        Advancement advancement = player.server.getAdvancements()
                .getAdvancement(new ResourceLocation(LOLAccessories.MOD_ID, advancementId));
        if (advancement == null) {
            return;
        }
        if (player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            return;
        }
        LolAdvancementService.grantAdvancement(player, advancementId);
    }

    // ================= 进度授予 → 发放装备 =================

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.level().isClientSide) {
            return;
        }
        ResourceLocation id = event.getAdvancement().getId();
        if (!LOLAccessories.MOD_ID.equals(id.getNamespace())) {
            return;
        }
        // 遍历已注册神话装备（gear_id 命名空间固定为本模组），按配置匹配进度
        for (String gearId : new String[]{"clear_skys_wish", "demon_heart"}) {
            MythicChallengeConfig config = MythicChallengeManager.get(gearId);
            if (!config.enabled || config.advancement_id == null || config.advancement_id.isEmpty()) {
                continue;
            }
            if (!config.advancement_id.equals(id.getPath())) {
                continue;
            }
            giveGear(player, gearId);
        }
    }

    /** 把神话装备发放到玩家背包；背包满则掉落在玩家脚下。 */
    private static void giveGear(ServerPlayer player, String gearId) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(LOLAccessories.MOD_ID, gearId));
        if (item == null) {
            LOLAccessories.LOGGER.error("[神话解锁] 未注册的神话装备 id：{}", gearId);
            return;
        }
        ItemStack stack = new ItemStack(item);
        String where;
        if (player.getInventory().add(stack)) {
            where = "已放入背包";
        } else {
            // 背包满：掉落在玩家脚下
            player.drop(stack, false);
            where = "背包已满，掉落在脚下";
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.0F);
        player.displayClientMessage(Component.translatable(
                "message.lolaccessories.mythic_unlocked",
                new ItemStack(item).getHoverName()), false);
        LOLAccessories.LOGGER.info("[神话解锁] {} 完成挑战，发放 {}（{}）",
                player.getName().getString(), gearId, where);
    }
}
