package com.example.lolaccessories.event;

/**
 * 护盾类型（真护盾系统，取代旧「原版黄心」方案）。
 *
 * <ul>
 *   <li>{@link #WHITE}——白盾（普通护盾）：可抵消<b>除虚空伤害外</b>的所有伤害；</li>
 *   <li>{@link #MAGIC}——紫盾（魔法护盾）：只抵消铁魔法学派伤害与原版魔法伤害；</li>
 *   <li>{@link #PHYSICAL}——橙盾（物理护盾）：只抵消近战与弹射物伤害。</li>
 * </ul>
 *
 * <p>同一玩家可同时持有多种护盾；HUD 上只显示数额最多的那种，抵消时对应类型优先、
 * 不足部分由白盾兜底。</p>
 */
public enum ShieldType {
    WHITE, MAGIC, PHYSICAL
}
