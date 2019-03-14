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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the Things Search service.
 */
public class ThingsSearchService extends AbstractSearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThingsSearchService.class);

    private ThingsSearchService() {
        super(LOGGER);
    }

    /**
     * Starts the Things service.
     *
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        final ThingsSearchService searchService = new ThingsSearchService();
        searchService.start();
    }
}
