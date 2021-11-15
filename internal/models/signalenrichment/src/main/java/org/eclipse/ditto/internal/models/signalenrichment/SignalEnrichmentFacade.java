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
package org.eclipse.ditto.internal.models.signalenrichment;

import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.ThingId;

/**
 * Asynchronous interface for retrieving things to enrich signals from and to those things,
 * either by request-response, by caching or by any other source of information.
 */
public interface SignalEnrichmentFacade {

    /**
     * Retrieve parts of a thing.
     *
     * @param thingId ID of the thing.
     * @param jsonFieldSelector the selected fields of the thing or {@code null} if the complete thing should be
     * retrieved.
     * @param dittoHeaders Ditto headers containing authorization information.
     * @param concernedSignal the Signal which caused that this partial thing retrieval was triggered
     * (e.g. a {@code ThingEvent})
     * @return future that completes with the parts of a thing or fails with an error.
     */
    CompletionStage<JsonObject> retrievePartialThing(ThingId thingId, @Nullable JsonFieldSelector jsonFieldSelector,
            DittoHeaders dittoHeaders, @Nullable Signal<?> concernedSignal);

}
