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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.atteo.classindex.ClassIndex;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.MappingContext;
import org.eclipse.ditto.connectivity.model.MessageMapperConfigurationFailedException;
import org.eclipse.ditto.connectivity.model.MessageMapperConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.PayloadMappingDefinition;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.json.JsonObject;

import akka.actor.ActorSystem;
import akka.actor.DynamicAccess;
import akka.actor.ExtendedActorSystem;
import akka.event.LoggingAdapter;
import scala.collection.immutable.List$;
import scala.reflect.ClassTag;
import scala.util.Try;

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
    private final List<MessageMapperExtension> messageMapperExtensions;
    private static final List<Class<? extends MessageMapperExtension>> messageMapperExtensionClasses =
            loadMessageMapperExtensionClasses();

    private static final Map<String, Class<?>> registeredMappers = tryToLoadPayloadMappers();

    private final LoggingAdapter log;

    private DefaultMessageMapperFactory(final Connection connection,
            final ConnectivityConfig connectivityConfig,
            final ExtendedActorSystem actorSystem,
            final List<MessageMapperExtension> messageMapperExtensions,
            final LoggingAdapter log) {

        this.connection = checkNotNull(connection, "connection");
        this.connectivityConfig = checkNotNull(connectivityConfig, "connectivityConfig");
        this.actorSystem = checkNotNull(actorSystem);
        this.messageMapperExtensions = checkNotNull(messageMapperExtensions);
        this.log = checkNotNull(log);
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
        final List<MessageMapperExtension> messageMapperExtensions =
                tryToLoadMessageMappersExtensions(extendedActorSystem);
        return new DefaultMessageMapperFactory(connection, connectivityConfig, extendedActorSystem,
                messageMapperExtensions, log);
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
            LOGGER.info("Mapper {} with mapping engine {} not found.", mapperId, mappingContext.getMappingEngine());
        }
        return mapper.map(WrappingMessageMapper::wrap).flatMap(m -> configureInstance(m, options));
    }

    private MergedJsonObjectMap mergeMappingOptions(final JsonObject defaultOptions,
            final JsonObject configuredOptions) {
        return MergedJsonObjectMap.of(configuredOptions, defaultOptions);
    }

    @Override
    public MessageMapperRegistry registryOf(final MappingContext defaultContext,
            final PayloadMappingDefinition payloadMappingDefinition) {

        final MessageMapper defaultMapper = mapperOf("default", defaultContext)
                .orElseThrow(() -> new IllegalArgumentException("No default mapper found: " + defaultContext));

        final Map<String, MessageMapper> mappersFromConnectionConfig =
                instantiateMappers(payloadMappingDefinition.getDefinitions().entrySet().stream());

        final Map<String, MessageMapper> fallbackMappers =
                instantiateMappers(registeredMappers.entrySet().stream()
                        .filter(requiresNoMandatoryConfiguration())
                        .map(Map.Entry::getKey)
                        .map(this::getEmptyMappingContextForAlias));

        return DefaultMessageMapperRegistry.of(defaultMapper, mappersFromConnectionConfig, fallbackMappers);
    }

    private Map.Entry<String, MappingContext> getEmptyMappingContextForAlias(final String alias) {
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

    private static Map<String, Class<?>> tryToLoadPayloadMappers() {
        try {
            final Iterable<Class<?>> payloadMappers = ClassIndex.getAnnotated(PayloadMapper.class);
            final Map<String, Class<?>> mappers = new HashMap<>();
            for (final Class<?> payloadMapper : payloadMappers) {
                LOGGER.info("Loading payload mapper <{}>.", payloadMapper);
                if (!MessageMapper.class.isAssignableFrom(payloadMapper)) {
                    throw new IllegalStateException("The class " + payloadMapper.getName() + " does not implement " +
                            MessageMapper.class.getName());
                }
                final PayloadMapper annotation = payloadMapper.getAnnotation(PayloadMapper.class);
                if (annotation == null) {
                    throw new IllegalStateException("The mapper " + payloadMapper.getName() + " is not annotated with" +
                            " @PayloadMapper.");
                }
                final String[] aliases = annotation.alias();
                if (aliases.length == 0) {
                    throw new IllegalStateException("No alias configured for " + payloadMapper.getName());
                }

                Stream.of(aliases).forEach(alias -> {
                    final Class<?> existingMapper = mappers.get(alias);
                    if (existingMapper == null) {
                        mappers.put(alias, payloadMapper);
                        LOGGER.info("Registered mapper {} for alias {}.", payloadMapper.getName(), alias);
                    } else if (annotation.priority() == existingMapper.getAnnotation(PayloadMapper.class).priority()) {
                        throw new IllegalStateException("Mapper alias <" + alias + "> was already registered and is " +
                                "tried to register again for " + payloadMapper.getName());
                    } else if (annotation.priority() >
                            existingMapper.getAnnotation(PayloadMapper.class).priority()) {
                        mappers.replace(alias, payloadMapper);
                        LOGGER.info("Replaced mapper {} by higher priority", payloadMapper.getName());
                    } else {
                        LOGGER.info("Skipped registration of mapper {} because of lower priority",
                                payloadMapper.getName());
                    }
                });
            }
            return mappers;
        } catch (final Exception e) {
            final String message = e.getClass().getCanonicalName() + ": " + e.getMessage();
            throw MessageMapperConfigurationFailedException.newBuilder(message).build();
        }
    }

    private static List<MessageMapperExtension> tryToLoadMessageMappersExtensions(
            final ExtendedActorSystem actorSystem) {
        try {
            return loadMessageMapperExtensions(actorSystem.dynamicAccess());
        } catch (final Exception e) {
            final String message = e.getClass().getCanonicalName() + ": " + e.getMessage();
            throw MessageMapperConfigurationFailedException.newBuilder(message).build();
        }
    }

    private static List<MessageMapperExtension> loadMessageMapperExtensions(final DynamicAccess dynamicAccess) {
        return messageMapperExtensionClasses.stream().map(clazz -> {
            final ClassTag<MessageMapperExtension> tag =
                    scala.reflect.ClassTag$.MODULE$.apply(MessageMapperExtension.class);
            return dynamicAccess.createInstanceFor(clazz, List$.MODULE$.empty(), tag).get();
        }).toList();
    }

    private static List<Class<? extends MessageMapperExtension>> loadMessageMapperExtensionClasses() {
        return StreamSupport.stream(ClassIndex.getSubclasses(MessageMapperExtension.class).spliterator(), false)
                .toList();
    }

    /**
     * Instantiates a mapper for the specified mapping context.
     *
     * @return the instantiated mapper if it can be instantiated from the configured factory class.
     */
    Optional<MessageMapper> createMessageMapperInstance(final String mappingEngine) {
        final var connectionId = connection.getId();
        if (registeredMappers.containsKey(mappingEngine)) {
            final Class<?> messageMapperClass = registeredMappers.get(mappingEngine);
            MessageMapper result = createAnyMessageMapper(messageMapperClass,
                    actorSystem.dynamicAccess());
            for (final MessageMapperExtension extension : messageMapperExtensions) {
                if (null == result) {
                    return Optional.empty();
                }
                result = extension.apply(connectionId, result, actorSystem);
            }
            return Optional.ofNullable(result);
        } else {
            log.info("Mapper {} not found in {}.", mappingEngine, registeredMappers);
            return Optional.empty();
        }
    }

    @Nullable
    private static MessageMapper createAnyMessageMapper(final Class<?> clazz,
            final DynamicAccess dynamicAccess) {
        final ClassTag<MessageMapper> tag = scala.reflect.ClassTag$.MODULE$.apply(MessageMapper.class);
        final Try<MessageMapper> mapperTry = dynamicAccess.createInstanceFor(clazz, List$.MODULE$.empty(), tag);

        if (mapperTry.isFailure()) {
            final Throwable error = mapperTry.failed().get();
            if (error instanceof ClassNotFoundException || error instanceof InstantiationException ||
                    error instanceof ClassCastException) {
                LOGGER.warn("Could not instantiate message mapper.", error);
                return null;
            } else {
                throw new IllegalStateException("There was an unknown error when trying to creating instance for '"
                        + clazz + "'", error);
            }
        }
        final MessageMapper messageMapper = mapperTry.get();
        if (messageMapper == null) {
            LOGGER.warn("Could not instantiate message mapper because result was null.");
        }
        return messageMapper;
    }

    private Predicate<? super Map.Entry<String, Class<?>>> requiresNoMandatoryConfiguration() {
        return e -> !getPayloadMapperAnnotation(e).requiresMandatoryConfiguration();
    }

    private static PayloadMapper getPayloadMapperAnnotation(final Map.Entry<String, Class<?>> entry) {
        final Class<?> mapperClass = entry.getValue();
        return mapperClass.getAnnotation(PayloadMapper.class);
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
                Objects.equals(messageMapperExtensions, that.messageMapperExtensions) &&
                Objects.equals(log, that.log);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connection, connectivityConfig, actorSystem, messageMapperExtensions, log);
    }

}
