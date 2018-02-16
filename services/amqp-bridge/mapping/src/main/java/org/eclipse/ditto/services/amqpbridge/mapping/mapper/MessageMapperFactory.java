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
package org.eclipse.ditto.services.amqpbridge.mapping.mapper;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.eclipse.ditto.model.amqpbridge.MappingContext;

import akka.actor.ExtendedActorSystem;
import akka.event.DiagnosticLoggingAdapter;
import scala.Tuple2;
import scala.collection.JavaConversions;
import scala.reflect.ClassTag;
import scala.util.Try;

/**
 * Encapsulates responsibility for instantiating {@link MessageMapper} objects.
 *
 * As the message mapper instantiation is usually triggered by an actor, there are only limitited possiblilities of
 * logging fine grained errors and at the same time keep all responsibility for mapper instantiation behavior away
 * from the actor.
 * Due to this, the factory can be instantiated with a reference to the actors log adapter and will log problems to
 * the debug and warning level (no info and error). Setting a log adapter does not change factory behaviour!
 */
public class MessageMapperFactory {

    /**
     * The actor system used for dynamic class instantiation.
     */
    private final ExtendedActorSystem actorSystem;


    /**
     * The class scanned for static {@link MessageMapper} factory functions.
     * Might be useful to accept an array of those in future.
     */
    private final Class<?> factoryClass;

    private final DiagnosticLoggingAdapter log;


    /**
     * Constructor
     *
     * @param actorSystem the actor system used for dynamic class instantiation
     * @param factoryClass the factory class scanned for factory functions
     */
    public MessageMapperFactory(final ExtendedActorSystem actorSystem, final Class<?> factoryClass, final
    @Nullable DiagnosticLoggingAdapter log) {
        this.actorSystem = actorSystem;
        this.factoryClass = factoryClass;
        //noinspection ConstantConditions
        this.log = log;
    }


    /**
     * Tries to match a factory function for the specified context and uses this to instantiate a mapper.
     * When no factory function is found, the contexts mapping engine name is interpreted as a canonical class name
     * which is used to dynamically instantiate a mapper.
     *
     * @param mappingContext the mapping context
     * @return the instantiated mapper, if an instantiation mechanism matched.
     */
    public Optional<MessageMapper> loadMapper(final MappingContext mappingContext) {

        Optional<MessageMapper> mapper = Optional.empty();
        try {
            mapper = findFactoryMethodAndCreateInstance(mappingContext);
        } catch (IllegalAccessException e) {
            tryLogWarning("Failed to load mapper from ctx <{}>! Can't access factory function: {}", mappingContext,
                    e.getMessage());
        } catch (InvocationTargetException e) {
            tryLogWarning("Failed to load mapper from ctx <{}>! Can't invoke factory function: {}", mappingContext,
                    e.getMessage());
        }

        if (!mapper.isPresent()) {
            try {
                mapper = findClassAndCreateInstance(mappingContext);
            } catch (InstantiationException e) {
                tryLogWarning("Failed to load mapper from ctx <{}>! Can't instantiate mapper class: {}",
                        mappingContext, e.getMessage());
            } catch (ClassCastException e) {
                tryLogWarning("Failed to load mapper from ctx <{}>! Class is no MessageMapper: {}",
                        mappingContext, e.getMessage());
            }
        }

        final MessageMapperConfiguration options = MessageMapperConfiguration.from(mappingContext.getOptions());
        return mapper.map(m -> configureInstance(m, options));
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
            tryLogDebug("No factory method found for ctx: <{}>", mappingContext);
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
        } catch (ClassNotFoundException e) {
            tryLogDebug("No mapper class found for ctx: <{}>", mappingContext);
            return Optional.empty();
        }
    }

    /**
     * Instantiates message mappers from a list of contexts. If there is no mapper for a context or the instantiation
     * process fails, then the context will be ignored and all exceptions are catched and logged! Compare list
     * lengths to ensure everything worked.
     *
     * @param contexts the contexts
     * @return the instantiated mappers
     */
    public List<MessageMapper> loadMappers(final List<MappingContext> contexts) {
        checkNotNull(contexts);

        return contexts.stream().filter(Objects::nonNull)
                .map(this::loadMapper)
                .map(m -> m.orElse(null))
                .filter(Objects::nonNull)
                .peek(m -> tryLogDebug("MessageMapper loaded: <{}>", m))
                .collect(Collectors.toList());
    }

//    --

    public MessageMapperRegistry loadRegistry(final MessageMapper defaultMapper,
            final List<MappingContext> contexts) {
        final List<MessageMapper> mappers = loadMappers(contexts);

        return new MessageMapperRegistry(defaultMapper, mappers);
    }



//    --

    @Nullable
    private MessageMapper configureInstance(@Nonnull final MessageMapper mapper,
            @Nonnull final MessageMapperConfiguration options)
    {
        try {
            mapper.configure(options);
            return mapper;
        } catch (IllegalArgumentException e) {
            tryLogWarning("Failed to apply configuration <{}> to mapper instance <{}>: {}", options, mapper,
                    e.getMessage());
            return null;
        }
    }

    private MessageMapper createInstanceFor(final String className) throws ClassNotFoundException,
            InstantiationException {
        final ClassTag<MessageMapper> tag = scala.reflect.ClassTag$.MODULE$.apply(MessageMapper.class);
        final List<Tuple2<Class<?>, Object>> constructorArgs = new ArrayList<>();

        final Try<MessageMapper> mapperTry = this.actorSystem.dynamicAccess()
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
                //TODO: cast all possible exceptions to their appropriate type, not sure if i got all
                throw new IllegalStateException("No throw handling for exception found", error);
            }
        }

        return mapperTry.get();
    }


    private static Optional<Method> findMessageMapperFactoryMethod(final Class<?> factory, final MappingContext ctx) {
        return Arrays.stream(factory.getDeclaredMethods())
                .filter(MessageMapperFactory::isFactoryMethod)
                .filter(m -> m.getName().toLowerCase().contains(ctx.getMappingEngine().toLowerCase()))
                .findFirst();
    }

    private static boolean isFactoryMethod(final Method m) {
        return m.getReturnType().equals(MessageMapper.class) && m.getParameterTypes().length == 0;
    }

    @SuppressWarnings("ConstantConditions")
    private void tryLogWarning(String template, Object... args) {
        //noinspection ConstantConditions
        if (Objects.isNull(log)) return;
        log.warning(template, args);
    }

    private void tryLogDebug(String template, Object... args) {
        //noinspection ConstantConditions
        if (Objects.isNull(log)) return;
        log.debug(template, args);
    }
}
