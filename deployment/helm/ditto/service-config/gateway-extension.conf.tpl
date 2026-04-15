# Ditto "Gateway" configuration extension file to be placed at /opt/ditto/gateway-extension.conf
ditto {
  headers {
    redacted-in-log = [
    {{- range $index, $header := .Values.global.headersRedactedInLog }}
      "{{$header}}"
    {{- end }}
    ]
  }

  gateway {
    authentication {
      oauth {
        openid-connect-issuers {
        {{- range $key, $value := .Values.gateway.config.authentication.oauth.openidConnectIssuers }}
          {{$key}} = {
            issuer = "{{$value.issuer}}"
            auth-subjects = [
            {{- range $index, $subject := $value.authSubjects }}
              "{{$subject}}"
            {{- end }}
            ]
            inject-claims-into-headers = {
            {{- range $claimKey, $claimValue := $value.injectClaimsIntoHeaders }}
              {{$claimKey}} = "{{$claimValue}}"
            {{- end }}
            }
            {{- if $value.prerequisiteConditions }}
            prerequisite-conditions = [
            {{- range $index, $condition := $value.prerequisiteConditions }}
              "{{$condition}}"
            {{- end }}
            ]
            {{- end }}
          }
        {{- end }}
        }
      }

      {{- if .Values.gateway.config.authentication.namespaceAccess }}
      namespace-access = [
      {{- range $index, $rule := .Values.gateway.config.authentication.namespaceAccess }}
        {
          conditions = [
          {{- range $condIdx, $condition := $rule.conditions }}
            "{{$condition}}"
          {{- end }}
          ]
          {{- if $rule.resourceTypes }}
          resource-types = [
          {{- range $rtIdx, $rt := $rule.resourceTypes }}
            "{{$rt}}"
          {{- end }}
          ]
          {{- end }}
          {{- if $rule.allowedNamespaces }}
          allowed-namespaces = [
          {{- range $nsIdx, $ns := $rule.allowedNamespaces }}
            "{{$ns}}"
          {{- end }}
          ]
          {{- end }}
          {{- if $rule.blockedNamespaces }}
          blocked-namespaces = [
          {{- range $nsIdx, $ns := $rule.blockedNamespaces }}
            "{{$ns}}"
          {{- end }}
          ]
          {{- end }}
        }
      {{- end }}
      ]
      {{- end }}

      devops {
        oauth {
          openid-connect-issuers {
          {{- range $key, $value := .Values.gateway.config.authentication.devops.oauth.openidConnectIssuers }}
            {{$key}} = {
              issuer = "{{$value.issuer}}"
              auth-subjects = [
              {{- range $index, $subject := $value.authSubjects }}
                "{{$subject}}"
              {{- end }}
              ]
              inject-claims-into-headers = {
              {{- range $claimKey, $claimValue := $value.injectClaimsIntoHeaders }}
                {{$claimKey}} = "{{$claimValue}}"
              {{- end }}
              }
              {{- if $value.prerequisiteConditions }}
              prerequisite-conditions = [
              {{- range $index, $condition := $value.prerequisiteConditions }}
                "{{$condition}}"
              {{- end }}
              ]
              {{- end }}
            }
          {{- end }}
          }
        }
        devops-oauth2-subjects = [
        {{- range $index, $oauthSubject := .Values.gateway.config.authentication.devops.oauthSubjects }}
          "{{$oauthSubject}}"
        {{- end }}
        ]
        status-oauth2-subjects = [
        {{- range $index, $oauthSubject := .Values.gateway.config.authentication.devops.statusOauthSubjects }}
          "{{$oauthSubject}}"
        {{- end }}
        ]
      }
    }

    wot-directory {
      base-prefix = "{{ .Values.gateway.config.wotDirectory.basePrefix }}"
      authentication-required = {{ .Values.gateway.config.wotDirectory.authenticationRequired }}
    }
  }
}
