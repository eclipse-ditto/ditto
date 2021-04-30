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
package org.eclipse.ditto.gateway.service.security.utils;

import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.service.config.http.HttpProxyConfig;

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.settings.ConnectionPoolSettings;

/**
 * Default implementation of {@link HttpClientFacade}.
 */
public final class DefaultHttpClientFacade implements HttpClientFacade {

    @Nullable private static DefaultHttpClientFacade instance;

    private final ActorSystem actorSystem;
    private final ConnectionPoolSettings connectionPoolSettings;

    private DefaultHttpClientFacade(final ActorSystem actorSystem,
            final ConnectionPoolSettings connectionPoolSettings) {

        this.actorSystem = actorSystem;
        this.connectionPoolSettings = connectionPoolSettings;
    }

    /**
     * Returns the {@code HttpClientProvider} instance.
     *
     * @param actorSystem the {@code ActorSystem} in which to create the HttpClientProvider.
     * @param httpProxyConfig the config of the HTTP proxy.
     * @return the instance.
     */
    public static DefaultHttpClientFacade getInstance(final ActorSystem actorSystem,
            final HttpProxyConfig httpProxyConfig) {

        // the HttpClientProvider is only configured at the very first invocation of getInstance(Config) as we can
        // assume that the config does not change during runtime
        DefaultHttpClientFacade result = instance;
        if (null == result) {
            result = createInstance(actorSystem, httpProxyConfig);
            instance = result;
        }
        return result;
    }

    private static DefaultHttpClientFacade createInstance(final ActorSystem actorSystem,
            final HttpProxyConfig proxyConfig) {
        ConnectionPoolSettings connectionPoolSettings = ConnectionPoolSettings.create(actorSystem);
        if (proxyConfig.isEnabled()) {
            connectionPoolSettings = connectionPoolSettings.withTransport(proxyConfig.toClientTransport());
        }
        return new DefaultHttpClientFacade(actorSystem, connectionPoolSettings);
    }

    @Override
    public CompletionStage<HttpResponse> createSingleHttpRequest(final HttpRequest request) {
        return Http.get(actorSystem)
                .singleRequest(request, Http.get(actorSystem).defaultClientHttpsContext(),
                        connectionPoolSettings,
                        actorSystem.log()
                );
    }

    @Override
    public ActorSystem getActorSystem() {
        return actorSystem;
    }

}
