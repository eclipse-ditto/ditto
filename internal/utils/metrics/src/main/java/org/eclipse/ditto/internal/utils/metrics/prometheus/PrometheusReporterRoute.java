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
package org.eclipse.ditto.internal.utils.metrics.prometheus;

import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.get;

import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.util.ByteString;
import kamon.prometheus.PrometheusReporter;

/**
 * Provides a Route for a HTTP Prometheus endpoint where Prometheus can scrape metrics from.
 */
public final class PrometheusReporterRoute {

    private static final ContentType CONTENT_TYPE = ContentTypes.parse("text/plain; version=0.0.4; charset=utf-8");

    private PrometheusReporterRoute() {
        throw new AssertionError();
    }

    /**
     * Builds a Route for a HTTP Prometheus endpoint where Prometheus can scrape metrics from.
     *
     * @param prometheusReporter the PrometheusReporter to retrieve the {@code scrapeData} from
     * @return the Prometheus Route
     */
    public static Route buildPrometheusReporterRoute(final PrometheusReporter prometheusReporter) {
        return get(() ->
                complete(HttpResponse.create()
                        .withStatus(StatusCodes.OK)
                        .withEntity(CONTENT_TYPE, ByteString.fromString(prometheusReporter.scrapeData()))
                )
        );
    }
}
