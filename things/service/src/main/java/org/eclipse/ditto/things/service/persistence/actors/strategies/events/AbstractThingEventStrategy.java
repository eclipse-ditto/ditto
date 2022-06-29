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
package org.eclipse.ditto.things.service.persistence.actors.strategies.events;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.entity.metadata.MetadataBuilder;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingBuilder;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingModifiedEvent;

/**
 * This abstract implementation of {@code EventStrategy} checks if the Thing to be handled is {@code null}.
 * If the Thing is {@code null} the {@code handle} method returns with {@code null}; otherwise a ThingBuilder will be
 * derived from the Thing with the revision and modified timestamp set.
 * This builder is then passed to the {@link #applyEvent(T, org.eclipse.ditto.things.model.ThingBuilder.FromCopy)}
 * method for further handling.
 * However, sub-classes are free to implement the {@code handle} method directly and thus completely circumvent the
 * {@code applyEvent} method.
 *
 * @param <T> the type of the handled ThingEvent.
 */
@Immutable
abstract class AbstractThingEventStrategy<T extends ThingEvent<T>> implements EventStrategy<T, Thing> {

    /**
     * Constructs a new {@code AbstractThingEventStrategy} object.
     */
    protected AbstractThingEventStrategy() {
        super();
    }

    @Nullable
    @Override
    public Thing handle(final T event, @Nullable final Thing thing, final long revision) {
        if (null != thing) {
            final ThingBuilder.FromCopy thingBuilder = thing.toBuilder()
                    .setRevision(revision)
                    .setModified(event.getTimestamp().orElse(null))
                    .setMetadata(mergeMetadata(thing, event));
            return applyEvent(event, thingBuilder).build();
        }
        return null;
    }

    @Nullable
    protected Metadata mergeMetadata(@Nullable final Thing thing, final T event) {

        final JsonPointer eventMetadataResourcePath = event.getResourcePath();
        final Optional<Metadata> eventMetadataOpt = event.getMetadata();
        final Optional<Metadata> thingMetadata = Optional.ofNullable(thing).flatMap(Thing::getMetadata);
        final MetadataBuilder metadataBuilder =
                thingMetadata.map(Metadata::toBuilder).orElseGet(Metadata::newBuilder);

        if (eventMetadataResourcePath.isEmpty() && eventMetadataOpt.isPresent()) {
            return eventMetadataOpt.get();
        } else if (eventMetadataOpt.isPresent() && thing != null) {
            final Metadata eventMetadata = eventMetadataOpt.get();

            return deleteMetadataForMergeAndModifiedEvents(event,
                            metadataBuilder.set(eventMetadataResourcePath, eventMetadata.toJson()));
        } else if (thingMetadata.isPresent()){
            return deleteMetadataForMergeAndModifiedEvents(event, metadataBuilder);
        } else {
            return null;
        }
    }

    /**
     * Apply the specified event to the also specified ThingBuilder.
     * The builder has already the specified revision set as well as the event's timestamp.
     *
     * @param event the ThingEvent<?> to be applied.
     * @param thingBuilder builder which is derived from the {@code event}'s Thing with the revision and event
     * timestamp already set.
     * @return the updated {@code thingBuilder} after applying {@code event}.
     */
    protected ThingBuilder.FromCopy applyEvent(final T event, final ThingBuilder.FromCopy thingBuilder) {
        return thingBuilder;
    }

    private Metadata deleteMetadataForMergeAndModifiedEvents(final T event, final MetadataBuilder metadataBuilder) {
        if (event instanceof ThingModifiedEvent && event.getCommandCategory().equals(Command.Category.DELETE)) {
            return metadataBuilder.remove(event.getResourcePath()).build();
        } else if (event instanceof ThingModifiedEvent && event.getCommandCategory().equals(Command.Category.MERGE)) {
            final Optional<JsonValue> optionalJsonValue = event.getEntity();
            if (optionalJsonValue.isEmpty() || optionalJsonValue.get().isNull()) {
                return metadataBuilder.remove(event.getResourcePath()).build();
            } else if (optionalJsonValue.get().isObject()) {
                final JsonObject jsonObject = optionalJsonValue.get().asObject();
                final Set<JsonKey> jsonKeysForNullValue = jsonObject.getKeys().stream()
                        .filter(jsonKey -> jsonObject.getValue(jsonKey).orElseThrow().isNull())
                        .collect(Collectors.toSet());

                jsonKeysForNullValue.forEach(jsonKey ->
                        metadataBuilder.remove(event.getResourcePath() + "/" + jsonKey.toString()));

                return metadataBuilder.build();
            }
        } else if (event instanceof ThingModifiedEvent && event.getCommandCategory().equals(Command.Category.MODIFY)) {
            final Optional<JsonValue> optionalJsonValue = event.getEntity();
            if (optionalJsonValue.isPresent() && optionalJsonValue.get().isObject()
                    && optionalJsonValue.get().asObject().isEmpty()) {
                return metadataBuilder.set(event.getResourcePath(), JsonFactory.newObject()).build();
            }
        }

        return metadataBuilder.build();
    }

}
