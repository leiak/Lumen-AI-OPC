import useUserStore from '@/store/modules/user'

/**
 * 当前登录用户所属公司（租户）id。
 *
 * <p>此前 ERP / HR / Content 共 19 个视图写死了 `const companyId = 1` ——
 * 租户变成了客户端说了算，把参数改成 2 就能看别家的数据。现在服务端一律以
 * 登录态为准（租户守卫），前端这边也改成读登录态：登录后由 {@code getInfo}
 * 把 {@code companyId} 写进 user store。</p>
 *
 * <p>取值拿不到时兜底 <b>0</b>，不兜底 1。</p>
 *
 * <p><b>为什么是 0 而不是 1</b>：公司 1 是「示例一人公司」—— 演示环境里真实存在的一家
 * 租户。兜底成 1 等于宣称「用户就在示例公司里」，这既不属实，也让「没关联任何企业」
 * 和「正好在示例公司」在界面上再也分不开（前端只有这一个数字，没有第二个来源可比对）。</p>
 *
 * <p>兜底成 1 <b>今天不会让谁看到别家的数据</b>：服务端 {@code TenantCompanyFilter}
 * （查询参数）和 {@code CompanyIdBodyAdvice}（请求体 DTO）都会把客户端给的 companyId
 * 改写成登录态里的 {@code LoginUser.companyId}；登录了但没加入任何公司时，
 * {@code TenantGuard#resolveAllowed} 因为「客户端点名了某家公司」直接 403
 * —— 传 1 和传 0 是同一个结果、同一条消息。</p>
 *
 * <p>但它确实留着一条缝：{@code opc.tenant.enabled=false} 是给运维准备的「不用重新发版
 * 就能止血」的开关，守卫关掉后客户端的值就成了唯一依据 —— 那时每个没关联企业的账号
 * 都会以示例公司的身份读写。兜底成 0 就不会：{@code opc_company_profile} 的 id 自增从 1 起，
 * 库里没有 id=0 的公司，0 查不到任何数据。</p>
 *
 * <p><b>注意别把两种 0 混为一谈</b>：这里的 0 是<b>前端语义</b>的「无归属」；
 * 而库里 {@code opc_llm_usage.company_id = 0} 是<b>系统</b>记账的意思
 * （见种子 {@code finance_daily_report_v1} 的说明 —— 工作流不带 companyId 触发时
 * 归属只能从工作流行取，取不到就落 0）。所以这个 0 <b>不要</b>直接传给按用量过滤的接口，
 * 那会把系统行的用量显示成「这家公司的」。</p>
 *
 * <p><b>注意</b>：这里返回的是<b>普通数字</b>，在 setup 里取一次就固定了。
 * 切换公司后这些视图不会自动跟着变 —— 切租户要整页重载
 * （见 {@code useCompanySwitch}），重载后 setup 重跑，取到的就是新租户。</p>
 */
export function useCompanyId(): number {
  const userStore = useUserStore()
  return userStore.companyId || 0
}
