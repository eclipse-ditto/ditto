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
package org.eclipse.ditto.internal.utils.tracing.instruments.trace;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.metrics.instruments.TaggableMetricsInstrument;
import org.eclipse.ditto.internal.utils.tracing.TracingTags;

/**
 * Defines well-known and often used trace tags like {@code correlationId}.
 *
 * @param <T> the type of the implementing class
 */
public interface TraceTags<T extends TaggableMetricsInstrument<T>> extends TaggableMetricsInstrument<T> {

    /**
     * Adds a {@link TracingTags#CORRELATION_ID} tag to the trace.
     *
     * @param correlationId the correlation ID to add to the trace
     * @return the new TaggedMetricInstrument instance containing the tag
     */
    default T correlationId(@Nullable final CharSequence correlationId) {
        if (null == correlationId) {
            return self();
        }
        return tag(TracingTags.CORRELATION_ID, correlationId.toString());
    }

    /**
     * Adds a {@link TracingTags#CONNECTION_ID} tag to the trace.
     *
     * @param connectionId the connection ID to add to the trace
     * @return the new TaggedMetricInstrument instance containing the tag
     */
    default T connectionId(@Nullable final CharSequence connectionId) {
        if (null == connectionId) {
            return self();
        }
        return tag(TracingTags.CONNECTION_ID, connectionId.toString());
    }

    /**
     * Adds a {@link TracingTags#CONNECTION_TYPE} tag to the trace.
     *
     * @param connectionType the connection type to add to the trace
     * @return the new TaggedMetricInstrument instance containing the tag
     */
    default T connectionType(@Nullable final CharSequence connectionType) {
        if (null == connectionType) {
            return self();
        }
        return tag(TracingTags.CONNECTION_TYPE, connectionType.toString());
    }

}
