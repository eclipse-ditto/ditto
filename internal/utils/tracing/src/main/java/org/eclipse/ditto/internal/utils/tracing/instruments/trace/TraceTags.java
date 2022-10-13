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
        final T result;
        if (null == correlationId) {
            result = self();
        } else {
            result = tag(TracingTags.CORRELATION_ID, correlationId.toString());
        }
        return result;
    }

    /**
     * Adds a {@link TracingTags#CONNECTION_ID} tag to the trace.
     *
     * @param connectionId the connection ID to add to the trace
     * @return the new TaggedMetricInstrument instance containing the tag
     */
    default T connectionId(@Nullable final CharSequence connectionId) {
        final T result;
        if (null == connectionId) {
            result = self();
        } else {
            result = tag(TracingTags.CONNECTION_ID, connectionId.toString());
        }
        return result;
    }

    /**
     * Puts the specified entity ID as value for {@link TracingTags#ENTITY_ID} tag to the trace.
     *
     * @param entityId the entity ID to put to the trace.
     * @return this instance to allow method chaining.
     */
    default T entityId(@Nullable final CharSequence entityId) {
        final T result;
        if (null == entityId) {
            result = self();
        } else {
            result = tag(TracingTags.ENTITY_ID, entityId.toString());
        }
        return result;
    }

}
