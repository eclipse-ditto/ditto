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
package org.eclipse.ditto.things.service.persistence.serializer;

import java.time.Instant;
import java.util.Optional;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.base.api.persistence.PersistenceLifecycle;
import org.eclipse.ditto.base.api.persistence.SnapshotTaken;
import org.eclipse.ditto.base.model.entity.Revision;
import org.eclipse.ditto.internal.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.internal.utils.persistence.mongo.AbstractMongoSnapshotAdapter;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.api.ThingSnapshotTaken;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingLifecycle;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;

/**
 * A {@link org.eclipse.ditto.internal.utils.persistence.SnapshotAdapter} for snapshotting a
 * {@link org.eclipse.ditto.things.model.Thing}.
 */
@ThreadSafe
public final class ThingMongoSnapshotAdapter extends AbstractMongoSnapshotAdapter<Thing> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThingMongoSnapshotAdapter.class);

    private final ActorRef pubSubMediator;

    /**
     * Constructs a new {@code ThingMongoSnapshotAdapter}.
     *
     * @param pubSubMediator Akka pubsub mediator with which to publish snapshot events.
     */
    public ThingMongoSnapshotAdapter(final ActorRef pubSubMediator) {
        super(LOGGER);
        this.pubSubMediator = pubSubMediator;
    }

    @Override
    protected Thing createJsonifiableFrom(final JsonObject jsonObject) {
        return ThingsModelFactory.newThing(jsonObject);
    }

    @Override
    protected boolean isDeleted(final Thing snapshotEntity) {
        return snapshotEntity.hasLifecycle(ThingLifecycle.DELETED);
    }

    @Override
    protected JsonField getDeletedLifecycleJsonField() {
        final var field = Thing.JsonFields.LIFECYCLE;
        return JsonField.newInstance(field.getPointer().getRoot().orElseThrow(),
                JsonValue.of(ThingLifecycle.DELETED.name()), field);
    }

    @Override
    protected Optional<JsonField> getRevisionJsonField(final Thing entity) {
        final var field = Thing.JsonFields.REVISION;
        return entity.getRevision().map(revision ->
                JsonField.newInstance(field.getPointer().getRoot().orElseThrow(), JsonValue.of(revision.toLong())));
    }

    @Override
    protected void onSnapshotStoreConversion(final Thing thing, final JsonObject thingJson) {
        final Optional<ThingId> thingId = thing.getEntityId();
        if (thingId.isPresent()) {
            final var thingSnapshotTaken = ThingSnapshotTaken.newBuilder(thingId.get(),
                            thing.getRevision().map(Revision::toLong).orElse(0L),
                            thing.getLifecycle()
                                    .map(ThingLifecycle::name)
                                    .flatMap(PersistenceLifecycle::forName)
                                    .orElse(PersistenceLifecycle.ACTIVE),
                            thingJson)
                    .timestamp(Instant.now())
                    .build();
            publishThingSnapshotTaken(thingSnapshotTaken);
        } else {
            LOGGER.warn("Could not publish snapshot taken event for thing <{}>.", thing);
        }
    }

    private void publishThingSnapshotTaken(final SnapshotTaken<ThingSnapshotTaken> snapshotTakenEvent) {
        final var publish = DistPubSubAccess.publishViaGroup(snapshotTakenEvent.getPubSubTopic(), snapshotTakenEvent);
        pubSubMediator.tell(publish, ActorRef.noSender());
    }

}
