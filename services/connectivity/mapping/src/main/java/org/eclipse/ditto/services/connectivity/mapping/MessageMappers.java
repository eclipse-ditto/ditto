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

import java.util.Collections;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.services.connectivity.mapping.javascript.JavaScriptMessageMapperConfiguration;
import org.eclipse.ditto.services.connectivity.mapping.javascript.JavaScriptMessageMapperFactory;

import akka.actor.DynamicAccess;
import akka.actor.ExtendedActorSystem;
import scala.collection.immutable.List$;
import scala.reflect.ClassTag;
import scala.util.Try;

/**
 * Factory for creating known {@link MessageMapper} instances and helpers useful for {@link MessageMapper}
 * implementations.
 */
@Immutable
public final class MessageMappers implements MessageMapperInstantiation {

    /**
     * Constructs a new {@code MessageMappers} object.
     */
    public MessageMappers() {
        super();
    }

    /**
     * Factory method for a Rhino mapper.
     *
     * @return the mapper.
     */
    public static MessageMapper createJavaScriptMessageMapper() {
        return JavaScriptMessageMapperFactory.createJavaScriptMessageMapperRhino();
    }

    /**
     * Creates a new {@link JavaScriptMessageMapperConfiguration.Builder}.
     *
     * @return the builder.
     */
    public static JavaScriptMessageMapperConfiguration.Builder createJavaScriptMapperConfigurationBuilder() {
        return createJavaScriptMapperConfigurationBuilder(Collections.emptyMap());
    }

    /**
     * Creates a new {@link JavaScriptMessageMapperConfiguration.Builder} with options.
     *
     * @param options configuration properties to initialize the builder with.
     * @return the builder.
     */
    public static JavaScriptMessageMapperConfiguration.Builder createJavaScriptMapperConfigurationBuilder(
            final Map<String, String> options) {

        return JavaScriptMessageMapperFactory.createJavaScriptMessageMapperConfigurationBuilder(options);
    }

    /**
     * Creates a Rhino mapper if the mapping engine is 'javascript', dynamically instantiates the mapper if the mapping
     * engine is a class name on the class-path, or {@code null} otherwise.
     *
     * @param connectionId ID of the connection or {@code null} as it is not used by this method.
     * @param mappingContext the mapping context that configures the mapper.
     * @param actorSystem actor system the message mapper is created for.
     * @return the created message mapper instance or {@code null}.
     * @throws NullPointerException if {@code mappingContext} or {@code actorSystem} is {@code null}.
     */
    @Nullable
    @Override
    public MessageMapper apply(@Nullable final EntityId connectionId, final MappingContext mappingContext,
            final ExtendedActorSystem actorSystem) {

        final String mapperName = checkNotNull(mappingContext, "MappingContext").getMappingEngine();
        if ("javascript".equalsIgnoreCase(mapperName)) {
            return createJavaScriptMessageMapper();
        }

        return createAnyMessageMapper(mapperName, checkNotNull(actorSystem, "ActorSystem").dynamicAccess());
    }

    /**
     * Try to create an instance of any message mapper class on the class-path.
     *
     * @param className name of the message mapper class.
     * @return a new instance of the message mapper class if the mapper can be found and instantiated, or null
     * otherwise.
     */
    @Nullable
    private static MessageMapper createAnyMessageMapper(final String className, final DynamicAccess dynamicAccess) {
        final ClassTag<MessageMapper> tag = scala.reflect.ClassTag$.MODULE$.apply(MessageMapper.class);
        final Try<MessageMapper> mapperTry = dynamicAccess.createInstanceFor(className, List$.MODULE$.empty(), tag);

        if (mapperTry.isFailure()) {
            final Throwable error = mapperTry.failed().get();
            if (error instanceof ClassNotFoundException || error instanceof InstantiationException ||
                    error instanceof ClassCastException) {
                return null;
            } else {
                throw new IllegalStateException("There was an unknown error when trying to creating instance for '"
                        + className + "'", error);
            }
        }

        return mapperTry.get();
    }

}
