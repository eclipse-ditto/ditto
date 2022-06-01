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
package org.eclipse.ditto.thingsearch.api;

import javax.annotation.concurrent.Immutable;

/**
 * Constants for the Things-Search .
 */
@Immutable
public final class ThingsSearchConstants {

    /**
     * Name of the shard region for Things-Search updater.
     */
    public static final String SHARD_REGION = "search-wildcard-updater";

    /**
     * Name of the akka cluster role.
     */
    public static final String CLUSTER_ROLE = "things-wildcard-search";

    @SuppressWarnings("squid:S1075")
    private static final String USER_PATH = "/user";

    public static final String ROOT_ACTOR_NAME = "thingsWildcardSearchRoot";

    /**
     * Path of the root actor.
     */
    public static final String ROOT_ACTOR_PATH = USER_PATH + "/" + ROOT_ACTOR_NAME;

    /**
     * Path of the search actor.
     */
    public static final String SEARCH_ACTOR_NAME = "thingsSearch";
    public static final String SEARCH_ACTOR_PATH = ROOT_ACTOR_PATH + "/" + SEARCH_ACTOR_NAME;

    /*
     * Inhibit instantiation of this utility class.
     */
    private ThingsSearchConstants() {
        // no-op
    }
}
