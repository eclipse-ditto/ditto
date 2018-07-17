/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
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
