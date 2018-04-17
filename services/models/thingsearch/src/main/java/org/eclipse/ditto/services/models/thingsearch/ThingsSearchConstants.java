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
package org.eclipse.ditto.services.models.thingsearch;

import javax.annotation.concurrent.Immutable;

/**
 * Constants for the Things-Search .
 */
@Immutable
public final class ThingsSearchConstants {

    /**
     * Name of the shard region for Things-Search updater.
     */
    public static final String SHARD_REGION = "search-updater";

    /**
     * Name of the akka cluster role.
     */
    public static final String CLUSTER_ROLE = "things-search";

    /**
     * Path of the search actor.
     */
    @SuppressWarnings("squid:S1075")
    public static final String SEARCH_ACTOR_PATH = "/user/thingsSearchRoot/thingsSearch";

    /*
     * Inhibit instantiation of this utility class.
     */
    private ThingsSearchConstants() {
        // no-op
    }
}
