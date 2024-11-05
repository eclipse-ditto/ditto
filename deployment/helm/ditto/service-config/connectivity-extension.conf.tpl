# Ditto "Connectivity" configuration extension file to be placed at /opt/ditto/connectivity-extension.conf
ditto {
  connectivity {
    connection {
      event {
        historical-headers-to-persist = [
        {{- range $index, $header := .Values.connectivity.config.persistence.events.historicalHeadersToPersist }}
          "{{$header}}"
        {{- end }}
        ]
      }
    }
  }
}
