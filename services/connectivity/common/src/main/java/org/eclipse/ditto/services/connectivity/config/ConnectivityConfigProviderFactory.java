/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

/*
 * Copyright Bosch.IO GmbH 2020
 *
 * All rights reserved, also regarding any disposal, exploitation,
 * reproduction, editing, distribution, as well as in the event of
 * applications for industrial property rights.
 *
 * This software is the confidential and proprietary information
 * of Bosch.IO GmbH. You shall not disclose
 * such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you
 * entered into with Bosch.IO GmbH.
 */

package org.eclipse.ditto.services.connectivity.config;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.atteo.classindex.ClassIndex;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;
import akka.actor.DynamicAccess;
import akka.actor.ExtendedActorSystem;
import scala.Tuple2;
import scala.jdk.javaapi.CollectionConverters;
import scala.reflect.ClassTag;
import scala.reflect.ClassTag$;
import scala.util.Try;

/**
 * Factory to instantiate new {@link ConnectivityConfigProvider}s.
 */
public final class ConnectivityConfigProviderFactory {

    /**
     * If this config property is {@code false} then {@code #getInstance} will throw an exception if no config
     * provider other than the {@link #DEFAULT_CONNECTIVITY_CONFIG_PROVIDER_CLASS} is found.
     */
    private static final String DEFAULT_CONFIG_PROVIDER_CONFIG = "ditto.connectivity.default-config-provider";

    private static final Class<DittoConnectivityConfigProvider>
            DEFAULT_CONNECTIVITY_CONFIG_PROVIDER_CLASS = DittoConnectivityConfigProvider.class;
    private static final String CONNECTIVITY_CONFIG_PROVIDER_MISSING = "connectivity.config.provider.missing";
    private static final String CONNECTIVITY_CONFIG_PROVIDER_FAILED = "connectivity.config.provider.failed";

    /**
     * Creates a new instance of a {@link ConnectivityConfigProvider} by looking up implementation via {@code
     * ClassIndex}.
     *
     * @param actorSystem required to instantiate the provider via reflection
     * @return the new instance of the {@link ConnectivityConfigProvider}
     */
    public static ConnectivityConfigProvider getInstance(final ActorSystem actorSystem) {

        final Config config = actorSystem.settings().config();
        final boolean loadDefaultProvider = config.getBoolean(DEFAULT_CONFIG_PROVIDER_CONFIG);

        final Class<? extends ConnectivityConfigProvider> providerClass =
                findProviderClass(c -> filterDefaultProvider(c, loadDefaultProvider));

        try {
            final ClassTag<ConnectivityConfigProvider> tag =
                    ClassTag$.MODULE$.apply(ConnectivityConfigProvider.class);
            final Tuple2<Class<?>, Object> args = new Tuple2<>(ActorSystem.class, actorSystem);
            final DynamicAccess dynamicAccess = ((ExtendedActorSystem) actorSystem).dynamicAccess();
            final Try<ConnectivityConfigProvider> providerBox = dynamicAccess.createInstanceFor(providerClass,
                    CollectionConverters.asScala(Collections.singleton(args)).toList(), tag);
            return providerBox.get();
        } catch (final Exception e) {
            throw configProviderInstantiationFailed(providerClass, e);
        }

    }

    private static Class<? extends ConnectivityConfigProvider> findProviderClass(
            final Predicate<Class<? extends ConnectivityConfigProvider>> classPredicate) {
        final Iterable<Class<? extends ConnectivityConfigProvider>> subclasses =
                ClassIndex.getSubclasses(ConnectivityConfigProvider.class);

        final List<Class<? extends ConnectivityConfigProvider>> candidates =
                StreamSupport.stream(subclasses.spliterator(), false)
                        .filter(classPredicate)
                        .collect(Collectors.toList());

        if (candidates.size() == 1) {
            return candidates.get(0);
        } else {
            throw ConnectivityConfigProviderFactory.configProviderNotFound(candidates);
        }
    }

    private static boolean filterDefaultProvider(final Class<? extends ConnectivityConfigProvider> c,
            final boolean loadDefaultProvider) {
        if (!loadDefaultProvider) {
            return !DEFAULT_CONNECTIVITY_CONFIG_PROVIDER_CLASS.equals(c);
        }
        return true;
    }

    private static DittoRuntimeException configProviderNotFound(
            final List<Class<? extends ConnectivityConfigProvider>> candidates) {
        final String message =
                String.format("Failed to find a suitable ConnectivityConfigProvider implementation in candidates: %s.",
                        candidates);
        return DittoRuntimeException.newBuilder(CONNECTIVITY_CONFIG_PROVIDER_MISSING,
                HttpStatusCode.INTERNAL_SERVER_ERROR)
                .message(message)
                .build();
    }

    private static DittoRuntimeException configProviderInstantiationFailed(final Class<?
            extends ConnectivityConfigProvider> c, final Exception cause) {
        return DittoRuntimeException.newBuilder(CONNECTIVITY_CONFIG_PROVIDER_FAILED,
                HttpStatusCode.INTERNAL_SERVER_ERROR)
                .message(String.format("Failed to instantiate %s.", c.getName()))
                .cause(cause)
                .build();
    }

}
