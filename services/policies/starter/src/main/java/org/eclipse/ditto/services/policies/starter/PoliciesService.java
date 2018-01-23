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
package org.eclipse.ditto.services.policies.starter;

import org.eclipse.ditto.services.policies.persistence.serializer.PolicyMongoSnapshotAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

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
        policiesService.start();
    }

    @Override
    protected Props getMainRootActorProps(final Config config, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {
        return PoliciesRootActor.props(config, new PolicyMongoSnapshotAdapter(), pubSubMediator, materializer);
    }

}
