package com.example.lolaccessories.config;

/**
 * 神话装备「挑战解锁」配置（每件神话装备一个 JSON，可由整合包作者魔改）。
 *
 * <p>文件：{@code config/lolaccessories/mythic/<gear_id>.json}。字段：</p>
 * <ul>
 *   <li>{@code enabled}——挑战解锁总开关；置 false 后本装备不再由挑战发放，
 *       整合包可自行用合成 / 战利品表 / 指令等任意方式投放；</li>
 *   <li>{@code advancement_id}——解锁对应进度（成就）id（lolaccessories 命名空间），
 *       进度授予瞬间由服务端把装备发放给玩家；进度被重置（{@code /advancement revoke}）
 *       后可再次完成挑战再次获取——「终生一次」以进度状态为准，无需额外标记；</li>
 *   <li>{@code challenge.type}——挑战类型，当前支持：
 *       {@code projectile_single_damage}（单次弹射物直击伤害 ≥ threshold）与
 *       {@code max_health_and_collection}（最大生命 ≥ health_threshold 且已收集的
 *       collection_tag 品阶装备 ≥ collection_count 种）；</li>
 *   <li>{@code challenge.threshold}——类型 1 的伤害阈值。</li>
 * </ul>
 *
 * <p>新增神话装备：加一份 JSON + 对应进度（ModAdvancementProvider）即可，挑战类型后续按需扩展。</p>
 */
public class MythicChallengeConfig {

    public String gear_id = "";
    public boolean enabled = true;
    public String advancement_id = "";
    public Challenge challenge = new Challenge();

    /** 挑战条件。 */
    public static class Challenge {
        public String type = "";
        /** 类型 1：单次弹射物直击伤害阈值。 */
        public double threshold = 0.0;
        /** 类型 2：最大生命值阈值（如 100000）。 */
        public double health_threshold = 0.0;
        /** 类型 2：收集品阶标签路径（tier1/tier2/tier3/tier4）。 */
        public String collection_tag = "";
        /** 类型 2：需收集的装备种类数。 */
        public int collection_count = 0;
    }

    /** 缺失/损坏配置时的兜底（禁用态，不发放）。 */
    public MythicChallengeConfig fallback(String gearId) {
        this.gear_id = gearId;
        this.enabled = false;
        this.advancement_id = "";
        this.challenge = new Challenge();
        return this;
    }
}
