/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.base.model.signals;

import org.eclipse.ditto.base.model.headers.DittoHeaders;

/**
 * Decides based on system properties whether certain features of Ditto are enabled or throws an
 * {@link UnsupportedSignalException} if a feature is disabled.
 *
 * @since 2.0.0
 */
public final class FeatureToggle {

    /**
     * System property name of the property defining whether the merge feature is enabled.
     */
    public static final String MERGE_THINGS_ENABLED = "ditto.devops.feature.merge-things-enabled";

    /**
     * System property name of the property defining whether the WoT (Web of Things) integration is enabled.
     * @since 2.4.0
     */
    public static final String WOT_INTEGRATION_ENABLED = "ditto.devops.feature.wot-integration-enabled";

    /**
     * System property name of the property defining whether the historical API access is enabled.
     * @since 3.2.0
     */
    public static final String HISTORICAL_APIS_ENABLED = "ditto.devops.feature.historical-apis-enabled";

    /**
     * System property name of the property defining whether the known MQTT headers (e.g., mqtt.topic) are preserved in outgoing message.
     * @since 3.4.0
     */
    public static final String PRESERVE_KNOWN_MQTT_HEADERS_ENABLED = "ditto.devops.feature.preserve-known-mqtt-headers-enabled";

    /**
     * System property name of the property defining whether JSON keys should be validated via the {@code JsonKeyValidator}.
     * As this is quite expensive to do, disabling it might be a good idea for scenarios where it is ensured in a different way
     * that only valid JSON keys are used.
     *
     * @since 3.7.5
     */
    public static final String JSON_KEY_VALIDATION_ENABLED = "ditto.devops.feature.json-key-validation-enabled";

    /**
     * System property name of the property defining whether when tracing is activated span metrics are also recorded
     * and exposed as Prometheus metrics. This is a built-in default of Kamon, Ditto's used tracing library, but can
     * be deactivated to reduce the amount of recorded metrics.
     *
     * @since 3.8.7
     */
    public static final String TRACING_SPAN_METRICS_ENABLED = "ditto.devops.feature.tracing-span-metrics-enabled";

    /**
     * System property name of the property defining whether policy enforcement should use the default throughput
     * optimized policy evaluator implementation or instead the memory optimized one which provides slower policy
     * evaluation but uses less memory.
     *
     * @since 3.8.8
     */
    public static final String POLICY_ENFORCEMENT_USE_THROUGHPUT_OPTIMIZED_EVALUATOR_ENABLED =
            "ditto.devops.feature.policy-enforcement-use-throughput-optimized-evaluator-enabled";

    /**
     * System property name of the property defining whether WoT Thing Description responses are filtered based on
     * the requesting user's policy permissions. When enabled, TDs only contain properties, actions, and events the
     * user is authorized to access. When disabled, TDs are returned unfiltered (legacy behavior).
     *
     * @since 3.9.0
     */
    public static final String WOT_TD_PERMISSION_FILTERING_ENABLED =
            "ditto.devops.feature.wot-td-permission-filtering-enabled";

    /**
     * System property name of the property defining whether {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException}
     * subclasses representing flow-control (HTTP status &lt; 500) suppress their stack trace and suppressed-exception
     * list. When enabled (default), such exceptions degenerate {@code Throwable.<init>} to a few field assignments —
     * eliminating the per-throw {@code fillInStackTrace()} native call and {@code StackTraceElement[]} allocation.
     * When disabled, every Ditto exception captures a writable stack trace as in earlier releases.
     * <p>
     * 5xx ({@link org.eclipse.ditto.base.model.common.HttpStatus#INTERNAL_SERVER_ERROR} and above) are unaffected and
     * always keep their stack trace, regardless of this toggle.
     * <p>
     * Resolved once at JVM start (static-final). Changing the system property after class load has no effect.
     *
     * @since 3.9.0
     */
    public static final String STACKLESS_FLOW_CONTROL_EXCEPTIONS_ENABLED =
            "ditto.devops.feature.stackless-flow-control-exceptions-enabled";

