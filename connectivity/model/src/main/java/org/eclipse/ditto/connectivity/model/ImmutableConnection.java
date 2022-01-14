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
package org.eclipse.ditto.connectivity.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkArgument;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
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
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

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
 * Immutable implementation of {@link Connection}.
 */
@Immutable
final class ImmutableConnection implements Connection {

    private final ConnectionId id;
    @Nullable private final String name;
    private final ConnectionType connectionType;
    private final ConnectivityStatus connectionStatus;
    private final ConnectionUri uri;
    @Nullable private final Credentials credentials;
    @Nullable private final String trustedCertificates;
    @Nullable private final ConnectionLifecycle lifecycle;


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

    private ImmutableConnection(final Builder builder) {
        id = checkNotNull(builder.id, "id");
        name = builder.name;
        connectionType = builder.connectionType;
        connectionStatus = checkNotNull(builder.connectionStatus, "connectionStatus");
        credentials = builder.credentials;
        trustedCertificates = builder.trustedCertificates;
        uri = ConnectionUri.of(checkNotNull(builder.uri, "uri"));
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
        sshTunnel = builder.sshTunnel;
    }

    /**
     * Returns a new {@code ConnectionBuilder} object.
     *
     * @param id the connection ID.
     * @param connectionType the connection type.
     * @param connectionStatus the connection status.
     * @param uri the URI.
     * @return new instance of {@code ConnectionBuilder}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ConnectionBuilder getBuilder(final ConnectionId id,
            final ConnectionType connectionType,
            final ConnectivityStatus connectionStatus,
            final String uri) {

        return new Builder(connectionType)
                .id(id)
                .connectionStatus(connectionStatus)
                .uri(uri);
    }

    /**
     * Returns a new {@code ConnectionBuilder} object.
     *
     * @param connection the connection to use for initializing the builder.
     * @return new instance of {@code ImmutableConnectionBuilder}.
     * @throws NullPointerException if {@code connection} is {@code null}.
     */
    public static ConnectionBuilder getBuilder(final Connection connection) {
        checkNotNull(connection, "Connection");

        return new Builder(connection.getConnectionType())
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
                .lifecycle(connection.getLifecycle().orElse(null));
    }

    /**
     * Creates a new {@code Connection} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the Connection to be created.
     * @return a new Connection which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static Connection fromJson(final JsonObject jsonObject) {
        final ConnectionType type = getConnectionTypeOrThrow(jsonObject);
        final MappingContext mappingContext = jsonObject.getValue(JsonFields.MAPPING_CONTEXT)
                .map(ConnectivityModelFactory::mappingContextFromJson)
                .orElse(null);

        final PayloadMappingDefinition payloadMappingDefinition =
                jsonObject.getValue(JsonFields.MAPPING_DEFINITIONS)
                        .map(ImmutablePayloadMappingDefinition::fromJson)
                        .orElse(ConnectivityModelFactory.emptyPayloadMappingDefinition());

        final ConnectionBuilder builder = new Builder(type)
                .id(ConnectionId.of(jsonObject.getValueOrThrow(JsonFields.ID)))
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
        jsonObject.getValue(JsonFields.CREDENTIALS).ifPresent(builder::credentialsFromJson);
        jsonObject.getValue(JsonFields.CLIENT_COUNT).ifPresent(builder::clientCount);
        jsonObject.getValue(JsonFields.FAILOVER_ENABLED).ifPresent(builder::failoverEnabled);
        jsonObject.getValue(JsonFields.VALIDATE_CERTIFICATES).ifPresent(builder::validateCertificate);
        jsonObject.getValue(JsonFields.PROCESSOR_POOL_SIZE).ifPresent(builder::processorPoolSize);
        jsonObject.getValue(JsonFields.TRUSTED_CERTIFICATES).ifPresent(builder::trustedCertificates);
        jsonObject.getValue(JsonFields.SSH_TUNNEL)
                .ifPresent(jsonFields -> builder.sshTunnel(ImmutableSshTunnel.fromJson(jsonFields)));

        return builder.build();
    }

    private static ConnectionType getConnectionTypeOrThrow(final JsonObject jsonObject) {
        final String readConnectionType = jsonObject.getValueOrThrow(JsonFields.CONNECTION_TYPE);
        return ConnectionType.forName(readConnectionType)
                .orElseThrow(() -> JsonParseException.newBuilder()
                        .message(MessageFormat.format("Connection type <{0}> is invalid!", readConnectionType))
                        .build());
    }

    private static ConnectivityStatus getConnectionStatusOrThrow(final JsonObject jsonObject) {
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
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        if (null != lifecycle) {
            jsonObjectBuilder.set(JsonFields.LIFECYCLE, lifecycle.name(), predicate);
        }
        jsonObjectBuilder.set(JsonFields.ID, String.valueOf(id), predicate);
        jsonObjectBuilder.set(JsonFields.NAME, name, predicate);
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

    @SuppressWarnings("OverlyComplexMethod")
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableConnection that = (ImmutableConnection) o;
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
                Objects.equals(sshTunnel, that.sshTunnel) &&
                Objects.equals(tags, that.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, connectionType, connectionStatus, sources, targets, clientCount, failOverEnabled,
                credentials, trustedCertificates, uri, validateCertificate, processorPoolSize, specificConfig,
                payloadMappingDefinition, sshTunnel, tags, lifecycle);
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
                "]";
    }

    /**
     * Builder for {@code ImmutableConnection}.
     */
    @NotThreadSafe
    private static final class Builder implements ConnectionBuilder {

