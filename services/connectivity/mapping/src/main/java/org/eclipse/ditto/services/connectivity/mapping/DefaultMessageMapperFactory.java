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
package org.eclipse.ditto.services.connectivity.mapping;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationFailedException;
import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.PayloadMappingDefinition;

import akka.actor.ActorSystem;
import akka.actor.DynamicAccess;
import akka.actor.ExtendedActorSystem;
import akka.event.DiagnosticLoggingAdapter;
import scala.collection.immutable.List$;
import scala.reflect.ClassTag;

/**
 * Encapsulates responsibility for instantiating {@link MessageMapper} objects.
 * <p>
 * As the message mapper instantiation is usually triggered by an actor, there are only limited possibilities of
 * logging fine grained errors and at the same time keep all responsibility for mapper instantiation behavior away
 * of the actor.
 * </p>
 * <p>
 * Due to this, the factory can be instantiated with a reference to the actors log adapter and will log problems to
 * the debug and warning level (no info and error).
 * Setting a log adapter does not change factory behaviour!
 * </p>
 */
@Immutable
public final class DefaultMessageMapperFactory implements MessageMapperFactory {

    private final ConnectionId connectionId;
    private final MappingConfig mappingConfig;

    /**
     * The actor system used for dynamic class instantiation.
     */
    private final ExtendedActorSystem actorSystem;

    /**
     * The factory function that creates instances of {@link MessageMapper}.
     */
    private final MessageMapperInstantiation messageMappers;

    private final DiagnosticLoggingAdapter log;

    private DefaultMessageMapperFactory(final ConnectionId connectionId,
            final MappingConfig mappingConfig,
            final ExtendedActorSystem actorSystem,
            final MessageMapperInstantiation messageMappers,
            final DiagnosticLoggingAdapter log) {

        this.connectionId = checkNotNull(connectionId);
        this.mappingConfig = checkNotNull(mappingConfig, "MappingConfig");
        this.actorSystem = checkNotNull(actorSystem);
        this.messageMappers = checkNotNull(messageMappers);
        this.log = checkNotNull(log);
    }

    /**
     * Creates a new factory and returns the instance
     *
     * @param connectionId ID of the connection.
     * @param actorSystem the actor system to use for mapping config + dynamicAccess.
     * @param mappingConfig the configuration of the mapping behaviour.
     * @param log the log adapter used for debug and warning logs.
     * @return the new instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DefaultMessageMapperFactory of(final ConnectionId connectionId,
            final ActorSystem actorSystem,
            final MappingConfig mappingConfig,
            final DiagnosticLoggingAdapter log) {

        final ExtendedActorSystem extendedActorSystem = (ExtendedActorSystem) actorSystem;
        final MessageMapperInstantiation messageMappers =
                tryToLoadMessageMappersInstantiation(mappingConfig, extendedActorSystem);

        return new DefaultMessageMapperFactory(connectionId, mappingConfig, extendedActorSystem, messageMappers, log);
    }

    private static MessageMapperInstantiation tryToLoadMessageMappersInstantiation(final MappingConfig mappingConfig,
            final ExtendedActorSystem actorSystem) {

        try {
            return loadMessageMappersInstantiation(mappingConfig.getFactoryName(), actorSystem.dynamicAccess());
        } catch (final Exception e) {
            final String message = e.getClass().getCanonicalName() + ": " + e.getMessage();
            throw MessageMapperConfigurationFailedException.newBuilder(message).build();
        }
    }

    private static MessageMapperInstantiation loadMessageMappersInstantiation(final String className,
            final DynamicAccess dynamicAccess) {

        final ClassTag<MessageMapperInstantiation> tag =
                scala.reflect.ClassTag$.MODULE$.apply(MessageMapperInstantiation.class);
        return dynamicAccess.createInstanceFor(className, List$.MODULE$.empty(), tag).get();
    }

    @Override
    public Optional<MessageMapper> mapperOf(final String mapperId, final MappingContext mappingContext) {
        final Optional<MessageMapper> mapper = createMessageMapperInstance(mappingContext);
        final MessageMapperConfiguration options =
                DefaultMessageMapperConfiguration.of(mapperId, mappingContext.getOptions());
        return mapper.map(m -> configureInstance(m, options) ? m : null);
    }

    /**
     * Instantiates a mapper for the specified mapping context.
     *
     * @param mappingContext the mapping context.
     * @return the instantiated mapper if it can be instantiated from the configured factory class.
     */
    Optional<MessageMapper> createMessageMapperInstance(final MappingContext mappingContext) {
        return Optional.ofNullable(messageMappers.apply(connectionId, mappingContext, actorSystem));
    }

    @Override
    public MessageMapperRegistry registryOf(final MappingContext defaultContext,
            final PayloadMappingDefinition payloadMappingDefinition) {

        final MessageMapper defaultMapper = mapperOf("default", defaultContext)
                .map(WrappingMessageMapper::wrap)
                .orElseThrow(() -> new IllegalArgumentException("No default mapper found: " + defaultContext));

        final Map<String, MessageMapper> mappers = payloadMappingDefinition.getDefinitions()
                .entrySet()
                .stream()
                .map(e -> {
                    final MessageMapper messageMapper =
                            mapperOf(e.getKey(), e.getValue()).map(WrappingMessageMapper::wrap).orElse(null);
                    return new SimpleImmutableEntry<>(e.getKey(), messageMapper);
                })
                .collect(Collectors.toMap(SimpleImmutableEntry::getKey, SimpleImmutableEntry::getValue));

        return DefaultMessageMapperRegistry.of(defaultMapper, mappers);
    }

    private boolean configureInstance(final MessageMapper mapper, final MessageMapperConfiguration options) {
        try {
            mapper.configure(mappingConfig, options);
            return true;
        } catch (final MessageMapperConfigurationInvalidException e) {
            log.warning("Failed to apply configuration <{}> to mapper instance <{}>: {}", options, mapper,
                    e.getMessage());
            return false;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultMessageMapperFactory that = (DefaultMessageMapperFactory) o;
        return Objects.equals(actorSystem, that.actorSystem) &&
                Objects.equals(messageMappers, that.messageMappers) &&
                Objects.equals(log, that.log);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actorSystem, messageMappers, log);
    }

}
