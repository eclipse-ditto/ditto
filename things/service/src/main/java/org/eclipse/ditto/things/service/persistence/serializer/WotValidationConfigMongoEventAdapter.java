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

import java.util.Collections;
import java.util.Set;

import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.Tagged;
import org.bson.BsonDocument;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.GlobalEventRegistry;
import org.eclipse.ditto.base.service.config.DittoServiceConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.AbstractMongoEventAdapter;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent;
import org.eclipse.ditto.things.service.common.config.DefaultThingConfig;

/**
 * EventAdapter for {@link WotValidationConfigEvent}s persisted into pekko-persistence event-journal.
 * Converts Event to MongoDB BSON objects and vice versa.
 */
public final class WotValidationConfigMongoEventAdapter extends AbstractMongoEventAdapter<WotValidationConfigEvent<?>> {

    private static final String JOURNAL_TAG_PREFIX = "wot-validation-config";
    private static final String JOURNAL_TAG_CREATED = JOURNAL_TAG_PREFIX + "-created";
    private static final String JOURNAL_TAG_MODIFIED = JOURNAL_TAG_PREFIX + "-modified";
    private static final String JOURNAL_TAG_DELETED = JOURNAL_TAG_PREFIX + "-deleted";

    /**
     * Constructs a new {@code WotValidationConfigMongoEventAdapter}.
     *
     * @param system the actor system in which to load the extension.
     */
    public WotValidationConfigMongoEventAdapter(final ExtendedActorSystem system) {
        super(system, GlobalEventRegistry.getInstance(), DefaultThingConfig.of(
                DittoServiceConfig.of(DefaultScopedConfig.dittoScoped(system.settings().config()), "things")
        ).getEventConfig());
    }

    @Override
    public Object toJournal(final Object event) {
        if (event instanceof WotValidationConfigEvent<?> wotEvent) {
            final JsonSchemaVersion schemaVersion = wotEvent.getImplementedSchemaVersion();
            final JsonObject jsonObject = performToJournalMigration(wotEvent,
                    wotEvent.toJson(schemaVersion, FieldType.regularOrSpecial())
            ).build();
            final BsonDocument bson = DittoBsonJson.getInstance().parse(jsonObject);
            return new Tagged(bson, determineJournalTags(wotEvent));
        } else {
            throw new IllegalArgumentException("Unable to toJournal a non-'WotValidationConfigEvent' object! Was: " + 
                    (event != null ? event.getClass() : "null"));
        }
    }

    private Set<String> determineJournalTags(final WotValidationConfigEvent<?> event) {
        final String eventType = event.getType();
        if (eventType.contains("created")) {
            return Collections.singleton(JOURNAL_TAG_CREATED);
        } else if (eventType.contains("modified")) {
            return Collections.singleton(JOURNAL_TAG_MODIFIED);
        } else if (eventType.contains("deleted")) {
            return Collections.singleton(JOURNAL_TAG_DELETED);
        } else {
            return Collections.singleton(JOURNAL_TAG_PREFIX);
        }
    }

    @Override
    protected JsonObjectBuilder performToJournalMigration(final Event<?> event, final JsonObject jsonObject) {
        if (!(event instanceof WotValidationConfigEvent<?>)) {
            throw new IllegalArgumentException("Expected WotValidationConfigEvent but got: " + event.getClass());
        }
        // Currently no custom migration needed for WoT validation config events
        return super.performToJournalMigration(event, jsonObject);
    }
} 