/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.gateway.streaming;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.Nullable;

/**
 * Represents the identifier of a streaming session combined of a client session identifier and a server session
 * identifier.
 *
 * @since 1.1.0
 */
public final class StreamingSessionIdentifier implements CharSequence {

    /**
     * This MUST NOT be a colon ":" as we use the combined identifier as Akka Actor name which has a problem when this
     * is a colon. Must also not likely be used as part of a {@code correlation-id} send by the client.
     */
    static final String DELIMITER = "_-_";

    private final String clientSessionId;
    private final String serverSessionId;

    private StreamingSessionIdentifier(final String clientSessionId, final String serverSessionId) {
        this.clientSessionId = clientSessionId;
        this.serverSessionId = serverSessionId;
    }

    /**
     * Creates a new StreamingSessionIdentifier of the passed CharSequence already containing the delimiter
     * {@value #DELIMITER} delimiting clientSessionId and serverSessionId.
     *
     * @param streamingSessionIdentifier the combined session identifier containing clientSessionId and serverSessionId.
     * @return the instance.
     * @throws IllegalArgumentException if the passed {@code streamingSessionIdentifier} did not contain a
     * {@value #DELIMITER}.
     * @throws NullPointerException if {@code streamingSessionIdentifier} is {@code null}.
     */
    public static StreamingSessionIdentifier of(final CharSequence streamingSessionIdentifier) {
        checkNotNull(streamingSessionIdentifier, "streamingSessionIdentifier");
        if (streamingSessionIdentifier instanceof StreamingSessionIdentifier) {
            return (StreamingSessionIdentifier) streamingSessionIdentifier;
        }

        final String combinedSessionId = streamingSessionIdentifier.toString();
        final int delimiterIdx = combinedSessionId.indexOf(DELIMITER);
        if (delimiterIdx < 0) {
            throw new IllegalArgumentException("Combined streaming session identifier does not contain the required " +
                    " delimiter: " + DELIMITER);
        }
        final String clientSessionId = combinedSessionId.substring(0, delimiterIdx);
        final String serverSessionId = combinedSessionId.substring(delimiterIdx + DELIMITER.length());
        return of(clientSessionId, serverSessionId);
    }

    /**
     * Creates a new StreamingSessionIdentifier of the passed {@code clientSessionId} and {@code serverSessionId}.
     *
     * @param clientSessionId the client session identifier potentially passed by the client itself.
     * @param serverSessionId the server session identifier which was created by the server.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static StreamingSessionIdentifier of(final CharSequence clientSessionId,
            final CharSequence serverSessionId) {
        checkNotNull(clientSessionId, "clientSessionId");
        checkNotNull(serverSessionId, "serverSessionId");
        return new StreamingSessionIdentifier(clientSessionId.toString(), serverSessionId.toString());
    }

    /**
     * Returns the client session identifier potentially passed by the client itself.
     *
     * @return the client session identifier potentially passed by the client itself.
     */
    public String getClientSessionId() {
        return clientSessionId;
    }

    /**
     * Returns the server session identifier which was created by the server.
     *
     * @return the server session identifier which was created by the server.
     */
    public String getServerSessionId() {
        return serverSessionId;
    }

    @Override
    public int length() {
        return toString().length();
    }

    @Override
    public char charAt(final int index) {
        return toString().charAt(index);
    }

    @Override
    public CharSequence subSequence(final int start, final int end) {
        return toString().subSequence(start, end);
    }

    @SuppressWarnings("squid:S2097")
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (null == o) {
            return false;
        }
        final Class<? extends StreamingSessionIdentifier> thisClass = getClass();
        final Class<?> otherClass = o.getClass();
        if (thisClass == otherClass) {
            final StreamingSessionIdentifier that = (StreamingSessionIdentifier) o;
            return Objects.equals(clientSessionId, that.clientSessionId) &&
                    Objects.equals(serverSessionId, that.serverSessionId);
        }
        return Objects.equals(toString(), o.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(toString());
    }

    @Override
    public String toString() {
        return clientSessionId + DELIMITER + serverSessionId;
    }
}
