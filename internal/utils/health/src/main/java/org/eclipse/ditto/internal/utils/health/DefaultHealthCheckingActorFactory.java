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
package org.eclipse.ditto.internal.utils.health;

import java.util.LinkedHashMap;

import javax.annotation.Nullable;

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
     * @param otherProps props of other child actors.
     * @return the Akka configuration Props object.
     */
    public static Props props(final HealthCheckingActorOptions options, @Nullable final Props persistenceCheckerProps,
            final Props... otherProps) {
        final LinkedHashMap<String, Props> childActorProps = new LinkedHashMap<>();
        if (options.isPersistenceCheckEnabled() && persistenceCheckerProps != null) {
            childActorProps.put(PERSISTENCE_LABEL, persistenceCheckerProps);
        }
        for (final Props props : otherProps) {
            childActorProps.put(props.actorClass().getSimpleName(), props);
        }
        return CompositeCachingHealthCheckingActor.props(childActorProps, options.getInterval(),
                options.isHealthCheckEnabled());
    }

}
