/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors;

import java.time.Instant;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.cluster.pubsub.DistributedPubSubMediator;
import org.apache.pekko.persistence.RecoveryCompleted;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.ActivityCheckConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultActivityCheckConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultSnapshotConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.SnapshotConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistentactors.AbstractPersistenceActor;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.commands.DefaultContext;
import org.eclipse.ditto.internal.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.things.model.devops.WotValidationConfig;
import org.eclipse.ditto.things.model.devops.WotValidationConfigId;
import org.eclipse.ditto.things.model.devops.events.WotValidationConfigEvent;
import org.eclipse.ditto.things.model.devops.exceptions.WotValidationConfigHistoryNotAccessibleException;
import org.eclipse.ditto.things.model.devops.exceptions.WotValidationConfigNotAccessibleException;
import org.eclipse.ditto.things.service.persistence.actors.strategies.commands.WotValidationConfigCommandStrategies;
import org.eclipse.ditto.things.service.persistence.actors.strategies.commands.WotValidationConfigDData;
import org.eclipse.ditto.things.service.persistence.actors.strategies.events.WotValidationConfigEventStrategies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistence actor responsible for managing the lifecycle and distributed state of WoT validation configurations.
 * <p>
 * Handles persistence, event sourcing, and DData replication for WoT validation config entities. Integrates with
 * the Ditto distributed data system to ensure consistency and availability across the cluster. Publishes events
 * and updates DData on changes, and recovers state on startup.
 * </p>
 *
 * @since 3.8.0
 */
public final class WotValidationConfigPersistenceActor
        extends
        AbstractPersistenceActor<Command<?>, WotValidationConfig, WotValidationConfigId, WotValidationConfigId, WotValidationConfigEvent<?>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(WotValidationConfigPersistenceActor.class);

    /**
     * The prefix of the persistenceId for WoT validation configs.
     */
    static final String PERSISTENCE_ID_PREFIX = "wot-validation-config:";

    /**
     * The ID of the journal plugin this persistence actor uses.
     */
    static final String JOURNAL_PLUGIN_ID = "pekko-contrib-mongodb-persistence-wot-validation-config-journal";

    /**
     * The ID of the snapshot plugin this persistence actor uses.
     */
    static final String SNAPSHOT_PLUGIN_ID = "pekko-contrib-mongodb-persistence-wot-validation-config-snapshots";

    private final ActorRef pubSubMediator;
    private final DefaultActivityCheckConfig activityCheckConfig;
    private final DefaultSnapshotConfig snapshotConfig;
    private final WotValidationConfigDData ddata;
    private final EventStrategy<WotValidationConfigEvent<?>, WotValidationConfig> eventStrategy;
    private final WotValidationConfigCommandStrategies commandStrategies;

    @SuppressWarnings("unused")
    private WotValidationConfigPersistenceActor(final WotValidationConfigId entityId,
            final MongoReadJournal mongoReadJournal,
            final ActorRef pubSubMediator) {
        super(entityId, mongoReadJournal);
        this.pubSubMediator = pubSubMediator;

        final var actorSystem = getContext().getSystem();
        final var config = DefaultScopedConfig.dittoScoped(actorSystem.settings().config());
        this.activityCheckConfig = DefaultActivityCheckConfig.of(config);
        this.snapshotConfig = DefaultSnapshotConfig.of(config);
        this.ddata = WotValidationConfigDData.of(actorSystem);
        this.eventStrategy = WotValidationConfigEventStrategies.getInstance();
        this.commandStrategies = WotValidationConfigCommandStrategies.getInstance(actorSystem);
    }

    public static Props props(final WotValidationConfigId entityId, final MongoReadJournal mongoReadJournal,
            final ActorRef pubSubMediator) {
        return Props.create(WotValidationConfigPersistenceActor.class, entityId, mongoReadJournal, pubSubMediator);
    }

    @Override
    public String persistenceId() {
        return PERSISTENCE_ID_PREFIX + entityId;
    }

    @Override
    public String journalPluginId() {
        return JOURNAL_PLUGIN_ID;
    }

    @Override
    public String snapshotPluginId() {
        return SNAPSHOT_PLUGIN_ID;
    }

    @Override
    protected Class<?> getEventClass() {
        return WotValidationConfigEvent.class;
    }

    @Override
    protected CommandStrategy.Context<WotValidationConfigId> getStrategyContext() {
        return DefaultContext.getInstance(entityId, log, getContext().getSystem());
    }

    @Override
    protected EventStrategy<WotValidationConfigEvent<?>, WotValidationConfig> getEventStrategy() {
        return eventStrategy;
    }

    @Override
    protected JsonSchemaVersion getEntitySchemaVersion(final WotValidationConfig entity) {
        return JsonSchemaVersion.V_2;
    }

    @Override
    protected boolean shouldSendResponse(final DittoHeaders dittoHeaders) {
        return true;
    }

    @Override
    protected boolean isEntityAlwaysAlive() {
        return false;
    }

    @Override
    protected ActivityCheckConfig getActivityCheckConfig() {
        return activityCheckConfig;
    }

    @Override
    protected SnapshotConfig getSnapshotConfig() {
        return snapshotConfig;
    }

    @Override
    protected DittoRuntimeExceptionBuilder<?> newNotAccessibleExceptionBuilder() {
        return WotValidationConfigNotAccessibleException.newBuilder(entityId);
    }

    @Override
    protected DittoRuntimeExceptionBuilder<WotValidationConfigHistoryNotAccessibleException> newHistoryNotAccessibleExceptionBuilder(
            final long revision) {
        return WotValidationConfigHistoryNotAccessibleException.newBuilder(entityId, revision);
    }

    @Override
    protected DittoRuntimeExceptionBuilder<WotValidationConfigHistoryNotAccessibleException> newHistoryNotAccessibleExceptionBuilder(
            final Instant timestamp) {
        return WotValidationConfigHistoryNotAccessibleException.newBuilder(entityId, timestamp);
    }

    @Override
    protected boolean entityExistsAsDeleted() {
        return false;
    }

    @Override
    protected void publishEvent(@Nullable final WotValidationConfig entity, final WotValidationConfigEvent<?> event) {
        final var publish = new DistributedPubSubMediator.Publish(
                event.getType(),
                event,
                true
        );
        pubSubMediator.tell(publish, getSelf());
    }

    @Override
    protected CommandStrategy<Command<?>, WotValidationConfig, WotValidationConfigId, WotValidationConfigEvent<?>> getCreatedStrategy() {
        return commandStrategies;
    }

    @Override
    protected CommandStrategy<Command<?>, WotValidationConfig, WotValidationConfigId, WotValidationConfigEvent<?>> getDeletedStrategy() {
        return commandStrategies;
    }

    @Override
    protected void recoveryCompleted(final RecoveryCompleted event) {
        super.recoveryCompleted(event);
        LOGGER.debug("recoveryCompleted called for entityId: {}. Entity: {}", entityId,
                entity != null ? entity.toString() : null);
        if (entity != null) {
            LOGGER.debug("Starting DData update for recovered entity: {}", entity.getConfigId());
            ddata.add(entity.toJson())
                    .whenComplete((v, error) -> {
                        if (error != null) {
                            LOGGER.error("Failed to publish WoT validation config to DData: {}", error.getMessage(),
                                    error);
                        } else {
                            LOGGER.debug("Successfully published WoT validation config to DData: {}",
                                    entity.getConfigId());
                        }
                    });
        } else {
            LOGGER.info("No WoT validation config to publish to DData after recovery.");
        }
    }
}