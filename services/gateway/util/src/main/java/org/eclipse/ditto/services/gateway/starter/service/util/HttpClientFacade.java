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
package org.eclipse.ditto.services.gateway.starter.service.util;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletionStage;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;
import akka.http.javadsl.ClientTransport;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.headers.HttpCredentials;
import akka.http.javadsl.settings.ConnectionPoolSettings;
import akka.stream.ActorMaterializer;

/**
 * Provides a pre-configured Akka HTTP client.
 */
public final class HttpClientFacade {

    private static HttpClientFacade INSTANCE;

    private final ActorSystem actorSystem;
    private final ConnectionPoolSettings connectionPoolSettings;
    private final ActorMaterializer actorMaterializer;

    private HttpClientFacade(final ActorSystem actorSystem,
            final boolean proxyEnabled, final String proxyHost, final Integer proxyPort,
            final String proxyUsername, final String proxyPassword) {

        this.actorSystem = actorSystem;
        if (proxyEnabled) {
            final ClientTransport proxyClientTransport;
            if (proxyHost != null && proxyPort != null && proxyUsername != null && proxyPassword != null) {
                proxyClientTransport = ClientTransport.httpsProxy(
                        InetSocketAddress.createUnresolved(proxyHost, proxyPort),
                        HttpCredentials.create(proxyUsername, proxyPassword)
                );
            } else if (proxyHost != null && proxyPort != null) {
                proxyClientTransport = ClientTransport.httpsProxy(
                        InetSocketAddress.createUnresolved(proxyHost, proxyPort)
                );
            } else {
                throw new IllegalArgumentException("When HTTP proxy is enabled via config '" +
                        ConfigKeys.AUTHENTICATION_HTTP_PROXY_ENABLED +
                        "' at least proxy host and port must be configured as well");
            }
            connectionPoolSettings = ConnectionPoolSettings.create(actorSystem).withTransport(proxyClientTransport);
        } else {
            connectionPoolSettings = ConnectionPoolSettings.create(actorSystem);
        }
        actorMaterializer = ActorMaterializer.create(actorSystem);
    }

    /**
     * Returns the {@code HttpClientProvider} instance.
     *
     * @param actorSystem the {@code ActorSystem} in which to create the HttpClientProvider.
     * @return the instance.
     */
    public static HttpClientFacade getInstance(final ActorSystem actorSystem) {
        final Config config = actorSystem.settings().config();
        // the HttpClientProvider is only configured at the very first invocation of getInstance(Config) as we can assume
        // that the config does not change during Runtime
        if (null == INSTANCE) {
            // this is the only non-null mandatory entry:
            final boolean proxyEnabled = config.getBoolean(ConfigKeys.AUTHENTICATION_HTTP_PROXY_ENABLED);

            // all others may also be null:
            final String proxyHost = getFromConf(config, ConfigKeys.AUTHENTICATION_HTTP_PROXY_HOST, String.class);
            Integer proxyPort;
            try {
                proxyPort = getFromConf(config, ConfigKeys.AUTHENTICATION_HTTP_PROXY_PORT, Integer.class);
                // this might be a String when passed via system properties - therefore we might get a ClassCastException:
            } catch (final ClassCastException e) {
                // try String as fallback
                final String proxyPortString = getFromConf(config, ConfigKeys.AUTHENTICATION_HTTP_PROXY_PORT, String.class);
                proxyPort = proxyPortString != null ? Integer.parseInt(proxyPortString) : null;
            }
            final String proxyUsername =
                    getFromConf(config, ConfigKeys.AUTHENTICATION_HTTP_PROXY_USERNAME, String.class);
            final String proxyPassword =
                    getFromConf(config, ConfigKeys.AUTHENTICATION_HTTP_PROXY_PASSWORD, String.class);
            INSTANCE =
                    new HttpClientFacade(actorSystem, proxyEnabled, proxyHost, proxyPort, proxyUsername, proxyPassword);
        }
        return INSTANCE;
    }

    private static <T> T getFromConf(final Config config, final String configKey, final Class<T> expectedType) {
        if (config.hasPath(configKey)) {
            return expectedType.cast(config.getValue(configKey).unwrapped());
        } else {
            return null;
        }
    }

    /**
     * Creates a CompletionStage for the passed {@link HttpRequest} containing the {@link HttpResponse}.
     *
     * @return the HttpResponse CompletionStage.
     */
    public CompletionStage<HttpResponse> createSingleHttpRequest(final HttpRequest request) {
        return Http.get(actorSystem).singleRequest(
                request,
                Http.get(actorSystem).defaultClientHttpsContext(),
                connectionPoolSettings,
                actorSystem.log(),
                actorMaterializer
        );
    }

    /**
     * @return an {@link ActorMaterializer} instance which can be used for stream execution.
     */
    public ActorMaterializer getActorMaterializer() {
        return actorMaterializer;
    }
}
