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
package org.eclipse.ditto.connectivity.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Represents an uri within the Connectivity service.
 *
 * @since 3.2.0
 */
@Immutable
final class ConnectionUri {

    private static final String MASKED_URI_PATTERN = "{0}://{1}{2}:{3,number,#}{4}";

    @SuppressWarnings("squid:S2068") // S2068 tripped due to 'PASSWORD' in variable name
    private static final String USERNAME_PASSWORD_SEPARATOR = ":";

    private final String uriString;
    private final String protocol;
    private final String hostname;
    private final int port;
    private final String path;
    @Nullable private final String userName;
    @Nullable private final String password;
    private final String uriStringWithMaskedPassword;

    private ConnectionUri(@Nullable final String theUriString) {
        if (!isBlankOrNull(theUriString)) {
            final URI uri;
            try {
                uri = new URI(theUriString).parseServerAuthority();
            } catch (final URISyntaxException e) {
                throw ConnectionUriInvalidException.newBuilder(theUriString).build();
            }
            // validate self
            if (!isValid(uri)) {
                throw ConnectionUriInvalidException.newBuilder(theUriString).build();
            }
            uriString = uri.toASCIIString();
            protocol = uri.getScheme();
            hostname = uri.getHost();
            port = uri.getPort();
            path = uri.getPath();

            // initialize nullable fields
            final String userInfo = uri.getUserInfo();
            if (userInfo != null && userInfo.contains(USERNAME_PASSWORD_SEPARATOR)) {
                final int separatorIndex = userInfo.indexOf(USERNAME_PASSWORD_SEPARATOR);
                userName = userInfo.substring(0, separatorIndex);
                password = userInfo.substring(separatorIndex + 1);
            } else {
                userName = null;
                password = null;
            }

            // must be initialized after all else
            uriStringWithMaskedPassword = createUriStringWithMaskedPassword();
        } else {
            uriString = "";
            protocol = "";
            hostname = "";
            port = 9999;
            path = "";
            userName = null;
            password = null;
            uriStringWithMaskedPassword = "";
        }
    }

    private String createUriStringWithMaskedPassword() {
        return MessageFormat.format(MASKED_URI_PATTERN, protocol, getUserCredentialsOrEmptyString(), hostname, port,
                getPathOrEmptyString());
    }

    private String getUserCredentialsOrEmptyString() {
        if (null != userName && null != password) {
            return userName + ":*****@";
        }
        return "";
    }

    private String getPathOrEmptyString() {
        return getPath().orElse("");
    }

    /**
     * Test validity of a connection URI. A connection URI is valid if it has an explicit port number ,has no query
     * parameters, and has a nonempty password whenever it has a nonempty username.
     *
     * @param uri the URI object with which the connection URI is created.
     * @return whether the connection URI is valid.
     */
    static boolean isValid(final URI uri) {
        return uri.getPort() > 0 && uri.getQuery() == null;
    }

    /**
     * Returns a new instance of {@code ConnectionUri}. The is the reverse function of {@link #toString()}.
     *
     * @param uriString the string representation of the Connection URI.
     * @return the instance.
     * @throws NullPointerException if {@code uriString} is {@code null}.
     * @throws ConnectionUriInvalidException if {@code uriString} is not a valid URI.
     * @see #toString()
     */
    static ConnectionUri of( @Nullable final String uriString) {
        return new ConnectionUri(uriString);
    }

    String getProtocol() {
        return protocol;
    }

    Optional<String> getUserName() {
        return Optional.ofNullable(userName);
    }

    Optional<String> getPassword() {
        return Optional.ofNullable(password);
    }

    String getHostname() {
        return hostname;
    }

    int getPort() {
        return port;
    }

    static boolean isBlankOrNull(@Nullable final String toTest) {
        return null == toTest || toTest.trim().isEmpty();
    }

    /**
     * Returns the path or an empty string.
     *
     * @return the path or an empty string.
     */
    Optional<String> getPath() {
        return path.isEmpty() ? Optional.empty() : Optional.of(path);
    }

    String getUriStringWithMaskedPassword() {
        return uriStringWithMaskedPassword;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ConnectionUri that = (ConnectionUri) o;
        return Objects.equals(uriString, that.uriString);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uriString);
    }

    /**
     * @return the string representation of this ConnectionUri. This is the reverse function of {@link #of(String)}.
     * @see #of(String)
     */
    @Override
    public String toString() {
        return uriString;
    }

}