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
package org.eclipse.ditto.services.gateway.endpoints.utils;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.gateway.endpoints.config.HttpProxyConfig;
import org.eclipse.ditto.services.gateway.util.HttpClientFacade;
import org.eclipse.ditto.services.utils.config.DittoConfigError;

import akka.actor.ActorSystem;
import akka.http.javadsl.ClientTransport;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.headers.HttpCredentials;
import akka.http.javadsl.settings.ConnectionPoolSettings;
import akka.stream.ActorMaterializer;

/**
 * Default implementation of {@link HttpClientFacade}.
 */
public final class DefaultHttpClientFacade implements HttpClientFacade {

    @Nullable private static DefaultHttpClientFacade instance;

    private final ActorSystem actorSystem;
    private final ConnectionPoolSettings connectionPoolSettings;
    private final ActorMaterializer actorMaterializer;

    private DefaultHttpClientFacade(final ActorSystem actorSystem, final ActorMaterializer actorMaterializer,
            final ConnectionPoolSettings connectionPoolSettings) {

        this.actorSystem = actorSystem;
        this.actorMaterializer = actorMaterializer;
        this.connectionPoolSettings = connectionPoolSettings;
    }

    /**
     * Returns the {@code HttpClientProvider} instance.
     *
     * @param actorSystem the {@code ActorSystem} in which to create the HttpClientProvider.
     * @param httpProxyConfig the config of the HTTP proxy.
     * @return the instance.
     */
    public static DefaultHttpClientFacade getInstance(final ActorSystem actorSystem, final HttpProxyConfig httpProxyConfig) {

        // the HttpClientProvider is only configured at the very first invocation of getInstance(Config) as we can
        // assume that the config does not change during runtime
        DefaultHttpClientFacade result = instance;
        if (null == result) {
            result = createInstance(actorSystem, httpProxyConfig);
            instance = result;
        }
        return result;
    }

    private static DefaultHttpClientFacade createInstance(final ActorSystem actorSystem, final HttpProxyConfig proxyConfig) {
        ConnectionPoolSettings connectionPoolSettings = ConnectionPoolSettings.create(actorSystem);
        if (proxyConfig.isEnabled()) {
            connectionPoolSettings = connectionPoolSettings.withTransport(getProxyClientTransport(proxyConfig));
        }
        return new DefaultHttpClientFacade(actorSystem, ActorMaterializer.create(actorSystem), connectionPoolSettings);
    }

    private static ClientTransport getProxyClientTransport(final HttpProxyConfig proxyConfig) {
        final String hostname = proxyConfig.getHostname();
        final int port = proxyConfig.getPort();
        if (hostname.isEmpty() || 0 == port) {
            throw new DittoConfigError("When HTTP proxy is enabled via config, at least proxy hostname and port must " +
                    "be configured as well!");
        }
        final InetSocketAddress inetSocketAddress = InetSocketAddress.createUnresolved(hostname, port);

        final String username = proxyConfig.getUsername();
        final String password = proxyConfig.getPassword();
        if (!username.isEmpty() && !password.isEmpty()) {
            return ClientTransport.httpsProxy(inetSocketAddress, HttpCredentials.create(username, password));
        }
        return ClientTransport.httpsProxy(inetSocketAddress);
    }

    @Override
    public CompletionStage<HttpResponse> createSingleHttpRequest(final HttpRequest request) {
        return Http.get(actorSystem).singleRequest(request,
                Http.get(actorSystem).defaultClientHttpsContext(),
                connectionPoolSettings,
                actorSystem.log(),
                actorMaterializer
        );
    }

    @Override
    public ActorMaterializer getActorMaterializer() {
        return actorMaterializer;
    }

}
