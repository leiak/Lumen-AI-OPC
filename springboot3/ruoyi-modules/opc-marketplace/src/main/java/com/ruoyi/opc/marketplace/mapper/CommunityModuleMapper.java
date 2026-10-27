package com.ruoyi.opc.marketplace.mapper;

import com.ruoyi.opc.marketplace.domain.MpCommunityModule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 社区模块的<b>只读</b> Mapper — W80 P2
 *
 * <p><b>边界</b>：{@code opc_community_module} 归 opc-community 所有。
 * marketplace 与它同库，所以能直接读；但<b>只读</b> ——
 * 这里刻意<b>不</b>提供 insert / update / delete，
 * 写操作一律走 {@code CommunityClient} 让社区自己写（见
 * {@code com.ruoyi.opc.marketplace.feign.CommunityClient}）。
 *
 * <p>为什么 marketplace 需要读它：模块商品（{@code mp_publication.module_id}）
 * 此前<b>完全不校验</b>指向的模块是否存在 ——
 * 种子里就有一条「电商运营助手」指着 id=1 的 {@code crm-starter}。
 * 应用自己 {@code create()} 出来的商品也不会拦（create 只校验 moduleId 非空）。
 * 这个 mapper 就是那道校验的依据。
 *
 * @author OAC
 */
@Mapper
public interface CommunityModuleMapper {

    /** 按 id 取社区模块；不存在返回 {@code null}。 */
    MpCommunityModule selectById(@Param("id") Long id);

    /** 按 code 取社区模块；不存在返回 {@code null}。 */
    MpCommunityModule selectByCode(@Param("code") String code);
}
