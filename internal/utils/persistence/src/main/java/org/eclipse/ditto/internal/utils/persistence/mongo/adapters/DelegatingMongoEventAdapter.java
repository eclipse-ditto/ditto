/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.mongo.adapters;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.EventAdapter;
import org.apache.pekko.persistence.journal.EventSeq;
import org.apache.pekko.persistence.journal.Tagged;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.internal.utils.persistence.api.serializer.EventSerializer;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

import scala.Tuple2;
import scala.jdk.javaapi.CollectionConverters;
import scala.reflect.ClassTag;
import scala.reflect.ClassTag$;
import scala.util.Try;

/**
 * MongoDB event adapter that delegates the whole domain&harr;{@link JsonObject} serialization to a backend-neutral
 * {@link EventSerializer} and adds only the BSON envelope: {@link #toJournal(Object)} encodes the serializer's
 * JsonObject to BSON and attaches the serializer's journal tags; {@link #fromJournal(Object, String)} decodes a stored
 * {@link BsonValue} back to a JsonObject for the serializer.
 * <p>
 * A concrete adapter is a one-line subclass naming its serializer class; the serializer is instantiated by name
 * through its public {@code (ExtendedActorSystem)} constructor, so this module does not compile against the service
 * modules that provide the serializers.
 */
public abstract class DelegatingMongoEventAdapter implements EventAdapter {

    private final EventSerializer<?> eventSerializer;

    /**
     * Constructs the adapter and instantiates its serializer by class name.
     *
     * @param system the actor system to instantiate the serializer with.
     * @param eventSerializerClassName the fully qualified name of an {@link EventSerializer} subclass with a public
     * {@code (ExtendedActorSystem)} constructor.
     * @throws IllegalArgumentException if the serializer cannot be instantiated.
     */
    protected DelegatingMongoEventAdapter(final ExtendedActorSystem system, final String eventSerializerClassName) {
        eventSerializer = instantiateSerializer(system, eventSerializerClassName, getClass());
    }

    /**
     * Constructs the adapter around an existing serializer.
     *
     * @param eventSerializer the serializer to delegate to.
     */
    protected DelegatingMongoEventAdapter(final EventSerializer<?> eventSerializer) {
        this.eventSerializer = eventSerializer;
    }

    @Override
    public String manifest(final Object event) {
        return eventSerializer.manifest(event);
    }

    @Override
    public Object toJournal(final Object event) {
        if (event instanceof Event<?> theEvent) {
            final JsonObject jsonObject = eventSerializer.toJournalJson(theEvent);
            final BsonDocument bson = DittoBsonJson.getInstance().parse(jsonObject);
            return new Tagged(bson, eventSerializer.getJournalTags(theEvent));
        } else {
            throw new IllegalArgumentException("Unable to toJournal a non-'Event' object! Was: " + event.getClass());
        }
    }

    @Override
    public EventSeq fromJournal(final Object event, @Nullable final String manifest) {
        if (event instanceof BsonValue bsonValue) {
            final JsonValue jsonValue = DittoBsonJson.getInstance().serialize(bsonValue);
            return eventSerializer.fromJournalJson(jsonValue.asObject(), manifest);
        } else {
            throw new IllegalArgumentException(
                    "Unable to fromJournal a non-'BsonValue' object! Was: " + event.getClass());
        }
    }

    private static EventSerializer<?> instantiateSerializer(final ExtendedActorSystem system, final String className,
            final Class<?> adapterClass) {
        final List<Tuple2<Class<?>, Object>> constructorArgs =
                Collections.singletonList(new Tuple2<>(ExtendedActorSystem.class, system));
        final Try<EventSerializer<?>> serializer = system.dynamicAccess().createInstanceFor(className,
                CollectionConverters.asScala(constructorArgs).toSeq(), eventSerializerClassTag());
        if (serializer.isFailure()) {
            throw new IllegalArgumentException("Could not instantiate EventSerializer <" + className +
                    "> for event adapter " + adapterClass.getName(), serializer.failed().get());
        }
        return serializer.get();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ClassTag<EventSerializer<?>> eventSerializerClassTag() {
        return (ClassTag) ClassTag$.MODULE$.apply(EventSerializer.class);
    }

}
