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
package org.eclipse.ditto.internal.utils.health;

import static akka.http.javadsl.server.Directives.completeWithFuture;

import java.time.Duration;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import akka.actor.ActorRef;
import akka.event.LoggingAdapter;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Route;
import akka.pattern.Patterns;

/**
 * Function for Akka HTTP Routes to transform a RequestContext into a RouteResult with the expected status code for
 * health (200/503) and the required format.
 */
public class HealthRouteSupplier implements Supplier<Route> {

    private static final int HEALTH_CHECK_TIMEOUT = 60;
    private static final Duration TIMEOUT = Duration.ofSeconds(HEALTH_CHECK_TIMEOUT);

    private static final int HTTP_STATUS_OK = 200;
    private static final int HTTP_STATUS_SERVICE_UNAVAILABLE = 503;

    private final ActorRef healthCheckingActor;
    private final LoggingAdapter log;

    /**
     * Constructs a new {@code HealthRouteFunction}.
     *
     * @param healthCheckingActor the Actor selection to the health-checking actor to use.
     * @param log the logging adatper to use.
     */
    public HealthRouteSupplier(final ActorRef healthCheckingActor, final LoggingAdapter log) {
        this.healthCheckingActor = healthCheckingActor;
        this.log = log;
    }

    @Override
    public Route get() {
        return completeWithFuture(
                Patterns.ask(healthCheckingActor, RetrieveHealth.newInstance(), TIMEOUT)
                        .handle(this::handleHealthResult)
        );
    }

    private HttpResponse handleHealthResult(@Nullable final Object health, @Nullable final Throwable failure) {
        if (null != health) {
            if (health instanceof StatusInfo statusInfo) {
                return completeHealthRequest(statusInfo);
            } else {
                return completeHealthRequestForUnexpectedHealthResult(health);
            }
        } else if (null != failure) {
            return completeHealthRequest(failure);
        } else {
            return completeHealthRequestForUnexpectedHealthResult(null);
        }
    }

    private HttpResponse completeHealthRequest(final StatusInfo statusInfo) {
        final int httpStatusCode =
                statusInfo.getStatus() == StatusInfo.Status.DOWN ? HTTP_STATUS_SERVICE_UNAVAILABLE : HTTP_STATUS_OK;

        if (statusInfo.getStatus() == StatusInfo.Status.DOWN) {
            log.warning("Own health check returned DOWN: {}", statusInfo);
        }

        return HttpResponse.create()
                .withEntity(ContentTypes.APPLICATION_JSON, statusInfo.toJsonString())
                .withStatus(httpStatusCode);
    }

    private HttpResponse completeHealthRequest(final Throwable failure) {
        log.error(failure, "Health check resulted in failure: '{}'", failure.getMessage());
        return HttpResponse.create()
                .withStatus(HTTP_STATUS_SERVICE_UNAVAILABLE);
    }

    private HttpResponse completeHealthRequestForUnexpectedHealthResult(@Nullable final Object healthResult) {
        log.error( "Health check returned an unexpected result: '{}'", healthResult);
        return HttpResponse.create()
                .withStatus(HTTP_STATUS_SERVICE_UNAVAILABLE);
    }
}
