package com.example.lolaccessories.networking;

/**
 * 装备视觉特效类型（客户端叠加特效）。
 *
 * <p>只承载“在目标实体身上显示什么类型的特效”，具体几何与颜色在客户端
 * {@code client.GearFxRenderer} 里按 kind 绘制：</p>
 * <ul>
 *   <li>{@link #INSPIRE}——战歌·鼓舞增幅（头顶竖立旋转的金色符文轮盘）；</li>
 *   <li>{@link #REALIZE}——实现器·法力成真（脚下六边形法阵 + 六芒星 + 升腾流光）；</li>
 *   <li>{@link #FANFARE}——班德尔音管·嘹亮旋律增幅（上升的翠绿旋律音浪环）；</li>
 *   <li>{@link #BARRAGE}——猎魔人弩箭·开战弹幕（周身赤焰箭雨：环绕弩矢 + 双层对旋火星 + 战火环）；</li>
 *   <li>{@link #SHIELD_ROOKERN}——败魔·魔盾球罩（紫色半透明晶壳，菲涅尔描边）；</li>
 *   <li>{@link #SHIELD_PROTOPLASM}——原生质护带·救主灵刃（青色贴身水膜罩 + 上浮气泡）；</li>
 *   <li>{@link #SHIELD_SERAPH}——炽天使之拥·应急护盾（金白圣光球罩）；</li>
 *   <li>{@link #SHIELD_FIMBULWINTER}——冬之誓·永恒（冰蓝寒霜球罩）；</li>
 *   <li>{@link #SHIELD_STERAK}——斯特拉克的挑战护手·救主灵刃（金色巨力球罩）；</li>
 *   <li>{@link #SHIELD_BLOODTHIRSTER}——饮血剑·余烬（深红溢血球罩）；</li>
 *   <li>{@link #ZEKES_STORM}——基克的聚合·聚合风暴（冰蓝+炽橙双色对流螺旋）；</li>
 *   <li>{@link #SUNFIRE_AEGIS}——日炎圣盾·献祭（常驻贴体火焰 + 脚下灼热环）；</li>
 *   <li>{@link #HEXPLATE_OVERDRIVE}——海克斯注力刚壁·超速驱动（电蓝攻速能量环）；</li>
 *   <li>{@link #TORCH}——黯炎火炬·黯炎灼烧（目标身上收敛的暗紫妖火）。</li>
 * </ul>
 *
 * <p>护罩类（ROOKERN / PROTOPLASM / SERAPH / FIMBULWINTER / STERAK / BLOODTHIRSTER）
 * 客户端按“同一实体同一槽位”处理，后到先占、不同时叠加；增幅类同样每种 kind 同一实体
 * 只存一份，重复触发直接刷新。</p>
 */
public enum FxKind {
    INSPIRE,
    REALIZE,
    FANFARE,
    BARRAGE,
    SHIELD_ROOKERN,
    SHIELD_PROTOPLASM,
    SHIELD_SERAPH,
    SHIELD_FIMBULWINTER,
    SHIELD_STERAK,
    SHIELD_BLOODTHIRSTER,
    /** 玛莫提乌斯之噬·救主灵刃（暗紫魔法护盾球罩）。 */
    SHIELD_MAW,
    ZEKES_STORM,
    SUNFIRE_AEGIS,
    HEXPLATE_OVERDRIVE,
    TORCH,
    /** 救赎·降临——施法预告：以施法点为中心渐亮展开的圣光法阵（锚定地点，走 FxSpotPacket）。 */
    REDEMPTION_CAST,
    /** 救赎·降临——圣光落下：从天而降的光柱 + 落点冲击环（锚定地点，走 FxSpotPacket）。 */
    REDEMPTION_DESCENT,
    /** 狂徒铠甲·狂徒之心——回血时的贴地柔和绿金光环（锚定玩家，克制不遮挡视野）。 */
    WARMOG_RESTORE,
    /** 女妖面纱·废除——法术屏障（紫罗兰缓旋涡环，法盾就绪时常驻）。 */
    SHIELD_BANSHEE,
    /** 残疫·憎恨之雾——紫色贴地法阵圈界定恨雾范围（锚定地点，走 FxSpotPacket）。 */
    HATEFOG,
    /** 蜕生·死中新生——击杀位置爆发的绿色治疗新星（大光球 + 展开回血法阵，锚定地点，走 FxSpotPacket）。 */
    LIFE_FROM_DEATH,
    /** 翡翠城·再见桃花源——抹杀翡翠法阵：贴地三层展开法阵 + 对旋星芒环 + 八根上升光柱（锚定地点，走 FxSpotPacket）。 */
    EMERALD_DOOM,
    /** 自然之力·坚韧——满层时佩戴者周身的翠绿自然光环（上升风叶 + 大地法阵，锚定佩戴者）。 */
    STEADFAST_AURA,
    /** 风暴狂涌·骤风——标记目标周身攒聚的紫金电弧（延迟引爆前的风暴攒聚，锚定目标）。 */
    STORMSURGE_MARK
}
