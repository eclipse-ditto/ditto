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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.model.amqpbridge.MappingContext;

import akka.actor.ExtendedActorSystem;
import scala.Tuple2;
import scala.collection.JavaConversions;
import scala.reflect.ClassTag;
import scala.util.Try;

/**
 * Encapsulates responsibility for instantiating {@link PayloadMapper} objects.
 */
public class PayloadMapperFactory {

    /**
     * The actor system used for dynamic class instantiation.
     */
    private final ExtendedActorSystem actorSystem;

    /**
     * The class scanned for static {@link PayloadMapper} factory functions.
     * Might be useful to accept an array of those in future.
     */
    private final Class<?> factoryClass;


    /**
     * Constructor
     * @param actorSystem the actor system used for dynamic class instantiation
     * @param factoryClass the factory class scanned for factory functions
     */
    public PayloadMapperFactory(final ExtendedActorSystem actorSystem, final Class<?> factoryClass) {
        this.actorSystem = actorSystem;
        this.factoryClass = factoryClass;
    }


    /**
     * Tries to match a factory function for the specified context and uses this to instantiate a mapper.
     * When no factory function is found, the contexts mapping engine name is interpreted as a canonical class name
     * which is used to dynamically instantiate a mapper.
     *
     * @param mappingContext the mapping context
     * @return the instantiated mapper, if an instantiation mechanism matched.
     * @throws InvocationTargetException if a factory function matched, but could not be invoked.
     * @throws IllegalAccessException if a factory function matched, but is not accessible.
     * @throws InstantiationException if a class matched, but mapper instantiation failed.
     * @throws ClassCastException if a class matched but does not conform to the {@link PayloadMapper} interface.
     */
    public Optional<PayloadMapper> findAndCreateInstanceFor(final MappingContext mappingContext)
            throws InvocationTargetException, IllegalAccessException, InstantiationException {
        Optional<PayloadMapper> mapper = findFactoryMethodAndCreateInstanceFor(mappingContext);
        if (!mapper.isPresent()) {
            mapper = findClassAndCreateInstanceFor(mappingContext);
        }
        return mapper;
    }


    /**
     * Tries to match a factory function for the specified context and uses this to instantiate a mapper.
     *
     * @param mappingContext the mapping context
     * @return the instantiated mapper, if a factory function matched.
     * @throws InvocationTargetException if a factory function matched, but could not be invoked.
     * @throws IllegalAccessException if a factory function matched, but is not accessible.
     */
    public Optional<PayloadMapper> findFactoryMethodAndCreateInstanceFor(final MappingContext mappingContext)
            throws IllegalAccessException, InvocationTargetException {
        final Optional<Method> factoryMethod = findPayloadMapperFactoryMethod(factoryClass, mappingContext);
        if (!factoryMethod.isPresent()) {
            return Optional.empty();
        }

        final PayloadMapperOptions options = createOptions(mappingContext);
        final PayloadMapper mapper = (PayloadMapper) factoryMethod.get().invoke(null, options);
        return Optional.of(mapper);
    }


    /**
     * Interprets the mapping engine name as a canonical class name which is used to dynamically instantiate a mapper.
     *
     * @param mappingContext the mapping context
     * @return the instantiated mapper, if a class matched.
     * @throws InstantiationException if a class matched, but mapper instantiation failed.
     * @throws ClassCastException if a class matched but does not conform to the {@link PayloadMapper} interface.
     */
    public Optional<PayloadMapper> findClassAndCreateInstanceFor(final MappingContext mappingContext)
            throws InstantiationException
    {
        final PayloadMapperOptions options = createOptions(mappingContext);
        try {
            return Optional.of(createConfiguredInstanceFor(mappingContext.getMappingEngine(), options));
        } catch (ClassNotFoundException e) {
            // means there is no mapper
            return Optional.empty();
        }
    }


    private PayloadMapper createConfiguredInstanceFor(final String className, final PayloadMapperOptions options)
            throws ClassNotFoundException, InstantiationException
    {
        final PayloadMapper instance = createInstanceFor(className);
        instance.configure(options);
        return instance;
    }


    private PayloadMapper createInstanceFor(final String className) throws ClassNotFoundException,
            InstantiationException
    {
        final ClassTag<PayloadMapper> tag = scala.reflect.ClassTag$.MODULE$.apply(PayloadMapper.class);
        final List<Tuple2<Class<?>, Object>> constructorArgs = new ArrayList<>();

        final Try<PayloadMapper> payloadMapperImpl = this.actorSystem.dynamicAccess()
                .createInstanceFor(className, JavaConversions.asScalaBuffer(constructorArgs).toList(), tag);

        if (payloadMapperImpl.isFailure()) {
            final Throwable error = payloadMapperImpl.failed().get();
            if (error.getClass().isAssignableFrom(ClassNotFoundException.class)) {
                throw (ClassNotFoundException) error;
            } else if (error.getClass().isAssignableFrom(InstantiationException.class)) {
                throw (InstantiationException) error;
            } else if (error.getClass().isAssignableFrom(ClassCastException.class)) {
                throw (ClassCastException) error;
            } else {
                //TODO: cast all possible exceptions to their appropriate type
                throw new IllegalStateException("No throw handling for exception found", error);
            }
        }

        return payloadMapperImpl.get();
    }


    private static Optional<Method> findPayloadMapperFactoryMethod(final Class<?> factory, final MappingContext ctx) {
        return Arrays.stream(factory.getDeclaredMethods())
                .filter(PayloadMapperFactory::isFactoryMethod)
                .filter(m -> m.getName().toLowerCase().contains(ctx.getMappingEngine().toLowerCase()))
                .findFirst();
    }


    private static boolean isFactoryMethod(final Method m) {
        return m.getReturnType().equals(PayloadMapper.class)
                && m.getParameterTypes().length == 1
                && m.getParameterTypes()[0].isAssignableFrom(PayloadMapperOptions.class);
    }


    private static PayloadMapperOptions createOptions(final MappingContext ctx) {
        final Map<String, String> optionsMap = ctx.getOptions();
        final PayloadMapperOptions.Builder optionsBuilder =
                PayloadMappers.createMapperOptionsBuilder(optionsMap);
        return optionsBuilder.build();
    }
}
