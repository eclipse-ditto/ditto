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
package org.eclipse.ditto.internal.models.signalenrichment;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * {@link SignalEnrichmentFacade}, adding functionality to retrieve a whole thing.
 */
public interface CachingSignalEnrichmentFacade extends SignalEnrichmentFacade {

    /**
     * Retrieve thing given a list of thing events.
     *
     * @param thingId the thing to retrieve.
     * @param events received thing events to reduce traffic. If there are no events, a fresh entry is retrieved.
     * @param minAcceptableSeqNr the minimum sequence number acceptable as result. If negative,
     * cache loading is forced.
     * @return future of the retrieved thing.
     */
    CompletionStage<JsonObject> retrieveThing(ThingId thingId, List<ThingEvent<?>> events, long minAcceptableSeqNr);

    default JsonObject applyJsonFieldSelector(final JsonObject jsonObject,
            @Nullable final JsonFieldSelector fieldSelector) {
        final JsonObject result;

        if (fieldSelector == null) {
            result = jsonObject;
        } else {
            final Collection<JsonKey> featureIds = jsonObject.getValue(Thing.JsonFields.FEATURES.getPointer())
                    .filter(JsonValue::isObject)
                    .map(JsonValue::asObject)
                    .map(JsonObject::getKeys)
                    .orElse(Collections.emptyList());
            final JsonFieldSelector expandedSelector =
                    ThingsModelFactory.expandFeatureIdWildcards(featureIds, fieldSelector);
            result = jsonObject.get(expandedSelector);
        }

        return result;
    }
}
