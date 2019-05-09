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
package org.eclipse.ditto.services.policies.starter;

import org.eclipse.ditto.services.base.DittoService;
import org.eclipse.ditto.services.policies.persistence.serializer.PolicyMongoSnapshotAdapter;
import org.eclipse.ditto.services.policies.starter.config.DittoPoliciesConfig;
import org.eclipse.ditto.services.policies.starter.config.PoliciesConfig;
import org.eclipse.ditto.services.utils.config.ScopedConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.ActorMaterializer;

/**
 * Entry point of the Policies Service.
 */
public final class PoliciesService extends DittoService<PoliciesConfig> {

    /**
     * Name of the Policies Service.
     */
    public static final String SERVICE_NAME = "policies";

    private static final Logger LOGGER = LoggerFactory.getLogger(PoliciesService.class);

    private PoliciesService() {
        super(LOGGER, SERVICE_NAME, PoliciesRootActor.ACTOR_NAME);
    }

    /**
     * Starts the Policies service.
     *
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        final PoliciesService policiesService = new PoliciesService();
        policiesService.start().getWhenTerminated().toCompletableFuture().join();
    }

    @Override
    protected PoliciesConfig getServiceSpecificConfig(final ScopedConfig dittoConfig) {
        return DittoPoliciesConfig.of(dittoConfig);
    }

    @Override
    protected Props getMainRootActorProps(final PoliciesConfig policiesConfig, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        return PoliciesRootActor.props(policiesConfig, new PolicyMongoSnapshotAdapter(), pubSubMediator, materializer);
    }

}
