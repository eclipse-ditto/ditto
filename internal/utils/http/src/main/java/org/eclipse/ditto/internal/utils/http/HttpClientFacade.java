/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.http;

import java.util.concurrent.CompletionStage;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;

/**
 * Provides a pre-configured Akka HTTP client.
 */
public interface HttpClientFacade {

    /**
     * Creates a CompletionStage for the passed {@link HttpRequest} containing the {@link HttpResponse}.
     *
     * @param request the HTTP to create the response for.
     * @return the HttpResponse CompletionStage.
     */
    CompletionStage<HttpResponse> createSingleHttpRequest(HttpRequest request);

    /**
     * @return an {@link akka.actor.ActorSystem} instance which can be used for stream execution.
     */
    ActorSystem getActorSystem();

}
