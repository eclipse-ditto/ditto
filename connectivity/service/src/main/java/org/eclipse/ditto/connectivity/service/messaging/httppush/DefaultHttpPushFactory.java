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
package org.eclipse.ditto.connectivity.service.messaging.httppush;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.service.config.HttpPushConfig;
import org.eclipse.ditto.connectivity.service.messaging.tunnel.SshTunnelState;
import org.eclipse.ditto.connectivity.service.messaging.internal.ssl.SSLContextCreator;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.services.utils.akka.controlflow.TimeoutFlow;

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
import akka.stream.javadsl.Flow;
import scala.util.Failure;
import scala.util.Try;

/**
 * Default implementation of {@link HttpPushFactory}.
 */
final class DefaultHttpPushFactory implements HttpPushFactory {

    private static final String PATH_DELIMITER = "/";

    private final Connection connection;
    private final int parallelism;
    private final Supplier<SshTunnelState> tunnelConfigSupplier;

    @Nullable
    private final ClientTransport clientTransport;

    @Nullable
    private final HttpsConnectionContext httpsConnectionContext;

    private DefaultHttpPushFactory(final Connection connection, final int parallelism,
            final HttpPushConfig httpPushConfig, @Nullable final HttpsConnectionContext httpsConnectionContext,
            final Supplier<SshTunnelState> tunnelConfigSupplier) {
        this.connection = connection;
        this.parallelism = parallelism;
        this.tunnelConfigSupplier = tunnelConfigSupplier;
        if (!httpPushConfig.getHttpProxyConfig().isEnabled()) {
            clientTransport = null;
        } else {
            clientTransport = httpPushConfig.getHttpProxyConfig().toClientTransport();
        }
        this.httpsConnectionContext = httpsConnectionContext;
    }

    static HttpPushFactory of(final Connection connection, final HttpPushConfig httpPushConfig,
            final ConnectionLogger connectionLogger, final Supplier<SshTunnelState> tunnelConfigSupplier) {
        final Uri baseUri = Uri.create(connection.getUri());
        final int parallelism = parseParallelism(connection.getSpecificConfig());

        final HttpsConnectionContext httpsConnectionContext;
        if (HttpPushValidator.isSecureScheme(baseUri.getScheme())) {
            final SSLContextCreator sslContextCreator =
                    SSLContextCreator.fromConnection(connection, DittoHeaders.empty(), connectionLogger);
            final SSLContext sslContext = connection.getCredentials()
                    .map(credentials -> credentials.accept(sslContextCreator))
                    .orElse(sslContextCreator.withoutClientCertificate());
            httpsConnectionContext = ConnectionContext.https(sslContext);
        } else {
            httpsConnectionContext = null;
        }

        return new DefaultHttpPushFactory(connection, parallelism, httpPushConfig, httpsConnectionContext, tunnelConfigSupplier);
    }

    @Override
    public HttpRequest newRequest(final HttpPublishTarget httpPublishTarget) {
        final Uri baseUri = getBaseUri();
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

    private Uri getBaseUri() {
        return Uri.create(tunnelConfigSupplier.get().getURI(connection).toString());
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
            final LoggingAdapter log, final Duration requestTimeout) {

        final Http http = Http.get(system);
        final ConnectionPoolSettings poolSettings = getConnectionPoolSettings(system);
        final Flow<Pair<HttpRequest, T>, Pair<Try<HttpResponse>, T>, ?> flow;
        final Uri baseUri = getBaseUri();
        if (null != httpsConnectionContext) {
            final ConnectHttp connectHttpsWithCustomSSLContext =
                    ConnectHttp.toHostHttps(baseUri).withCustomHttpsContext(httpsConnectionContext);
            // explicitly added <T> as in (some?) IntelliJ idea the line would show an error:
            flow = http.<T>cachedHostConnectionPoolHttps(connectHttpsWithCustomSSLContext, poolSettings, log);
        } else {
            // explicitly added <T> as in (some?) IntelliJ idea the line would show an error:
            // no SSL, hence no need for SSLContextCreator
            flow = http.<T>cachedHostConnectionPool(ConnectHttp.toHost(baseUri), poolSettings, log);
        }

        // make requests in parallel
        return Flow.<Pair<HttpRequest, T>>create().flatMapMerge(parallelism, request ->
                TimeoutFlow.single(request, flow, requestTimeout, DefaultHttpPushFactory::onRequestTimeout));
    }

    private ConnectionPoolSettings getConnectionPoolSettings(final ActorSystem system) {
        final ConnectionPoolSettings settings =
                disambiguateByConnectionId(system, connection.getId()).withMaxConnections(parallelism);
        return clientTransport == null
                ? settings
                : settings.withTransport(clientTransport);
    }

    private static <T> Pair<Try<HttpResponse>, T> onRequestTimeout(final Pair<HttpRequest, T> requestPair) {
        final Try<HttpResponse> failure =
                new Failure<>(new TimeoutException("Request timed out: " + requestPair.first().getUri()));
        return Pair.create(failure, requestPair.second());
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

        final ParserSettings parserSettings = ParserSettings.forClient(system);

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
