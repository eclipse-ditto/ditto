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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;

/**
 * Abstract implementation for common aspects of {@link Connection}.
 *
 * @since 3.2.0
 */
abstract class AbstractConnection implements Connection {

    private final ConnectionId id;
    @Nullable private final String name;
    private final ConnectionType connectionType;
    private final ConnectivityStatus connectionStatus;
    final ConnectionUri uri;
    @Nullable private final Credentials credentials;
    @Nullable private final String trustedCertificates;
    @Nullable private final ConnectionLifecycle lifecycle;
    @Nullable private final ConnectionRevision revision;
    @Nullable private final Instant modified;
    @Nullable private final Instant created;
    private final List<Source> sources;
    private final List<Target> targets;
    private final int clientCount;
    private final boolean failOverEnabled;
    private final boolean validateCertificate;
    private final int processorPoolSize;
    private final Map<String, String> specificConfig;
    private final PayloadMappingDefinition payloadMappingDefinition;
    private final Set<String> tags;
    @Nullable private final SshTunnel sshTunnel;

    AbstractConnection(final AbstractConnectionBuilder builder) {
        id = checkNotNull(builder.id, "id");
        name = builder.name;
        connectionType = builder.connectionType;
        connectionStatus = checkNotNull(builder.connectionStatus, "connectionStatus");
        credentials = builder.credentials;
        trustedCertificates = builder.trustedCertificates;
        uri = getConnectionUri(builder.uri);
        sources = Collections.unmodifiableList(new ArrayList<>(builder.sources));
        targets = Collections.unmodifiableList(new ArrayList<>(builder.targets));
        clientCount = builder.clientCount;
        failOverEnabled = builder.failOverEnabled;
        validateCertificate = builder.validateCertificate;
        processorPoolSize = builder.processorPoolSize;
        specificConfig = Collections.unmodifiableMap(new HashMap<>(builder.specificConfig));
        payloadMappingDefinition = builder.payloadMappingDefinition;
        tags = Collections.unmodifiableSet(new LinkedHashSet<>(builder.tags));
        lifecycle = builder.lifecycle;
        revision = builder.revision;
        modified = builder.modified;
        created = builder.created;
        sshTunnel = builder.sshTunnel;
    }

    abstract ConnectionUri getConnectionUri(@Nullable String builderConnectionUri);

    static void buildFromJson(final JsonObject jsonObject, final AbstractConnectionBuilder builder) {
        final MappingContext mappingContext = jsonObject.getValue(JsonFields.MAPPING_CONTEXT)
                .map(ConnectivityModelFactory::mappingContextFromJson)
                .orElse(null);

        final PayloadMappingDefinition payloadMappingDefinition =
                jsonObject.getValue(JsonFields.MAPPING_DEFINITIONS)
                        .map(ImmutablePayloadMappingDefinition::fromJson)
                        .orElse(ConnectivityModelFactory.emptyPayloadMappingDefinition());
        builder.id(ConnectionId.of(jsonObject.getValueOrThrow(JsonFields.ID)))
                .connectionStatus(getConnectionStatusOrThrow(jsonObject))
                .uri(jsonObject.getValueOrThrow(JsonFields.URI))
                .sources(getSources(jsonObject))
                .targets(getTargets(jsonObject))
                .name(jsonObject.getValue(JsonFields.NAME).orElse(null))
                .mappingContext(mappingContext)
                .payloadMappingDefinition(payloadMappingDefinition)
                .specificConfig(getSpecificConfiguration(jsonObject))
                .tags(getTags(jsonObject));

        jsonObject.getValue(JsonFields.LIFECYCLE)
                .flatMap(ConnectionLifecycle::forName).ifPresent(builder::lifecycle);
        jsonObject.getValue(JsonFields.REVISION)
                .map(ConnectionRevision::newInstance).ifPresent(builder::revision);
        jsonObject.getValue(JsonFields.MODIFIED)
                .map(AbstractConnection::tryToParseInstant).ifPresent(builder::modified);
        jsonObject.getValue(JsonFields.CREATED)
                .map(AbstractConnection::tryToParseInstant).ifPresent(builder::created);
        jsonObject.getValue(JsonFields.CREDENTIALS).ifPresent(builder::credentialsFromJson);
        jsonObject.getValue(JsonFields.CLIENT_COUNT).ifPresent(builder::clientCount);
        jsonObject.getValue(JsonFields.FAILOVER_ENABLED).ifPresent(builder::failoverEnabled);
        jsonObject.getValue(JsonFields.VALIDATE_CERTIFICATES).ifPresent(builder::validateCertificate);
        jsonObject.getValue(JsonFields.PROCESSOR_POOL_SIZE).ifPresent(builder::processorPoolSize);
        jsonObject.getValue(JsonFields.TRUSTED_CERTIFICATES).ifPresent(builder::trustedCertificates);
        jsonObject.getValue(JsonFields.SSH_TUNNEL)
                .ifPresent(jsonFields -> builder.sshTunnel(ImmutableSshTunnel.fromJson(jsonFields)));
    }

