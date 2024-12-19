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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.persistence.RecoveryCompleted;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.base.model.exceptions.InvalidRqlExpressionException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.LiveChannelTimeoutStrategy;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
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
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.placeholders.HeadersPlaceholder;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.rql.parser.RqlPredicateParser;
import org.eclipse.ditto.rql.query.filter.QueryFilterCriteriaFactory;
import org.eclipse.ditto.rql.query.things.ThingPredicateVisitor;
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
import org.eclipse.ditto.things.service.common.config.PreDefinedExtraFieldsConfig;
import org.eclipse.ditto.things.service.common.config.ThingConfig;
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

    private static final TimePlaceholder TIME_PLACEHOLDER = TimePlaceholder.getInstance();
    private static final HeadersPlaceholder HEADERS_PLACEHOLDER = PlaceholderFactory.newHeadersPlaceholder();

    private static final AckExtractor<ThingEvent<?>> ACK_EXTRACTOR =
            AckExtractor.of(ThingEvent::getEntityId, ThingEvent::getDittoHeaders);

    private final ThingConfig thingConfig;
    private final DistributedPub<ThingEvent<?>> distributedPub;
    @Nullable private final ActorRef searchShardRegionProxy;
    private final PolicyEnforcerProvider policyEnforcerProvider;

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
        this.policyEnforcerProvider = policyEnforcerProvider;
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
        response.thenAccept(r -> {
            doOnQuery(command, r, sender);
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
        enrichEventWithPredefinedExtraFields(
                Optional.ofNullable(previousEntity).flatMap(Thing::getPolicyId).orElse(null), event)
                .whenComplete((modifiedEvent, ex) -> {
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

    private CompletionStage<ThingEvent<?>> enrichEventWithPredefinedExtraFields(@Nullable final PolicyId policyId,
            final ThingEvent<?> event
    ) {
        final List<PreDefinedExtraFieldsConfig> predefinedExtraFieldsConfigs = thingConfig.getEventConfig()
                .getPredefinedExtraFieldsConfigs();
        if (null != entity && !predefinedExtraFieldsConfigs.isEmpty()) {
            final List<PreDefinedExtraFieldsConfig> matchingPreDefinedFieldsConfigs =
                    predefinedExtraFieldsConfigs.stream()
                            .filter(conf -> conf
                                    .getNamespace().stream()
                                    .anyMatch(pattern -> pattern.matcher(entityId.getNamespace()).matches())
                            )
                            .filter(applyPredefinedExtraFieldsCondition(event))
                            .toList();
            final JsonFieldSelector combinedPredefinedExtraFields = matchingPreDefinedFieldsConfigs.stream()
                    .map(PreDefinedExtraFieldsConfig::getExtraFields)
                    .reduce(JsonFactory.newFieldSelector(List.of()), (a, b) -> {
                        final Set<JsonPointer> combinedPointerSet = new LinkedHashSet<>(a.getPointers());
                        combinedPointerSet.addAll(b.getPointers());
                        return JsonFactory.newFieldSelector(combinedPointerSet);
                    });
            return buildPredefinedExtraFieldsHeaderReadGrantObject(policyId, combinedPredefinedExtraFields)
                    .thenApply(predefinedExtraFieldsHeaderReadGrantObject ->
                            event.setDittoHeaders(event.getDittoHeaders()
                                    .toBuilder()
                                    .putHeader(DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS.getKey(),
                                            buildPredefinedExtraFieldsHeaderList(combinedPredefinedExtraFields)
                                    )
                                    .putHeader(DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS_READ_GRANT_OBJECT.getKey(),
                                            predefinedExtraFieldsHeaderReadGrantObject
                                    )
                                    .putHeader(DittoHeaderDefinition.PRE_DEFINED_EXTRA_FIELDS_OBJECT.getKey(),
                                            buildPredefinedExtraFieldsHeaderObject(entity,
                                                    combinedPredefinedExtraFields).toString()
                                    )
                                    .build()
                            )
                    );
        } else {
            return CompletableFuture.completedStage(event);
        }
    }

    private Predicate<PreDefinedExtraFieldsConfig> applyPredefinedExtraFieldsCondition(final ThingEvent<?> event) {
        return conf -> {
            if (conf.getCondition().isEmpty()) {
                return true;
            } else {
                final String rqlCondition = conf.getCondition().get();
                try {
                    final var criteria = QueryFilterCriteriaFactory
                            .modelBased(RqlPredicateParser.getInstance())
                            .filterCriteria(rqlCondition, event.getDittoHeaders());

                    final var predicate = ThingPredicateVisitor.apply(
                            criteria,
                            PlaceholderFactory.newPlaceholderResolver(TIME_PLACEHOLDER,
                                    new Object()),
                            PlaceholderFactory.newPlaceholderResolver(HEADERS_PLACEHOLDER,
                                    event.getDittoHeaders())
                    );
                    return predicate.test(entity);
                } catch (final InvalidRqlExpressionException e) {
                    log.warning(e, "Encountered invalid RQL condition <{}> for enriching " +
                            "predefined extra fields: <{}>", rqlCondition, e.getMessage());
                    return true;
                }
            }
        };
    }

    private static String buildPredefinedExtraFieldsHeaderList(final JsonFieldSelector preDefinedExtraFields) {
        return StreamSupport.stream(preDefinedExtraFields.spliterator(), false)
                .map(JsonPointer::toString)
                .map(JsonValue::of)
                .collect(JsonCollectors.valuesToArray())
                .toString();
    }

    private CompletionStage<String> buildPredefinedExtraFieldsHeaderReadGrantObject(@Nullable final PolicyId policyId,
            final JsonFieldSelector preDefinedExtraFields)
    {
        return policyEnforcerProvider.getPolicyEnforcer(policyId)
                .thenApply(policyEnforcerOpt ->
                        policyEnforcerOpt.map(policyEnforcer ->
                                StreamSupport.stream(preDefinedExtraFields.spliterator(), false)
                                        .map(pointer -> {
                                            final JsonArray unrestrictedReadSubjects = policyEnforcer.getEnforcer()
                                                    .getSubjectsWithUnrestrictedPermission(
                                                            PoliciesResourceType.thingResource(pointer),
                                                            Permissions.newInstance(Permission.READ)
                                                    )
                                                    .stream()
                                                    .map(AuthorizationSubject::getId)
                                                    .map(JsonValue::of)
                                                    .collect(JsonCollectors.valuesToArray());
                                            return JsonField.newInstance(pointer.toString(), unrestrictedReadSubjects);
                                        })
                                        .collect(JsonCollectors.fieldsToObject())
                                        .toString()
                        ).orElse("{}")
                );
    }

    private static JsonObject buildPredefinedExtraFieldsHeaderObject(
            final Thing thing,
            final JsonFieldSelector preDefinedExtraFields
    ) {
        final JsonObjectBuilder builder = JsonObject.newBuilder();
        final JsonObject thingJson = thing.toJson();
        preDefinedExtraFields.getPointers().forEach(pointer ->
                thingJson.getValue(pointer).ifPresent(thingValue -> builder.set(pointer, thingValue))
        );
        return builder.build();
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

}
