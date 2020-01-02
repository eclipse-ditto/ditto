/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.gateway.streaming.actors;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.query.criteria.Criteria;

/**
 * Package-private store of the needed information about a streaming session of a single streaming type.
 */
final class StreamingSession {

    private final List<String> namespaces;
    @Nullable private final Criteria eventFilterCriteria;
    @Nullable private final JsonFieldSelector extraFields;

    private StreamingSession(final List<String> namespaces, @Nullable final Criteria eventFilterCriteria,
            @Nullable final JsonFieldSelector extraFields) {
        this.namespaces = namespaces;
        this.eventFilterCriteria = eventFilterCriteria;
        this.extraFields = extraFields;
    }

    static StreamingSession of(final List<String> namespaces, @Nullable final Criteria eventFilterCriteria,
            @Nullable final JsonFieldSelector extraFields) {

        return new StreamingSession(namespaces, eventFilterCriteria, extraFields);
    }

    /**
     * @return namespaces of the session.
     */
    public List<String> getNamespaces() {
        return namespaces;
    }

    /**
     * @return filter criteria of the session if any is given.
     */
    public Optional<Criteria> getEventFilterCriteria() {
        return Optional.ofNullable(eventFilterCriteria);
    }

    /**
     * @return extra fields of the session if any is given.
     */
    public Optional<JsonFieldSelector> getExtraFields() {
        return Optional.ofNullable(extraFields);
    }
}
