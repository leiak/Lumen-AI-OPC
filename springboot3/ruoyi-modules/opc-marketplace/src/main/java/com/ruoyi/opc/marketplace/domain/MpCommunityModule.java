package com.ruoyi.opc.marketplace.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 社区模块的只读投影 — W80 P2
 *
 * <p>对应 {@code opc_community_module} 表。这张表归 opc-community 所有，
 * marketplace 只<b>读</b>它（校验 module_id 指向真实存在的模块、
 * 核对商品类目与模块类目是否对得上），<b>不写</b> —— 写一律走
 * {@code CommunityClient} 让社区自己写。
 *
 * <p>字段刻意只留校验要用的那几个：id / code / name / category / installCount。
 * 刻意不把整张表映射过来（owner_email 之类）—— marketplace 没有读它的必要，
 * 少读一点少泄漏一点。
 *
 * @author OAC
 */
@Data
public class MpCommunityModule implements Serializable {

    private static final long serialVersionUID = 1L;

    /** {@code opc_community_module.id} —— mp_publication.module_id 指的就是它。 */
    private Long id;

    /** 模块 code（全局唯一），例如 {@code ecom-ops}。 */
    private String code;

    /** 模块名。 */
    private String name;

    /** 模块类目（CRM / FINANCE / ECOM …），商品类目要跟它对得上。 */
    private String category;

    /** 社区侧记的安装次数（marketplace 只读，不写）。 */
    private Integer installCount;
}
