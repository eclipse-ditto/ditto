# Ditto "Things Search" configuration extension file to be placed at /opt/ditto/search-extension.conf
ditto {
  headers {
    redacted-in-log = [
    {{- range $index, $header := .Values.thingsSearch.config.headersRedactedInLog }}
      "{{$header}}"
    {{- end }}
    ]
  }

  {{- if .Values.thingsSearch.config.namespacePolicies }}
  namespace-policies {
  {{- range $pattern, $policyIds := .Values.thingsSearch.config.namespacePolicies }}
    "{{$pattern}}" = [
    {{- range $idx, $policyId := $policyIds }}
      "{{$policyId}}"
    {{- end }}
    ]
  {{- end }}
  }
  {{- end }}

  {{- if .Values.thingsSearch.config.indexedFieldsLimiting.enabled }}
  extensions {
    caching-signal-enrichment-facade-provider = "org.eclipse.ditto.thingsearch.service.persistence.write.streaming.SearchIndexingSignalEnrichmentFacadeProvider"
  }
  {{- end }}

  search {
    {{- if .Values.thingsSearch.config.countHintIndexName }}
    mongo-count-hint-index-name = "{{ .Values.thingsSearch.config.countHintIndexName }}"
    {{- end }}

    index-initialization {
        enabled = {{ .Values.thingsSearch.config.indexInitialization.enabled }}
    {{- if gt (len .Values.thingsSearch.config.indexInitialization.activatedIndexNames) 0 }}
        activated-index-names = [
        {{- range $index, $indexName := .Values.thingsSearch.config.indexInitialization.activatedIndexNames }}
          "{{$indexName}}"
        {{- end }}
        ]
    {{- end }}
    }

    {{- if .Values.thingsSearch.config.customIndexes }}
    updater {
      persistence {
        custom-indexes {
        {{- range $indexName, $fields := .Values.thingsSearch.config.customIndexes }}
          "{{$indexName}}" {
            fields = [
            {{- range $fieldIdx, $field := $fields }}
              { name = "{{$field.name}}", direction = "{{$field.direction}}" }
            {{- end }}
            ]
          }
        {{- end }}
        }
      }
    }
    {{- end }}

    {{- if .Values.thingsSearch.config.indexedFieldsLimiting.enabled }}
    namespace-indexed-fields = [
      {{- range $index, $value := .Values.thingsSearch.config.indexedFieldsLimiting.items }}
      {
        namespace-pattern = "{{$value.namespacePattern}}"
        indexed-fields = [
        {{- range $fieldIndex, $indexedField := $value.indexedFields }}
         "{{$indexedField}}"
        {{- end }}
        ]
      }
      {{- end }}
    ]
    {{- end }}

    operator-metrics {
      custom-metrics {
        {{- range $cmKey, $cmValue := .Values.thingsSearch.config.operatorMetrics.customMetrics }}
        {{$cmKey}} = {
          enabled = {{$cmValue.enabled}}
          {{- if $cmValue.scrapeInterval }}
          scrape-interval = "{{$cmValue.scrapeInterval}}"
          {{- end }}
          namespaces = [
          {{- range $index, $namespace := $cmValue.namespaces }}
            "{{$namespace}}"
          {{- end }}
          ]
          filter = "{{$cmValue.filter}}"
          tags {
          {{- range $tagKey, $tagValue := $cmValue.tags }}
            {{$tagKey}} = "{{$tagValue}}"
          {{- end }}
          }
          {{- if $cmValue.indexHint }}
          {{- if kindIs "string" $cmValue.indexHint }}
          index-hint = "{{$cmValue.indexHint}}"
          {{- else }}
          index-hint {
          {{- range $hintKey, $hintValue := $cmValue.indexHint }}
            "{{$hintKey}}" = {{$hintValue}}
          {{- end }}
          }
          {{- end }}
          {{- end }}
        }
        {{- end }}
      }

      custom-aggregation-metrics {
        {{- range $camKey, $camValue := .Values.thingsSearch.config.operatorMetrics.customAggregationMetrics }}
        {{$camKey}} = {
          enabled = {{$camValue.enabled}}
          {{- if $camValue.scrapeInterval }}
          scrape-interval = "{{$camValue.scrapeInterval}}"
          {{- end }}
          namespaces = [
          {{- range $index, $namespace := $camValue.namespaces }}
            "{{$namespace}}"
          {{- end }}
          ]
          group-by {
          {{- range $gbKey, $gbValue := $camValue.groupBy }}
            {{$gbKey}} = "{{$gbValue}}"
          {{- end }}
          }
          tags {
          {{- range $tagKey, $tagValue := $camValue.tags }}
            {{$tagKey}} = "{{$tagValue}}"
          {{- end }}
          }
          filter = "{{$camValue.filter}}"
          {{- if $camValue.indexHint }}
          {{- if kindIs "string" $camValue.indexHint }}
          index-hint = "{{$camValue.indexHint}}"
          {{- else }}
          index-hint {
          {{- range $hintKey, $hintValue := $camValue.indexHint }}
            "{{$hintKey}}" = {{$hintValue}}
          {{- end }}
          }
          {{- end }}
          {{- end }}
        }
        {{- end }}
      }
    }
  }
}
