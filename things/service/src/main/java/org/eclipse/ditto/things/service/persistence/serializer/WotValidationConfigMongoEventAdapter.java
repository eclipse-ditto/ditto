/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.serializer;

import java.util.Set;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.EventSeq;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.GlobalEventRegistry;
import org.eclipse.ditto.base.service.config.DittoServiceConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.AbstractMongoEventAdapter;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent;
import org.eclipse.ditto.things.service.common.config.DefaultThingConfig;

/**
 * EventAdapter for {@link WotValidationConfigEvent}s persisted into the pekko-persistence event-journal: converts the
 * journal JSON to MongoDB BSON and back; the domain hooks are in {@link WotValidationConfigEventSerializer}.
 */
public final class WotValidationConfigMongoEventAdapter extends AbstractMongoEventAdapter<WotValidationConfigEvent<?>> {

    private final WotValidationConfigEventSerializer serializer;

    /**
     * Constructs a new {@code WotValidationConfigMongoEventAdapter}.
     *
     * @param system the actor system in which to load the extension.
     */
    public WotValidationConfigMongoEventAdapter(final ExtendedActorSystem system) {
        super(system, GlobalEventRegistry.getInstance(), DefaultThingConfig.of(
                DittoServiceConfig.of(DefaultScopedConfig.dittoScoped(system.settings().config()), "things")
        ).getEventConfig());
        serializer = new WotValidationConfigEventSerializer(system);
    }

    @Override
    public JsonObject toJournalJson(final Event<?> event) {
        return serializer.toJournalJson(event);
    }

    @Override
    public Set<String> getJournalTags(final Event<?> event) {
        return serializer.getJournalTags(event);
    }

    @Override
    public EventSeq fromJournalJson(final JsonObject jsonObject, @Nullable final String manifest) {
        return serializer.fromJournalJson(jsonObject, manifest);
    }

    @Override
    public EventSeq fromJournalJson(final JsonObject jsonObject) {
        return serializer.fromJournalJson(jsonObject);
    }

}
