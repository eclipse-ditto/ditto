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

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.services.base.config.http.HttpProxyConfig;
import org.eclipse.ditto.services.connectivity.messaging.internal.ssl.SSLContextCreator;

import akka.actor.ActorSystem;
import akka.event.LoggingAdapter;
import akka.http.javadsl.ClientTransport;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.ConnectionContext;
import akka.http.javadsl.Http;
import akka.http.javadsl.HttpsConnectionContext;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.model.headers.HttpCredentials;
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

    @Nullable
    private final ClientTransport clientTransport;

    private DefaultHttpPushFactory(final ConnectionId connectionId, final Uri baseUri, final int parallelism,
            final SSLContextCreator sslContextCreator, @Nullable final HttpProxyConfig httpProxyConfig) {
        this.connectionId = connectionId;
        this.baseUri = baseUri;
        this.parallelism = parallelism;
        this.sslContextCreator = sslContextCreator;
        if (httpProxyConfig == null || !httpProxyConfig.isEnabled()) {
            clientTransport = null;
        } else {
            clientTransport = httpProxyConfig.toClientTransport();
        }
    }

    static HttpPushFactory of(final Connection connection, @Nullable final HttpProxyConfig httpProxyConfig) {
        final ConnectionId connectionId = connection.getId();
        final Uri baseUri = Uri.create(connection.getUri());
        final int parallelism = parseParallelism(connection.getSpecificConfig());
        final SSLContextCreator sslContextCreator = SSLContextCreator.fromConnection(connection, DittoHeaders.empty());
        return new DefaultHttpPushFactory(connectionId, baseUri, parallelism, sslContextCreator, httpProxyConfig);
    }

    @Override
    public HttpRequest newRequest(final HttpPublishTarget httpPublishTarget) {
        final String baseUriStrToUse = determineBaseUri(baseUri);
        final String pathWithQueryToUse = determineHttpPath(httpPublishTarget);
        final String userInfo = baseUri.getUserInfo();
        final int passwordSeparatorLocation = userInfo.indexOf(':');
        final HttpRequest request = HttpRequest.create()
                .withMethod(httpPublishTarget.getMethod())
                .withUri(Uri.create(baseUriStrToUse + pathWithQueryToUse));
        if (passwordSeparatorLocation >= 0) {
            final String username = userInfo.substring(0, passwordSeparatorLocation);
            final String password = userInfo.substring(Math.min(userInfo.length(), passwordSeparatorLocation + 1));
            return request.addCredentials(HttpCredentials.createBasicHttpCredentials(username, password));
        } else {
            return request;
        }
    }

    private static String determineBaseUri(final Uri baseUri) {
        final String baseUriStr = baseUri.toString();
        final String baseUriStrToUse;
        if (baseUriStr.endsWith(PATH_DELIMITER)) {
            // avoid double "/", so cut it off at the baseUri to use:
            baseUriStrToUse = baseUriStr.substring(0, baseUriStr.length() - 1);
        } else {
            baseUriStrToUse = baseUriStr;
        }
        return baseUriStrToUse;
    }

    private static String determineHttpPath(final HttpPublishTarget httpPublishTarget) {
        final String pathWithQuery = httpPublishTarget.getPathWithQuery();
        final String pathWithQueryToUse;
        if (pathWithQuery.startsWith(PATH_DELIMITER) || pathWithQuery.startsWith("?") ||
                pathWithQuery.startsWith("#")) {
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
            flow = http.cachedHostConnectionPoolHttps(connectHttpsWithCustomSSLContext, poolSettings, log);
        } else {
            // no SSL, hence no need for SSLContextCreator
            flow = http.cachedHostConnectionPool(ConnectHttp.toHost(baseUri), poolSettings, log);
        }
        return flow.buffer(parallelism, OverflowStrategy.backpressure());
    }

    private ConnectionPoolSettings getConnectionPoolSettings(final ActorSystem system) {
        final ConnectionPoolSettings settings =
                disambiguateByConnectionId(system, connectionId).withMaxConnections(parallelism);
        return clientTransport == null
                ? settings
                : settings.withTransport(clientTransport);
    }

    private HttpsConnectionContext getHttpsConnectionContext() {
        return ConnectionContext.https(sslContextCreator.withoutClientCertificate());
    }

    /**
     * Create connection pool settings unique for the connection ID but functionally identical for
     * identically configured connections.
     *
     * @param system the actor system that runs this HTTP-push factory.
     * @param id the connection ID.
     * @return artificially unique connection pool settings.
     */
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
