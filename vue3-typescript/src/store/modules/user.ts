import router from '@/router'
import cache from '@/plugins/cache'
import { ElMessageBox } from 'element-plus'
import { login, logout, getInfo } from '@/api/login'
import { listMyCompanyMembers, switchCompany as switchCompanyApi } from '@/api/opc/user'
import { getToken, setToken, removeToken } from '@/utils/auth'
import { isEmpty } from "@/utils/validate"
import useLockStore from '@/store/modules/lock'
import defAva from '@/assets/images/profile.jpg'

/** 我加入的公司（可切换的租户） */
export interface CompanyBrief {
  id: number
  companyName: string
  role: string
  current: boolean
}

interface UserState {
  token: string | undefined
  id: string | number
  name: string
  nickName: string
  avatar: string
  roles: string[]
  permissions: string[]
  /** 所属公司（租户）id —— 后端 getInfo 下发，前端据此决定 companyId。 */
  companyId: number
  /** 我加入的公司（可切换的租户）—— 由 loadCompanies() 拉取。 */
  companies: CompanyBrief[]
}

const useUserStore = defineStore(
  'user',
  {
    state: (): UserState => ({
      token: getToken(),
      id: '',
      name: '',
      nickName: '',
      avatar: '',
      roles: [],
      permissions: [],
      companyId: 0,
      companies: []
    }),
    actions: {
      // 登录
      login(userInfo: { username: string; password: string; code: string; uuid: string }) {
        const username = userInfo.username.trim()
        const password = userInfo.password
        const code = userInfo.code
        const uuid = userInfo.uuid
        return new Promise<void>((resolve, reject) => {
          login(username, password, code, uuid).then(res => {
            let data = res.data
            setToken(data.access_token)
            this.token = data.access_token
            useLockStore().unlockScreen()
            resolve()
          }).catch(error => {
            reject(error)
          })
        })
      },
      // 获取用户信息
      getInfo() {
        return new Promise((resolve, reject) => {
          getInfo().then(res => {
            const user = res.user
            const avatar = (isEmpty(user.avatar)) ? defAva : user.avatar
            if (res.roles && res.roles.length > 0) { // 验证返回的roles是否是一个非空数组
              this.roles = res.roles
              this.permissions = res.permissions
            } else {
              this.roles = ['ROLE_DEFAULT']
            }
            this.id = user.userId || ''
            this.name = user.userName || ''
            this.nickName = user.nickName || ''
            this.avatar = avatar
            // 租户（公司）归属 —— 服务端以登录态为准，前端不再硬编码 companyId
            this.companyId = Number(res.companyId) || 0
            cache.session.set('pwrChrtype', res.pwdChrtype || '')
            /* 初始密码提示 */
            if(res.isDefaultModifyPwd) {
              ElMessageBox.confirm('您的密码还是初始密码，请修改密码！',  '安全提示', {  confirmButtonText: '确定',  cancelButtonText: '取消',  type: 'warning' }).then(() => {
                router.push({ name: 'Profile', params: { activeTab: 'resetPwd' } })
              }).catch(() => {})
            }
            /* 过期密码提示 */
            if(!res.isDefaultModifyPwd && res.isPasswordExpired) {
              ElMessageBox.confirm('您的密码已过期，请尽快修改密码！',  '安全提示', {  confirmButtonText: '确定',  cancelButtonText: '取消',  type: 'warning' }).then(() => {
                router.push({ name: 'Profile', params: { activeTab: 'resetPwd' } })
              }).catch(() => {})
            }
            resolve(res)
          }).catch(error => {
            reject(error)
          })
        })
      },
      // 退出系统
      logOut() {
        return new Promise<void>((resolve, reject) => {
          logout().then(() => {
            this.token = ''
            this.roles = []
            this.permissions = []
            // 租户也要一起清 —— 换个人登录时不能还留着上一个人的公司列表
            this.companyId = 0
            this.companies = []
            removeToken()
            resolve()
          }).catch((error: any) => {
            reject(error)
          })
        })
      },
      /**
       * 拉取「我加入的公司」（= 可切换的租户）。
       *
       * <p>按<b>成员关系</b>取，不是按「我创办的」—— 被邀请进别人公司的员工
       * 也是有租户的人，前端不能对他显示「你没有公司」。</p>
       */
      async loadCompanies(): Promise<CompanyBrief[]> {
        const res: any = await listMyCompanyMembers()
        const list: CompanyBrief[] = Array.isArray(res?.data) ? res.data : []
        this.companies = list
        return list
      },
      /**
       * 视图用：默认该落在哪家公司 —— 当前租户优先，否则第一家。
       *
       * <p>「当前租户优先」很重要：公司选择器如果默认选中第一家，
       * 用户看到的公司名就和他真正所在的公司对不上（数据其实是对的，
       * 服务端一律以登录态为准，但界面在骗人）。</p>
       */
      defaultCompanyId(companies?: CompanyBrief[]): number | undefined {
        const list = companies && companies.length ? companies : this.companies
        if (!list.length) return undefined
        const hit = list.find(c => c.id === this.companyId)
        return hit ? hit.id : list[0].id
      },
      /**
       * 切换当前公司（租户）。
       *
       * <p>服务端改的是 Redis 里的登录态，所以切换后<b>所有</b>下游服务
       * 读到的都是新公司；前端这边同步把 companyId 和列表里的 current 标记更新掉。</p>
       */
      async switchCompany(targetCompanyId: number): Promise<number> {
        const res: any = await switchCompanyApi(targetCompanyId)
        const newId = Number(res?.data?.companyId ?? res?.data) || 0
        this.companyId = newId
        this.companies = this.companies.map(c => ({ ...c, current: c.id === newId }))
        return newId
      }
    }
  })

export default useUserStore
