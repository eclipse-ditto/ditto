/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import java.util.Set;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.EventSeq;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.GlobalEventRegistry;
import org.eclipse.ditto.base.service.config.DittoServiceConfig;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;
import org.eclipse.ditto.connectivity.service.config.DefaultConnectionConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.AbstractMongoEventAdapter;
import org.eclipse.ditto.json.JsonObject;
import org.slf4j.LoggerFactory;

/**
 * EventAdapter for {@link ConnectivityEvent}s persisted into the pekko-persistence event-journal: converts the journal
 * JSON to MongoDB BSON and back; the domain hooks (field encryption, connection migration) are in
 * {@link ConnectivityEventSerializer}, which logs the encryption settings under this class's logger.
 */
public final class ConnectivityMongoEventAdapter extends AbstractMongoEventAdapter<ConnectivityEvent<?>> {

    private final ConnectivityEventSerializer serializer;

    public ConnectivityMongoEventAdapter(final ExtendedActorSystem system) {
        super(system, GlobalEventRegistry.getInstance(), DefaultConnectionConfig.of(
                        DittoServiceConfig.of(DefaultScopedConfig.dittoScoped(system.settings().config()), "connectivity"))
                .getEventConfig());
        serializer =
                new ConnectivityEventSerializer(system, LoggerFactory.getLogger(ConnectivityMongoEventAdapter.class));
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
