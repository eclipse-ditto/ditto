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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkArgument;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Abstract implementation for common aspects of {@link ConnectionBuilder}.
 *
 * @since 3.2.0
 */

@Immutable
abstract class AbstractConnectionBuilder implements ConnectionBuilder {

    private static final String MIGRATED_MAPPER_ID = "javascript";

    // required but changeable:
    @Nullable ConnectionId id;
    @Nullable ConnectivityStatus connectionStatus;
    String uri;
    // optional:
    @Nullable String name = null;
    @Nullable Credentials credentials;
    @Nullable MappingContext mappingContext = null;
    @Nullable String trustedCertificates;
    @Nullable ConnectionLifecycle lifecycle = null;
    @Nullable ConnectionRevision revision = null;
    @Nullable Instant modified = null;
    @Nullable Instant created = null;
    @Nullable SshTunnel sshTunnel = null;

    // optional with default:
    Set<String> tags = new LinkedHashSet<>();
    boolean failOverEnabled = true;
    boolean validateCertificate = true;
    final List<Source> sources = new ArrayList<>();
    final List<Target> targets = new ArrayList<>();
    int clientCount = 1;
    int processorPoolSize = 1;
    PayloadMappingDefinition payloadMappingDefinition =
            ConnectivityModelFactory.emptyPayloadMappingDefinition();
    final Map<String, String> specificConfig = new HashMap<>();
    ConnectionType connectionType;

    AbstractConnectionBuilder(final ConnectionType connectionType) {
        this.connectionType = connectionType;
    }

    private static boolean isBlankOrNull(@Nullable final String toTest) {
        return null == toTest || toTest.trim().isEmpty();
    }

    @Override
    public ConnectionBuilder id(final ConnectionId id) {
        this.id = checkNotNull(id, "id");
        return this;
    }

    @Override
    public ConnectionBuilder name(@Nullable final String name) {
        this.name = name;
        return this;
    }

    @Override
    public ConnectionBuilder credentials(@Nullable final Credentials credentials) {
        this.credentials = credentials;
        return this;
    }

    @Override
    public ConnectionBuilder trustedCertificates(@Nullable final String trustedCertificates) {
        if (isBlankOrNull(trustedCertificates)) {
            this.trustedCertificates = null;
        } else {
            this.trustedCertificates = trustedCertificates;
        }
        return this;
    }

    @Override
    public ConnectionBuilder uri(final String uri) {
        this.uri = checkNotNull(uri, "uri");
        return this;
    }

    @Override
    public ConnectionBuilder connectionStatus(final ConnectivityStatus connectionStatus) {
        this.connectionStatus = checkNotNull(connectionStatus, "connectionStatus");
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
        checkArgument(clientCount, ps -> ps > 0, () -> "The client count must be positive!");
        this.clientCount = clientCount;
        return this;
    }

    @Override
    public ConnectionBuilder specificConfig(final Map<String, String> specificConfig) {
        this.specificConfig.putAll(checkNotNull(specificConfig, "specificConfig"));
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
    public ConnectionBuilder revision(@Nullable final ConnectionRevision revision) {
        this.revision = revision;
        return this;
    }

    @Override
    public ConnectionBuilder modified(@Nullable final Instant modified) {
        this.modified = modified;
        return this;
    }

    @Override
    public ConnectionBuilder created(@Nullable final Instant created) {
        this.created = created;
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

    private boolean shouldMigrateMappingContext() {
        return mappingContext != null;
    }

    void migrateLegacyConfigurationOnTheFly() {
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
            case HONO:
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

    void checkSourceAndTargetAreValid() {
        if (sources.isEmpty() && targets.isEmpty()) {
            throw ConnectionConfigurationInvalidException.newBuilder("Either a source or a target must be " +
                    "specified in the configuration of a connection!").build();
        }
    }

    /**
     * If no context is set on connection level each target and source must have its own context.
     */
    void checkAuthorizationContextsAreValid() {
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

    void checkConnectionAnnouncementsOnlySetIfClientCount1() {
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