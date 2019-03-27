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

import static java.util.Objects.requireNonNull;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import akka.http.javadsl.model.HttpEntity;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.util.ByteString;

/**
 * A reader for a {@link akka.http.javadsl.model.HttpEntity}.
 */
public final class HttpEntityReader {

    private static final long TIMEOUT_SECONDS = 5;

    private HttpEntityReader() {
        throw new AssertionError();
    }

    /**
     * Unfolds the entity into a string using akka streams.
     *
     * @param entity the entity to unfold.
     * @param materializer the materializer to run the unfold stream.
     * @return the string representation of the entity.
     */
    public static String entityToString(final HttpEntity entity, final Materializer materializer) {
        requireNonNull(entity);
        requireNonNull(materializer);

        if (entity.isKnownEmpty()) {
            return "";
        }

        try {
            return entity.getDataBytes()
                    .fold(ByteString.empty(), ByteString::concat)
                    .map(ByteString::utf8String)
                    .runWith(Sink.head(), materializer)
                    .toCompletableFuture()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (final InterruptedException | ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to read entity.", e);
        }
    }

}
