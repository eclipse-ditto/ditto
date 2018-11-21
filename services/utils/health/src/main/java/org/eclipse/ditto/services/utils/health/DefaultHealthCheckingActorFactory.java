/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.health;

import java.util.Collections;
import java.util.LinkedHashMap;

import akka.actor.Props;

/**
 * Provides an actor for checking and caching the health of a ditto service.
 */
public final class DefaultHealthCheckingActorFactory {

    private DefaultHealthCheckingActorFactory() {
        throw new AssertionError();
    }

    /**
     * The actor name of the Actor to be created in the ActorSystem.
     */
    public static final String ACTOR_NAME = AbstractHealthCheckingActor.ACTOR_NAME;

    private static final String PERSISTENCE_LABEL = "persistence";

    /**
     * Creates Akka configuration object Props for a health checking actor.
     *
     * @param options the options to configure this actor.
     * @param persistenceCheckerProps props to create persistence health checkers.
     * @return the Akka configuration Props object
     */
    public static Props props(final HealthCheckingActorOptions options, Props persistenceCheckerProps) {
        final LinkedHashMap<String, Props> childActorProps = new LinkedHashMap<>();
        if (options.isPersistenceCheckEnabled()) {
            childActorProps.put(PERSISTENCE_LABEL, persistenceCheckerProps);
        }
        return CompositeCachingHealthCheckingActor.props(childActorProps, options.getInterval(),
                options.isHealthCheckEnabled());
    }

    /**
     * Creates Akka configuration object Props for a health checking actor that does not check the persistence.
     *
     * @param options the options to configure this actor.
     * @return the Akka configuration Props object
     */
    public static Props propsWithoutPersistence(final HealthCheckingActorOptions options) {
        return CompositeCachingHealthCheckingActor.props(Collections.emptyMap(), options.getInterval(),
                options.isHealthCheckEnabled());
    }

}
