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
package org.eclipse.ditto.connectivity.service.messaging.signing;

import java.time.Instant;
import java.util.Optional;

import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.connectivity.service.messaging.amqp.AmqpConnectionSigning;
import org.eclipse.ditto.connectivity.service.messaging.httppush.HttpRequestSigning;

import akka.NotUsed;
import akka.http.javadsl.model.HttpRequest;
import akka.stream.javadsl.Source;

/**
 * No-op implementation of {@link Signing} for other authentication mechanisms.
 */
public final class NoOpSigning implements HttpRequestSigning, AmqpConnectionSigning {

    public static final NoOpSigning INSTANCE = new NoOpSigning();

    private NoOpSigning() {}

    @Override
    public Source<HttpRequest, NotUsed> sign(final HttpRequest request, final Instant timestamp) {
        return Source.single(request);
    }

    @Override
    public Source<HttpRequest, NotUsed> sign(final HttpRequest request) {
        return Source.single(request);
    }

    @Override
    public Optional<UserPasswordCredentials> createSignedCredentials(final Instant timestamp) {
        return Optional.empty();
    }

}
