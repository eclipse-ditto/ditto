/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.connectivity.model.HmacCredentials;

import akka.actor.ActorSystem;

/**
 * Functional interface for creator of {@code RequestSigning} from {@code HmacCredentials}.
 */
@FunctionalInterface
public interface HttpRequestSigningFactory {

    /**
     * Create a {@code RequestSigning} object from HMAC credentials.
     *
     * @param actorSystem the actor system.
     * @param credentials the credentials.
     * @return the request signing process using the given credentials.
     */
    HttpRequestSigning create(ActorSystem actorSystem, HmacCredentials credentials);

}
