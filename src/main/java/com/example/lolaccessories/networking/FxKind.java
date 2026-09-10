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
    ZEKES_STORM,
    SUNFIRE_AEGIS,
    HEXPLATE_OVERDRIVE,
    TORCH
}
