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
package org.eclipse.ditto.connectivity.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * Immutable implementation of {@link SshTunnel}.
 */
@Immutable
final class ImmutableSshTunnel implements SshTunnel {

    private final boolean enabled;
    private final Credentials credentials;
    private final boolean validateHost;
    private final List<String> knownHosts;
    private final String uri;

    private ImmutableSshTunnel(final Builder builder) {
        enabled = checkNotNull(builder.enabled, "enabled");
        credentials = checkNotNull(builder.credentials, "credentials");
        validateHost = builder.validateHost;
        knownHosts = Collections.unmodifiableList(new ArrayList<>(checkNotNull(builder.knownHosts, "knownHosts")));
        uri = checkNotNull(builder.uri, "uri");
    }

    /**
     * Returns a new {@code SshTunnelBuilder} object.
     *
     * @param enabled the enabled status.
     * @param credentials the credentials.
     * @param validateHost {@code true} if host validation is enabled
     * @param knownHosts the known hosts.
     * @param uri the URI.
     * @return new instance of {@code SshTunnelBuilder}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SshTunnelBuilder getBuilder(final boolean enabled,
            final Credentials credentials,
            final boolean validateHost,
            final List<String> knownHosts,
            final String uri) {

        return new Builder(enabled, credentials, validateHost, knownHosts, uri);
    }

    /**
     * Returns a new {@code SshTunnelBuilder} object.
     *
     * @param enabled the enabled status.
     * @param credentials the credentials.
     * @param uri the URI.
     * @return new instance of {@code SshTunnelBuilder}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static SshTunnelBuilder getBuilder(final boolean enabled,
            final Credentials credentials,
            final String uri) {

        return new Builder(enabled, credentials, uri);
    }


    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Credentials getCredentials() {
        return credentials;
    }

    @Override
    public boolean isValidateHost() {
        return validateHost;
    }

    @Override
    public List<String> getKnownHosts() {
        return knownHosts;
    }

    @Override
    public String getUri() {
        return uri;
    }

    /**
     * Creates a new {@code SshTunnel} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the SshTunnel to be created.
     * @return a new SshTunnel which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static SshTunnel fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "jsonObject");
        return new Builder(extractEnabled(jsonObject),
                extractCredentials(jsonObject),
                extractValidateHost(jsonObject),
                extractKnownHosts(jsonObject),
                extractUri(jsonObject))
                .build();
    }

    private static boolean extractEnabled(final JsonObject jsonObject) {
        return jsonObject.getValueOrThrow(JsonFields.ENABLED);
    }

    private static Credentials extractCredentials(final JsonObject jsonObject) {
        return Credentials.fromJson(jsonObject.getValueOrThrow(JsonFields.CREDENTIALS));
    }

    private static List<String> extractKnownHosts(final JsonObject jsonObject) {
        return jsonObject.getValue(JsonFields.KNOWN_HOSTS)
                .map(array -> array.stream()
                        .map(JsonValue::asString)
                        .collect(Collectors.toList())).orElse(Collections.emptyList());
    }

    private static boolean extractValidateHost(final JsonObject jsonObject) {
        return jsonObject.getValueOrThrow(JsonFields.VALIDATE_HOST);
    }

    private static String extractUri(final JsonObject jsonObject) {
        return jsonObject.getValueOrThrow(JsonFields.URI);
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);

        return JsonObject.newBuilder()
                .set(JsonFields.ENABLED, enabled, predicate)
                .set(JsonFields.CREDENTIALS, credentials.toJson(), predicate)
                .set(JsonFields.VALIDATE_HOST, validateHost, predicate)
                .set(JsonFields.KNOWN_HOSTS, JsonArray.of(knownHosts), predicate)
                .set(JsonFields.URI, uri, predicate)
                .build();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableSshTunnel that = (ImmutableSshTunnel) o;
        return Objects.equals(enabled, that.enabled) &&
                Objects.equals(credentials, that.credentials) &&
                Objects.equals(validateHost, that.validateHost) &&
                Objects.equals(knownHosts, that.knownHosts) &&
                Objects.equals(uri, that.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, credentials, validateHost, knownHosts, uri);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enabled=" + enabled +
                ", credentials=" + credentials +
                ", validateHost=" + validateHost +
                ", knownHosts=" + knownHosts +
                ", uri=" + uri +
                "]";
    }

    /**
     * Builder for {@code SshTunnel}.
     */
    @NotThreadSafe
    static final class Builder implements SshTunnelBuilder {

        // required but changeable:
        private boolean enabled;
        private Credentials credentials;
        private String uri;

        // optional with Default:
        private boolean validateHost = false;
        private List<String> knownHosts = new ArrayList<>();

        Builder(final boolean enabled, final Credentials credentials, final String uri) {
            this.enabled = enabled;
            this.credentials = credentials;
            this.uri = uri;
        }

        Builder(final boolean enabled, final Credentials credentials, final boolean validateHost,
                final List<String> knownHosts, final String uri) {
            this.enabled = enabled;
            this.credentials = credentials;
            this.uri = uri;
            this.validateHost = validateHost;
            this.knownHosts = knownHosts;
        }

        Builder(final SshTunnel sshTunnel) {
            enabled = sshTunnel.isEnabled();
            credentials = sshTunnel.getCredentials();
            uri = sshTunnel.getUri();
            validateHost = sshTunnel.isValidateHost();
            knownHosts = sshTunnel.getKnownHosts();
        }

        @Override
        public SshTunnelBuilder enabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        @Override
        public SshTunnelBuilder credentials(final Credentials credentials) {
            this.credentials = checkNotNull(credentials, "credentials");
            return this;
        }

        @Override
        public SshTunnelBuilder validateHost(final boolean validateHost) {
            this.validateHost = validateHost;
            return this;
        }

        @Override
        public SshTunnelBuilder knownHosts(final List<String> knownHosts) {
            this.knownHosts = checkNotNull(knownHosts, "knownHosts");
            return this;
        }

        @Override
        public SshTunnelBuilder uri(final String uri) {
            this.uri = checkNotNull(uri, "uri");
            return this;
        }

        @Override
        public SshTunnel build() {
            return new ImmutableSshTunnel(this);
        }

    }

}
