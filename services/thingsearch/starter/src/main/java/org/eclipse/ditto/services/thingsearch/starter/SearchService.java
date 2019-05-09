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
package org.eclipse.ditto.services.thingsearch.starter;

import org.eclipse.ditto.services.base.DittoService;
import org.eclipse.ditto.services.thingsearch.starter.actors.SearchRootActor;
import org.eclipse.ditto.services.thingsearch.starter.config.SearchConfig;
import org.eclipse.ditto.services.utils.config.ScopedConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public final class SearchService extends DittoService<SearchConfig> {

    /**
     * Name of things-search service.
     */
    public static final String SERVICE_NAME = "things-search";

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchService.class);

    private SearchService() {
        super(LOGGER, SERVICE_NAME, SearchRootActor.ACTOR_NAME);
    }

    /**
     * Starts the Search service.
     *
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        final SearchService searchService = new SearchService();
        searchService.start().getWhenTerminated().toCompletableFuture().join();
    }

    @Override
    protected SearchConfig getServiceSpecificConfig(final ScopedConfig dittoConfig) {
        return null;
    }

    @Override
    protected Props getMainRootActorProps(final SearchConfig searchConfig, final ActorRef pubSubMediator,
            final ActorMaterializer materializer) {

        return SearchRootActor.props(searchConfig, pubSubMediator, materializer);
    }

}
