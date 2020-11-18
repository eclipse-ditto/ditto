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
package org.eclipse.ditto.services.connectivity.messaging.httppush;

import org.eclipse.ditto.services.models.connectivity.ExternalMessage;

import akka.http.javadsl.model.HttpResponse;
import scala.util.Try;

/**
 * Context which passes through the {@link ExternalMessage} to publish to an HTTP endpoint and the {@code requestUri}.
 * Contains the logic to handle 1 response from the HTTP endpoint.
 */
@FunctionalInterface
interface HttpPushContext {

    void onResponse(final Try<HttpResponse> response);
}
