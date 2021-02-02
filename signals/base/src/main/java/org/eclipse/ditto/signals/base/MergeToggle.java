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
 * Decides based on the system property {@value MERGE_THINGS_ENABLED} whether the merge thing feature
 * is enabled and throws an {@link org.eclipse.ditto.signals.base.UnsupportedSignalException} if the property is set
 * to {@code false}.
 */
public final class MergeToggle {

    /**
     * System property name of the property defining whether the merge feature is enabled.
     */
    private static final String MERGE_THINGS_ENABLED = "ditto.devops.feature.merge-things-enabled";

    /**
     * Resolves the system property {@value MERGE_THINGS_ENABLED}.
     */
    private static final boolean IS_MERGE_THINGS_ENABLED = Boolean.getBoolean(MERGE_THINGS_ENABLED);

    private MergeToggle() {}

    /**
     * Checks if the merge feature is enabled based on the system property {@value MERGE_THINGS_ENABLED}.
     *
     * @param signal the name of the signal that was supposed to be processed
     * @throws org.eclipse.ditto.signals.base.UnsupportedSignalException if the system property
     * {@value MERGE_THINGS_ENABLED} resolves to {@code false}
     */
    public static void checkMergeFeatureEnabled(final String signal) {
        if (!IS_MERGE_THINGS_ENABLED) {
            throw UnsupportedSignalException.newBuilder(signal).build();
        }
    }
}
