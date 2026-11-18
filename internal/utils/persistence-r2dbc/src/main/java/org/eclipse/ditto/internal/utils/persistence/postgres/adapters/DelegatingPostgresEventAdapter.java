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
package org.eclipse.ditto.internal.utils.persistence.postgres.adapters;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.journal.EventAdapter;
import org.apache.pekko.persistence.journal.EventSeq;
import org.apache.pekko.persistence.journal.Tagged;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.internal.utils.persistence.api.serializer.EventSerializer;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;

import scala.Tuple2;
import scala.jdk.javaapi.CollectionConverters;
import scala.reflect.ClassTag;
import scala.reflect.ClassTag$;
import scala.util.Try;

/**
 * PostgreSQL event adapter that delegates the whole domain&harr;{@link JsonObject} serialization to a backend-neutral
 * {@link EventSerializer} and adds only the JSONB envelope: {@link #toJournal(Object)} renders the serializer's
 * JsonObject to JSON text (the value the PostgreSQL journal binds to a {@code ::jsonb} column) and attaches the
 * serializer's journal tags; {@link #fromJournal(Object, String)} decodes the stored JSONB, as the r2dbc driver
 * surfaces it ({@code String}, {@code byte[]} or an already parsed {@link JsonObject}), back to a JsonObject for the
 * serializer.
 * <p>
 * A concrete adapter is a one-line subclass naming its serializer class; the serializer is instantiated by name
 * through its public {@code (ExtendedActorSystem)} constructor, so this module does not compile against the service
 * modules that provide the serializers.
 */
public abstract class DelegatingPostgresEventAdapter implements EventAdapter {

    private final EventSerializer<?> eventSerializer;

    /**
     * Constructs the adapter and instantiates its serializer by class name.
     *
     * @param system the actor system to instantiate the serializer with.
     * @param eventSerializerClassName the fully qualified name of an {@link EventSerializer} subclass with a public
     * {@code (ExtendedActorSystem)} constructor.
     * @throws IllegalArgumentException if the serializer cannot be instantiated.
     */
    protected DelegatingPostgresEventAdapter(final ExtendedActorSystem system, final String eventSerializerClassName) {
        eventSerializer = instantiateSerializer(system, eventSerializerClassName, getClass());
    }

    /**
     * Constructs the adapter around an existing serializer.
     *
     * @param eventSerializer the serializer to delegate to.
     */
    protected DelegatingPostgresEventAdapter(final EventSerializer<?> eventSerializer) {
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
            return new Tagged(jsonObject.toString(), eventSerializer.getJournalTags(theEvent));
        } else {
            throw new IllegalArgumentException("Unable to toJournal a non-'Event' object! Was: " + event.getClass());
        }
    }

    @Override
    public EventSeq fromJournal(@Nullable final Object event, @Nullable final String manifest) {
        return eventSerializer.fromJournalJson(decodeJsonb(event), manifest);
    }

    private static JsonObject decodeJsonb(@Nullable final Object rawEvent) {
        if (rawEvent instanceof JsonObject jsonObject) {
            return jsonObject;
        }
        if (rawEvent instanceof CharSequence charSequence) {
            return JsonFactory.newObject(charSequence.toString());
        }
        if (rawEvent instanceof byte[] bytes) {
            return JsonFactory.newObject(new String(bytes, StandardCharsets.UTF_8));
        }
        throw new IllegalArgumentException("Unable to fromJournal a non JSONB-decodable object (expected JsonObject, "
                + "String or byte[])! Was: " + (rawEvent != null ? rawEvent.getClass() : "null"));
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
