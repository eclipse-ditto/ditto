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
package org.eclipse.ditto.services.authorization.util.enforcement;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.services.authorization.util.EntityRegionMap;
import org.eclipse.ditto.services.authorization.util.cache.AuthorizationCaches;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Factory which creates Props for an {@link EnforcerActor}.
 */
public final class EnforcerActorFactory {

    private EnforcerActorFactory() {
        throw new AssertionError();
    }

    /**
     * Creates Akka configuration object Props for this EnforcerActor.
     *
     * @param pubSubMediator Akka pub sub mediator.
     * @param entityRegionMap map from resource types to entity shard regions.
     * @param authorizationCaches cache of information relevant for authorization.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator, final EntityRegionMap entityRegionMap,
            final AuthorizationCaches authorizationCaches) {
        return props(pubSubMediator, entityRegionMap, authorizationCaches, null);
    }


    /**
     * Creates Akka configuration object Props for this EnforcerActor.
     *
     * @param pubSubMediator Akka pub sub mediator.
     * @param entityRegionMap map from resource types to entity shard regions.
     * @param authorizationCaches cache of information relevant for authorization.
     * @param preEnforcer a function executed as pre-enforcement, may be {@code null}.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator, final EntityRegionMap entityRegionMap,
            final AuthorizationCaches authorizationCaches,
            @Nullable final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer) {
        final Set<EnforcementProvider<?>> enforcementProviders = new HashSet<>();
        enforcementProviders.add(new ThingCommandEnforcementProvider());
        enforcementProviders.add(new PolicyCommandEnforcementProvider());
        enforcementProviders.add(new MessageCommandEnforcement.Provider());
        enforcementProviders.add(new LiveSignalEnforcement.Provider());

        return EnforcerActor.props(pubSubMediator, entityRegionMap, authorizationCaches, enforcementProviders,
                preEnforcer);
    }
}
