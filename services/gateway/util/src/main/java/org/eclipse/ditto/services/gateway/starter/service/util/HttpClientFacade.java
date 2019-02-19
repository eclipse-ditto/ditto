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
package org.eclipse.ditto.services.gateway.starter.service.util;

import java.util.concurrent.CompletionStage;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.stream.ActorMaterializer;

public interface HttpClientFacade {

    /**
     * Returns the http client facade.
     *
     * @param actorSystem the {@code ActorSystem} in which to create the http client facade.
     * @return the instance.
     */
    static HttpClientFacade getInstance(ActorSystem actorSystem) {
        return DefaultHttpClientFacade.getInstance(actorSystem);
    }

    /**
     * Creates a CompletionStage for the passed {@link akka.http.javadsl.model.HttpRequest} containing the {@link akka.http.javadsl.model.HttpResponse}.
     *
     * @return the HttpResponse CompletionStage.
     */
    CompletionStage<HttpResponse> createSingleHttpRequest(HttpRequest request);

    /**
     * @return an {@link akka.stream.ActorMaterializer} instance which can be used for stream execution.
     */
    ActorMaterializer getActorMaterializer();
}
