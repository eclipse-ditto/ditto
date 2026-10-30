---
paths:
  - "base/model/src/main/java/org/eclipse/ditto/base/model/signals/FeatureToggle.java"
  - "internal/utils/config/src/main/resources/ditto-devops.conf"
---

# Feature toggles

A change to existing behavior ships behind a toggle so operators can switch back. The
existing toggles in `FeatureToggle.java` are the template; adding one means:

1. **`FeatureToggle.java`** (`base/model` — public API, Java 8, needs `@since`): a
   `public static final String` with the system property name
   `ditto.devops.feature.<name>-enabled`, a private `IS_..._ENABLED = resolveProperty(...)`,
   and either an `is...Enabled()` accessor or a `check...Enabled(signal, dittoHeaders)` method
   that throws `UnsupportedSignalException` when off. Document in the Javadoc what "off"
   restores.
2. **`internal/utils/config/src/main/resources/ditto-devops.conf`**, under `ditto.devops.feature`:

   ```hocon
   my-feature-enabled = true
   my-feature-enabled = ${?DITTO_DEVOPS_FEATURE_MY_FEATURE_ENABLED}
   ```

3. **`base/service/src/main/java/org/eclipse/ditto/base/service/DittoService.java`**,
   `injectSystemPropertiesLimits`: copy the config value into the system property. This is the
   easy one to forget — `FeatureToggle` reads only system properties, so without this line the
   `.conf` value and its env var have no effect and the toggle is stuck on.
4. **Helm**: `global.featureFlags.myFeatureEnabled` in `values.yaml`, passed as
   `DITTO_DEVOPS_FEATURE_MY_FEATURE_ENABLED` in the deployment template of every service that
   reads it; then the chart `version` bump and `CHANGELOG.md` entry.

`FeatureToggle` resolves each property once, when the class loads; setting the property later
in a running JVM (e.g. inside a test) has no effect.
