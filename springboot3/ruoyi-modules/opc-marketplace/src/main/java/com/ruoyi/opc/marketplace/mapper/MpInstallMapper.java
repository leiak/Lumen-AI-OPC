package com.ruoyi.opc.marketplace.mapper;

import com.ruoyi.opc.marketplace.domain.MpInstall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * mp_install Mapper
 *
 * @author OAC
 */
@Mapper
public interface MpInstallMapper {
    int insert(MpInstall i);

    MpInstall selectById(Long id);

    /**
     * 按 uk_install(publication_id, tenant_company_id, user_id) 取唯一记录。
     * 该唯一键不含 deleted —— 软删除后仍占用，故此处不加 deleted 过滤，
     * 否则「已卸载 → 重装」会返回 null 并在 insert 时撞唯一键。
     */
    MpInstall selectUnique(@Param("publicationId") Long publicationId,
                           @Param("tenantCompanyId") Long tenantCompanyId,
                           @Param("userId") Long userId);

    List<MpInstall> selectByUser(Long userId);

    List<MpInstall> selectByTenant(Long tenantCompanyId);

    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("uninstalledAt") LocalDateTime uninstalledAt);

    /**
     * 重新安装：复活既有行。
     * 背景：uk_install(publication_id, tenant_company_id, user_id) 不含 deleted，
     * 软删除行仍占用唯一键，重复 INSERT 会撞 1062；且 updateStatus 的 <set> 无法清空
     * uninstalled_at，故单独提供本方法，显式置 NULL 并复位 deleted。
     */
    int reactivate(@Param("id") Long id,
                   @Param("agentInstanceId") Long agentInstanceId,
                   @Param("installedAt") LocalDateTime installedAt,
                   @Param("updateBy") String updateBy);
}