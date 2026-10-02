{{- define "hr-portal.name" -}}
{{- .Chart.Name -}}
{{- end -}}

{{- define "hr-portal.selectorLabels" -}}
app.kubernetes.io/name: {{ include "hr-portal.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "hr-portal.labels" -}}
{{ include "hr-portal.selectorLabels" . }}
app.kubernetes.io/version: {{ .Values.image.tag | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{- end -}}
