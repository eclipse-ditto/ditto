/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.utils.cluster;

import java.util.Collections;

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.Props;
import akka.cluster.routing.ClusterRouterGroup;
import akka.cluster.routing.ClusterRouterGroupSettings;
import akka.routing.ConsistentHashingGroup;

/**
 * Creates {@link Props} for a routing actor that can be used to send {@link WithDittoHeaders}s to the gateway proxy actor.
 * It uses consistent hashing based on the correlation id i.e. corresponding signals are routed to the same gateway
 * instance.
 */
public class CommandRouterPropsFactory {

    private static final String COMMAND_ROUTER_PREFIX = "ditto.cluster.command-router.";
    private static final String CONFIG_TARGET_ACTOR = COMMAND_ROUTER_PREFIX + "target-actor";
    private static final String CONFIG_TOTAL_INSTANCES = COMMAND_ROUTER_PREFIX + "total-instances";
    private static final String CONFIG_ROLE = COMMAND_ROUTER_PREFIX + "role";

    private static final String DEFAULT_PROXY_ACTOR_PATH = "/user/gatewayRoot/proxy";
    private static final String DEFAULT_ROLE = "gateway";
    private static final int DEFAULT_TOTAL_INSTANCES = 100;
    private static final boolean ALLOW_LOCAL_ROUTEES = true;

    /**
     * @return the props that can be used to create the router actor
     */
    public static Props getProps(final Config config) {

        final Config configWithDefaults = config.withFallback(defaults());
        final String targetActor = configWithDefaults.getString(CONFIG_TARGET_ACTOR);
        final String role = configWithDefaults.getString(CONFIG_ROLE);
        final Integer totalInstances = configWithDefaults.getInt(CONFIG_TOTAL_INSTANCES);

        final Iterable<String> routeesPaths = Collections.singletonList(targetActor);
        return new ClusterRouterGroup(new ConsistentHashingGroup(routeesPaths)
                .withHashMapper(
                        message -> {
                            if (message instanceof WithDittoHeaders) {
                                return ((WithDittoHeaders) message).getDittoHeaders().getCorrelationId().orElse(null);
                            } else {
                                return null;
                            }
                        }),
                new ClusterRouterGroupSettings(totalInstances, routeesPaths, ALLOW_LOCAL_ROUTEES,
                        Collections.singleton(role))).props();
    }

    private static Config defaults() {
        return ConfigFactory.empty()
                .withValue(CONFIG_TARGET_ACTOR, ConfigValueFactory.fromAnyRef(DEFAULT_PROXY_ACTOR_PATH))
                .withValue(CONFIG_ROLE, ConfigValueFactory.fromAnyRef(DEFAULT_ROLE))
                .withValue(CONFIG_TOTAL_INSTANCES, ConfigValueFactory.fromAnyRef(DEFAULT_TOTAL_INSTANCES));
    }
}
