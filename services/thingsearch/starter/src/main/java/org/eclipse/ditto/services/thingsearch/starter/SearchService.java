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
package org.eclipse.ditto.services.thingsearch.starter;

import org.eclipse.ditto.services.base.BaseConfigKey;
import org.eclipse.ditto.services.base.BaseConfigKeys;
import org.eclipse.ditto.services.base.DittoService;
import org.eclipse.ditto.services.thingsearch.common.util.ConfigKeys;
import org.eclipse.ditto.services.thingsearch.starter.actors.SearchRootActor;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.ActorMaterializer;

/**
 * Entry point of the Search service.
 * <ul>
 * <li>Reads configuration, enhances it with cloud environment settings.</li>
 * <li>Sets up Akka actor system.</li>
 * <li>Wires up Akka HTTP Routes.</li>
 * </ul>
 */
@AllParametersAndReturnValuesAreNonnullByDefault
public final class SearchService extends DittoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchService.class);

    /**
     * Name for the Search service.
     */
    private static final String SERVICE_NAME = ConfigKeys.SERVICE_NAME;

    private SearchService() {
        super(LOGGER, SERVICE_NAME, SearchRootActor.ACTOR_NAME, BaseConfigKeys.getBuilder()
                .put(BaseConfigKey.Cluster.MAJORITY_CHECK_ENABLED, ConfigKeys.CLUSTER_MAJORITY_CHECK_ENABLED)
                .put(BaseConfigKey.Cluster.MAJORITY_CHECK_DELAY, ConfigKeys.CLUSTER_MAJORITY_CHECK_DELAY)
                .build());
    }

    /**
     * Starts the Search service.
     *
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        final SearchService searchService = new SearchService();
        searchService.start();
    }

    @Override
    protected Props getMainRootActorProps(final Config config, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        return SearchRootActor.props(config, pubSubMediator, materializer);
    }
}
