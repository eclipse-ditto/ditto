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
package org.eclipse.ditto.gateway.service.endpoints.utils;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;

import akka.http.javadsl.model.HttpRequest;

/**
 * Provider of {@link SignalEnrichmentFacade} to be loaded by reflection.
 * Implementations MUST have a public constructor taking the following parameters as arguments:
 * <ul>
 * <li>ActorSystem actorSystem: actor system in which this provider is loaded,</li>
 * <li>Config config: configuration for the facade provider.</li>
 * </ul>
 */
public interface GatewaySignalEnrichmentProvider {

    /**
     * Create a {@link SignalEnrichmentFacade} from the HTTP request that
     * created the websocket or SSE stream that requires it.
     *
     * @param request the HTTP request.
     * @return the signal-enriching facade.
     */
    CompletionStage<SignalEnrichmentFacade> getFacade(HttpRequest request);

}
