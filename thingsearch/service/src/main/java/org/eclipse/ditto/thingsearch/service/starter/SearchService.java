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
package org.eclipse.ditto.thingsearch.service.starter;

import org.eclipse.ditto.base.service.DittoService;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.thingsearch.service.common.config.DittoSearchConfig;
import org.eclipse.ditto.thingsearch.service.common.config.SearchConfig;
import org.eclipse.ditto.thingsearch.service.starter.actors.SearchRootActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Entry point for the Things Search service.
 */
public class SearchService extends DittoService<SearchConfig> {

    /**
     * Name of things-search service.
     */
    public static final String SERVICE_NAME = "search";

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchService.class);

    private SearchService() {
        super(LOGGER, SERVICE_NAME, SearchRootActor.ACTOR_NAME);
    }

    /**
     * Starts the ThingsSearch service.
     *
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        final SearchService searchService = new SearchService();
        searchService.start();
    }

    @Override
    protected SearchConfig getServiceSpecificConfig(final ScopedConfig dittoConfig) {
        return DittoSearchConfig.of(dittoConfig);
    }

    @Override
    protected Props getMainRootActorProps(final SearchConfig searchConfig, final ActorRef pubSubMediator) {
        return SearchRootActor.props(searchConfig, pubSubMediator);
    }

}
