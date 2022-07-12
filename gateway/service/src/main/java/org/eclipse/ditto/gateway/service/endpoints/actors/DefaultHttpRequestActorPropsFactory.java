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
package org.eclipse.ditto.gateway.service.endpoints.actors;

import java.util.concurrent.CompletableFuture;

import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.gateway.service.util.config.endpoints.CommandConfig;
import org.eclipse.ditto.gateway.service.util.config.endpoints.HttpConfig;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;

/**
 * Default creator of Props of HTTP request actors.
 */
public final class DefaultHttpRequestActorPropsFactory implements HttpRequestActorPropsFactory {

    private DefaultHttpRequestActorPropsFactory(final ActorSystem actorSystem, final Config config) {
        //NoOp Constructor to match extension instantiation
    }

    @Override
    public Props props(final ActorRef proxyActor, final HeaderTranslator headerTranslator,
            final HttpRequest httpRequest,
            final CompletableFuture<HttpResponse> httpResponseFuture,
            final HttpConfig httpConfig,
            final CommandConfig commandConfig) {

        return HttpRequestActor.props(proxyActor,
                headerTranslator,
                httpRequest,
                httpResponseFuture,
                httpConfig,
                commandConfig);
    }

}
