import axios from 'axios'
import { ElLoading, ElMessage } from 'element-plus'
import { saveAs } from 'file-saver'
import { getToken } from '@/utils/auth'
import errorCode from '@/utils/errorCode'
import { blobValidate } from '@/utils/ruoyi'

const baseURL = import.meta.env.VITE_APP_BASE_API
let downloadLoadingInstance: ReturnType<typeof ElLoading.service>

export default {
  zip(url: string, name: string) {
    const downloadUrl = baseURL + url
    downloadLoadingInstance = ElLoading.service({ text: "正在下载数据，请稍候", background: "rgba(0, 0, 0, 0.7)", })
    axios({
      method: 'get',
      url: downloadUrl,
      responseType: 'blob',
      headers: { 'Authorization': 'Bearer ' + getToken() }
    }).then(async (res: any) => {
      const isBlob = blobValidate(res.data)
      if (isBlob) {
        const blob = new Blob([res.data], { type: 'application/zip' })
        this.saveAs(blob, name)
      } else {
        // W12.2 此前是 `await this.printErrMsg(res.data)` 不带 await:若响应体不是 JSON
        // (nginx 的 502 HTML 页),JSON.parse 在一个无主的 promise 里抛错 ->
        // unhandled rejection,用户看不到任何提示。await 让它落进下面的 .catch。
        await this.printErrMsg(res.data)
      }
      downloadLoadingInstance.close()
    }).catch((r: any) => {
      console.error(r)
      // W12.2 与 utils/request.ts 的下载 .catch 同形:优先显示真实原因,
      // r 没有 .msg 时才落回硬编码兜底。
      ElMessage.error(r?.msg || '下载文件出现错误，请联系管理员！')
      downloadLoadingInstance.close()
    })
  },
  saveAs(text: Blob, name: string, opts?: any) {
    saveAs(text, name, opts)
  },
  async printErrMsg(data: any) {
    const resText = await data.text()
    const rspObj = JSON.parse(resText)
    // W12.2 与 utils/request.ts:93 同序:后端 msg 优先,errorCode 表只作兜底。
    const errMsg = rspObj.msg || errorCode[rspObj.code] || errorCode['default']
    ElMessage.error(errMsg)
  }
}
