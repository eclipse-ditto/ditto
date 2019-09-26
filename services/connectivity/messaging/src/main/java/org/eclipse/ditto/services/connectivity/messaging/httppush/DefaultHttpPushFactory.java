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
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.settings.ClientConnectionSettings;
import akka.http.javadsl.settings.ConnectionPoolSettings;
import akka.http.javadsl.settings.ParserSettings;
import akka.japi.Pair;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Flow;
import scala.util.Try;

/**
 * Default implementation of {@link HttpPushFactory}.
 */
final class DefaultHttpPushFactory implements HttpPushFactory {

    private static final String PATH_DELIMITER = "/";

    private final ConnectionId connectionId;
    private final Uri baseUri;
    private final int parallelism;
    private final SSLContextCreator sslContextCreator;

    private DefaultHttpPushFactory(final ConnectionId connectionId, final Uri baseUri, final int parallelism,
            final SSLContextCreator sslContextCreator) {
        this.connectionId = connectionId;
        this.baseUri = baseUri;
        this.parallelism = parallelism;
        this.sslContextCreator = sslContextCreator;
    }

    static HttpPushFactory of(final Connection connection) {
        final ConnectionId connectionId = connection.getId();
        final Uri baseUri = Uri.create(connection.getUri());
        final int parallelism = parseParallelism(connection.getSpecificConfig());
        final SSLContextCreator sslContextCreator = SSLContextCreator.fromConnection(connection, DittoHeaders.empty());
        return new DefaultHttpPushFactory(connectionId, baseUri, parallelism, sslContextCreator);
    }

    @Override
    public HttpRequest newRequest(final HttpPublishTarget httpPublishTarget) {
        final String baseUriStrToUse = determineBaseUri(baseUri);
        final String pathWithQueryToUse = determineHttpPath(httpPublishTarget);
        return HttpRequest.create()
                .withMethod(httpPublishTarget.getMethod())
                .withUri(Uri.create(baseUriStrToUse + pathWithQueryToUse));
    }

    private static String determineBaseUri(final Uri baseUri) {
        final String baseUriStr = baseUri.toString();
        final String baseUriStrToUse;
        if (baseUriStr.endsWith(PATH_DELIMITER)) {
            // avoid double "/", so cut it off at the baseUri to use:
            baseUriStrToUse = baseUriStr.substring(0, baseUriStr.length()-1);
        } else {
            baseUriStrToUse = baseUriStr;
        }
        return baseUriStrToUse;
    }

    private static String determineHttpPath(final HttpPublishTarget httpPublishTarget) {
        final String pathWithQuery = httpPublishTarget.getPathWithQuery();
        final String pathWithQueryToUse;
        if (pathWithQuery.startsWith(PATH_DELIMITER) || pathWithQuery.startsWith("?") || pathWithQuery.startsWith("#")) {
            pathWithQueryToUse = pathWithQuery;
        } else {
            pathWithQueryToUse = PATH_DELIMITER + pathWithQuery;
        }
        return pathWithQueryToUse;
    }

    @Override
    public <T> Flow<Pair<HttpRequest, T>, Pair<Try<HttpResponse>, T>, ?> createFlow(final ActorSystem system,
            final LoggingAdapter log) {

        final Http http = Http.get(system);
        final ConnectionPoolSettings poolSettings = getConnectionPoolSettings(system);
        final Flow<Pair<HttpRequest, T>, Pair<Try<HttpResponse>, T>, ?> flow;
        if (HttpPushValidator.isSecureScheme(baseUri.getScheme())) {
            final ConnectHttp connectHttpsWithCustomSSLContext =
                    ConnectHttp.toHostHttps(baseUri).withCustomHttpsContext(getHttpsConnectionContext());
            flow = http.<T>cachedHostConnectionPoolHttps(connectHttpsWithCustomSSLContext, poolSettings, log);
        } else {
            // no SSL, hence no need for SSLContextCreator
            flow = http.<T>cachedHostConnectionPool(ConnectHttp.toHost(baseUri), poolSettings, log);
        }
        return flow.buffer(parallelism, OverflowStrategy.backpressure());
    }

    private ConnectionPoolSettings getConnectionPoolSettings(final ActorSystem system) {
        return disambiguateByConnectionId(system, connectionId).withMaxConnections(parallelism);
    }

    private HttpsConnectionContext getHttpsConnectionContext() {
        return ConnectionContext.https(sslContextCreator.withoutClientCertificate());
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

    private static int parseParallelism(final Map<String, String> specificConfig) {
        return Optional.ofNullable(specificConfig.get(HttpPushFactory.PARALLELISM))
                .map(Integer::valueOf)
                .orElse(1);
    }

}
