/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging.httppush;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.services.connectivity.messaging.internal.ssl.SSLContextCreator;

import akka.actor.ActorSystem;
import akka.event.LoggingAdapter;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.ConnectionContext;
import akka.http.javadsl.Http;
import akka.http.javadsl.HttpsConnectionContext;
import akka.http.javadsl.model.HttpMethod;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.settings.ClientConnectionSettings;
import akka.http.javadsl.settings.ConnectionPoolSettings;
import akka.http.javadsl.settings.ParserSettings;
import akka.japi.Pair;
import akka.stream.javadsl.Flow;
import scala.util.Try;

final class HttpPushFactory {

    static final String METHOD = "method";

    static final String PARALLELISM = "parallelism";

    private final ConnectionId connectionId;
    private final HttpMethod method;
    private final Uri baseUri;
    private final int parallelism;
    private final SSLContextCreator sslContextCreator;

    private HttpPushFactory(final ConnectionId connectionId,
            final HttpMethod method, final Uri baseUri, final int parallelism,
            final SSLContextCreator sslContextCreator) {
        this.connectionId = connectionId;
        this.method = method;
        this.baseUri = baseUri;
        this.parallelism = parallelism;
        this.sslContextCreator = sslContextCreator;
    }

    static HttpPushFactory of(final Connection connection) {
        final ConnectionId connectionId = connection.getId();
        final Uri baseUri = Uri.create(connection.getUri());
        final HttpMethod method = parseHttpMethod(connection.getSpecificConfig());
        final int parallelism = parseParallelism(connection.getSpecificConfig());
        final SSLContextCreator sslContextCreator = SSLContextCreator.fromConnection(connection, DittoHeaders.empty());
        return new HttpPushFactory(connectionId, method, baseUri, parallelism, sslContextCreator);
    }

    HttpRequest newRequest(final HttpPublishTarget httpPublishTarget) {
        Uri uri = baseUri;
        for (final String segment : httpPublishTarget.getPathSegments()) {
            uri = uri.addPathSegment(segment);
        }
        return HttpRequest.create().withMethod(method).withUri(uri);
    }

    /**
     * Create a flow to send HTTP(S) requests.
     * TODO: check failure restart behavior.
     *
     * @param system the actor system with the default Akka HTTP configuration.
     * @param log logger for the flow.
     * @param <T> type of correlation IDs.
     * @return flow from request-correlationId pairs to response-correlationId pairs.
     */
    <T> Flow<Pair<HttpRequest, T>, Pair<Try<HttpResponse>, T>, ?> createFlow(final ActorSystem system,
            final LoggingAdapter log) {

        final Http http = Http.get(system);
        final ConnectionPoolSettings poolSettings =
                disambiguateByConnectionId(system, connectionId).withMaxConnections(parallelism);
        if ("https".equals(baseUri.getScheme())) {
            final HttpsConnectionContext customHttpsContext =
                    ConnectionContext.https(sslContextCreator.withoutClientCertificate());
            final ConnectHttp connectHttpsWithCustomSSLContext =
                    ConnectHttp.toHostHttps(baseUri).withCustomHttpsContext(customHttpsContext);
            return http.cachedHostConnectionPoolHttps(connectHttpsWithCustomSSLContext, poolSettings, log);
        } else {
            // no SSL, hence no need for SSLContextCreator
            return http.cachedHostConnectionPool(ConnectHttp.toHost(baseUri), poolSettings, log);
        }
    }

    private static ConnectionPoolSettings disambiguateByConnectionId(final ActorSystem system, final ConnectionId id) {

        final ParserSettings parserSettings = ParserSettings.create(system);

        // start with the default maximum cached value per header of Akka HTTP.
        // "default=12" should be kept consistent with akka-http reference.conf
        final Map<String, Object> disambiguator = new HashMap<>(parserSettings.getHeaderValueCacheLimits());
        disambiguator.put(id.toString(), disambiguator.getOrDefault("default", 12));

        return ConnectionPoolSettings.create(system)
                .withConnectionSettings(ClientConnectionSettings.create(system)
                        .withParserSettings(parserSettings.withHeaderValueCacheLimits(disambiguator)));
    }

    private static HttpMethod parseHttpMethod(final Map<String, String> specificConfig) {
        return Optional.ofNullable(specificConfig.get(METHOD))
                .flatMap(HttpMethods::lookup)
                .orElse(HttpMethods.POST);
    }

    private static int parseParallelism(final Map<String, String> specificConfig) {
        return Optional.ofNullable(specificConfig.get(PARALLELISM))
                .map(Integer::valueOf)
                .orElse(1);
    }
}
