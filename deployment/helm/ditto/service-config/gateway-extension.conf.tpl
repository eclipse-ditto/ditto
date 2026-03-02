# Ditto "Gateway" configuration extension file to be placed at /opt/ditto/gateway-extension.conf
ditto {
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
            prerequisite-conditions = [
            {{- range $index, $condition := $value.prerequisiteConditions }}
              "{{$condition}}"
            {{- end }}
            ]
          }
        {{- end }}
        }
      }

      {{- if .Values.gateway.config.authentication.namespaceAccess }}
      namespace-access = [
      {{- range $ruleIndex, $rule := .Values.gateway.config.authentication.namespaceAccess }}
        {
          conditions = [
          {{- range $condIndex, $condition := $rule.conditions }}
            "{{$condition}}"
          {{- end }}
          ]
          resource-types = [
          {{- range $rtIndex, $resourceType := $rule.resourceTypes }}
            "{{$resourceType}}"
          {{- end }}
          ]
          allowed-namespaces = [
          {{- range $nsIndex, $namespace := $rule.allowedNamespaces }}
            "{{$namespace}}"
          {{- end }}
          ]
          blocked-namespaces = [
          {{- range $bnsIndex, $namespace := $rule.blockedNamespaces }}
            "{{$namespace}}"
          {{- end }}
          ]
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
              prerequisite-conditions = [
              {{- range $index, $condition := $value.prerequisiteConditions }}
                "{{$condition}}"
              {{- end }}
              ]
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
  }
}
