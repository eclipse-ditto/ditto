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

import org.eclipse.ditto.internal.utils.metrics.instruments.TaggedMetricInstrument;
import org.eclipse.ditto.internal.utils.tracing.TracingTags;

/**
 * Defines well-known and often used trace tags like {@code correlationId}.
 *
 * @param <T> the type of the implementing class
 */
public interface TraceTags<T extends TaggedMetricInstrument<T>> extends TaggedMetricInstrument<T> {

    default T correlationId(final String correlationId) {
        return tag(TracingTags.CORRELATION_ID, correlationId);
    }

    default T connectionId(final String connectionId) {
        return tag(TracingTags.CONNECTION_ID, connectionId);
    }

    default T connectionType(final String connectionType) {
        return tag(TracingTags.COMMAND_TYPE, connectionType);
    }

}