    /**
     * System property name of the property defining whether the policy lockout prevention check performed by
     * {@code PoliciesValidator} is enabled. The check rejects any create or modify operation whose resulting policy
     * lacks at least one permanent subject with {@code WRITE} permission on resource {@code policy:/}.
     * <p>
     * With namespace-scoped root policies (since 3.9.0), the implicitly imported global policy may already supply
     * the required permission, in which case the persistence-side check is overly strict (it does not see the
     * namespace root). Setting this toggle to {@code false} disables the validator's lockout-prevention
     * short-circuit so the policy is accepted regardless of its own subjects' permissions on {@code policy:/}.
     * <p>
     * The enforcement-side check in {@code PolicyCommandEnforcement.authorizeCreatePolicy} is unaffected by this
     * toggle — it operates on an enforcer that already includes namespace-scoped root policies.
     *
     * @since 3.9.1
     */
    public static final String POLICY_LOCKOUT_PREVENTION_ENABLED =
            "ditto.devops.feature.policy-lockout-prevention-enabled";

    /**
     * System property name of the property defining whether the timeseries feature
     * (per-Thing ingest from WoT-annotated property events, RetrieveTimeseries query) is enabled.
     * When disabled, things-service does not start the {@code TimeseriesIngestPublisher}
     * (so no per-event WoT-resolution work on the write path) and gateway-routed
     * {@code RetrieveTimeseries} commands are rejected with {@link UnsupportedSignalException}.
     *
     * @since 4.0.0
     */
    public static final String TIMESERIES_ENABLED = "ditto.devops.feature.timeseries-enabled";

    /**
     * Resolves the system property {@value MERGE_THINGS_ENABLED}.
     */
    private static final boolean IS_MERGE_THINGS_ENABLED = resolveProperty(MERGE_THINGS_ENABLED);

    /**
     * Resolves the system property {@value WOT_INTEGRATION_ENABLED}.
     */
    private static final boolean IS_WOT_INTEGRATION_ENABLED = resolveProperty(WOT_INTEGRATION_ENABLED);

    /**
     * Resolves the system property {@value HISTORICAL_APIS_ENABLED}.
     */
    private static final boolean IS_HISTORICAL_APIS_ENABLED = resolveProperty(HISTORICAL_APIS_ENABLED);

    /**
     * Resolves the system property {@value PRESERVE_KNOWN_MQTT_HEADERS_ENABLED}.
     */
    private static final boolean IS_PRESERVE_KNOWN_MQTT_HEADERS_ENABLED = resolveProperty(PRESERVE_KNOWN_MQTT_HEADERS_ENABLED);

    /**
     * Resolves the system property {@value JSON_KEY_VALIDATION_ENABLED}.
     */
    private static final boolean IS_JSON_KEY_VALIDATION_ENABLED = resolveProperty(JSON_KEY_VALIDATION_ENABLED);

    /**
     * Resolves the system property {@value TRACING_SPAN_METRICS_ENABLED}.
     */
    private static final boolean IS_TRACING_SPAN_METRICS_ENABLED = resolveProperty(TRACING_SPAN_METRICS_ENABLED);

    /**
     * Resolves the system property {@value POLICY_ENFORCEMENT_USE_THROUGHPUT_OPTIMIZED_EVALUATOR_ENABLED}.
     */
    private static final boolean IS_POLICY_ENFORCEMENT_USE_THROUGHPUT_OPTIMIZED_EVALUATOR_ENABLED =
            resolveProperty(POLICY_ENFORCEMENT_USE_THROUGHPUT_OPTIMIZED_EVALUATOR_ENABLED);

    /**
     * Resolves the system property {@value WOT_TD_PERMISSION_FILTERING_ENABLED}.
     */
    private static final boolean IS_WOT_TD_PERMISSION_FILTERING_ENABLED =
            resolveProperty(WOT_TD_PERMISSION_FILTERING_ENABLED);

    /**
     * Resolves the system property {@value STACKLESS_FLOW_CONTROL_EXCEPTIONS_ENABLED}.
     */
    private static final boolean IS_STACKLESS_FLOW_CONTROL_EXCEPTIONS_ENABLED =
            resolveProperty(STACKLESS_FLOW_CONTROL_EXCEPTIONS_ENABLED);

