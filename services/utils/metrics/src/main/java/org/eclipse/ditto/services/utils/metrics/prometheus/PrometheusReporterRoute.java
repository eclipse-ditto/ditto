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
package org.eclipse.ditto.services.utils.metrics.prometheus;

import static akka.http.javadsl.server.Directives.complete;
import static akka.http.javadsl.server.Directives.get;

import org.eclipse.ditto.services.utils.metrics.dropwizard.DropwizardMetricsPrometheusReporter;

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
                        .withEntity(CONTENT_TYPE, ByteString.fromString(buildMetricsString(prometheusReporter)))
                )
        );
    }

    private static String buildMetricsString(final PrometheusReporter prometheusReporter) {
        return "#Kamon Metrics\n" +
                prometheusReporter.scrapeData() +
                "#Dropwizard Metrics\n" +
                DropwizardMetricsPrometheusReporter.getData();
    }
}
