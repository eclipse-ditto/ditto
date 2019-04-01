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
package org.eclipse.ditto.services.policies.starter;

import org.eclipse.ditto.services.base.config.ServiceConfigReader;
import org.eclipse.ditto.services.policies.persistence.serializer.PolicyMongoSnapshotAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.ActorMaterializer;

/**
 * Entry point of the Policies Service.
 */
public final class PoliciesService extends AbstractPoliciesService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PoliciesService.class);

    private PoliciesService() {
        super(LOGGER);
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
    protected Props getMainRootActorProps(final ServiceConfigReader configReader, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        return PoliciesRootActor.props(configReader, new PolicyMongoSnapshotAdapter(), pubSubMediator, materializer);
    }

}
