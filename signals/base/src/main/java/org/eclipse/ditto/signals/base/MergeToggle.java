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
package org.eclipse.ditto.signals.base;

/**
 * Decides based on the system property {@value DITTO_FEATURE_MERGE_THINGS_DISABLED} whether the merge thing feature
 * is disabled and throws an {@link org.eclipse.ditto.signals.base.UnsupportedSignalException} if the property is set
 * to {@code true}.
 */
public final class MergeToggle {

    /**
     * System property name of the property defining if the merge feature is disabled.
     */
    private static final String DITTO_FEATURE_MERGE_THINGS_DISABLED = "ditto.feature.merge-things.disabled";

    /**
     * Resolves the system property {@value DITTO_FEATURE_MERGE_THINGS_DISABLED}.
     */
    private static final boolean IS_MERGE_THINGS_DISABLED = Boolean.getBoolean(DITTO_FEATURE_MERGE_THINGS_DISABLED);

    private MergeToggle() {}

    /**
     * Checks if the merge feature is enabled based on the system property {@value DITTO_FEATURE_MERGE_THINGS_DISABLED}.
     *
     * @param signal the name of the signal that was supposed to be processed
     * @throws org.eclipse.ditto.signals.base.UnsupportedSignalException if the system property
     * {@value DITTO_FEATURE_MERGE_THINGS_DISABLED} resolves to {@code true}
     */
    public static void checkMergeFeatureEnabled(final String signal) {
        if (IS_MERGE_THINGS_DISABLED) {
            throw UnsupportedSignalException.newBuilder(signal).build();
        }
    }
}
