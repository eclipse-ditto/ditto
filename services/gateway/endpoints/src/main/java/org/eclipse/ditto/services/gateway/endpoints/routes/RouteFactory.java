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
package org.eclipse.ditto.services.gateway.endpoints.routes;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.function.Supplier;

import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.GatewayAuthenticationDirective;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.GatewayAuthenticationDirectiveFactory;
import org.eclipse.ditto.services.gateway.endpoints.routes.devops.DevOpsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.health.CachingHealthRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.policies.PoliciesRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.sse.SseThingsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.stats.StatsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.status.OverallStatusRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.things.ThingsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.thingsearch.ThingSearchRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.websocket.WebsocketRoute;
import org.eclipse.ditto.services.gateway.health.DittoStatusAndHealthProviderFactory;
import org.eclipse.ditto.services.gateway.health.StatusAndHealthProvider;
import org.eclipse.ditto.services.gateway.starter.service.util.ConfigKeys;
import org.eclipse.ditto.services.utils.health.cluster.ClusterStatus;
import org.eclipse.ditto.services.utils.health.routes.StatusRoute;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.services.utils.protocol.ProtocolConfigReader;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * A factory for all kinds of sub-routes.
 */
public final class RouteFactory {

    private final ActorSystem actorSystem;
    private final Config config;
    private final ActorRef proxyActor;
    private final ActorRef streamingActor;
    private final ActorRef healthCheckingActor;
    private final Supplier<ClusterStatus> clusterStateSupplier;
    private final StatusAndHealthProvider statusAndHealthProvider;
    private final ProtocolAdapterProvider protocolAdapterProvider;
    private final HeaderTranslator headerTranslator;
    private final GatewayAuthenticationDirectiveFactory authenticationDirectiveFactory;

    private RouteFactory(final ActorSystem actorSystem,
            final Config config,
            final ActorRef proxyActor,
            final ActorRef streamingActor,
            final ActorRef healthCheckingActor,
            final Supplier<ClusterStatus> clusterStateSupplier,
            final StatusAndHealthProvider statusAndHealthProvider,
            final ProtocolAdapterProvider protocolAdapterProvider,
            final HeaderTranslator headerTranslator,
            final GatewayAuthenticationDirectiveFactory authenticationDirectiveFactory) {
        this.actorSystem = actorSystem;
        this.config = config;
        this.proxyActor = proxyActor;
        this.streamingActor = streamingActor;
        this.healthCheckingActor = healthCheckingActor;
        this.clusterStateSupplier = clusterStateSupplier;
        this.statusAndHealthProvider = statusAndHealthProvider;
        this.protocolAdapterProvider = protocolAdapterProvider;
        this.headerTranslator = headerTranslator;
        this.authenticationDirectiveFactory = authenticationDirectiveFactory;
    }

    /**
     * Creates a new {@code RouteFactory}.
     *
     * @param actorSystem actor system used for creating sub-routes.
     * @param proxyActor actor to which http requests will be forwarded.
     * @param streamingActor actor to which streams events to websocket and sse session actors.
     * @param healthCheckingActor actor which checks the health status of the service.
     * @param clusterStateSupplier supplier which provides the state of the akka cluster.
     * @param authenticationDirectiveFactory factory to create authentication directives.
     * @return the route factory.
     */
    public static RouteFactory newInstance(final ActorSystem actorSystem,
            final ActorRef proxyActor,
            final ActorRef streamingActor,
            final ActorRef healthCheckingActor,
            final Supplier<ClusterStatus> clusterStateSupplier,
            final GatewayAuthenticationDirectiveFactory authenticationDirectiveFactory) {
        checkNotNull(actorSystem, "Actor System");
        checkNotNull(proxyActor, "Proxy Actor");
        checkNotNull(streamingActor, "Streaming Actor");
        checkNotNull(clusterStateSupplier, "ClusterState Supplier");
        final StatusAndHealthProvider statusAndHealthProvider =
                DittoStatusAndHealthProviderFactory.of(actorSystem, clusterStateSupplier);
        final Config config = actorSystem.settings().config();
        final ProtocolConfigReader protocolConfig = ProtocolConfigReader.fromRawConfig(config);
        final ProtocolAdapterProvider protocolAdapterProvider = protocolConfig.loadProtocolAdapterProvider(actorSystem);
        final HeaderTranslator headerTranslator = protocolAdapterProvider.getHttpHeaderTranslator();
        return new RouteFactory(actorSystem, config, proxyActor, streamingActor, healthCheckingActor,
                clusterStateSupplier, statusAndHealthProvider, protocolAdapterProvider, headerTranslator,
                authenticationDirectiveFactory);
    }

