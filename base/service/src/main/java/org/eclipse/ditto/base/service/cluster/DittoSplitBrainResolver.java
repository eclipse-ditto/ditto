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

import java.time.Duration;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;

/**
 * This is just a wrapper for the {@link akka.cluster.sbr.SplitBrainResolver akka sbr} with an addition, that this
 * sbr can be turned off/on via {@link ModifySplitBrainResolver} sent as piggyback command to /system/cluster/core/daemon/downingProvider
 */
final class DittoSplitBrainResolver extends AbstractActor {

    private static final String SBR_ENABLING_DELAY = "ditto.cluster.sbr.auto-enable-after";
    private static final Duration AUTO_ENABLE_AFTER_FALLBACK = Duration.ofHours(1);
    private static final ModifySplitBrainResolver ENABLE = ModifySplitBrainResolver.of(true);
    private static final Logger LOGGER = LoggerFactory.getLogger(DittoSplitBrainResolver.class);

    private final Props splitBrainResolverProps;
    private final Cancellable autoEnabling;

    @Nullable
    private ActorRef splitBrainResolverActor;

    @SuppressWarnings("unused")
    private DittoSplitBrainResolver(final Props splitBrainResolverProps) {
        this.splitBrainResolverProps = splitBrainResolverProps;
        final Duration autoEnableAfter = getAutoEnableAfter();
        // Enable automatically after some configurable time
        autoEnabling = context().system()
                .scheduler()
                .scheduleOnce(autoEnableAfter, getSelf(), ENABLE, context().dispatcher(), getSelf());
        LOGGER.info("SBR will be automatically enabled after <{}>", autoEnableAfter);
    }

    static Props props(@Nullable final Props splitBrainResolverProps) {
        return Props.create(DittoSplitBrainResolver.class, splitBrainResolverProps);
    }

    private Duration getAutoEnableAfter() {
        try {
            return context().system().settings().config().getDuration(SBR_ENABLING_DELAY);
        } catch (final Exception ex) {
            return AUTO_ENABLE_AFTER_FALLBACK;
        }
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ModifySplitBrainResolver.class, this::updateEnabled)
                .matchAny(() -> splitBrainResolverActor != null, this::forward)
                .matchAny(this::logDropped)
                .build();
    }

    private ActorRef startChildActor(final Props props) {
        return getContext().actorOf(props);
    }

    private void updateEnabled(final ModifySplitBrainResolver modifySplitBrainResolver) {
        if (!autoEnabling.isCancelled()) {
            autoEnabling.cancel();
        }
        if (modifySplitBrainResolver.isEnabled() && splitBrainResolverActor == null) {
            LOGGER.info("Enabling Akka split brain resolver");
            splitBrainResolverActor = startChildActor(splitBrainResolverProps);
        } else if (!modifySplitBrainResolver.isEnabled() && splitBrainResolverActor != null) {
            LOGGER.info("Stopping Akka split brain resolver");
            getContext().stop(splitBrainResolverActor);
            splitBrainResolverActor = null;
        }
        sender().tell(ModifySplitBrainResolverResponse.of(modifySplitBrainResolver), getSelf());
    }

    private void forward(final Object obj) {
        if (splitBrainResolverActor != null) {
            splitBrainResolverActor.forward(obj, getContext());
        }
    }

    private void logDropped(final Object obj) {
        LOGGER.info("Dropped message <{}> because split brain resolver is disabled", obj);
    }

}
