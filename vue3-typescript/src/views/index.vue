<template>
  <div class="app-container home">
    <el-row :gutter="20">
      <el-col :sm="24" :lg="12" style="padding-left: 20px">
        <h2>OPC 管理后台</h2>
        <p>
          OPC (Open Personal Cloud) 是面向中小团队的 AI 中后台，基于 Spring Boot 3 微服务架构构建，已扩展 AI 核心、客户关系管理（CRM）、人力资源（HR）、洞察分析、实时通知、模块市场等 OPC 业务模块，可用于网站管理后台、会员中心、CRM、OA、AI Agent 平台等场景。所有前后端代码封装精简、出错概率低，同时支持移动端访问。系统会持续迭代 OPC 业务能力。
        </p>
        <p>
          <b>当前版本:</b> <span>v{{ version }}</span>
        </p>
        <p>
          <el-tag type="danger">&yen;免费开源</el-tag>
        </p>
        <p>
          <el-button
            type="primary"
            icon="Cloudy"
            plain
            @click="goTarget('https://gitee.com/lumen-opc/opc-cloud')"
            >访问码云</el-button
          >
          <el-button
            icon="HomeFilled"
            plain
            @click="goTarget('https://opc.cloud')"
            >访问主页</el-button
          >
        </p>
      </el-col>

      <el-col :sm="24" :lg="12" style="padding-left: 50px">
        <el-row>
          <el-col :span="12">
            <h2>技术选型</h2>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="6">
            <h4>后端技术</h4>
            <ul>
              <li>SpringBoot</li>
              <li>SpringCloud</li>
              <li>Nacos</li>
              <li>Sentinel</li>
              <li>Seata</li>
              <li>Minio</li>
              <li>...</li>
            </ul>
          </el-col>
          <el-col :span="6">
            <h4>前端技术</h4>
            <ul>
              <li>Vue</li>
              <li>Vuex</li>
              <li>Element-ui</li>
              <li>Axios</li>
              <li>Echarts</li>
              <li>Quill</li>
              <li>...</li>
            </ul>
          </el-col>
        </el-row>
      </el-col>
    </el-row>
    <el-divider />
    <el-row :gutter="20">
      <el-col :xs="24" :sm="24" :md="12" :lg="8">
        <el-card class="update-log">
          <template v-slot:header>
            <div class="clearfix">
              <span>联系信息</span>
            </div>
          </template>
          <div class="body">
            <p>
              <i class="el-icon-s-promotion"></i> 官网：<el-link
                href="https://opc.cloud"
                target="_blank"
                >https://opc.cloud</el-link
              >
            </p>
            <p>
              <i class="el-icon-document"></i> 文档：<el-link
                href="https://opc.cloud/docs"
                target="_blank"
                >https://opc.cloud/docs</el-link
              >
            </p>
            <p>
              <i class="el-icon-folder"></i> 仓库：<el-link
                href="https://gitee.com/lumen-opc/opc-cloud"
                target="_blank"
                >https://gitee.com/lumen-opc/opc-cloud</el-link
              >
            </p>
            <p>
              <i class="el-icon-user-solid"></i> QQ群：<a
                href="http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=jR7HqTiLzuLocIH6aNNtTW3LlCV8UQ6d&authKey=GJZj%2Bnwmsvk6j%2FWrX7FKstAaRjgUC1WL%2F%2B8aVTaPbHX5wOimH5q9igaOSXP7cF9L&noverify=0&group_code=112869560"
                target="_blank"
                >112869560</a
              >
            </p>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="24" :md="12" :lg="8">
        <el-card class="update-log">
          <template v-slot:header>
            <div class="clearfix">
              <span>更新日志</span>
            </div>
          </template>
          <div class="body">
            <p>OPC 业务迭代日志与每周发布条目见项目仓库:</p>
            <p>
              <el-link href="https://gitee.com/lumen-opc/opc-cloud" target="_blank">https://gitee.com/lumen-opc/opc-cloud</el-link>
            </p>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="24" :md="12" :lg="8">
        <el-card class="update-log">
          <template v-slot:header>
            <div class="clearfix">
              <span>捐赠支持</span>
            </div>
          </template>
          <div class="body">
            <img
              src="@/assets/images/pay.png"
              alt="donate"
              style="width:100%"
            />
            <span style="display: inline-block; height: 30px; line-height: 30px"
              >你可以请作者喝杯咖啡表示鼓励</span
            >
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts" name="Index">
// 从 vite 注入的 package.json 同步(避免硬编码)— 重新 `npm install` 即可更新
import opcPkg from '@/../package.json'
const version = ref<string>((opcPkg as any).version ?? '1.0.0')

function goTarget(url: string): void {
  window.open(url, '__blank')
}
</script>

<style scoped lang="scss">
.home {
  blockquote {
    padding: 10px 20px;
    margin: 0 0 20px;
    font-size: 17.5px;
    border-left: 5px solid #eee;
  }
  hr {
    margin-top: 20px;
    margin-bottom: 20px;
    border: 0;
    border-top: 1px solid #eee;
  }
  .col-item {
    margin-bottom: 20px;
  }

  ul {
    padding: 0;
    margin: 0;
  }

  font-family: "open sans", "Helvetica Neue", Helvetica, Arial, sans-serif;
  font-size: 13px;
  color: #676a6c;
  overflow-x: hidden;

  ul {
    list-style-type: none;
  }

  h4 {
    margin-top: 0px;
  }

  h2 {
    margin-top: 10px;
    font-size: 26px;
    font-weight: 100;
  }

  p {
    margin-top: 10px;

    b {
      font-weight: 700;
    }
  }

  .update-log {
    ol {
      display: block;
      list-style-type: decimal;
      margin-block-start: 1em;
      margin-block-end: 1em;
      margin-inline-start: 0;
      margin-inline-end: 0;
      padding-inline-start: 40px;
    }
  }
}
</style>

