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

import java.time.Instant;

import akka.NotUsed;
import akka.http.javadsl.model.HttpRequest;
import akka.stream.javadsl.Source;

/**
 * No-op implementation of {@code RequestSigning} for other authentication mechanisms.
 */
final class NoOpRequestSigning implements RequestSigning {

    @Override
    public Source<HttpRequest, NotUsed> sign(final HttpRequest request, final Instant timestamp) {
        return Source.single(request);
    }

    @Override
    public Source<HttpRequest, NotUsed> sign(final HttpRequest request) {
        return Source.single(request);
    }
}
