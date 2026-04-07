# Ditto "Policies" configuration extension file to be placed at /opt/ditto/policies-extension.conf
ditto {
  headers {
    redacted-in-log = [
    {{- range $index, $header := .Values.policies.config.headersRedactedInLog }}
      "{{$header}}"
    {{- end }}
    ]
  }

  {{- if .Values.policies.config.namespacePolicies }}
  namespace-policies {
  {{- range $pattern, $policyIds := .Values.policies.config.namespacePolicies }}
    "{{$pattern}}" = [
    {{- range $idx, $policyId := $policyIds }}
      "{{$policyId}}"
    {{- end }}
    ]
  {{- end }}
  }
  {{- end }}

  entity-creation {
    grant = [
    {{- range $grantIdx, $grant := .Values.policies.config.entityCreation.grants }}
      {
        resource-types = ["policy"]
        namespaces = [
        {{- range $namespaceIdx, $namespace := $grant.namespaces }}
          "{{$namespace}}"
        {{- end }}
        ]
        auth-subjects = [
        {{- range $subjectIdx, $subject := $grant.authSubjects }}
          "{{$subject}}"
        {{- end }}
        ]
      }
    {{- end }}
    ]
    revoke = [
    {{- range $revokeIdx, $revoke := .Values.policies.config.entityCreation.revokes }}
      {
        resource-types = ["policy"]
        namespaces = [
        {{- range $namespaceIdx, $namespace := $revoke.namespaces }}
          "{{$namespace}}"
        {{- end }}
        ]
        auth-subjects = [
        {{- range $subjectIdx, $subject := $revoke.authSubjects }}
          "{{$subject}}"
        {{- end }}
        ]
      }
    {{- end }}
    ]
  }
  policies {
    policy {
      {{- if .Values.policies.config.namespaceActivityCheck }}
      namespace-activity-check = [
      {{- range $index, $entry := .Values.policies.config.namespaceActivityCheck }}
        {
          namespace-pattern = "{{$entry.namespacePattern}}"
          inactive-interval = "{{$entry.inactiveInterval}}"
          deleted-interval = "{{$entry.deletedInterval}}"
        }
      {{- end }}
      ]
      {{- end }}
      event {
        historical-headers-to-persist = [
        {{- range $index, $header := .Values.policies.config.persistence.events.historicalHeadersToPersist }}
          "{{$header}}"
        {{- end }}
        ]
      }
    }
  }
}
