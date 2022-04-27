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
package org.eclipse.ditto.internal.utils.protocol;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.HeaderDefinition;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.internal.utils.protocol.config.ProtocolConfig;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapter;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

import akka.actor.ActorSystem;
import akka.actor.DynamicAccess;
import akka.actor.ExtendedActorSystem;
import scala.Tuple2;
import scala.jdk.javaapi.CollectionConverters;
import scala.reflect.ClassTag;
import scala.reflect.ClassTag$;
import scala.util.Try;

/**
 * Interface for loading protocol adapter at runtime.
 */
public abstract class ProtocolAdapterProvider {

    private final ProtocolConfig protocolConfig;

    /**
     * This constructor is the obligation of all subclasses of {@code ProtocolAdapterProvider}.
     *
     * @param protocolConfig the configuration settings of Ditto protocol adaption.
     */
    protected ProtocolAdapterProvider(final ProtocolConfig protocolConfig) {
        this.protocolConfig = checkNotNull(protocolConfig, "ProtocolConfig");
    }

    /**
     * Loads the configured {@code ProtocolAdapterProvider} by reflection.
     * This calls the 1-argument constructor every subclass of ProtocolAdapterProvider should implement.
     *
     * @param protocolConfig provides the class name of the ProtocolAdapterProvider to be loaded.
     * @param actorSystem Akka actor system to perform reflection with.
     * @return the loaded protocol adapter provider.
     */
    public static ProtocolAdapterProvider load(final ProtocolConfig protocolConfig, final ActorSystem actorSystem) {
        final String className = protocolConfig.getProviderClassName();
        final ClassTag<ProtocolAdapterProvider> tag = ClassTag$.MODULE$.apply(ProtocolAdapterProvider.class);
        final List<Tuple2<Class<?>, Object>> constructorArgs =
                Collections.singletonList(new Tuple2<>(ProtocolConfig.class, protocolConfig));
        final DynamicAccess dynamicAccess = ((ExtendedActorSystem) actorSystem).dynamicAccess();
        final Try<ProtocolAdapterProvider> providerBox = dynamicAccess.createInstanceFor(className,
                CollectionConverters.asScala(constructorArgs).toList(), tag);

        return providerBox.get();
    }

    /**
     * Gets a protocol adapter which is appropriate for the client specified by the given {@code userAgent}.
     *
     * @param userAgent the user-agent header provided by the client
     * @return the protocol adapter.
     */
    public abstract ProtocolAdapter getProtocolAdapter(@Nullable String userAgent);

    /**
     * Gets a header translator to filter incoming HTTP headers for the protocol adapter.
     *
     * @return the header translator.
     */
    public HeaderTranslator getHttpHeaderTranslator() {
        return createHttpHeaderTranslatorInstance(protocolConfig);
    }

    protected abstract HeaderTranslator createHttpHeaderTranslatorInstance(ProtocolConfig protocolConfig);

    /**
     * Header definition for headers ignored by Ditto.
     */
    @AllValuesAreNonnullByDefault
    protected static final class Ignored implements HeaderDefinition {

        private final String key;

        /**
         * Create an ignored header.
         *
         * @param key the ignored header key.
         */
        public Ignored(final String key) {
            this.key = key;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public Class<?> getJavaType() {
            return Object.class;
        }

        @Override
        public Class<?> getSerializationType() {
            return getJavaType();
        }

        @Override
        public boolean shouldReadFromExternalHeaders() {
            return false;
        }

        @Override
        public boolean shouldWriteToExternalHeaders() {
            return false;
        }

        @Override
        public void validateValue(@Nullable final CharSequence value) {
            // do nothing
        }

        @Override
        public String toString() {
            return getKey();
        }

    }

}
