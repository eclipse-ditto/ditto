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
package org.eclipse.ditto.things.service.persistence.actors;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.persistence.RecoveryCompleted;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.LiveChannelTimeoutStrategy;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.ActivityCheckConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.SnapshotConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.persistentactors.AbstractPersistenceActor;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.internal.utils.persistentactors.commands.DefaultContext;
import org.eclipse.ditto.internal.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.internal.utils.tracing.span.StartedSpan;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingBuilder;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingLifecycle;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingHistoryNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.ThingQueryCommandResponse;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.service.common.config.DittoThingsConfig;
import org.eclipse.ditto.things.service.common.config.ThingConfig;
import org.eclipse.ditto.things.service.persistence.actors.enrichment.EnrichSignalWithPreDefinedExtraFields;
import org.eclipse.ditto.things.service.persistence.actors.enrichment.EnrichSignalWithPreDefinedExtraFieldsResponse;
import org.eclipse.ditto.things.service.persistence.actors.enrichment.PreDefinedExtraFieldsEnricher;
import org.eclipse.ditto.things.service.persistence.actors.strategies.commands.ThingCommandStrategies;
import org.eclipse.ditto.things.service.persistence.actors.strategies.events.ThingEventStrategies;

/**
 * PersistentActor which "knows" the state of a single {@link Thing}.
 */