        private static final String MIGRATED_MAPPER_ID = "javascript";
        private final ConnectionType connectionType;

        // required but changeable:
        @Nullable private ConnectionId id;
        @Nullable private ConnectivityStatus connectionStatus;
        @Nullable private String uri;

        // optional:
        @Nullable private String name = null;
        @Nullable private Credentials credentials;
        @Nullable private MappingContext mappingContext = null;
        @Nullable private String trustedCertificates;
        @Nullable private ConnectionLifecycle lifecycle = null;
        @Nullable private SshTunnel sshTunnel = null;

        // optional with default:
        private Set<String> tags = new LinkedHashSet<>();
        private boolean failOverEnabled = true;
        private boolean validateCertificate = true;
        private final List<Source> sources = new ArrayList<>();
        private final List<Target> targets = new ArrayList<>();
        private int clientCount = 1;
        private int processorPoolSize = 1;
        private PayloadMappingDefinition payloadMappingDefinition =
                ConnectivityModelFactory.emptyPayloadMappingDefinition();
        private final Map<String, String> specificConfig = new HashMap<>();

        private Builder(final ConnectionType connectionType) {
            this.connectionType = checkNotNull(connectionType, "Connection Type");
        }

        private static boolean isBlankOrNull(@Nullable final String toTest) {
            return null == toTest || toTest.trim().isEmpty();
        }

        @Override
        public ConnectionBuilder id(final ConnectionId id) {
            this.id = checkNotNull(id, "ID");
            return this;
        }

        @Override
        public ConnectionBuilder name(@Nullable final String name) {
            this.name = name;
            return this;
        }

        @Override
        public ConnectionBuilder credentials(@Nullable Credentials credentials) {
            this.credentials = credentials;
            return this;
        }

        @Override
        public Builder trustedCertificates(@Nullable final String trustedCertificates) {
            if (isBlankOrNull(trustedCertificates)) {
                this.trustedCertificates = null;
            } else {
                this.trustedCertificates = trustedCertificates;
            }
            return this;
        }

        @Override
        public ConnectionBuilder uri(final String uri) {
            this.uri = checkNotNull(uri, "URI");
            return this;
        }

        @Override
        public ConnectionBuilder connectionStatus(final ConnectivityStatus connectionStatus) {
            this.connectionStatus = checkNotNull(connectionStatus, "ConnectionStatus");
            return this;
        }

        @Override
        public ConnectionBuilder failoverEnabled(final boolean failOverEnabled) {
            this.failOverEnabled = failOverEnabled;
            return this;
        }

        @Override
        public ConnectionBuilder validateCertificate(final boolean validateCertificate) {
            this.validateCertificate = validateCertificate;
            return this;
        }

        @Override
        public ConnectionBuilder processorPoolSize(final int processorPoolSize) {
            checkArgument(processorPoolSize, ps -> ps > 0, () -> "The processor pool size must be positive!");
            this.processorPoolSize = processorPoolSize;
            return this;
        }