    /**
     * Creates a new {@code StatsRoute}.
     *
     * @return the route.
     */
    public StatsRoute newStatsRoute() {
        return new StatsRoute(proxyActor, actorSystem);
    }

    /**
     * Creates a new {@code StatusRoute}.
     *
     * @return the route.
     */
    public StatusRoute newStatusRoute() {
        return new StatusRoute(clusterStateSupplier, healthCheckingActor, actorSystem);
    }

    /**
     * Creates a new {@code OverallStatsRoute}.
     *
     * @return the route.
     */
    public OverallStatusRoute newOverallStatusRoute() {
        return new OverallStatusRoute(clusterStateSupplier, statusAndHealthProvider);
    }

    /**
     * Creates a new {@code CachingHealthRoute}.
     *
     * @return the route.
     */
    public CachingHealthRoute newCachingHealthRoute() {
        return new CachingHealthRoute(statusAndHealthProvider,
                config.getDuration(ConfigKeys.STATUS_HEALTH_EXTERNAL_CACHE_TIMEOUT));
    }

    /**
     * Creates a new {@code DevOpsRoute}.
     *
     * @return the route.
     */
    public DevOpsRoute newDevopsRoute() {
        return new DevOpsRoute(proxyActor, actorSystem);
    }

    /**
     * Creates a new {@code PoliciesRoute}.
     *
     * @return the route.
     */
    public PoliciesRoute newPoliciesRoute() {
        return new PoliciesRoute(proxyActor, actorSystem);
    }

    /**
     * Creates a new {@code SseThingsRoute}.
     *
     * @return the route.
     */
    public SseThingsRoute newSseThingsRoute() {
        return new SseThingsRoute(proxyActor, actorSystem, streamingActor);
    }

    /**
     * Creates a new {@code ThingsRoute}.
     *
     * @return the route.
     */
    public ThingsRoute newThingsRoute() {
        return new ThingsRoute(proxyActor, actorSystem,
                config.getDuration(ConfigKeys.MESSAGE_DEFAULT_TIMEOUT),
                config.getDuration(ConfigKeys.MESSAGE_MAX_TIMEOUT),
                config.getDuration(ConfigKeys.CLAIMMESSAGE_DEFAULT_TIMEOUT),
                config.getDuration(ConfigKeys.CLAIMMESSAGE_MAX_TIMEOUT));
    }

    /**
     * Creates a new {@code ThingSearchRoute}.
     *
     * @return the route.
     */
    public ThingSearchRoute newThingSearchRoute() {
        return new ThingSearchRoute(proxyActor, actorSystem);
    }

    /**
     * Creates a new {@code WebsocketRoute}.
     *
     * @return the route.
     */
    public WebsocketRoute newWebSocketRoute() {
        return new WebsocketRoute(streamingActor,
                config.getInt(ConfigKeys.WEBSOCKET_SUBSCRIBER_BACKPRESSURE),
                config.getInt(ConfigKeys.WEBSOCKET_PUBLISHER_BACKPRESSURE),
                actorSystem.eventStream());
    }

    /**
     * Creates a new {@code GatewayAuthenticationDirective} for the HTTP API.
     *
     * @return the directive.
     */
    public GatewayAuthenticationDirective newHttpAuthenticationDirective() {
        return authenticationDirectiveFactory.buildHttpAuthentication();
    }

    /**
     * Creates a new {@code GatewayAuthenticationDirective} for the WebSocket API.
     *
     * @return the directive.
     */
    public GatewayAuthenticationDirective newWsAuthenticationDirective() {
        return authenticationDirectiveFactory.buildWsAuthentication();
    }

    /**
     * Creates a new {@code ProtocolAdapterProvider}.
     *
     * @return the provider.
     */
    public ProtocolAdapterProvider getProtocolAdapterProvider() {
        return protocolAdapterProvider;
    }

    /**
     * Creates a new {@code HeaderTranslator}.
     *
     * @return the translator.
     */
    public HeaderTranslator getHeaderTranslator() {
        return headerTranslator;
    }

}
