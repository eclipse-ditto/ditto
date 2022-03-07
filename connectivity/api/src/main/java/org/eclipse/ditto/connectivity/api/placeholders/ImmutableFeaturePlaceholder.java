/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.api.placeholders;

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithFeatureId;

/**
 * Placeholder implementation that replaces {@code feature:id}. The input value is a String and must be a
 * valid Feature ID.
 *
 * @since 1.5.0
 */
@Immutable
final class ImmutableFeaturePlaceholder implements FeaturePlaceholder {

    private static final String ID_PLACEHOLDER = "id";

    private static final List<String> SUPPORTED = Collections.singletonList(ID_PLACEHOLDER);

    /**
     * Singleton instance of the {@code ImmutableFeaturePlaceholder}.
     */
    static final ImmutableFeaturePlaceholder INSTANCE = new ImmutableFeaturePlaceholder();

    @Override
    public List<String> resolveValues(final Signal<?> signal, final String placeholder) {
        checkNotNull(signal, "signal");
        argumentNotEmpty(placeholder, "placeholder");
        if (ID_PLACEHOLDER.equals(placeholder)) {
            final List<String> featureIds;
            if (signal instanceof WithFeatureId) {
                featureIds = Collections.singletonList(((WithFeatureId) signal).getFeatureId());
            } else {
                featureIds = Collections.emptyList();
            }
            return featureIds;
        }
        return Collections.emptyList();
    }

    @Override
    public String getPrefix() {
        return "feature";
    }

    @Override
    public List<String> getSupportedNames() {
        return SUPPORTED;
    }

    @Override
    public boolean supports(final String name) {
        return SUPPORTED.contains(name);
    }

}