        @Override
        public ConnectionBuilder sources(final List<Source> sources) {
            this.sources.addAll(checkNotNull(sources, "sources"));
            return this;
        }

        @Override
        public ConnectionBuilder targets(final List<Target> targets) {
            this.targets.addAll(checkNotNull(targets, "targets"));
            return this;
        }

        @Override
        public ConnectionBuilder setSources(final List<Source> sources) {
            this.sources.clear();
            return sources(sources);
        }

        @Override
        public ConnectionBuilder setTargets(final List<Target> targets) {
            this.targets.clear();
            return targets(targets);
        }

        @Override
        public ConnectionBuilder clientCount(final int clientCount) {
            checkArgument(clientCount, ps -> ps > 0, () -> "The client count must be > 0!");
            this.clientCount = clientCount;
            return this;
        }

        @Override
        public ConnectionBuilder specificConfig(final Map<String, String> specificConfig) {
            this.specificConfig.putAll(checkNotNull(specificConfig, "Specific Config"));
            return this;
        }

        @Override
        public ConnectionBuilder mappingContext(@Nullable final MappingContext mappingContext) {
            this.mappingContext = mappingContext;
            return this;
        }

        @Override
        public ConnectionBuilder tags(final Collection<String> tags) {
            this.tags = new LinkedHashSet<>(checkNotNull(tags, "tags to set"));
            return this;
        }

        @Override
        public ConnectionBuilder tag(final String tag) {
            tags.add(checkNotNull(tag, "tag to set"));
            return this;
        }

        @Override
        public ConnectionBuilder lifecycle(@Nullable final ConnectionLifecycle lifecycle) {
            this.lifecycle = lifecycle;
            return this;
        }

        @Override
        public ConnectionBuilder sshTunnel(@Nullable final SshTunnel sshTunnel) {
            this.sshTunnel = sshTunnel;
            return this;
        }

        @Override
        public ConnectionBuilder payloadMappingDefinition(final PayloadMappingDefinition payloadMappingDefinition) {
            this.payloadMappingDefinition = payloadMappingDefinition;
            return this;
        }

        @Override
        public Connection build() {
            checkSourceAndTargetAreValid();
            checkAuthorizationContextsAreValid();
            checkConnectionAnnouncementsOnlySetIfClientCount1();
            migrateLegacyConfigurationOnTheFly();
            return new ImmutableConnection(this);
        }

        private boolean shouldMigrateMappingContext() {
            return mappingContext != null;
        }

        private void migrateLegacyConfigurationOnTheFly() {
            if (shouldMigrateMappingContext()) {
                this.payloadMappingDefinition =
                        payloadMappingDefinition.withDefinition(MIGRATED_MAPPER_ID, mappingContext);
            }
            setSources(sources.stream().map(this::migrateSource).collect(Collectors.toList()));
            setTargets(targets.stream().map(this::migrateTarget).collect(Collectors.toList()));
        }

        private Source migrateSource(final Source source) {
            final Source sourceAfterReplyTargetMigration = ImmutableSource.migrateReplyTarget(source, connectionType);
            if (shouldMigrateMappingContext()) {
                return new ImmutableSource.Builder(sourceAfterReplyTargetMigration)
                        .payloadMapping(addMigratedPayloadMappings(source.getPayloadMapping()))
                        .build();
            } else {
                return sourceAfterReplyTargetMigration;
            }
        }

        private Target migrateTarget(final Target target) {
            final boolean shouldAddHeaderMapping = shouldAddDefaultHeaderMappingToTarget(connectionType);
            final boolean shouldMigrateMappingContext = shouldMigrateMappingContext();
            if (shouldMigrateMappingContext || shouldAddHeaderMapping) {
                final TargetBuilder builder = new ImmutableTarget.Builder(target);
                if (shouldMigrateMappingContext) {
                    builder.payloadMapping(addMigratedPayloadMappings(target.getPayloadMapping()));
                }
                if (shouldAddHeaderMapping) {
                    builder.headerMapping(target.getHeaderMapping());
                }
                return builder.build();
            } else {
                return target;
            }
        }

