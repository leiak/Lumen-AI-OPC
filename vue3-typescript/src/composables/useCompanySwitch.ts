import { ElMessageBox } from 'element-plus'
import useUserStore from '@/store/modules/user'

/**
 * 切换当前公司（租户）。
 *
 * <p>租户不在 token 里，而在服务端的登录态（Redis）里，所以「切换」是一次
 * 服务端调用：{@code POST /opc/user/switch-company}。切换成功后前端必须
 * <b>整页重载</b> —— 19 个视图在 setup 里就把 companyId 取成了普通数字
 * （{@code const companyId = useCompanyId()}），不重载的话它们手里还是旧租户。</p>
 *
 * <p>为什么不给那些视图改成响应式：19 处全改是另一种风险（把一个纯数字
 * 换成一个 ref，模板里能自动解包、脚本里全要补 {@code .value}），
 * 而「切租户 = 重载」本来就是多租户系统的常规做法。</p>
 *
 * @author OAC
 */
export function useCompanySwitch() {
  const userStore = useUserStore()

  /**
   * 切到指定公司。
   *
   * @param targetCompanyId 目标公司 id
   * @param options.confirm  是否弹确认框（默认 true）。已经在当前公司时不会重复切。
   * @returns 是否真的切了（取消确认 / 已经在当前公司 → false）
   */
  async function switchTo(targetCompanyId: number, options?: { confirm?: boolean }): Promise<boolean> {
    if (!targetCompanyId) {
      return false
    }
    // 已经在当前公司 —— 什么都不用做（也别重载页面）
    if (targetCompanyId === userStore.companyId) {
      return false
    }
    if (options?.confirm !== false) {
      try {
        await ElMessageBox.confirm(
          '切换公司会重新加载当前页面，确认切换？',
          '切换公司',
          {
            confirmButtonText: '切换',
            cancelButtonText: '取消',
            type: 'warning',
          },
        )
      } catch {
        // 用户点了取消
        return false
      }
    }
    await userStore.switchCompany(targetCompanyId)
    location.reload()
    return true
  }

  return { switchTo }
}
