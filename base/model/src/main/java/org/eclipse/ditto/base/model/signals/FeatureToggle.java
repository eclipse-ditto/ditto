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
}
