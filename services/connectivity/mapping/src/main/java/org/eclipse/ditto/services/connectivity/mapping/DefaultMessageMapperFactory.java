/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.connectivity.mapping;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.model.connectivity.MessageMapperConfigurationInvalidException;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;
import akka.actor.DynamicAccess;
import akka.actor.ExtendedActorSystem;
import akka.event.DiagnosticLoggingAdapter;
import scala.Tuple2;
import scala.collection.JavaConversions;
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
 * the debug and warning level (no info and error). Setting a log adapter does not change factory behaviour!
 * <p>
 */
@Immutable
public final class DefaultMessageMapperFactory implements MessageMapperFactory {

    private final Config mappingConfig;

    /**
     * The actor system used for dynamic class instantiation.
     */
    private final DynamicAccess dynamicAccess;

    /**
     * The class scanned for static {@link MessageMapper} factory functions.
     */
    private final Class<?> factoryClass;

    private final DiagnosticLoggingAdapter log;

    /**
     * Constructor
     *
     * @param mappingConfig the static service configuration for mapping related stuff
     * @param dynamicAccess the actor systems dynamic access used for dynamic class instantiation
     * @param factoryClass the factory class scanned for factory functions
     * @param log the log adapter used for debug and warning logs
     */
    private DefaultMessageMapperFactory(final Config mappingConfig, final DynamicAccess dynamicAccess,
            final Class<?> factoryClass, final DiagnosticLoggingAdapter log) {
        this.mappingConfig = checkNotNull(mappingConfig);;
        this.dynamicAccess = checkNotNull(dynamicAccess);
        this.factoryClass = checkNotNull(factoryClass);
        this.log = checkNotNull(log);
    }

    /**
     * Creates a new factory and returns the instance
     *
     * @param actorSystem the actor system to use for mapping config + dynamicAccess
     * @param factoryClass the factory class scanned for factory functions
     * @param log the log adapter used for debug and warning logs
     * @return the new instance
     */
    public static DefaultMessageMapperFactory of(final ActorSystem actorSystem,
            final Class<?> factoryClass, final DiagnosticLoggingAdapter log) {
        final Config mappingConfig = actorSystem.settings().config().getConfig("ditto.connectivity.mapping");
        final DynamicAccess dynamicAccess = ((ExtendedActorSystem) actorSystem).dynamicAccess();
        return new DefaultMessageMapperFactory(mappingConfig, dynamicAccess, factoryClass, log);
    }

    @Override
    public Optional<MessageMapper> mapperOf(final MappingContext mappingContext) {

        Optional<MessageMapper> mapper = Optional.empty();
        try {
            mapper = findFactoryMethodAndCreateInstance(mappingContext);
        } catch (final IllegalAccessException e) {
            log.warning("Failed to load mapper of ctx <{}>! Can't access factory function: {}", mappingContext,
                    e.getMessage());
        } catch (final InvocationTargetException e) {
            log.warning("Failed to load mapper of ctx <{}>! Can't invoke factory function: {}", mappingContext,
                    e.getMessage());
        }

        if (!mapper.isPresent()) {
            try {
                mapper = findClassAndCreateInstance(mappingContext);
            } catch (final InstantiationException e) {
                log.warning("Failed to load mapper of ctx <{}>! Can't instantiate mapper class: {}",
                        mappingContext, e.getMessage());
            } catch (final ClassCastException e) {
                log.warning("Failed to load mapper of ctx <{}>! Class is no MessageMapper: {}",
                        mappingContext, e.getMessage());
            }
        }

        final DefaultMessageMapperConfiguration options =
                DefaultMessageMapperConfiguration.of(mappingContext.getOptions());
        return mapper.map(m -> configureInstance(m, options) ? m : null);
    }

    @Override
    public MessageMapperRegistry registryOf(final MappingContext defaultContext, @Nullable final MappingContext context) {
        final MessageMapper defaultMapper = mapperOf(defaultContext)
                .map(WrappingMessageMapper::wrap)
                .orElseThrow(() ->
                        new IllegalArgumentException("No mapper found for default context: " + defaultContext));

        final MessageMapper messageMapper;
        if (context != null) {
            messageMapper = mapperOf(context)
                    .map(WrappingMessageMapper::wrap).orElse(null);
        } else {
            messageMapper = null;
        }
        return DefaultMessageMapperRegistry.of(defaultMapper, messageMapper);
    }