        private boolean shouldAddDefaultHeaderMappingToTarget(final ConnectionType connectionType) {
            switch (connectionType) {
                case AMQP_091:
                case AMQP_10:
                case KAFKA:
                case MQTT_5:
                    return true;
                case MQTT:
                case HTTP_PUSH:
                default:
                    return false;
            }
        }


        private PayloadMapping addMigratedPayloadMappings(final PayloadMapping payloadMapping) {
            final ArrayList<String> merged = new ArrayList<>(payloadMapping.getMappings());
            merged.add(MIGRATED_MAPPER_ID);
            return ConnectivityModelFactory.newPayloadMapping(merged);
        }

        private void checkSourceAndTargetAreValid() {
            if (sources.isEmpty() && targets.isEmpty()) {
                throw ConnectionConfigurationInvalidException.newBuilder("Either a source or a target must be " +
                        "specified in the configuration of a connection!").build();
            }
        }

        /**
         * If no context is set on connection level each target and source must have its own context.
         */
        private void checkAuthorizationContextsAreValid() {
            // if the auth context on connection level is empty,
            // an auth context is required to be set on each source/target
            final Set<String> sourcesWithoutAuthContext = sources.stream()
                    .filter(source -> source.getAuthorizationContext().isEmpty())
                    .flatMap(source -> source.getAddresses().stream())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            final Set<String> targetsWithoutAuthContext = targets.stream()
                    .filter(target -> target.getAuthorizationContext().isEmpty())
                    .map(Target::getAddress)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (!sourcesWithoutAuthContext.isEmpty() || !targetsWithoutAuthContext.isEmpty()) {
                final StringBuilder message = new StringBuilder("The ");
                if (!sourcesWithoutAuthContext.isEmpty()) {
                    message.append("Sources ").append(sourcesWithoutAuthContext);
                }
                if (!sourcesWithoutAuthContext.isEmpty() && !targetsWithoutAuthContext.isEmpty()) {
                    message.append(" and ");
                }
                if (!targetsWithoutAuthContext.isEmpty()) {
                    message.append("Targets ").append(targetsWithoutAuthContext);
                }
                message.append(" are missing an authorization context.");
                throw ConnectionConfigurationInvalidException.newBuilder(message.toString()).build();
            }
        }

        private void checkConnectionAnnouncementsOnlySetIfClientCount1() {
            if (clientCount > 1 && containsTargetWithConnectionAnnouncementsTopic()) {
                final String message = MessageFormat.format("Connection announcements (topic {0}) can" +
                        " only be used with client count 1.", Topic.CONNECTION_ANNOUNCEMENTS.getName());
                throw ConnectionConfigurationInvalidException.newBuilder(message)
                        .build();
            }
        }

        private boolean containsTargetWithConnectionAnnouncementsTopic() {
            return targets.stream()
                    .map(Target::getTopics)
                    .flatMap(Set::stream)
                    .map(FilteredTopic::getTopic)
                    .anyMatch(Topic.CONNECTION_ANNOUNCEMENTS::equals);
        }

    }

    @Immutable
    static final class ConnectionUri {

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

        private ConnectionUri(final String theUriString) {
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
                userName = tryDecodeUriComponent(userInfo.substring(0, separatorIndex));
                password = tryDecodeUriComponent(userInfo.substring(separatorIndex + 1));
            } else {
                userName = null;
                password = null;
            }

            // must be initialized after all else
            uriStringWithMaskedPassword = createUriStringWithMaskedPassword();
        }

        private static String tryDecodeUriComponent(final String string) {
            try {
                final String withoutPlus = string.replace("+", "%2B");
                return URLDecoder.decode(withoutPlus, "UTF-8");
            } catch (final IllegalArgumentException | UnsupportedEncodingException e) {
                return string;
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
        private static boolean isValid(final URI uri) {
            return uri.getPort() > 0 && uri.getQuery() == null;
        }

        /**
         * Returns a new instance of {@code ConnectionUri}. The is the reverse function of {@link #toString()}.
         *
         * @param uriString the string representation of the Connection URI.
         * @return the instance.
         * @throws NullPointerException if {@code uriString} is {@code null}.
         * @throws ConnectionUriInvalidException if {@code uriString} is not a
         * valid URI.
         * @see #toString()
         */
        static ConnectionUri of(final String uriString) {
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

}
