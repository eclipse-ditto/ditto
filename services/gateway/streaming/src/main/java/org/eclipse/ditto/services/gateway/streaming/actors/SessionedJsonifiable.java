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

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.signals.base.Signal;

/**
 * Jsonifiable with the necessary information for enrichment.
 */
@Immutable
public final class JsonifiableWithExtraFields {

    private final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable;
    private final List<String> authorizationSubjects;
    @Nullable private final JsonFieldSelector extraFields;

    private JsonifiableWithExtraFields(
            final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable,
            final List<String> authorizationSubjects,
            @Nullable final JsonFieldSelector extraFields) {
        this.jsonifiable = jsonifiable;
        this.authorizationSubjects = authorizationSubjects;
        this.extraFields = extraFields;
    }

    static JsonifiableWithExtraFields forSignal(final Signal<?> signal, final List<String> authorizationSubjects,
            @Nullable final JsonFieldSelector extraFields) {
        return new JsonifiableWithExtraFields(signal, authorizationSubjects, extraFields);
    }

    static JsonifiableWithExtraFields forError(final DittoRuntimeException error) {
        return new JsonifiableWithExtraFields(error, Collections.emptyList(), null);
    }

    // TODO: equals, hashCode, toString
}
