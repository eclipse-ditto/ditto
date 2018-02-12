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
package org.eclipse.ditto.model.amqpbridge;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link AmqpConnection}.
 */
@Immutable
final class ImmutableAmqpConnection implements AmqpConnection {

    private static final Pattern URI_REGEX_PATTERN = Pattern.compile(AmqpConnection.UriRegex.REGEX);

    private final String id;
    private final ConnectionType connectionType;
    private final AuthorizationSubject authorizationSubject;
    private final Set<String> sources;
    private final boolean failoverEnabled;
    private final boolean validateCertificate;
    private final int throttle;
    private final String uri;
    private final String protocol;
    private final String username;
    private final String password;
    private final String hostname;
    private final int port;

    private ImmutableAmqpConnection(final String id,
            final ConnectionType connectionType, final String uri,
            final AuthorizationSubject authorizationSubject,
            final Set<String> sources, final boolean failoverEnabled, final boolean validateCertificates,
            final int throttle) {
        this.id = id;
        this.connectionType = connectionType;
        this.uri = uri;
        this.authorizationSubject = authorizationSubject;
        this.sources = Collections.unmodifiableSet(new HashSet<>(sources));
        this.failoverEnabled = failoverEnabled;
        this.validateCertificate = validateCertificates;
        this.throttle = throttle;

        final Matcher matcher = URI_REGEX_PATTERN.matcher(uri);

        if (matcher.matches()) {
            protocol = matcher.group(AmqpConnection.UriRegex.PROTOCOL_REGEX_GROUP);
            username = matcher.group(AmqpConnection.UriRegex.USERNAME_REGEX_GROUP);
            password = matcher.group(AmqpConnection.UriRegex.PASSWORD_REGEX_GROUP);
            hostname = matcher.group(AmqpConnection.UriRegex.HOSTNAME_REGEX_GROUP);
            port = Integer.parseInt(matcher.group(AmqpConnection.UriRegex.PORT_REGEX_GROUP));
        } else {
            throw ConnectionUriInvalidException.newBuilder(uri).build();
        }
    }

    /**
     * Returns a new {@code ImmutableConnection}.
     *
     * @param id the connection identifier.
     * @param connectionType the connection type
     * @param uri the connection uri.
     * @param authorizationSubject the connection authorization subject.
     * @param sources the connection sources.
     * @param failoverEnabled whether failover is enabled for the connection or not.
     * @param validateCertificates whether to validate server certificates
     * @param throttle limit of processed messages
     * @return the ImmutableConnection.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws ConnectionUriInvalidException if {@code uri} does not conform to {@link
     * AmqpConnection.UriRegex#REGEX}.
     */
    public static ImmutableAmqpConnection of(final String id,
            final ConnectionType connectionType, final String uri,
            final AuthorizationSubject authorizationSubject, final Set<String> sources, final boolean failoverEnabled,
            final boolean validateCertificates, final int throttle) {
        checkNotNull(id, "ID");
        checkNotNull(connectionType, "Connection Type");
        checkNotNull(uri, "URI");
        checkNotNull(authorizationSubject, "Authorization Subject");
        checkNotNull(sources, "Sources");
        checkNotNull(failoverEnabled, "Failover Enabled");

        return new ImmutableAmqpConnection(id, connectionType, uri, authorizationSubject, sources, failoverEnabled,
                validateCertificates, throttle);
    }

