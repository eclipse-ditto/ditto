# Ditto "Policies" configuration extension file to be placed at /opt/ditto/policies-extension.conf
ditto {
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