public final class ThingPersistenceActor
        extends AbstractPersistenceActor<Command<?>, Thing, ThingId, ThingId, ThingEvent<?>> {

    /**
     * The prefix of the persistenceId for Things.
     */
    static final String PERSISTENCE_ID_PREFIX = ThingConstants.ENTITY_TYPE + ":";

    /**
     * The ID of the journal plugin this persistence actor uses.
     */
    static final String JOURNAL_PLUGIN_ID = "pekko-contrib-mongodb-persistence-things-journal";

    /**
     * The ID of the snapshot plugin this persistence actor uses.
     */
    static final String SNAPSHOT_PLUGIN_ID = "pekko-contrib-mongodb-persistence-things-snapshots";

    private static final AckExtractor<ThingEvent<?>> ACK_EXTRACTOR =
            AckExtractor.of(ThingEvent::getEntityId, ThingEvent::getDittoHeaders);

    private final ThingConfig thingConfig;
    private final DistributedPub<ThingEvent<?>> distributedPub;
    @Nullable private final ActorRef searchShardRegionProxy;
    private final PreDefinedExtraFieldsEnricher eventPreDefinedExtraFieldsEnricher;
    private final PreDefinedExtraFieldsEnricher messagePreDefinedExtraFieldsEnricher;

    @SuppressWarnings("unused")
    private ThingPersistenceActor(final ThingId thingId,
            final MongoReadJournal mongoReadJournal,
            final DistributedPub<ThingEvent<?>> distributedPub,
            @Nullable final ActorRef searchShardRegionProxy,
            final PolicyEnforcerProvider policyEnforcerProvider) {

        super(thingId, mongoReadJournal);
        final DittoThingsConfig thingsConfig = DittoThingsConfig.of(
                DefaultScopedConfig.dittoScoped(getContext().getSystem().settings().config())
        );
        thingConfig = thingsConfig.getThingConfig();
        this.distributedPub = distributedPub;
        this.searchShardRegionProxy = searchShardRegionProxy;
        this.eventPreDefinedExtraFieldsEnricher = new PreDefinedExtraFieldsEnricher(
                thingConfig.getEventConfig().getPredefinedExtraFieldsConfigs(),
                policyEnforcerProvider
        );
        this.messagePreDefinedExtraFieldsEnricher = new PreDefinedExtraFieldsEnricher(
                thingConfig.getMessageConfig().getPredefinedExtraFieldsConfigs(),
                policyEnforcerProvider
        );
    }

    /**
     * Creates Pekko configuration object {@link Props} for this ThingPersistenceActor.
     *
     * @param thingId the Thing ID this Actor manages.
     * @param mongoReadJournal the ReadJournal used for gaining access to historical values of the thing.
     * @param distributedPub the distributed-pub access to publish thing events.
     * @param searchShardRegionProxy the proxy of the shard region of search updaters.
     * @param policyEnforcerProvider a provider for the used Policy {@code Enforcer} which "guards" the
     * ThingPersistenceActor for applying access control.
     * @return the Pekko configuration Props object
     */
    public static Props props(final ThingId thingId,
            final MongoReadJournal mongoReadJournal,
            final DistributedPub<ThingEvent<?>> distributedPub,
            @Nullable final ActorRef searchShardRegionProxy,
            final PolicyEnforcerProvider policyEnforcerProvider
    ) {
        return Props.create(ThingPersistenceActor.class, thingId, mongoReadJournal, distributedPub,
                searchShardRegionProxy, policyEnforcerProvider);
    }

    @Override
    public void onQuery(final Command<?> command, final WithDittoHeaders response) {
        final ActorRef sender = getSender();
        doOnQuery(command, response, sender);
    }

    @Override
    public void onStagedQuery(final Command<?> command, final CompletionStage<WithDittoHeaders> response,
            @Nullable final StartedSpan startedSpan) {
        final ActorRef sender = getSender();
        response.whenComplete((r, throwable) -> {
            if (throwable instanceof DittoRuntimeException dittoRuntimeException) {
                notifySender(sender, dittoRuntimeException);
            } else {
                doOnQuery(command, r, sender);
            }
            if (startedSpan != null) {
                startedSpan.finish();
            }
        });
    }


    private void doOnQuery(final Command<?> command, final WithDittoHeaders response, final ActorRef sender) {
        if (response.getDittoHeaders().didLiveChannelConditionMatch()) {
            final var liveChannelTimeoutStrategy = response.getDittoHeaders()
                    .getLiveChannelTimeoutStrategy()
                    .orElse(LiveChannelTimeoutStrategy.FAIL);
            if (liveChannelTimeoutStrategy != LiveChannelTimeoutStrategy.USE_TWIN &&
                    response instanceof ThingQueryCommandResponse<?> queryResponse &&
                    command.getDittoHeaders().isResponseRequired()) {
                notifySender(sender, queryResponse.setEntity(JsonFactory.nullLiteral()));
                return;
            }
        }

        if (command.getDittoHeaders().isResponseRequired()) {
            notifySender(sender, response);
        }
    }

    @Override
    public String persistenceId() {
        return entityId.getEntityType() + ":" + entityId;
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
        return ThingEvent.class;
    }

    @Override
    protected CommandStrategy.Context<ThingId> getStrategyContext() {
        return DefaultContext.getInstance(entityId, log, getContext().getSystem());
    }

    @Override
    protected ThingCommandStrategies getCreatedStrategy() {
        return ThingCommandStrategies.getInstance(getContext().getSystem());
    }

    @Override
    protected CommandStrategy<CreateThing, Thing, ThingId, ThingEvent<?>> getDeletedStrategy() {
        return ThingCommandStrategies.getCreateThingStrategy(getContext().getSystem());
    }

    @Override
    protected EventStrategy<ThingEvent<?>, Thing> getEventStrategy() {
        return ThingEventStrategies.getInstance();
    }

    @Override
    protected ActivityCheckConfig getActivityCheckConfig() {
        return thingConfig.getActivityCheckConfig();
    }

    @Override
    protected SnapshotConfig getSnapshotConfig() {
        return thingConfig.getSnapshotConfig();
    }

    @Override
    protected boolean entityExistsAsDeleted() {
        return null != entity && entity.hasLifecycle(ThingLifecycle.DELETED);
    }

    @Override
    protected Receive matchAnyAfterInitialization() {
        return ReceiveBuilder.create()
                .match(EnrichSignalWithPreDefinedExtraFields.class, this::enrichSignalWithPreDefinedExtraFields)
                .build()
                .orElse(super.matchAnyAfterInitialization());
    }

    @Override
    protected Receive matchAnyWhenDeleted() {
        return ReceiveBuilder.create()
                .match(RetrieveThing.class, this::handleByCommandStrategy)
                .match(SudoRetrieveThing.class, this::handleByCommandStrategy)
                .build()
                .orElse(super.matchAnyWhenDeleted());
    }

    @Override
    protected DittoRuntimeExceptionBuilder<?> newNotAccessibleExceptionBuilder() {
        return ThingNotAccessibleException.newBuilder(entityId);
    }

    @Override
    protected DittoRuntimeExceptionBuilder<?> newHistoryNotAccessibleExceptionBuilder(final long revision) {
        return ThingHistoryNotAccessibleException.newBuilder(entityId, revision);
    }

    @Override
    protected DittoRuntimeExceptionBuilder<?> newHistoryNotAccessibleExceptionBuilder(final Instant timestamp) {
        return ThingHistoryNotAccessibleException.newBuilder(entityId, timestamp);
    }

    @Override
    protected void recoveryCompleted(final RecoveryCompleted event) {
        if (entity != null) {
            entity = enhanceThingWithLifecycle(entity);
            log.info("Thing <{}> was recovered.", entityId);
        }
        super.recoveryCompleted(event);
    }

    @Override
    protected void publishEvent(@Nullable final Thing previousEntity, final ThingEvent<?> event) {
        final CompletionStage<ThingEvent<?>> stage = eventPreDefinedExtraFieldsEnricher.enrichWithPredefinedExtraFields(
                entityId,
                entity,
                Optional.ofNullable(previousEntity).flatMap(Thing::getPolicyId).orElse(null),
                event
        );
        stage.whenComplete((modifiedEvent, ex) -> {
            final ThingEvent<?> eventToPublish;
            if (ex != null) {
                eventToPublish = event;
            } else {
                eventToPublish = modifiedEvent;
            }
            distributedPub.publishWithAcks(eventToPublish, entityId, ACK_EXTRACTOR, getSelf());
            if (searchShardRegionProxy != null) {
                searchShardRegionProxy.tell(eventToPublish, getSelf());
            }
        });
    }

    @Override
    protected boolean shouldSendResponse(final DittoHeaders dittoHeaders) {
        return dittoHeaders.isResponseRequired() ||
                dittoHeaders.getAcknowledgementRequests()
                        .stream()
                        .anyMatch(ar -> DittoAcknowledgementLabel.TWIN_PERSISTED.equals(ar.getLabel()));
    }

    @Override
    protected boolean isEntityAlwaysAlive() {
        return false;
    }

    @Override
    protected JsonSchemaVersion getEntitySchemaVersion(final Thing entity) {
        return entity.getImplementedSchemaVersion();
    }

    private static Thing enhanceThingWithLifecycle(final Thing thing) {
        final ThingBuilder.FromCopy thingBuilder = ThingsModelFactory.newThingBuilder(thing);
        if (thing.getLifecycle().isEmpty()) {
            thingBuilder.setLifecycle(ThingLifecycle.ACTIVE);
        }

        return thingBuilder.build();
    }

    private void enrichSignalWithPreDefinedExtraFields(
            final EnrichSignalWithPreDefinedExtraFields enrichSignalWithPreDefinedExtraFields
    ) {
        final ActorRef sender = getSender();
        final Signal<?> signal = enrichSignalWithPreDefinedExtraFields.signal();
        final CompletionStage<Signal<?>> stage;
        switch (signal) {
            case MessageCommand<?, ?> messageCommand ->
                stage = messagePreDefinedExtraFieldsEnricher.enrichWithPredefinedExtraFields(
                        entityId,
                        entity,
                        Optional.ofNullable(entity).flatMap(Thing::getPolicyId).orElse(null),
                        messageCommand
                );
            case ThingEvent<?> thingEvent ->
                stage = eventPreDefinedExtraFieldsEnricher.enrichWithPredefinedExtraFields(
                        entityId,
                        entity,
                        Optional.ofNullable(entity).flatMap(Thing::getPolicyId).orElse(null),
                        thingEvent
                );
            default ->
                stage = CompletableFuture.completedStage(signal);
        }
        stage.thenAccept(modifiedSignal ->
                sender.tell(new EnrichSignalWithPreDefinedExtraFieldsResponse(modifiedSignal), getSelf())
        );
    }
}
