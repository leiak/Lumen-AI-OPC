import useUserStore from '@/store/modules/user'

/**
 * 当前登录用户所属公司（租户）id。
 *
 * <p>此前 ERP / HR / Content 共 19 个视图写死了 `const companyId = 1` ——
 * 租户变成了客户端说了算，把参数改成 2 就能看别家的数据。现在服务端一律以
 * 登录态为准（租户守卫），前端这边也改成读登录态：登录后由 {@code getInfo}
 * 把 {@code companyId} 写进 user store。</p>
 *
 * <p>取值拿不到时兜底 1（演示环境里公司 1 是公开展示的那一家）。
 * 真正的隔离由服务端兜底 —— 前端传什么都不算数。</p>
 */
export function useCompanyId(): number {
  const userStore = useUserStore()
  return userStore.companyId || 1
}
