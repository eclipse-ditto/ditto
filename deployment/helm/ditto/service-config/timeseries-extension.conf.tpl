# Ditto "Timeseries" configuration extension file to be placed at /opt/ditto/timeseries-extension.conf
ditto {
  headers {
    redacted-in-log = [
    {{- range $index, $header := .Values.global.headersRedactedInLog }}
      "{{$header}}"
    {{- end }}
    ]
  }

  {{- if .Values.global.namespacePolicies }}
  namespace-policies {
  {{- range $pattern, $policyIds := .Values.global.namespacePolicies }}
    "{{$pattern}}" = [
    {{- range $idx, $policyId := $policyIds }}
      "{{$policyId}}"
    {{- end }}
    ]
  {{- end }}
  }
  {{- end }}

  {{- with .Values.timeseries.config.adapter.mongodb.retentionOverrides }}
  # Per-namespace timeseries retention overrides (env vars can't carry a map; rendered here).
  timeseries.adapter.mongodb.retention-overrides {
  {{- range $namespace, $duration := . }}
    "{{ $namespace }}" = "{{ $duration }}"
  {{- end }}
  }
  {{- end }}
}