    /**
     * Creates a new {@code Connection} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the Connection to be created.
     * @return a new Connection which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static ImmutableAmqpConnection fromJson(final JsonObject jsonObject) {
        final String readId = jsonObject.getValueOrThrow(JsonFields.ID);
        final ConnectionType readConnectionType = ConnectionType.forName(readId.substring(0, readId.indexOf(':')))
                .orElseThrow(() -> JsonParseException.newBuilder().message("Invalid connection type.").build());
        final String readUri = jsonObject.getValueOrThrow(JsonFields.URI);
        final AuthorizationSubject readAuthorizationSubject =
                AuthorizationSubject.newInstance(jsonObject.getValueOrThrow(JsonFields.AUTHORIZATION_SUBJECT));
        final Set<String> readSources = jsonObject.getValueOrThrow(JsonFields.SOURCES).stream()
                .map(JsonValue::asString)
                .collect(Collectors.toSet());
        final Boolean readFailoverEnabled = jsonObject.getValueOrThrow(JsonFields.FAILOVER_ENABLED);
        final Boolean readValidateCertificates = jsonObject.getValue(JsonFields.VALIDATE_CERTIFICATES).orElse(true);
        final Integer readThrottle = jsonObject.getValue(JsonFields.THROTTLE).orElse(0);

        return of(readId, readConnectionType, readUri, readAuthorizationSubject, readSources, readFailoverEnabled,
                readValidateCertificates, readThrottle);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public ConnectionType getConnectionType() {
        return connectionType;
    }

    @Override
    public AuthorizationSubject getAuthorizationSubject() {
        return authorizationSubject;
    }

    @Override
    public Set<String> getSources() {
        return sources;
    }

    @Override
    public boolean isFailoverEnabled() {
        return failoverEnabled;
    }

    @Override
    public String getUri() {
        return uri;
    }

    @Override
    public String getProtocol() {
        return protocol;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getHostname() {
        return hostname;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public int getThrottle() {
        return throttle;
    }

    @Override
    public boolean isValidateCertificates() {
        return validateCertificate;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(JsonFields.SCHEMA_VERSION, schemaVersion.toInt(), predicate);
        jsonObjectBuilder.set(JsonFields.ID, id, predicate);
        jsonObjectBuilder.set(JsonFields.URI, uri, predicate);
        jsonObjectBuilder.set(JsonFields.AUTHORIZATION_SUBJECT, authorizationSubject.getId(), predicate);
        jsonObjectBuilder.set(JsonFields.SOURCES, sources.stream()
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray()), predicate);
        jsonObjectBuilder.set(JsonFields.FAILOVER_ENABLED, failoverEnabled, predicate);
        jsonObjectBuilder.set(JsonFields.VALIDATE_CERTIFICATES, validateCertificate, predicate);
        jsonObjectBuilder.set(JsonFields.THROTTLE, throttle, predicate);

        return jsonObjectBuilder.build();
    }

    @SuppressWarnings("OverlyComplexMethod")
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {return true;}
        if (o == null || getClass() != o.getClass()) {return false;}
        final ImmutableAmqpConnection that = (ImmutableAmqpConnection) o;
        return failoverEnabled == that.failoverEnabled &&
                port == that.port &&
                Objects.equals(id, that.id) &&
                Objects.equals(connectionType, that.connectionType) &&
                Objects.equals(authorizationSubject, that.authorizationSubject) &&
                Objects.equals(sources, that.sources) &&
                Objects.equals(uri, that.uri) &&
                Objects.equals(protocol, that.protocol) &&
                Objects.equals(username, that.username) &&
                Objects.equals(password, that.password) &&
                Objects.equals(hostname, that.hostname) &&
                Objects.equals(throttle, that.throttle) &&
                Objects.equals(validateCertificate, that.validateCertificate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, connectionType, authorizationSubject, sources, failoverEnabled, uri, protocol, username,
                password, hostname, port, validateCertificate, throttle);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "id=" + id +
                "type=" + connectionType +
                ", authorizationSubject=" + authorizationSubject +
                ", sources=" + sources +
                ", failoverEnabled=" + failoverEnabled +
                ", uri=" + uri +
                ", protocol=" + protocol +
                ", username=" + username +
                ", password=" + password +
                ", hostname=" + hostname +
                ", port=" + port +
                ", validateCertificate=" + validateCertificate +
                ", throttle=" + throttle +
                "]";
    }

}
