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
package org.eclipse.ditto.gateway.service.endpoints.routes.health;

import static akka.http.javadsl.server.Directives.completeWithFuture;
import static akka.http.javadsl.server.Directives.get;
import static akka.http.javadsl.server.Directives.path;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.gateway.service.health.StatusAndHealthProvider;
import org.eclipse.ditto.gateway.service.util.config.endpoints.PublicHealthConfig;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.internal.utils.health.StatusDetailMessage;
import org.eclipse.ditto.internal.utils.health.StatusInfo;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;

/**
 * Builder for creating Akka HTTP route for {@code /health}.
 */
public final class CachingHealthRoute {

    /**
     * Public endpoint of health.
     */
    public static final String PATH_HEALTH = "health";

    private final StatusAndHealthProvider statusHealthHelper;
    private final Duration refreshInterval;

    private CompletionStage<StatusInfo> cachedHealth;
    private Instant lastCheckInstant;

    /**
     * Constructs the {@code /health} route builder.
     *
     * @param statusHealthHelper the helper for retrieving health status of the cluster.
     * @param refreshInterval the interval for refreshing the health status.
     */
    public CachingHealthRoute(final StatusAndHealthProvider statusHealthHelper, final Duration refreshInterval) {
        this.statusHealthHelper = statusHealthHelper;
        this.refreshInterval = refreshInterval;
    }

    /**
     * Constructs a new {@code CachingHealthRoute} object.
     *
     * @param statusAndHealthProvider is used to retrieve the health status of the cluster.
     * @param publicHealthConfig the configuration settings of the public health endpoint.
     */
    public CachingHealthRoute(final StatusAndHealthProvider statusAndHealthProvider,
            final PublicHealthConfig publicHealthConfig) {

        statusHealthHelper = statusAndHealthProvider;
        refreshInterval = publicHealthConfig.getCacheTimeout();
    }

    /**
     * Builds the {@code /health} route.
     *
     * @return the {@code /health} route.
     */
    public Route buildHealthRoute() {
        return path(PATH_HEALTH, () -> // /health
                get(() -> // GET
                        completeWithFuture(createOverallHealthResponse())
                )
        );
    }

    private boolean isCacheTimedOut() {
        return lastCheckInstant.plusSeconds(refreshInterval.getSeconds()).isBefore(Instant.now());
    }

    private CompletionStage<HttpResponse> createOverallHealthResponse() {
        if (cachedHealth == null || isCacheTimedOut()) {
            cachedHealth = statusHealthHelper.retrieveHealth()
                    .exceptionally(t -> StatusInfo.fromDetail(StatusDetailMessage.of(StatusDetailMessage.Level.ERROR,
                            "Exception: " + t.getMessage())));
            lastCheckInstant = Instant.now();
        }

        return cachedHealth.thenApply(overallHealth -> {
            final JsonObject statusObject = JsonFactory.newObject()
                    .set(StatusInfo.JSON_KEY_STATUS, overallHealth.getStatus().toString());
            if (overallHealth.isHealthy()) {
                return HttpResponse.create()
                        .withStatus(StatusCodes.OK)
                        .withEntity(ContentTypes.APPLICATION_JSON, statusObject.toString());
            } else {
                return HttpResponse.create()
                        .withStatus(StatusCodes.SERVICE_UNAVAILABLE)
                        .withEntity(ContentTypes.APPLICATION_JSON, statusObject.toString());
            }
        });
    }

}
