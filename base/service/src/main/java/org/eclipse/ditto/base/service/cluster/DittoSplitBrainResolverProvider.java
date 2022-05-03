/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.service.cluster;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.DowningProvider;
import akka.cluster.sbr.SplitBrainResolverProvider;
import scala.Option;
import scala.concurrent.duration.FiniteDuration;

/**
 * This provider is providing the props of {@link DittoSplitBrainResolver}.
 */
@SuppressWarnings("unused")
final class DittoSplitBrainResolverProvider extends DowningProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(DittoSplitBrainResolverProvider.class);

    private final SplitBrainResolverProvider splitBrainResolverProvider;

    /**
     * This constructor is called by akka.
     *
     * @param actorSystem the actor system used to instantiate this provider.
     */
    @SuppressWarnings("unused")
    DittoSplitBrainResolverProvider(final ActorSystem actorSystem) {
        splitBrainResolverProvider = new SplitBrainResolverProvider(actorSystem);
    }

    @Override
    public FiniteDuration downRemovalMargin() {
        return splitBrainResolverProvider.downRemovalMargin();
    }

    @Override
    public Option<Props> downingActorProps() {
        try {
            final Props splitBrainResolverProps = splitBrainResolverProvider.downingActorProps().get();
            return Option.apply(DittoSplitBrainResolver.props(splitBrainResolverProps));
        } catch (final Exception e) {
            final String msg = "Could not create Ditto split brain resolver props.";
            LOGGER.error(msg, e);
            throw new DittoConfigError(msg, e);
        }
    }

}
