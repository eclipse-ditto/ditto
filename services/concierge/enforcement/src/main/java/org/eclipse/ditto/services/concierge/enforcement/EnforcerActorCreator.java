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
package org.eclipse.ditto.services.concierge.enforcement;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Creator for Actors that enforce authorization.
 */
public final class EnforcerActorCreator {

    private EnforcerActorCreator() {
        throw new AssertionError();
    }

    /**
     * Creates Akka configuration object Props for this EnforcerActor. Caution: The actor does not terminate itself
     * after a period of inactivity.
     *
     * @param pubSubMediator Akka pub sub mediator.
     * @param enforcementProviders a set of {@link EnforcementProvider}s.
     * @param askTimeout the ask timeout duration: the duration to wait for entity shard regions.
     * @param conciergeForwarder an actorRef to concierge forwarder.
     * @param enforcerExecutor the Executor to run async tasks on during enforcement.
     * @return the Akka configuration Props object.
     */
    public static Props props(final ActorRef pubSubMediator,
            final Set<EnforcementProvider<?>> enforcementProviders,
            final Duration askTimeout,
            final ActorRef conciergeForwarder,
            final Executor enforcerExecutor) {

        return props(pubSubMediator, enforcementProviders, askTimeout, conciergeForwarder, enforcerExecutor,
                null, null);
    }

    /**
     * Creates Akka configuration object Props for this EnforcerActor.
     *
     * @param pubSubMediator Akka pub sub mediator.
     * @param enforcementProviders a set of {@link EnforcementProvider}s.
     * @param askTimeout the ask timeout duration: the duration to wait for entity shard regions.
     * @param conciergeForwarder an actorRef to concierge forwarder.
     * @param enforcerExecutor the Executor to run async tasks on during enforcement.
     * @param preEnforcer a function executed before actual enforcement, may be {@code null}.
     * @param activityCheckInterval how often to check for actor activity for termination after an idle period.
     * @return the Akka configuration Props object.
     */
    // TODO: consolidate with EnforcerActor.
    public static Props props(final ActorRef pubSubMediator,
            final Set<EnforcementProvider<?>> enforcementProviders,
            final Duration askTimeout,
            final ActorRef conciergeForwarder,
            final Executor enforcerExecutor,
            @Nullable final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer,
            @Nullable final Duration activityCheckInterval) {

        return EnforcerActor.props(pubSubMediator, enforcementProviders, askTimeout, conciergeForwarder,
                enforcerExecutor, preEnforcer, activityCheckInterval);
    }

}
