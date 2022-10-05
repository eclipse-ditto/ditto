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
package org.eclipse.ditto.connectivity.service.mapping;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.MappingContext;
import org.eclipse.ditto.connectivity.model.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.PayloadMappingDefinition;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.json.JsonObject;

import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.event.LoggingAdapter;

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

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(DefaultMessageMapperFactory.class);
    /**
     * The actor system used for dynamic class instantiation.
     */
    private final ExtendedActorSystem actorSystem;

    private final Connection connection;
    private final ConnectivityConfig connectivityConfig;

    /**
     * The factory function that creates instances of {@link MessageMapper}.
     */
    private final MessageMapperExtension messageMapperExtension;
    private final Map<String, MessageMapper> messageMappers;

    private final LoggingAdapter log;

    private DefaultMessageMapperFactory(final Connection connection,
            final ConnectivityConfig connectivityConfig,
            final ExtendedActorSystem actorSystem,
            final LoggingAdapter log) {

        this.connection = checkNotNull(connection, "connection");
        this.connectivityConfig = checkNotNull(connectivityConfig, "connectivityConfig");
        this.actorSystem = checkNotNull(actorSystem);
        this.log = checkNotNull(log);

        messageMapperExtension = loadMessageMapperExtension(actorSystem);
        messageMappers = loadPayloadMapperProvider(actorSystem).getMessageMappers();
    }

    private static MessageMapperExtension loadMessageMapperExtension(final ActorSystem actorSystem) {
        final var extensionsConfig = ScopedConfig.dittoExtension(actorSystem.settings().config());
        return MessageMapperExtension.get(actorSystem, extensionsConfig);
    }

    private static MessageMapperProvider loadPayloadMapperProvider(final ActorSystem actorSystem) {
        final var extensionsConfig = ScopedConfig.dittoExtension(actorSystem.settings().config());
        return MessageMapperProvider.get(actorSystem, extensionsConfig);
    }

    /**
     * Creates a new factory and returns the instance
     *
     * @param connection the connection
     * @param connectivityConfig the effective connectivity config for the connection.
     * @param actorSystem the actor system to use for mapping config + dynamicAccess.
     * @param log the log adapter used for debug and warning logs.
     * @return the new instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DefaultMessageMapperFactory of(final Connection connection,
            final ConnectivityConfig connectivityConfig,
            final ActorSystem actorSystem,
            final LoggingAdapter log) {

        final ExtendedActorSystem extendedActorSystem = (ExtendedActorSystem) actorSystem;
        return new DefaultMessageMapperFactory(connection, connectivityConfig, extendedActorSystem, log);
    }

    @Override
    public Optional<MessageMapper> mapperOf(final String mapperId, final MappingContext mappingContext) {
        final Optional<MessageMapper> mapper = createMessageMapperInstance(mappingContext.getMappingEngine());
        final Map<String, String> configuredIncomingConditions = mappingContext.getIncomingConditions();
        final Map<String, String> configuredOutgoingConditions = mappingContext.getOutgoingConditions();
        final JsonObject defaultOptions =
                mapper.map(MessageMapper::getDefaultOptions).orElse(JsonObject.empty());
        final MergedJsonObjectMap configuredAndDefaultOptions =
                mergeMappingOptions(defaultOptions, mappingContext.getOptionsAsJson());
        final MessageMapperConfiguration options =
                DefaultMessageMapperConfiguration.of(mapperId, configuredAndDefaultOptions,
                        configuredIncomingConditions, configuredOutgoingConditions);
        if (mapper.isEmpty()) {
            LOGGER.info("Mapper <{}> with mapping engine <{}> not found.", mapperId, mappingContext.getMappingEngine());
        }
        return mapper.map(WrappingMessageMapper::wrap).flatMap(m -> configureInstance(m, options));
    }

    private static MergedJsonObjectMap mergeMappingOptions(final JsonObject defaultOptions,
            final JsonObject configuredOptions) {
        return MergedJsonObjectMap.of(configuredOptions, defaultOptions);
    }

    @Override
    public MessageMapperRegistry registryOf(final MappingContext defaultContext,
            final PayloadMappingDefinition payloadMappingDefinition) {

        final var defaultMapper = mapperOf("default", defaultContext)
                .orElseThrow(() -> new IllegalArgumentException("No default mapper found: " + defaultContext));

        final var mappersFromConnectionConfig =
                instantiateMappers(payloadMappingDefinition.getDefinitions().entrySet().stream());

        final var fallbackMappers = instantiateMappers(
                new LinkedHashSet<>(messageMappers.values())
                        .stream()
                        .filter(mm -> !mm.isConfigurationMandatory())
                        .map(MessageMapper::getAlias)
                        .map(DefaultMessageMapperFactory::getEmptyMappingContextForAlias)
        );

        return DefaultMessageMapperRegistry.of(defaultMapper, mappersFromConnectionConfig, fallbackMappers);
    }

    private static Map.Entry<String, MappingContext> getEmptyMappingContextForAlias(final String alias) {
        final MappingContext emptyMappingContext =
                ConnectivityModelFactory.newMappingContextBuilder(alias, JsonObject.empty()).build();
        return new SimpleImmutableEntry<>(alias, emptyMappingContext);
    }

    private Map<String, MessageMapper> instantiateMappers(final Stream<Map.Entry<String, MappingContext>> definitions) {
        return definitions
                .map(e -> {
                    final String alias = e.getKey();
                    final MessageMapper messageMapper =
                            mapperOf(alias, e.getValue()).orElse(null);
                    return new SimpleImmutableEntry<>(alias, messageMapper);
                })
                .filter(e -> null != e.getValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Instantiates a mapper for the specified mapping context.
     *
     * @return the instantiated mapper if it can be instantiated from the configured factory class.
     */
    Optional<MessageMapper> createMessageMapperInstance(final String mappingEngine) {
        return Optional.ofNullable(messageMappers.get(mappingEngine))
                .map(MessageMapper::createNewMapperInstance)
                .map(messageMapper -> messageMapperExtension.apply(connection.getId(), messageMapper));
    }

    private Optional<MessageMapper> configureInstance(final MessageMapper mapper,
            final MessageMapperConfiguration options) {
        try {
            mapper.configure(connection, connectivityConfig, options, actorSystem);
            return Optional.of(mapper);
        } catch (final MessageMapperConfigurationInvalidException e) {
            log.warning("Failed to apply configuration <{}> to mapper instance <{}>: {}", options, mapper,
                    e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DefaultMessageMapperFactory that = (DefaultMessageMapperFactory) o;
        return Objects.equals(connection, that.connection) &&
                Objects.equals(connectivityConfig, that.connectivityConfig) &&
                Objects.equals(actorSystem, that.actorSystem) &&
                Objects.equals(messageMapperExtension, that.messageMapperExtension) &&
                Objects.equals(log, that.log);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connection, connectivityConfig, actorSystem, messageMapperExtension, log);
    }

}
