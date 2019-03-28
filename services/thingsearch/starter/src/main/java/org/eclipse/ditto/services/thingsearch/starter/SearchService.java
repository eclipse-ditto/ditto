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
package org.eclipse.ditto.services.thingsearch.starter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point of the Search service.
 * <ul>
 * <li>Reads configuration, enhances it with cloud environment settings.</li>
 * <li>Sets up Akka actor system.</li>
 * <li>Wires up Akka HTTP Routes.</li>
 * </ul>
 */
public final class SearchService extends AbstractSearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchService.class);

    private SearchService() {
        super(LOGGER);
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

}