    /**
     * Resolves the system property {@value POLICY_LOCKOUT_PREVENTION_ENABLED}.
     */
    private static final boolean IS_POLICY_LOCKOUT_PREVENTION_ENABLED =
            resolveProperty(POLICY_LOCKOUT_PREVENTION_ENABLED);

    /**
     * Resolves the system property {@value TIMESERIES_ENABLED}.
     */
    private static final boolean IS_TIMESERIES_ENABLED = resolveProperty(TIMESERIES_ENABLED);

    private static boolean resolveProperty(final String propertyName) {
        final String propertyValue = System.getProperty(propertyName, Boolean.TRUE.toString());
        return !Boolean.FALSE.toString().equalsIgnoreCase(propertyValue);
    }

    private FeatureToggle() {
        throw new AssertionError();
    }

    /**
     * Checks if the merge feature is enabled based on the system property {@value MERGE_THINGS_ENABLED}.
     *
     * @param signal the name of the signal that was supposed to be processed
     * @param dittoHeaders headers used to build exception
     * @return the unmodified headers parameters
     * @throws UnsupportedSignalException if the system property
     * {@value MERGE_THINGS_ENABLED} resolves to {@code false}
     */
    public static DittoHeaders checkMergeFeatureEnabled(final String signal, final DittoHeaders dittoHeaders) {
        if (!IS_MERGE_THINGS_ENABLED) {
            throw UnsupportedSignalException
                    .newBuilder(signal)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
        return dittoHeaders;
    }

    /**
     * Checks if the WoT integration feature is enabled based on the system property {@value WOT_INTEGRATION_ENABLED}.
     *
     * @param signal the name of the signal that was supposed to be processed
     * @param dittoHeaders headers used to build exception
     * @return the unmodified headers parameters
     * @throws UnsupportedSignalException if the system property
     * {@value WOT_INTEGRATION_ENABLED} resolves to {@code false}
     * @since 2.4.0
     */
    public static DittoHeaders checkWotIntegrationFeatureEnabled(final String signal, final DittoHeaders dittoHeaders) {
        if (!isWotIntegrationFeatureEnabled()) {
            throw UnsupportedSignalException
                    .newBuilder(signal)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
        return dittoHeaders;
    }

    /**
     * Returns whether the WoT (Web of Things) integration feature is enabled based on the system property
     * {@value WOT_INTEGRATION_ENABLED}.
     *
     * @return whether the WoT integration feature is enabled or not.
     * @since 2.4.0
     */
    public static boolean isWotIntegrationFeatureEnabled() {
        return IS_WOT_INTEGRATION_ENABLED;
    }

    /**
     * Checks if the historical API access feature is enabled based on the system property {@value HISTORICAL_APIS_ENABLED}.
     *
     * @param signal the name of the signal that was supposed to be processed
     * @param dittoHeaders headers used to build exception
     * @return the unmodified headers parameters
     * @throws UnsupportedSignalException if the system property
     * {@value HISTORICAL_APIS_ENABLED} resolves to {@code false}
     * @since 3.2.0
     */
    public static DittoHeaders checkHistoricalApiAccessFeatureEnabled(final String signal, final DittoHeaders dittoHeaders) {
        if (!isHistoricalApiAccessFeatureEnabled()) {
            throw UnsupportedSignalException
                    .newBuilder(signal)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
        return dittoHeaders;
    }

    /**
     * Returns whether the historical API access feature is enabled based on the system property
     * {@value HISTORICAL_APIS_ENABLED}.
     *
     * @return whether the historical API access feature is enabled or not.
     * @since 3.2.0
     */
    public static boolean isHistoricalApiAccessFeatureEnabled() {
        return IS_HISTORICAL_APIS_ENABLED;
    }

    /**
     * Returns whether the known MQTT headers are preserved in outgoing message based on the system property
     * {@value PRESERVE_KNOWN_MQTT_HEADERS_ENABLED}.
     *
     * @return whether the known MQTT headers are preserved or not.
     * @since 3.4.0
     */
    public static boolean isPreserveKnownMqttHeadersFeatureEnabled() {
        return IS_PRESERVE_KNOWN_MQTT_HEADERS_ENABLED;
    }

    /**
     * Returns whether JSON key validation is enabled based on the system property {@value JSON_KEY_VALIDATION_ENABLED}.
     *
     * @return whether JSON key validation is enabled.
     * @since 3.7.5
     */
    public static boolean isJsonKeyValidationEnabled() {
        return IS_JSON_KEY_VALIDATION_ENABLED;
    }

    /**
     * Returns whether tracing span metric reporting is enabled based on the system property
     * {@value TRACING_SPAN_METRICS_ENABLED}.
     *
     * @return whether tracing span metric reporting is enabled.
     * @since 3.8.7
     */
    public static boolean isTracingSpanMetricsEnabled() {
        return IS_TRACING_SPAN_METRICS_ENABLED;
    }

    /**
     * Returns whether for policy enforcement the memory optimized evaluator is used based on the system property
     * {@value POLICY_ENFORCEMENT_USE_THROUGHPUT_OPTIMIZED_EVALUATOR_ENABLED}.
     *
     * @return whether tracing span metric reporting is enabled.
     * @since 3.8.8
     */
    public static boolean isPolicyEnforcementUseThroughputOptimizedEvaluatorEnabled() {
        return IS_POLICY_ENFORCEMENT_USE_THROUGHPUT_OPTIMIZED_EVALUATOR_ENABLED;
    }

    /**
     * Returns whether WoT Thing Description permission filtering is enabled based on the system property
     * {@value WOT_TD_PERMISSION_FILTERING_ENABLED}.
     *
     * @return whether WoT TD permission filtering is enabled.
     * @since 3.9.0
     */
    public static boolean isWotTdPermissionFilteringEnabled() {
        return IS_WOT_TD_PERMISSION_FILTERING_ENABLED;
    }

    /**
     * Returns whether stack-trace suppression for flow-control (HTTP status &lt; 500)
     * {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException} subclasses is enabled, based on the system
     * property {@value STACKLESS_FLOW_CONTROL_EXCEPTIONS_ENABLED}.
     *
     * @return whether stackless flow-control exceptions are enabled.
     * @since 3.9.0
     */
    public static boolean isStacklessFlowControlExceptionsEnabled() {
        return IS_STACKLESS_FLOW_CONTROL_EXCEPTIONS_ENABLED;
    }

    /**
     * Returns whether the policy lockout prevention check in {@code PoliciesValidator} is enabled, based on the system
     * property {@value POLICY_LOCKOUT_PREVENTION_ENABLED}. When {@code false}, the validator's "at least one permanent
     * subject must have WRITE on policy:/" check is bypassed.
     *
     * @return whether policy lockout prevention is enabled.
     * @since 3.9.1
     */
    public static boolean isPolicyLockoutPreventionEnabled() {
        return IS_POLICY_LOCKOUT_PREVENTION_ENABLED;
    }

    /**
     * Checks if the timeseries feature is enabled based on the system property {@value TIMESERIES_ENABLED}.
     *
     * @param signal the name of the signal that was supposed to be processed
     * @param dittoHeaders headers used to build exception
     * @return the unmodified headers parameters
     * @throws UnsupportedSignalException if the system property {@value TIMESERIES_ENABLED}
     * resolves to {@code false}
     * @since 4.0.0
     */
    public static DittoHeaders checkTimeseriesFeatureEnabled(final String signal, final DittoHeaders dittoHeaders) {
        if (!IS_TIMESERIES_ENABLED) {
            throw UnsupportedSignalException
                    .newBuilder(signal)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
        return dittoHeaders;
    }

    /**
     * Returns whether the timeseries feature is enabled based on the system property
     * {@value TIMESERIES_ENABLED}.
     *
     * @return whether the timeseries feature is enabled or not.
     * @since 4.0.0
     */
    public static boolean isTimeseriesFeatureEnabled() {
        return IS_TIMESERIES_ENABLED;
    }
}
