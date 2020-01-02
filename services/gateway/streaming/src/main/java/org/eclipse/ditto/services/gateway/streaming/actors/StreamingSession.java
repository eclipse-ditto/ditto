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

final class StreamSession {

    private final List<String> namespaces;
    @Nullable private final Criteria eventFilterCriteria;
    @Nullable private final JsonFieldSelector extraFields;

    private StreamSession(final List<String> namespaces, @Nullable final Criteria eventFilterCriteria,
            @Nullable final JsonFieldSelector extraFields) {
        this.namespaces = namespaces;
        this.eventFilterCriteria = eventFilterCriteria;
        this.extraFields = extraFields;
    }

    static StreamSession of(final List<String> namespaces, @Nullable final Criteria eventFilterCriteria,
            @Nullable final JsonFieldSelector extraFields) {

        return new StreamSession(namespaces, eventFilterCriteria, extraFields);
    }

    // TODO: javadoc equals hashCode toString

    public List<String> getNamespaces() {
        return namespaces;
    }

    public Optional<Criteria> getEventFilterCriteria() {
        return Optional.ofNullable(eventFilterCriteria);
    }

    public Optional<JsonFieldSelector> getExtraFields() {
        return Optional.ofNullable(extraFields);
    }
}
