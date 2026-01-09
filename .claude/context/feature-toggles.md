# Feature Toggle System

**CRITICAL**: New features **which change existing behavior** MUST be added behind feature toggles to reduce 
risk for existing deployments.

## Why Feature Toggles?

Feature toggles allow:
- Safe deployment of incomplete features
- Easy rollback if issues arise
- Gradual rollout to users
- A/B testing capabilities

## Adding a Feature Toggle

Follow these steps to add a new feature toggle:

### 1. Define in FeatureToggle.java

Add to `/base/model/src/main/java/org/eclipse/ditto/base/model/signals/FeatureToggle.java`:

```java
public static final String MY_FEATURE_ENABLED = "ditto.devops.feature.my-feature-enabled";

private static final boolean IS_MY_FEATURE_ENABLED = resolveProperty(MY_FEATURE_ENABLED);

public static DittoHeaders checkMyFeatureEnabled(final String signal, final DittoHeaders dittoHeaders) {
    if (!IS_MY_FEATURE_ENABLED) {
        throw UnsupportedSignalException
                .newBuilder(signal)
                .dittoHeaders(dittoHeaders)
                .build();
    }
    return dittoHeaders;
}
```

### 2. Configure in ditto-devops.conf

Add to `/internal/utils/config/src/main/resources/ditto-devops.conf`:

```hocon
my-feature-enabled = true
my-feature-enabled = ${?DITTO_DEVOPS_FEATURE_MY_FEATURE_ENABLED}
```

The `${?VAR}` syntax means: "use environment variable if set, otherwise keep default value"

### 3. Use in Code

Check the feature toggle before executing feature-specific code:

```java
FeatureToggle.checkMyFeatureEnabled(signal.getType(), dittoHeaders);
```

This will throw `UnsupportedSignalException` if the feature is disabled.

### 4. Configure via Environment

Enable/disable at runtime using environment variables:

```bash
DITTO_DEVOPS_FEATURE_MY_FEATURE_ENABLED=true
```

Or in Docker Compose:

```yaml
environment:
  - DITTO_DEVOPS_FEATURE_MY_FEATURE_ENABLED=false
```

## Existing Feature Toggles

Current feature toggles in the codebase:

- `MERGE_THINGS_ENABLED`: Merge Things feature
- `WOT_INTEGRATION_ENABLED`: Web of Things (WoT) integration
- `HISTORICAL_APIS_ENABLED`: Historical API access
- `PRESERVE_KNOWN_MQTT_HEADERS_ENABLED`: Preserve MQTT headers in outgoing messages
- `JSON_KEY_VALIDATION_ENABLED`: JSON key validation (performance impact)
- `TRACING_SPAN_METRICS_ENABLED`: Span metrics reporting by Kamon
- `POLICY_ENFORCEMENT_USE_THROUGHPUT_OPTIMIZED_EVALUATOR_ENABLED`: Throughput vs memory optimized policy evaluator

## Best Practices

- Always default to `true` for new features in development
- Document the feature toggle purpose in code comments
- Remove feature toggles once the feature is stable and widely adopted
- Use feature toggles for any non-trivial functionality changes
