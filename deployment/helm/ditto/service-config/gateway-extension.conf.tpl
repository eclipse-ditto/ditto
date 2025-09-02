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
          }
        {{- end }}
        }
      }

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
