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

import java.util.List;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

/**
 * {@link SignalEnrichmentFacade}, adding functionality to retrieve a whole thing.
 */
public interface CachingSignalEnrichmentFacade extends SignalEnrichmentFacade{

    /**
     * Retrieve thing given a list of thing events.
     *
     * @param thingId the thing to retrieve.
     * @param events received thing events to reduce traffic. If there are no events, a fresh entry is retrieved.
     * @param minAcceptableSeqNr the minimum sequence number acceptable as result. If negative,
     * cache loading is forced.
     * @return future of the retrieved thing.
     */
    CompletionStage<JsonObject> retrieveThing(EntityId thingId,List<ThingEvent<?>> events, long minAcceptableSeqNr);

}
