{{/*
Expand the name of the chart.
*/}}
{{- define "opc.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some K8s name fields are limited to this (RFC 1123).
*/}}
{{- define "opc.fullname" -}}
{{- if .Values.nameOverride }}
{{- .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Chart name and version label.
*/}}
{{- define "opc.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "opc.labels" -}}
helm.sh/chart: {{ include "opc.chart" . }}
{{ include "opc.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: opc-platform
{{- end }}

{{/*
Selector labels
*/}}
{{- define "opc.selectorLabels" -}}
app.kubernetes.io/name: {{ include "opc.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Service-specific selector labels
Usage: include "opc.serviceSelectorLabels" (dict "service" $service "context" $)
*/}}
{{- define "opc.serviceSelectorLabels" -}}
{{- $svc := .service -}}
app: {{ $svc.name }}
{{- end }}

{{/*
Image reference
Usage: include "opc.image" (dict "service" $service "context" $)
*/}}
{{- define "opc.image" -}}
{{- $svc := .service -}}
{{- $ctx := .context -}}
{{- if $ctx.Values.global.imageRegistry }}
{{- printf "%s/%s:%s" $ctx.Values.global.imageRegistry $svc.image.repository $ctx.Values.global.imageTag }}
{{- else }}
{{- printf "%s:%s" $svc.image.repository $ctx.Values.global.imageTag }}
{{- end }}
{{- end }}

{{/*
Common env vars (SPRING_PROFILES_ACTIVE + Nacos)
Usage: include "opc.commonEnv" (dict "context" $)
*/}}
{{- define "opc.commonEnv" -}}
- name: SPRING_PROFILES_ACTIVE
  value: {{ .context.Values.global.profile | quote }}
- name: NACOS_SERVER_ADDR
  value: {{ .context.Values.global.nacos.serverAddr | quote }}
- name: NACOS_NAMESPACE
  value: {{ .context.Values.global.nacos.namespace | quote }}
- name: SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR
  value: {{ .context.Values.global.nacos.serverAddr | quote }}
- name: SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR
  value: {{ .context.Values.global.nacos.serverAddr | quote }}
{{- if .context.Values.global.nacos.username }}
- name: NACOS_USERNAME
  value: {{ .context.Values.global.nacos.username | quote }}
{{- end }}
{{- if .context.Values.global.nacos.password }}
- name: NACOS_PASSWORD
  value: {{ .context.Values.global.nacos.password | quote }}
{{- end }}
- name: MYSQL_HOST
  value: {{ .context.Values.global.middleware.mysqlHost | quote }}
- name: MYSQL_PORT
  value: {{ .context.Values.global.middleware.mysqlPort | quote }}
- name: MYSQL_DB
  value: {{ .context.Values.global.middleware.mysqlDb | quote }}
- name: REDIS_HOST
  value: {{ .context.Values.global.middleware.redisHost | quote }}
- name: REDIS_PORT
  value: {{ .context.Values.global.middleware.redisPort | quote }}
- name: RABBITMQ_HOST
  value: {{ .context.Values.global.middleware.rabbitmqHost | quote }}
- name: RABBITMQ_PORT
  value: {{ .context.Values.global.middleware.rabbitmqPort | quote }}
- name: QDRANT_HOST
  value: {{ .context.Values.global.middleware.qdrantHost | quote }}
- name: QDRANT_PORT
  value: {{ .context.Values.global.middleware.qdrantPort | quote }}
{{- end }}

{{/*
Secret env (from service.secrets list)
Usage: include "opc.secretEnv" (dict "service" $service)
*/}}
{{- define "opc.secretEnv" -}}
{{- range .service.secrets }}
- name: {{ .name }}
  valueFrom:
    secretKeyRef:
      name: {{ .secret | quote }}
      key: {{ .key | quote }}
{{- end }}
{{- end }}

{{/*
JVM opts env
Usage: include "opc.jvmOpts" (dict "service" $service)
*/}}
{{- define "opc.jvmOpts" -}}
{{- if .service.jvmOpts }}
- name: JAVA_OPTS
  value: {{ .service.jvmOpts | quote }}
{{- end }}
{{- end }}

{{/*
ServiceAccount name
*/}}
{{- define "opc.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "opc.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}