    /**
     * Tries to match a factory function for the specified context and uses this to instantiate a mapper.
     *
     * @param mappingContext the mapping context
     * @return the instantiated mapper, if a factory function matched.
     * @throws InvocationTargetException if a factory function matched, but could not be invoked.
     * @throws IllegalAccessException if a factory function matched, but is not accessible.
     */
    Optional<MessageMapper> findFactoryMethodAndCreateInstance(final MappingContext mappingContext)
            throws IllegalAccessException, InvocationTargetException {
        final Optional<Method> factoryMethod = findMessageMapperFactoryMethod(factoryClass, mappingContext);
        if (!factoryMethod.isPresent()) {
            log.debug("No factory method found for ctx: <{}>", mappingContext);
            return Optional.empty();
        }

        final MessageMapper mapper = (MessageMapper) factoryMethod.get().invoke(null);
        return Optional.of(mapper);
    }


    /**
     * Interprets the mapping engine name as a canonical class name which is used to dynamically instantiate a mapper.
     *
     * @param mappingContext the mapping context
     * @return the instantiated mapper, if a class matched.
     * @throws InstantiationException if a class matched, but mapper instantiation failed.
     * @throws ClassCastException if a class matched but does not conform to the {@link MessageMapper} interface.
     */
    Optional<MessageMapper> findClassAndCreateInstance(final MappingContext mappingContext)
            throws InstantiationException {
        checkNotNull(mappingContext);

        try {
            return Optional.of(createInstanceFor(mappingContext.getMappingEngine()));
        } catch (final ClassNotFoundException e) {
            log.debug("No mapper class found for ctx: <{}>", mappingContext);
            return Optional.empty();
        }
    }

    private boolean configureInstance(final MessageMapper mapper, final DefaultMessageMapperConfiguration options) {
        try {
            mapper.configure(mappingConfig, options);
            return true;
        } catch (final MessageMapperConfigurationInvalidException e) {
            log.warning("Failed to apply configuration <{}> to mapper instance <{}>: {}", options, mapper,
                    e.getMessage());
            return false;
        }
    }

    private MessageMapper createInstanceFor(final String className) throws ClassNotFoundException,
            InstantiationException {
        final ClassTag<MessageMapper> tag = scala.reflect.ClassTag$.MODULE$.apply(MessageMapper.class);
        final List<Tuple2<Class<?>, Object>> constructorArgs = new ArrayList<>();

        final Try<MessageMapper> mapperTry = this.dynamicAccess
                .createInstanceFor(className, JavaConversions.asScalaBuffer(constructorArgs).toList(), tag);

        if (mapperTry.isFailure()) {
            final Throwable error = mapperTry.failed().get();
            if (error.getClass().isAssignableFrom(ClassNotFoundException.class)) {
                throw (ClassNotFoundException) error;
            } else if (error.getClass().isAssignableFrom(InstantiationException.class)) {
                throw (InstantiationException) error;
            } else if (error.getClass().isAssignableFrom(ClassCastException.class)) {
                throw (ClassCastException) error;
            } else {
                throw new IllegalStateException("There was an unknown error when trying to creating instance for '"
                        + className + "'", error);
            }
        }

        return mapperTry.get();
    }


    private static Optional<Method> findMessageMapperFactoryMethod(final Class<?> factory, final MappingContext ctx) {
        return Arrays.stream(factory.getDeclaredMethods())
                .filter(DefaultMessageMapperFactory::isFactoryMethod)
                .filter(m -> m.getName().toLowerCase().contains(ctx.getMappingEngine().toLowerCase()))
                .findFirst();
    }

    private static boolean isFactoryMethod(final Method m) {
        return m.getReturnType().equals(MessageMapper.class) && m.getParameterTypes().length == 0;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DefaultMessageMapperFactory that = (DefaultMessageMapperFactory) o;
        return Objects.equals(dynamicAccess, that.dynamicAccess) &&
                Objects.equals(factoryClass, that.factoryClass) &&
                Objects.equals(log, that.log);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dynamicAccess, factoryClass, log);
    }
}
