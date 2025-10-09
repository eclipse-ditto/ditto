# Ditto "Things" configuration extension file to be placed at /opt/ditto/things-extension.conf
ditto {
  entity-creation {
    grant = [
    {{- range $grantIdx, $grant := .Values.things.config.entityCreation.grants }}
      {
        resource-types = ["thing"]
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
        thing-definitions = [
        {{- range $subjectIdx, $thingDefinition := $grant.thingDefinitions }}
          {{- if $thingDefinition }}
          "{{$thingDefinition}}"
          {{- else }}
          null
          {{- end }}
        {{- end }}
        ]
      }
    {{- end }}
    ]
    revoke = [
    {{- range $revokeIdx, $revoke := .Values.things.config.entityCreation.revokes }}
      {
        resource-types = ["thing"]
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
        thing-definitions = [
        {{- range $subjectIdx, $thingDefinition := $revoke.thingDefinitions }}
          {{- if $thingDefinition }}
          "{{$thingDefinition}}"
          {{- else }}
          null
          {{- end }}
        {{- end }}
        ]
      }
    {{- end }}
    ]
  }
  things {
    thing {
      event {
        historical-headers-to-persist = [
        {{- range $index, $header := .Values.things.config.persistence.events.historicalHeadersToPersist }}
          "{{$header}}"
        {{- end }}
        ]
        pre-defined-extra-fields = [
        {{- range $index, $extraFieldConfig := .Values.things.config.event.preDefinedExtraFields }}
          {
            namespaces = [
            {{- range $index, $namespace := $extraFieldConfig.namespaces }}
              "{{$namespace}}"
            {{- end }}
            ]
            {{- if $extraFieldConfig.condition }}
            condition = "{{$extraFieldConfig.condition}}"
            {{- end }}
            extra-fields = [
            {{- range $index, $extraField := $extraFieldConfig.extraFields }}
              "{{$extraField}}"
            {{- end }}
            ]
          }
        {{- end }}
        ]
      }
      message {
        pre-defined-extra-fields = [
        {{- range $index, $extraFieldConfig := .Values.things.config.message.preDefinedExtraFields }}
          {
            namespaces = [
            {{- range $index, $namespace := $extraFieldConfig.namespaces }}
              "{{$namespace}}"
            {{- end }}
            ]
            {{- if $extraFieldConfig.condition }}
            condition = "{{$extraFieldConfig.condition}}"
            {{- end }}
            extra-fields = [
            {{- range $index, $extraField := $extraFieldConfig.extraFields }}
              "{{$extraField}}"
            {{- end }}
            ]
          }
        {{- end }}
        ]
      }
    }

    wot {
      to-thing-description {
        json-template {{ .Values.things.config.wot.tdJsonTemplate | indent 8 }}
      }

      tm-model-validation {
        dynamic-configuration = [
        {{- range $dynConfIdx, $dynamicWotTmValidationConfig := .Values.things.config.wot.tmValidation.dynamicConfig }}
          {
            validation-context {
            {{- if $dynamicWotTmValidationConfig.validationContext.dittoHeadersPatterns }}
            {{- if gt (len $dynamicWotTmValidationConfig.validationContext.dittoHeadersPatterns) 0 }}
              ditto-headers-patterns = [
              {{- range $dhpIdx, $dittoHeadersPatterns := $dynamicWotTmValidationConfig.validationContext.dittoHeadersPatterns }}
                {
                  {{- range $dhpKey, $dhpVal := $dittoHeadersPatterns }}
                  {{$dhpKey}} = "{{$dhpVal}}"
                  {{- end }}
                }
              {{- end }}
              ]
            {{- end }}
            {{- end }}
            {{- if $dynamicWotTmValidationConfig.validationContext.thingDefinitionPatterns }}
            {{- if gt (len $dynamicWotTmValidationConfig.validationContext.thingDefinitionPatterns) 0 }}
              thing-definition-patterns = [
              {{- range $tdpIdx, $thingDefinitionPattern := $dynamicWotTmValidationConfig.validationContext.thingDefinitionPatterns }}
                "{{$thingDefinitionPattern}}"
              {{- end }}
              ]
            {{- end }}
            {{- end }}
            {{- if $dynamicWotTmValidationConfig.validationContext.featureDefinitionPatterns }}
            {{- if gt (len $dynamicWotTmValidationConfig.validationContext.featureDefinitionPatterns) 0 }}
              feature-definition-patterns = [
              {{- range $fdpIdx, $featureDefinitionPattern := $dynamicWotTmValidationConfig.validationContext.featureDefinitionPatterns }}
                "{{$featureDefinitionPattern}}"
              {{- end }}
              ]
            {{- end }}
            {{- end }}
            }
            config-overrides {
            {{- range $configOverridesKey, $configOverridesValue := $dynamicWotTmValidationConfig.configOverrides }}
            {{- if or (eq (kindOf $configOverridesValue) "map") (eq (kindOf $configOverridesValue) "slice") }}
              {{$configOverridesKey}} {
              {{- range $nested1ConfigOverridesKey, $nested1ConfigOverridesValue := $configOverridesValue }}
              {{- if or (eq (kindOf $nested1ConfigOverridesValue) "map") (eq (kindOf $nested1ConfigOverridesValue) "slice") }}
                {{$nested1ConfigOverridesKey}} {
                {{- range $nested2ConfigOverridesKey, $nested2ConfigOverridesValue := $nested1ConfigOverridesValue }}
                {{- if not (kindIs "invalid" $nested2ConfigOverridesValue) }}
                  {{$nested2ConfigOverridesKey}} = {{$nested2ConfigOverridesValue}}
                {{- end }}
                {{- end }}
                }
              {{- else }}
                {{- if not (kindIs "invalid" $nested1ConfigOverridesValue) }}
                  {{$nested1ConfigOverridesKey}} = {{$nested1ConfigOverridesValue}}
                {{- end }}
              {{- end }}
              {{- end }}
              }
            {{- else }}
              {{- if not (kindIs "invalid" $configOverridesValue) }}
              {{$configOverridesKey}} = {{$configOverridesValue}}
              {{- end }}
            {{- end }}
            {{- end }}
            }
          }
        {{- end }}
        ]
      }
    }

  }
}
