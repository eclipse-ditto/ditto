/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.tracing.span;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.metrics.instruments.TaggableMetricsInstrument;

/**
 * Provides functionality to add tags to tracing span.
 * For convenience, this interface offers methods for adding well-known and often used tags like correlation ID.
 *
 * @param <T> the type of the implementing class.
 */
public interface SpanTagging<T extends TaggableMetricsInstrument<T>> extends TaggableMetricsInstrument<T> {

    /**
     * Adds a {@link SpanTagKey#CORRELATION_ID} tag to the span.
     *
     * @param correlationId the correlation ID to add to the span
     * @return the new TaggedMetricInstrument instance containing the tag.
     */
    default T correlationId(@Nullable final CharSequence correlationId) {
        final T result;
        if (null == correlationId) {
            result = self();
        } else {
            result = tag(SpanTagKey.CORRELATION_ID.getTagForValue(correlationId.toString()));
        }
        return result;
    }

    /**
     * Adds a {@link SpanTagKey#CONNECTION_ID} tag to the span.
     *
     * @param connectionId the connection ID to add to the span.
     * @return the new TaggedMetricInstrument instance containing the tag.
     */
    default T connectionId(@Nullable final CharSequence connectionId) {
        final T result;
        if (null == connectionId) {
            result = self();
        } else {
            result = tag(SpanTagKey.CONNECTION_ID.getTagForValue(connectionId));
        }
        return result;
    }

    /**
     * Puts the specified entity ID as value for {@link SpanTagKey#ENTITY_ID} tag to the span.
     *
     * @param entityId the entity ID to put to the span.
     * @return this instance to allow method chaining.
     */
    default T entityId(@Nullable final CharSequence entityId) {
        final T result;
        if (null == entityId) {
            result = self();
        } else {
            result = tag(SpanTagKey.ENTITY_ID.getTagForValue(entityId));
        }
        return result;
    }

}