    static ConnectivityStatus getConnectionStatusOrThrow(final JsonObject jsonObject) {
        final String readConnectionStatus = jsonObject.getValueOrThrow(JsonFields.CONNECTION_STATUS);
        return ConnectivityStatus.forName(readConnectionStatus)
                .orElseThrow(() -> JsonParseException.newBuilder()
                        .message(MessageFormat.format("Connection status <{0}> is invalid!", readConnectionStatus))
                        .build());
    }

    private static List<Source> getSources(final JsonObject jsonObject) {
        final Optional<JsonArray> sourcesArray = jsonObject.getValue(JsonFields.SOURCES);
        if (sourcesArray.isPresent()) {
            final JsonArray values = sourcesArray.get();
            return IntStream.range(0, values.getSize())
                    .mapToObj(index -> values.get(index)
                            .filter(JsonValue::isObject)
                            .map(JsonValue::asObject)
                            .map(valueAsObject -> ConnectivityModelFactory.sourceFromJson(valueAsObject, index)))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    private static List<Target> getTargets(final JsonObject jsonObject) {
        return jsonObject.getValue(JsonFields.TARGETS)
                .map(array -> array.stream()
                        .filter(JsonValue::isObject)
                        .map(JsonValue::asObject)
                        .map(ConnectivityModelFactory::targetFromJson)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    private static Map<String, String> getSpecificConfiguration(final JsonObject jsonObject) {
        return jsonObject.getValue(JsonFields.SPECIFIC_CONFIG)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(JsonObject::stream)
                .map(jsonFields -> jsonFields.collect(Collectors.toMap(JsonField::getKeyName,
                        f -> f.getValue().isString() ? f.getValue().asString() : f.getValue().toString())))
                .orElse(Collections.emptyMap());
    }

    private static Set<String> getTags(final JsonObject jsonObject) {
        return jsonObject.getValue(JsonFields.TAGS)
                .map(array -> array.stream()
                        .filter(JsonValue::isString)
                        .map(JsonValue::asString)
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
                .orElseGet(LinkedHashSet::new);
    }

    @Override
    public ConnectionId getId() {
        return id;
    }

    @Override
    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }

    @Override
    public ConnectionType getConnectionType() {
        return connectionType;
    }

    @Override
    public ConnectivityStatus getConnectionStatus() {
        return connectionStatus;
    }

    @Override
    public List<Source> getSources() {
        return sources;
    }

    @Override
    public List<Target> getTargets() {
        return targets;
    }

    @Override
    public Optional<SshTunnel> getSshTunnel() {
        return Optional.ofNullable(sshTunnel);
    }

    @Override
    public int getClientCount() {
        return clientCount;
    }

    @Override
    public boolean isFailoverEnabled() {
        return failOverEnabled;
    }

    @Override
    public Optional<Credentials> getCredentials() {
        return Optional.ofNullable(credentials);
    }

    @Override
    public Optional<String> getTrustedCertificates() {
        return Optional.ofNullable(trustedCertificates);
    }

    @Override
    public String getUri() {
        return uri.toString();
    }

    @Override
    public String getProtocol() {
        return uri.getProtocol();
    }

    @Override
    public Optional<String> getUsername() {
        return uri.getUserName();
    }

    @Override
    public Optional<String> getPassword() {
        return uri.getPassword();
    }

    @Override
    public String getHostname() {
        return uri.getHostname();
    }

    @Override
    public int getPort() {
        return uri.getPort();
    }

    @Override
    public Optional<String> getPath() {
        return uri.getPath();
    }

    @Override
    public boolean isValidateCertificates() {
        return validateCertificate;
    }

    @Override
    public int getProcessorPoolSize() {
        return processorPoolSize;
    }

    @Override
    public Map<String, String> getSpecificConfig() {
        return specificConfig;
    }

    @Override
    public PayloadMappingDefinition getPayloadMappingDefinition() {
        return payloadMappingDefinition;
    }

    @Override
    public Set<String> getTags() {
        return tags;
    }

    @Override
    public Optional<ConnectionLifecycle> getLifecycle() {
        return Optional.ofNullable(lifecycle);
    }

    @Override
    public Optional<? extends EntityId> getEntityId() {
        return Optional.of(id);
    }

    @Override
    public Optional<ConnectionRevision> getRevision() {
        return Optional.ofNullable(revision);
    }

    @Override
    public Optional<Instant> getModified() {
        return Optional.ofNullable(modified);
    }

    @Override
    public Optional<Instant> getCreated() {
        return Optional.ofNullable(created);
    }

    @Override
    public Optional<Metadata> getMetadata() {
        return Optional.empty(); // currently not metadata support for connections
    }

    @Override
    public boolean isDeleted() {
        return ConnectionLifecycle.DELETED.equals(lifecycle);
    }

    static ConnectionBuilder fromConnection(final Connection connection, final AbstractConnectionBuilder builder) {
        checkNotNull(connection, "Connection");

        return builder
                .id(connection.getId())
                .connectionStatus(connection.getConnectionStatus())
                .credentials(connection.getCredentials().orElse(null))
                .uri(connection.getUri())
                .trustedCertificates(connection.getTrustedCertificates().orElse(null))
                .failoverEnabled(connection.isFailoverEnabled())
                .validateCertificate(connection.isValidateCertificates())
                .processorPoolSize(connection.getProcessorPoolSize())
                .sources(connection.getSources())
                .targets(connection.getTargets())
                .clientCount(connection.getClientCount())
                .specificConfig(connection.getSpecificConfig())
                .payloadMappingDefinition(connection.getPayloadMappingDefinition())
                .name(connection.getName().orElse(null))
                .sshTunnel(connection.getSshTunnel().orElse(null))
                .tags(connection.getTags())
                .lifecycle(connection.getLifecycle().orElse(null))
                .revision(connection.getRevision().orElse(null))
                .modified(connection.getModified().orElse(null))
                .created(connection.getCreated().orElse(null));
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        if (null != lifecycle) {
            jsonObjectBuilder.set(JsonFields.LIFECYCLE, lifecycle.name(), predicate);
        }
        jsonObjectBuilder.set(JsonFields.ID, String.valueOf(id), predicate);
        jsonObjectBuilder.set(JsonFields.NAME, name, predicate);
        if (null != revision) {
            jsonObjectBuilder.set(JsonFields.REVISION, revision.toLong(), predicate);
        }
        if (null != modified) {
            jsonObjectBuilder.set(JsonFields.MODIFIED, modified.toString(), predicate);
        }
        if (null != created) {
            jsonObjectBuilder.set(JsonFields.CREATED, created.toString(), predicate);
        }
        jsonObjectBuilder.set(JsonFields.CONNECTION_TYPE, connectionType.getName(), predicate);
        jsonObjectBuilder.set(JsonFields.CONNECTION_STATUS, connectionStatus.getName(), predicate);
        jsonObjectBuilder.set(JsonFields.URI, uri.toString(), predicate);
        jsonObjectBuilder.set(JsonFields.SOURCES, sources.stream()
                .sorted(Comparator.comparingInt(Source::getIndex))
                .map(source -> source.toJson(schemaVersion, thePredicate))
                .collect(JsonCollectors.valuesToArray()), predicate.and(Objects::nonNull));
        jsonObjectBuilder.set(JsonFields.TARGETS, targets.stream()
                .map(target -> target.toJson(schemaVersion, thePredicate))
                .collect(JsonCollectors.valuesToArray()), predicate.and(Objects::nonNull));
        jsonObjectBuilder.set(JsonFields.CLIENT_COUNT, clientCount, predicate);
        jsonObjectBuilder.set(JsonFields.FAILOVER_ENABLED, failOverEnabled, predicate);
        jsonObjectBuilder.set(JsonFields.VALIDATE_CERTIFICATES, validateCertificate, predicate);
        jsonObjectBuilder.set(JsonFields.PROCESSOR_POOL_SIZE, processorPoolSize, predicate);
        if (!specificConfig.isEmpty()) {
            jsonObjectBuilder.set(JsonFields.SPECIFIC_CONFIG, specificConfig.entrySet()
                    .stream()
                    .map(entry -> JsonField.newInstance(entry.getKey(), JsonValue.of(entry.getValue())))
                    .collect(JsonCollectors.fieldsToObject()), predicate);
        }
        if (!payloadMappingDefinition.isEmpty()) {
            jsonObjectBuilder.set(JsonFields.MAPPING_DEFINITIONS,
                    payloadMappingDefinition.toJson(schemaVersion, thePredicate));
        }
        if (credentials != null) {
            jsonObjectBuilder.set(JsonFields.CREDENTIALS, credentials.toJson());
        }
        if (trustedCertificates != null) {
            jsonObjectBuilder.set(JsonFields.TRUSTED_CERTIFICATES, trustedCertificates, predicate);
        }
        if (sshTunnel != null) {
            jsonObjectBuilder.set(JsonFields.SSH_TUNNEL, sshTunnel.toJson(predicate), predicate);
        }
        jsonObjectBuilder.set(JsonFields.TAGS, tags.stream()
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray()), predicate);
        return jsonObjectBuilder.build();
    }

    private static Instant tryToParseInstant(final CharSequence dateTime) {
        try {
            return Instant.parse(dateTime);
        } catch (final DateTimeParseException e) {
            throw new JsonParseException("The JSON object's field '" + Connection.JsonFields.MODIFIED.getPointer() + "' " +
                    "is not in ISO-8601 format as expected");
        }
    }

    @SuppressWarnings("OverlyComplexMethod")
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractConnection that = (AbstractConnection) o;
        return failOverEnabled == that.failOverEnabled &&
                Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(connectionType, that.connectionType) &&
                Objects.equals(connectionStatus, that.connectionStatus) &&
                Objects.equals(sources, that.sources) &&
                Objects.equals(targets, that.targets) &&
                Objects.equals(clientCount, that.clientCount) &&
                Objects.equals(credentials, that.credentials) &&
                Objects.equals(trustedCertificates, that.trustedCertificates) &&
                Objects.equals(uri, that.uri) &&
                Objects.equals(processorPoolSize, that.processorPoolSize) &&
                Objects.equals(validateCertificate, that.validateCertificate) &&
                Objects.equals(specificConfig, that.specificConfig) &&
                Objects.equals(payloadMappingDefinition, that.payloadMappingDefinition) &&
                Objects.equals(lifecycle, that.lifecycle) &&
                Objects.equals(revision, that.revision) &&
                Objects.equals(modified, that.modified) &&
                Objects.equals(created, that.created) &&
                Objects.equals(sshTunnel, that.sshTunnel) &&
                Objects.equals(tags, that.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, connectionType, connectionStatus, sources, targets, clientCount, failOverEnabled,
                credentials, trustedCertificates, uri, validateCertificate, processorPoolSize, specificConfig,
                payloadMappingDefinition, sshTunnel, tags, lifecycle, revision, modified, created);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "id=" + id +
                ", name=" + name +
                ", connectionType=" + connectionType +
                ", connectionStatus=" + connectionStatus +
                ", failoverEnabled=" + failOverEnabled +
                ", credentials=" + credentials +
                ", trustedCertificates=hash:" + Objects.hash(trustedCertificates) +
                ", uri=" + uri.getUriStringWithMaskedPassword() +
                ", sources=" + sources +
                ", targets=" + targets +
                ", sshTunnel=" + sshTunnel +
                ", clientCount=" + clientCount +
                ", validateCertificate=" + validateCertificate +
                ", processorPoolSize=" + processorPoolSize +
                ", specificConfig=" + specificConfig +
                ", payloadMappingDefinition=" + payloadMappingDefinition +
                ", tags=" + tags +
                ", lifecycle=" + lifecycle +
                ", revision=" + revision +
                ", modified=" + modified +
                ", created=" + created +
                "]";
    }

}