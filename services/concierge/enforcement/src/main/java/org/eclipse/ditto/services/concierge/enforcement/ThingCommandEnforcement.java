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
package org.eclipse.ditto.services.concierge.enforcement;

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.model.things.Permission.ADMINISTRATE;
import static org.eclipse.ditto.services.models.policies.Permission.MIN_REQUIRED_POLICY_PERMISSIONS;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonFieldSelectorBuilder;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.enforcers.AclEnforcer;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.model.namespaces.NamespaceBlockedException;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyException;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclInvalidException;
import org.eclipse.ditto.model.things.AclNotAllowedException;
import org.eclipse.ditto.model.things.AclValidator;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.concierge.enforcement.placeholders.references.PolicyIdReferencePlaceholderResolver;
import org.eclipse.ditto.services.concierge.enforcement.placeholders.references.ReferencePlaceholder;
import org.eclipse.ditto.services.models.concierge.ConciergeMessagingConstants;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.services.models.policies.PoliciesAclMigrations;
import org.eclipse.ditto.services.models.policies.PoliciesValidator;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.EntityId;
import org.eclipse.ditto.services.utils.cache.InvalidateCacheEntry;
import org.eclipse.ditto.services.utils.cache.entry.Entry;
import org.eclipse.ditto.services.utils.cacheloaders.IdentityCache;
import org.eclipse.ditto.signals.commands.base.CommandToExceptionRegistry;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.PolicyErrorResponse;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyConflictException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyUnavailableException;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicy;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyIdNotAllowedException;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyInvalidException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingCommandToAccessExceptionRegistry;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingCommandToModifyExceptionRegistry;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotCreatableException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotModifiableException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingUnavailableException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.pattern.AskTimeoutException;
import akka.pattern.Patterns;

/**
 * Authorize {@code ThingCommand}.
 */
public final class ThingCommandEnforcement extends AbstractEnforcement<ThingCommand> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThingCommandEnforcement.class);

    /**
     * Label of default policy entry in default policy.
     */
    private static final String DEFAULT_POLICY_ENTRY_LABEL = "DEFAULT";

    /**
     * Json fields that are always shown regardless of authorization.
     */
    private static final JsonFieldSelector THING_QUERY_COMMAND_RESPONSE_WHITELIST =
            JsonFactory.newFieldSelector(Thing.JsonFields.ID);

    private final List<SubjectIssuer> subjectIssuersForPolicyMigration;
    private final ActorRef thingsShardRegion;
    private final ActorRef policiesShardRegion;
    private final EnforcerRetriever thingEnforcerRetriever;
    private final EnforcerRetriever policyEnforcerRetriever;
    private final Cache<EntityId, Entry<EntityId>> thingIdCache;
    private final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache;
    private final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer;
    private final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache;
    private final PolicyIdReferencePlaceholderResolver policyIdReferencePlaceholderResolver;

    private ThingCommandEnforcement(final Contextual<ThingCommand> data,
            final ActorRef thingsShardRegion,
            final ActorRef policiesShardRegion,
            final Cache<EntityId, Entry<EntityId>> thingIdCache,
            final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache,
            final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache,
            final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer,
            final List<SubjectIssuer> subjectIssuersForPolicyMigration) {

        super(data);
        this.thingsShardRegion = requireNonNull(thingsShardRegion);
        this.policiesShardRegion = requireNonNull(policiesShardRegion);
        this.subjectIssuersForPolicyMigration = requireNonNull(subjectIssuersForPolicyMigration);

        this.thingIdCache = requireNonNull(thingIdCache);
        this.policyEnforcerCache = requireNonNull(policyEnforcerCache);
        this.aclEnforcerCache = requireNonNull(aclEnforcerCache);
        this.preEnforcer = preEnforcer;
        thingEnforcerRetriever =
                PolicyOrAclEnforcerRetrieverFactory.create(thingIdCache, policyEnforcerCache, aclEnforcerCache);
        policyEnforcerRetriever = new EnforcerRetriever(IdentityCache.INSTANCE, policyEnforcerCache);
        policyIdReferencePlaceholderResolver =
                PolicyIdReferencePlaceholderResolver.of(conciergeForwarder(), getAskTimeout());
    }

    @Override
    public CompletionStage<Contextual<WithDittoHeaders>> enforce() {
        final ThingCommand signal = signal();
        LogUtil.enhanceLogWithCorrelationIdOrRandom(signal);

        try {
            // Validate Thing in CreateThing. Other commands validate their payload in constructor.
            if (signal instanceof CreateThing) {
                ((CreateThing) signal).getThing().validate(signal.getDittoHeaders());
            }
        } catch (final DittoRuntimeException e) {
            return CompletableFuture.completedFuture(handleExceptionally(e));
        }

        return thingEnforcerRetriever.retrieve(entityId(), (enforcerKeyEntry, enforcerEntry) -> {
            try {
                return doEnforce(enforcerKeyEntry, enforcerEntry)
                        .exceptionally(this::handleExceptionally);
            } catch (final RuntimeException e) {
                return CompletableFuture.completedFuture(handleExceptionally(e));
            }
        });
    }

    private CompletionStage<Contextual<WithDittoHeaders>> doEnforce(final Entry<EntityId> enforcerKeyEntry,
            final Entry<Enforcer> enforcerEntry) {
        if (!enforcerEntry.exists()) {
            return enforceThingCommandByNonexistentEnforcer(enforcerKeyEntry);
        } else if (isAclEnforcer(enforcerKeyEntry)) {
            return enforceThingCommandByAclEnforcer(enforcerEntry.getValueOrThrow());
        } else {
            final String policyId = enforcerKeyEntry.getValueOrThrow().getId();
            return enforceThingCommandByPolicyEnforcer(signal(), policyId, enforcerEntry.getValueOrThrow());
        }
    }

    /**
     * Authorize a thing command in the absence of an enforcer. This happens when the thing did not exist or when the
     * policy of the thing does not exist.
     *
     * @param enforcerKeyEntry cache entry in the entity ID cache for the enforcer cache key.
     * @return the completionStage of the contextual including message and receiver
     */
    private CompletionStage<Contextual<WithDittoHeaders>> enforceThingCommandByNonexistentEnforcer(final Entry<EntityId> enforcerKeyEntry) {
        if (enforcerKeyEntry.exists()) {
            // Thing exists but its policy is deleted.
            final String thingId = signal().getThingId();
            final String policyId = enforcerKeyEntry.getValueOrThrow().getId();
            final DittoRuntimeException error = errorForExistingThingWithDeletedPolicy(signal(), thingId, policyId);
            log().info("Enforcer was not existing for Thing <{}>, responding with: {}", thingId, error);
            throw error;
        } else {
            // Without prior enforcer in cache, enforce CreateThing by self.
            return enforceCreateThingBySelf()
                    .thenCompose(pair ->
                            handleInitialCreateThing(pair.createThing, pair.enforcer)
                                    .thenApply(create -> create.withReceiver(thingsShardRegion))
                    )
                    .exceptionally(throwable -> {
                        Throwable cause = throwable;
                        if (throwable instanceof CompletionException) {
                            cause = throwable.getCause();
                        }

                        if (cause instanceof DittoRuntimeException) {
                            LOGGER.debug(
                                    "DittoRuntimeException during enforceThingCommandByNonexistentEnforcer - {}: {}",
                                    cause.getClass().getSimpleName(), cause.getMessage());
                            throw (DittoRuntimeException) cause;
                        } else {
                            LOGGER.warn("Error during thing by itself enforcement - {}: {}",
                                    cause.getClass().getSimpleName(), cause.getMessage());
                            throw GatewayInternalErrorException.newBuilder()
                                    .cause(cause)
                                    .build();
                        }
                    });
        }
    }

    /**
     * Authorize a thing command by ACL enforcer with special handling for the field "/acl".
     *
     * @param enforcer the ACL enforcer.
     * @return the completionStage of the contextual including message and receiver
     */
    private CompletionStage<Contextual<WithDittoHeaders>> enforceThingCommandByAclEnforcer(final Enforcer enforcer) {
        final ThingCommand<?> thingCommand = signal();
        final Optional<? extends ThingCommand> authorizedCommand = authorizeByAcl(enforcer, thingCommand);

        if (authorizedCommand.isPresent()) {
            final ThingCommand commandWithReadSubjects = authorizedCommand.get();
            if (commandWithReadSubjects instanceof RetrieveThing &&
                    shouldRetrievePolicyWithThing(commandWithReadSubjects)) {
                final RetrieveThing retrieveThing = (RetrieveThing) commandWithReadSubjects;
                return retrieveThingAclAndMigrateToPolicy(retrieveThing, enforcer)
                        .thenApply(response -> withMessageToReceiver(response, sender()));
            } else {
                return CompletableFuture.completedFuture(forwardToThingsShardRegion(commandWithReadSubjects));
            }
        } else {
            throw errorForThingCommand(thingCommand);
        }
    }

    private CompletionStage<WithDittoHeaders> retrieveThingAclAndMigrateToPolicy(final RetrieveThing retrieveThing,
            final Enforcer enforcer) {
        final JsonFieldSelectorBuilder jsonFieldSelectorBuilder =
                JsonFactory.newFieldSelectorBuilder().addFieldDefinition(Thing.JsonFields.ACL);
        retrieveThing.getSelectedFields().ifPresent(jsonFieldSelectorBuilder::addPointers);
        final DittoHeaders dittoHeaders = retrieveThing
                .getDittoHeaders()
                .toBuilder()
                .schemaVersion(JsonSchemaVersion.V_1)
                .build();
        final RetrieveThing retrieveThingV1 = RetrieveThing.getBuilder(retrieveThing.getThingId(), dittoHeaders)
                .withSelectedFields(jsonFieldSelectorBuilder.build())
                .build();
        return Patterns.ask(thingsShardRegion, retrieveThingV1, getAskTimeout())
                .handle((response, error) -> {
                    if (response instanceof RetrieveThingResponse) {
                        final RetrieveThingResponse retrieveThingResponse = (RetrieveThingResponse) response;
                        final Optional<AccessControlList> aclOptional =
                                retrieveThingResponse.getThing().getAccessControlList();
                        if (aclOptional.isPresent()) {
                            final Policy policy =
                                    PoliciesAclMigrations.accessControlListToPolicyEntries(aclOptional.get(),
                                            retrieveThing.getThingId(), subjectIssuersForPolicyMigration);
                            return reportAggregatedThingAndPolicy(retrieveThing,
                                    retrieveThingResponse.setDittoHeaders(retrieveThing.getDittoHeaders()),
                                    policy, enforcer);
                        } else {
                            return retrieveThingResponse.setDittoHeaders(retrieveThing.getDittoHeaders());
                        }
                    } else if (isAskTimeoutException(response, error)) {
                        throw reportThingUnavailable();
                    } else {
                        throw reportUnexpectedErrorOrResponse("retrieving thing for ACL migration", response, error);
                    }
                });
    }

    /**
     * Authorize a thing command by policy enforcer with view restriction for query commands.
     *
     * @param policyId Id of the thing's policy.
     * @param enforcer the policy enforcer.
     * @return the completionStage of the contextual including message and receiver
     */
    private CompletionStage<Contextual<WithDittoHeaders>> enforceThingCommandByPolicyEnforcer(final ThingCommand<?> thingCommand,
            final String policyId, final Enforcer enforcer) {
        return authorizeByPolicy(enforcer, thingCommand)
                .map(commandWithReadSubjects -> {
                    if (commandWithReadSubjects instanceof ThingQueryCommand) {
                        final ThingQueryCommand thingQueryCommand = (ThingQueryCommand) commandWithReadSubjects;
                        if (thingQueryCommand instanceof RetrieveThing &&
                                shouldRetrievePolicyWithThing(thingQueryCommand)) {

                            final RetrieveThing retrieveThing = (RetrieveThing) thingQueryCommand;
                            return retrieveThingAndPolicy(retrieveThing, policyId, enforcer)
                                    .thenApply(response -> withMessageToReceiver(response, sender()));
                        } else {
                            return askThingsShardRegionAndBuildJsonView(thingQueryCommand, enforcer)
                                    .thenApply(response -> withMessageToReceiver(response, sender()));
                        }
                    } else {
                        return CompletableFuture.completedFuture(forwardToThingsShardRegion(commandWithReadSubjects));
                    }
                })
                .orElseThrow(() -> errorForThingCommand(thingCommand));
    }

    /**
     * Retrieve for response of a query command and limit the response according to a policy enforcer.
     *
     * @param commandWithReadSubjects the command to ask.
     * @param enforcer enforcer to build JsonView with.
     * @return always {@code true}.
     */
    private CompletionStage<WithDittoHeaders> askThingsShardRegionAndBuildJsonView(
            final ThingQueryCommand commandWithReadSubjects,
            final Enforcer enforcer) {

        return Patterns.ask(thingsShardRegion, commandWithReadSubjects, getAskTimeout())
                .handle((response, error) -> {
                    if (response instanceof ThingQueryCommandResponse) {
                        return reportJsonViewForThingQuery((ThingQueryCommandResponse) response, enforcer);
                    } else if (response instanceof DittoRuntimeException) {
                        return (DittoRuntimeException) response;
                    } else if (isAskTimeoutException(response, error)) {
                        final AskTimeoutException askTimeoutException = error instanceof AskTimeoutException
                                ? (AskTimeoutException) error
                                : (AskTimeoutException) response;
                        return reportTimeoutForThingQuery(commandWithReadSubjects, askTimeoutException);
                    } else if (error != null) {
                        return reportUnexpectedError("before building JsonView", error);
                    } else {
                        return reportUnknownResponse("before building JsonView", response);
                    }
                });
    }

    /**
     * Retrieve a thing and its policy and combine them into a response.
     *
     * @param retrieveThing the retrieve-thing command.
     * @param policyId ID of the thing's policy.
     * @param enforcer the enforcer for the command.
     * @return always {@code true}.
     */
    private CompletionStage<WithDittoHeaders> retrieveThingAndPolicy(
            final RetrieveThing retrieveThing,
            final String policyId,
            final Enforcer enforcer) {

        final DittoHeaders dittoHeadersWithoutPreconditionHeaders = retrieveThing.getDittoHeaders()
                .toBuilder()
                .removePreconditionHeaders()
                .build();

        final Optional<RetrievePolicy> retrievePolicyOptional = PolicyCommandEnforcement.authorizePolicyCommand(
                RetrievePolicy.of(policyId, dittoHeadersWithoutPreconditionHeaders), enforcer);

        if (retrievePolicyOptional.isPresent()) {
            return retrieveThingBeforePolicy(retrieveThing)
                    .thenCompose(retrieveThingResponse -> {
                        if (retrieveThingResponse instanceof RetrieveThingResponse) {
                            final RetrievePolicy retrievePolicy = retrievePolicyOptional.get();
                            return retrieveInlinedPolicyForThing(retrieveThing, retrievePolicy)
                                    .thenApply(policyResponse -> {
                                        if (policyResponse.isPresent()) {
                                            final RetrievePolicyResponse filteredPolicyResponse =
                                                    PolicyCommandEnforcement.buildJsonViewForPolicyQueryCommandResponse(
                                                            policyResponse.get(), enforcer);
                                            return reportAggregatedThingAndPolicyResponse(retrieveThing,
                                                    (RetrieveThingResponse) retrieveThingResponse,
                                                    filteredPolicyResponse, enforcer);
                                        } else {
                                            return retrieveThingResponse;
                                        }
                                    });
                        } else {
                            return CompletableFuture.completedFuture(retrieveThingResponse);
                        }
                    });
        } else {
            // sender is not authorized to view the policy, ignore the request to embed policy.
            return askThingsShardRegionAndBuildJsonView(retrieveThing, enforcer);
        }
    }

    /**
     * Retrieve a thing before retrieving its inlined policy. Report errors to sender.
     *
     * @param command the command.
     * @return future response from things-shard-region.
     */
    private CompletionStage<WithDittoHeaders> retrieveThingBeforePolicy(final RetrieveThing command) {

        return Patterns.ask(thingsShardRegion, command, getAskTimeout())
                .handle((response, error) -> {
                    if (response instanceof WithDittoHeaders) {
                        return (WithDittoHeaders) response;
                    } else if (isAskTimeoutException(response, error)) {
                        return reportThingUnavailable();
                    } else {
                        return reportUnexpectedErrorOrResponse("retrieving thing before inlined policy", response,
                                error);
                    }
                });
    }

    private ThingUnavailableException reportThingUnavailable() {
        return ThingUnavailableException.newBuilder(signal().getThingId()).dittoHeaders(dittoHeaders()).build();
    }

    /**
     * Retrieve inlined policy after retrieving a thing. Do not report errors.
     *
     * @param retrieveThing the original command.
     * @param retrievePolicy the command to retrieve the thing's policy.
     * @return future response from policies-shard-region.
     */
    private CompletionStage<Optional<RetrievePolicyResponse>> retrieveInlinedPolicyForThing(
            final RetrieveThing retrieveThing,
            final RetrievePolicy retrievePolicy) {

        return preEnforcer.apply(retrievePolicy)
                .thenCompose(msg -> Patterns.ask(policiesShardRegion, msg, getAskTimeout()))
                .handle((response, error) -> {
                    LOGGER.debug("Response of policiesShardRegion: <{}>", response);
                    if (response instanceof RetrievePolicyResponse) {
                        return Optional.of((RetrievePolicyResponse) response);
                    } else if (error != null) {
                        log(error).error(error, "retrieving inlined policy after RetrieveThing");
                    } else {
                        log(response).info(
                                "No authorized response when retrieving inlined policy <{}> for thing <{}>: {}",
                                retrievePolicy.getId(), retrieveThing.getThingId(), response);
                    }
                    return Optional.empty();
                });
    }

    /**
     * Put thing and policy together as response to the sender.
     *
     * @param retrieveThing the original command.
     * @param thingResponse response from things-shard-region.
     * @param policyResponse response from policies-shard-region.
     * @param enforcer enforcer to bulid the Json view.
     */
    private RetrieveThingResponse reportAggregatedThingAndPolicyResponse(
            final RetrieveThing retrieveThing,
            final RetrieveThingResponse thingResponse,
            final RetrievePolicyResponse policyResponse,
            final Enforcer enforcer) {

        return reportAggregatedThingAndPolicy(retrieveThing, thingResponse, policyResponse.getPolicy(), enforcer);
    }

    private RetrieveThingResponse reportAggregatedThingAndPolicy(
            final RetrieveThing retrieveThing,
            final RetrieveThingResponse retrieveThingResponse,
            final Policy policy,
            final Enforcer enforcer) {

        final RetrieveThingResponse limitedView =
                buildJsonViewForThingQueryCommandResponse(retrieveThingResponse, enforcer);

        final JsonObject inlinedPolicy =
                policy.toInlinedJson(retrieveThing.getImplementedSchemaVersion(), FieldType.notHidden());

        final JsonObject thingWithInlinedPolicy = limitedView.getEntity().asObject().toBuilder()
                .setAll(inlinedPolicy)
                .build();

        return limitedView.setEntity(thingWithInlinedPolicy);
    }

    /**
     * Report timeout of {@code ThingQueryCommand}.
     *
     * @param command the original command.
     * @param askTimeoutException the timeout exception.
     */
    private ThingUnavailableException reportTimeoutForThingQuery(
            final ThingQueryCommand command,
            final AskTimeoutException askTimeoutException) {
        log(command).error(askTimeoutException, "Timeout before building JsonView");
        return ThingUnavailableException.newBuilder(command.getThingId())
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    /**
     * Mixin-private: report thing query response with view on entity restricted by enforcer.
     *
     * @param thingQueryCommandResponse response of query.
     * @param enforcer the enforcer.
     */
    private ThingQueryCommandResponse reportJsonViewForThingQuery(
            final ThingQueryCommandResponse<?> thingQueryCommandResponse,
            final Enforcer enforcer) {

        try {
            return buildJsonViewForThingQueryCommandResponse(thingQueryCommandResponse, enforcer);
        } catch (final RuntimeException e) {
            throw reportError("Error after building JsonView", e);
        }
    }

    /**
     * Query caches again to authorize a {@code CreateThing} command with explicit policy ID and no inline policy.
     *
     * @param command the command.
     * @param policyId the policy ID.
     * @return the completionStage of contextual including message and receiver
     */
    private CompletionStage<Contextual<WithDittoHeaders>> enforceCreateThingForNonexistentThingWithPolicyId(
            final CreateThing command, final String policyId) {

        final EntityId policyEntityId = EntityId.of(PolicyCommand.RESOURCE_TYPE, policyId);
        return policyEnforcerRetriever.retrieve(policyEntityId, (policyIdEntry, policyEnforcerEntry) -> {
            if (policyEnforcerEntry.exists()) {
                return enforceThingCommandByPolicyEnforcer(command, policyId, policyEnforcerEntry.getValueOrThrow());
            } else {
                throw errorForExistingThingWithDeletedPolicy(command, command.getThingId(), policyId);
            }
        });
    }

    /**
     * Limit view on entity of {@code ThingQueryCommandResponse} by enforcer.
     *
     * @param response the response.
     * @param enforcer the enforcer.
     * @return response with view on entity restricted by enforcer.
     */
    private static <T extends ThingQueryCommandResponse> T buildJsonViewForThingQueryCommandResponse(
            final ThingQueryCommandResponse<T> response,
            final Enforcer enforcer) {

        final JsonValue entity = response.getEntity();
        if (entity.isObject()) {
            final JsonObject filteredView =
                    getJsonViewForThingQueryCommandResponse(entity.asObject(), response, enforcer);
            return response.setEntity(filteredView);
        } else {
            return response.setEntity(entity);
        }
    }

    /**
     * Forward a command to things-shard-region.
     *
     * @param command command to forward.
     * @return the contextual including message and receiver
     */
    private Contextual<WithDittoHeaders> forwardToThingsShardRegion(final ThingCommand command) {
        if (command instanceof ThingModifyCommand && ((ThingModifyCommand) command).changesAuthorization()) {
            invalidateThingCaches(command.getThingId());
        }
        return withMessageToReceiver(command, thingsShardRegion);
    }

    /**
     * Whenever a Command changed the authorization, the caches must be invalidated - otherwise a directly following
     * Command targeted for the same entity will probably fail as the enforcer was not yet updated.
     *
     * @param thingId the ID of the Thing to invalidate caches for.
     */
    private void invalidateThingCaches(final String thingId) {
        final EntityId entityId = EntityId.of(ThingCommand.RESOURCE_TYPE, thingId);
        thingIdCache.invalidate(entityId);
        aclEnforcerCache.invalidate(entityId);
        pubSubMediator().tell(new DistributedPubSubMediator.SendToAll(
                        ConciergeMessagingConstants.ENFORCER_ACTOR_PATH,
                        InvalidateCacheEntry.of(entityId),
                        true),
                self());
    }

    private void invalidatePolicyCache(final String policyId) {
        final EntityId entityId = EntityId.of(PolicyCommand.RESOURCE_TYPE, policyId);
        policyEnforcerCache.invalidate(entityId);
        pubSubMediator().tell(new DistributedPubSubMediator.SendToAll(
                        ConciergeMessagingConstants.ENFORCER_ACTOR_PATH,
                        InvalidateCacheEntry.of(entityId),
                        true),
                self());
    }

    /**
     * Restrict view on a JSON object by enforcer.
     *
     * @param responseEntity the JSON object to restrict view on.
     * @param response the response containing the object.
     * @param enforcer the enforcer.
     * @return JSON object with view restricted by enforcer.
     */
    private static JsonObject getJsonViewForThingQueryCommandResponse(final JsonObject responseEntity,
            final ThingQueryCommandResponse response,
            final Enforcer enforcer) {


        final ResourceKey resourceKey = ResourceKey.newInstance(ThingCommand.RESOURCE_TYPE, response.getResourcePath());
        final AuthorizationContext authorizationContext = response.getDittoHeaders().getAuthorizationContext();

        return enforcer.buildJsonView(resourceKey, responseEntity, authorizationContext,
                THING_QUERY_COMMAND_RESPONSE_WHITELIST, Permissions.newInstance(Permission.READ));
    }


    /**
     * Create error for commands to an existing thing whose policy is deleted.
     *
     * @param thingCommand the triggering command.
     * @param thingId ID of the thing.
     * @param policyId ID of the deleted policy.
     * @return an appropriate error.
     */
    private static DittoRuntimeException errorForExistingThingWithDeletedPolicy(
            final ThingCommand thingCommand,
            final String thingId,
            final String policyId) {

        final String message = String.format(
                "The Thing with ID '%s' could not be accessed as its Policy with ID '%s' is not or no longer existing.",
                thingId, policyId);
        final String description = String.format(
                "Recreate/create the Policy with ID '%s' in order to get access to the Thing again.",
                policyId);

        if (thingCommand instanceof ThingModifyCommand) {
            return ThingNotModifiableException.newBuilder(thingId)
                    .message(message)
                    .description(description)
                    .dittoHeaders(thingCommand.getDittoHeaders())
                    .build();
        } else {
            return ThingNotAccessibleException.newBuilder(thingId)
                    .message(message)
                    .description(description)
                    .dittoHeaders(thingCommand.getDittoHeaders())
                    .build();
        }
    }

    /**
     * Create error due to failing to execute a thing-command in the expected way.
     *
     * @param thingCommand the command.
     * @return the error.
     */
    static DittoRuntimeException errorForThingCommand(final ThingCommand thingCommand) {
        final CommandToExceptionRegistry<ThingCommand, DittoRuntimeException> registry =
                thingCommand instanceof ThingModifyCommand
                        ? ThingCommandToModifyExceptionRegistry.getInstance()
                        : ThingCommandToAccessExceptionRegistry.getInstance();
        return registry.exceptionFrom(thingCommand);
    }

    /**
     * Authorize a thing-command by authorization information contained in itself. Only {@code CreateThing} commands are
     * authorized in this manner in the absence of an existing enforcer. {@code ModifyThing} commands are transformed to
     * {@code CreateThing} commands before being processed.
     *
     * @return optionally the authorized command extended by  read subjects.
     */
    private CompletionStage<CreateThingWithEnforcer> enforceCreateThingBySelf() {

        final ThingCommand thingCommand = transformModifyThingToCreateThing(signal());
        if (thingCommand instanceof CreateThing) {
            final CompletionStage<CreateThing> createThingFuture =
                    replaceInitialPolicyWithCopiedPolicyIfPresent((CreateThing) thingCommand);
            return createThingFuture.thenApply(createThing -> {
                final Optional<JsonObject> initialPolicyOptional = createThing.getInitialPolicy();
                if (initialPolicyOptional.isPresent()) {
                    return enforceCreateThingByOwnInlinedPolicy(createThing, initialPolicyOptional.get())
                            .orElse(null);
                } else {
                    final Optional<AccessControlList> aclOptional =
                            createThing.getThing().getAccessControlList().filter(acl -> !acl.isEmpty());
                    return aclOptional.map(aclEntries -> enforceCreateThingByOwnAcl(createThing, aclEntries))
                            .orElseGet(() -> enforceCreateThingByAuthorizationContext(createThing))
                            .orElse(null);
                }
            });
        } else {
            // Other commands cannot be authorized by ACL or policy contained in self.
            final DittoRuntimeException error =
                    ThingNotAccessibleException.newBuilder(thingCommand.getThingId())
                            .dittoHeaders(thingCommand.getDittoHeaders())
                            .build();
            log().info("Enforcer was not existing for Thing <{}> and no auth info was inlined, " +
                    "responding with: {}", thingCommand.getThingId(), error);
            throw error;
        }
    }

    private CompletionStage<CreateThing> replaceInitialPolicyWithCopiedPolicyIfPresent(final CreateThing createThing) {

        return getInitialPolicyOrCopiedPolicy(createThing).thenApply(initialPolicyOrCopiedPolicy ->
                CreateThing.of(createThing.getThing(), initialPolicyOrCopiedPolicy, createThing.getDittoHeaders())
        );
    }

    private CompletionStage<JsonObject> getInitialPolicyOrCopiedPolicy(final CreateThing createThing) {

        return createThing.getPolicyIdOrPlaceholder()
                .flatMap(ReferencePlaceholder::fromCharSequence)
                .map(referencePlaceholder -> {
                    log(createThing).debug(
                            "CreateThing command contains a reference placeholder for the policy it wants to copy: {}",
                            referencePlaceholder);
                    return policyIdReferencePlaceholderResolver.resolve(referencePlaceholder, dittoHeaders());
                })
                .orElseGet(() -> CompletableFuture.completedFuture(createThing.getPolicyIdOrPlaceholder().orElse(null)))
                .thenCompose(policyId -> {
                    if (policyId != null) {
                        log().debug("CreateThing command wants to use a copy of Policy <{}>", policyId);
                        return retrievePolicyWithEnforcement(policyId)
                                .thenApply(policy -> policy.toJson(JsonSchemaVersion.V_2).remove("policyId"));
                    } else {
                        log().debug("CreateThing command did not contain a policy that should be copied.");
                        return CompletableFuture.completedFuture(createThing.getInitialPolicy().orElse(null));
                    }
                });
    }

    private CompletionStage<Policy> retrievePolicyWithEnforcement(final String policyId) {

        return Patterns.ask(conciergeForwarder(), RetrievePolicy.of(policyId, dittoHeaders()), getAskTimeout())
                .thenApply(response -> {
                    if (response instanceof RetrievePolicyResponse) {
                        return ((RetrievePolicyResponse) response).getPolicy();
                    } else if (response instanceof PolicyErrorResponse) {
                        throw ((PolicyErrorResponse) response).getDittoRuntimeException();
                    } else if (response instanceof DittoRuntimeException) {
                        throw (DittoRuntimeException) response;
                    } else {
                        log().error(
                                "Got an unexpected response while retrieving a Policy that should be copied" +
                                        " during Thing creation: {}", response);
                        throw GatewayInternalErrorException.newBuilder().build();
                    }
                });

    }

    private Optional<CreateThingWithEnforcer> enforceCreateThingByAuthorizationContext(final CreateThing createThing) {
        // Command without authorization information is authorized by default.
        final Set<String> authorizedSubjects = createThing.getDittoHeaders()
                .getAuthorizationContext()
                .getFirstAuthorizationSubject()
                .map(subject -> Collections.singleton(subject.getId()))
                .orElse(Collections.emptySet());
        final CreateThing command =
                AbstractEnforcement.addReadSubjectsToSignal(createThing, authorizedSubjects);
        final Enforcer enforcer = new AuthorizedSubjectsEnforcer(authorizedSubjects);
        return Optional.of(new CreateThingWithEnforcer(command, enforcer));
    }

    private Optional<CreateThingWithEnforcer> enforceCreateThingByOwnInlinedPolicy(final CreateThing createThing,
            final JsonObject inlinedPolicy) {

        return checkInitialPolicy(createThing, inlinedPolicy).flatMap(initialPolicy -> {

            if (PoliciesValidator.newInstance(initialPolicy).isValid()) {
                final Enforcer initialEnforcer = PolicyEnforcers.defaultEvaluator(initialPolicy);
                return attachEnforcerOrReplyWithError(createThing, initialEnforcer,
                        ThingCommandEnforcement::authorizeByPolicy);
            } else {
                throw PolicyInvalidException.newBuilder(MIN_REQUIRED_POLICY_PERMISSIONS, createThing.getThingId())
                                .dittoHeaders(createThing.getDittoHeaders())
                                .build();
            }
        });
    }

    private Optional<Policy> checkInitialPolicy(final CreateThing createThing, final JsonObject inlinedPolicy) {

        try {
            // Java doesn't permit conversion of this early return into assignment to final variable.
            return Optional.of(PoliciesModelFactory.newPolicy(inlinedPolicy));
        } catch (final JsonRuntimeException | DittoJsonException e) {
            final String thingId = createThing.getThingId();
            throw PolicyInvalidException.newBuilderForCause(e, thingId)
                    .dittoHeaders(createThing.getDittoHeaders())
                    .build();
        } catch (final DittoRuntimeException e) {
            final DittoHeaders dittoHeaders = createThing.getDittoHeaders();
            if (e instanceof PolicyException) {
                // user error; no need to log stack trace.
                throw e.setDittoHeaders(dittoHeaders);
            } else {
                throw reportError("Error during creation of inline policy from JSON", e);
            }
        }
    }

    private Optional<CreateThingWithEnforcer> enforceCreateThingByOwnAcl(final CreateThing command,
            final AccessControlList acl) {

        if (AclValidator.newInstance(acl, Thing.MIN_REQUIRED_PERMISSIONS).isValid()) {
            final Enforcer initialEnforcer = AclEnforcer.of(acl);
            return attachEnforcerOrReplyWithError(command, initialEnforcer, ThingCommandEnforcement::authorizeByAcl);
        } else {
            throw AclInvalidException.newBuilder(command.getThingId())
                    .dittoHeaders(command.getDittoHeaders())
                    .build();
        }

    }

    private Optional<CreateThingWithEnforcer> attachEnforcerOrReplyWithError(final CreateThing command,
            final Enforcer enforcer,
            final BiFunction<Enforcer, ThingCommand<CreateThing>, Optional<CreateThing>> authorization) {

        final Optional<CreateThing> authorizedCommand = authorization.apply(enforcer, command);
        if (authorizedCommand.isPresent()) {
            return authorizedCommand.map(cmd -> new CreateThingWithEnforcer(cmd, enforcer));
        } else {
            throw errorForThingCommand(command);
        }
    }

    /**
     * Transform a {@code ModifyThing} command sent to nonexistent thing to {@code CreateThing} command if it is sent to
     * a nonexistent thing.
     *
     * @param receivedCommand the command to transform.
     * @return {@code CreateThing} command containing the same information if the argument is a {@code ModifyThing}
     * command. Otherwise return the command itself.
     */
    private static ThingCommand transformModifyThingToCreateThing(final ThingCommand receivedCommand) {
        if (receivedCommand instanceof ModifyThing) {
            final ModifyThing modifyThing = (ModifyThing) receivedCommand;
            final JsonObject initialPolicy = modifyThing.getInitialPolicy().orElse(null);
            final String policyIdOrPlaceholder = modifyThing.getPolicyIdOrPlaceholder().orElse(null);
            final Thing newThing = modifyThing.getThing().toBuilder()
                    .setId(modifyThing.getThingId())
                    .build();
            return CreateThing.of(newThing, initialPolicy, policyIdOrPlaceholder, modifyThing.getDittoHeaders());
        } else {
            return receivedCommand;
        }
    }

    /**
     * Authorize a thing-command by a policy enforcer.
     *
     * @param <T> type of the thing-command.
     * @param policyEnforcer the policy enforcer.
     * @param command the command to authorize.
     * @return optionally the authorized command extended by read subjects.
     */
    static <T extends ThingCommand> Optional<T> authorizeByPolicy(final Enforcer policyEnforcer,
            final ThingCommand<T> command) {

        final ResourceKey thingResourceKey = PoliciesResourceType.thingResource(command.getResourcePath());
        final AuthorizationContext authorizationContext = command.getDittoHeaders().getAuthorizationContext();
        final boolean authorized;
        if (command instanceof ThingModifyCommand) {
            final String permission = Permission.WRITE;
            authorized = policyEnforcer.hasUnrestrictedPermissions(thingResourceKey, authorizationContext, permission);
        } else {
            final String permission = Permission.READ;
            authorized = policyEnforcer.hasPartialPermissions(thingResourceKey, authorizationContext, permission);
        }
        return authorized
                ? Optional.of(AbstractEnforcement.addReadSubjectsToThingSignal(command, policyEnforcer))
                : Optional.empty();
    }

    /**
     * Authorize a thing-command by an ACL enforcer.
     *
     * @param <T> type of the thing-command.
     * @param aclEnforcer the ACL enforcer.
     * @param command the command to authorize.
     * @return optionally the authorized command extended by read subjects.
     */
    static <T extends ThingCommand> Optional<T> authorizeByAcl(final Enforcer aclEnforcer,
            final ThingCommand<T> command) {
        final ResourceKey thingResourceKey = PoliciesResourceType.thingResource(command.getResourcePath());
        final AuthorizationContext authorizationContext = command.getDittoHeaders().getAuthorizationContext();
        final Permissions permissions = command instanceof ThingModifyCommand
                ? computeAclPermissions((ThingModifyCommand) command)
                : Permissions.newInstance(Permission.READ);
        return aclEnforcer.hasUnrestrictedPermissions(thingResourceKey, authorizationContext, permissions)
                ? Optional.of(AbstractEnforcement.addReadSubjectsToThingSignal(command, aclEnforcer))
                : Optional.empty();
    }

    /**
     * Compute ACL permissions relevant for a {@code ThingModifyCommand}. The field "/acl" is handled specially with the
     * "ADMINISTRATE" permission.
     *
     * @param command the command.
     * @return permissions needed to execute the command.
     */
    private static Permissions computeAclPermissions(final ThingModifyCommand command) {
        return command.changesAuthorization()
                ? Permissions.newInstance(Permission.WRITE, ADMINISTRATE.name())
                : Permissions.newInstance(Permission.WRITE);
    }

    /**
     * Check if inlined policy should be retrieved together with the thing.
     *
     * @param command the thing query command.
     * @return whether it is necessary to retrieve the thing's policy.
     */
    private static boolean shouldRetrievePolicyWithThing(final ThingCommand command) {
        final RetrieveThing retrieveThing = (RetrieveThing) command;
        final boolean isNotV1 = JsonSchemaVersion.V_1 != command.getImplementedSchemaVersion();
        return isNotV1 && retrieveThing.getSelectedFields().filter(selector ->
                selector.getPointers().stream().anyMatch(jsonPointer ->
                        jsonPointer.getRoot()
                                .filter(jsonKey -> Policy.INLINED_FIELD_NAME.equals(jsonKey.toString()))
                                .isPresent()))
                .isPresent();
    }

    private CompletionStage<Contextual<WithDittoHeaders>> handleInitialCreateThing(
            final CreateThing createThing, final Enforcer enforcer) {

        if (shouldCreatePolicyForCreateThing(createThing)) {
            final Optional<DittoRuntimeException> errorOpt = checkForErrorsInCreateThingWithPolicy(createThing);
            if (errorOpt.isPresent()) {
                throw errorOpt.get();
            } else {
                return createThingWithInitialPolicy(createThing, enforcer)
                        .thenApply(this::forwardToThingsShardRegion);
            }
        } else if (createThing.getThing().getPolicyId().isPresent()) {
            final String policyId = createThing.getThing().getPolicyId().orElseThrow(IllegalStateException::new);
            final Optional<DittoRuntimeException> errorOpt = checkForErrorsInCreateThingWithPolicy(createThing);
            if (errorOpt.isPresent()) {
                throw errorOpt.get();
            } else {
                return enforceCreateThingForNonexistentThingWithPolicyId(createThing, policyId);
            }
        } else {
            // nothing to do with policy, simply forward the command
            return CompletableFuture.completedFuture(forwardToThingsShardRegion(createThing));
        }
    }

    private CompletionStage<CreateThing> createThingWithInitialPolicy(final CreateThing createThing, final Enforcer enforcer) {

        try {
            final Optional<Policy> policy =
                    getInlinedOrDefaultPolicyForCreateThing(createThing);

            if (policy.isPresent()) {

                final DittoHeaders dittoHeadersWithoutPreconditionHeaders = createThing.getDittoHeaders()
                        .toBuilder()
                        .removePreconditionHeaders()
                        .build();

                final CreatePolicy createPolicy = CreatePolicy.of(policy.get(), dittoHeadersWithoutPreconditionHeaders);
                final Optional<CreatePolicy> authorizedCreatePolicy =
                        PolicyCommandEnforcement.authorizePolicyCommand(createPolicy, enforcer);

                // CreatePolicy is rejected; abort CreateThing.
                return authorizedCreatePolicy
                        .map(cmd -> createPolicyAndThing(cmd, createThing))
                        .orElseThrow(() -> errorForThingCommand(createThing));
            } else {
                // cannot create policy.
                final String thingId = createThing.getThingId();
                final String message = String.format("The Thing with ID '%s' could not be created with implicit " +
                        "Policy because no authorization subject is present.", thingId);
                throw ThingNotCreatableException.newBuilderForPolicyMissing(thingId, thingId)
                                .message(message)
                                .description(() -> null)
                                .dittoHeaders(createThing.getDittoHeaders())
                                .build();
            }
        } catch (final RuntimeException error) {
            throw reportError("error before creating thing with initial policy", error);
        }
    }

    private CompletionStage<CreateThing> createPolicyAndThing(final CreatePolicy createPolicy,
            final CreateThing createThingWithoutPolicyId) {

        final CreateThing createThing = CreateThing.of(
                createThingWithoutPolicyId.getThing().setPolicyId(createPolicy.getId()),
                null,
                createThingWithoutPolicyId.getDittoHeaders());

        invalidatePolicyCache(createPolicy.getId());
        return preEnforcer.apply(createPolicy)
                .thenCompose(msg -> Patterns.ask(policiesShardRegion, msg, getAskTimeout()))
                .thenApply(policyResponse -> {
                    handlePolicyResponseForCreateThing(createPolicy, createThing, policyResponse);

                    // TODO TJ invalidate the caches AFTER the thing was created .. but how!? should work for both ACL + policies based
                    invalidateThingCaches(createThing.getThingId());

                    return createThing;
                });
    }

    private void handlePolicyResponseForCreateThing(
            final CreatePolicy createPolicy,
            final CreateThing createThing,
            final Object policyResponse) {

        if (!(policyResponse instanceof CreatePolicyResponse)) {
            if (shouldReportInitialPolicyCreationFailure(policyResponse)) {

                throw reportInitialPolicyCreationFailure(createPolicy.getId(), createThing);
            } else if (isAskTimeoutException(policyResponse, null)) {

                throw PolicyUnavailableException.newBuilder(createThing.getThingId())
                        .dittoHeaders(createThing.getDittoHeaders())
                        .build();
            } else {

                final String hint =
                        String.format("creating initial policy during creation of Thing <%s>",
                                createThing.getThingId());
                throw reportUnexpectedErrorOrResponse(hint, policyResponse, null);
            }
        }
    }

    private boolean shouldReportInitialPolicyCreationFailure(final Object policyResponse) {
        return policyResponse instanceof PolicyConflictException ||
                policyResponse instanceof PolicyNotAccessibleException ||
                policyResponse instanceof NamespaceBlockedException;
    }

    private ThingNotCreatableException reportInitialPolicyCreationFailure(final String policyId, final CreateThing command) {

        log(command).info("Failed to create Policy with ID '{}' is already existing, the CreateThing " +
                "command which would have created a Policy for the Thing with ID '{}' " +
                "is therefore not handled", policyId, command.getThingId());
        return ThingNotCreatableException.newBuilderForPolicyExisting(command.getThingId(), policyId)
                        .dittoHeaders(command.getDittoHeaders())
                        .build();
    }

    private static Optional<Policy> getInlinedOrDefaultPolicyForCreateThing(final CreateThing createThing) {
        final Optional<JsonObject> initialPolicy = createThing.getInitialPolicy();
        if (initialPolicy.isPresent()) {
            final JsonObject policyJson = initialPolicy.get();
            final JsonObjectBuilder policyJsonBuilder = policyJson.toBuilder();
            final Thing thing = createThing.getThing();
            if (thing.getPolicyId().isPresent() || !policyJson.contains(Policy.JsonFields.ID.getPointer())) {
                final String policyId = thing.getPolicyId().orElse(createThing.getThingId());
                policyJsonBuilder.set(Policy.JsonFields.ID, policyId);
            }
            return Optional.of(PoliciesModelFactory.newPolicy(policyJsonBuilder.build()));
        } else {
            return getDefaultPolicy(createThing.getDittoHeaders().getAuthorizationContext(), createThing.getThingId());
        }
    }

    private static Optional<DittoRuntimeException> checkForErrorsInCreateThingWithPolicy(final CreateThing command) {
        return checkAclAbsenceInCreateThing(command)
                .map(Optional::of)
                .orElseGet(() -> checkPolicyIdValidityForCreateThing(command));
    }

    private static Optional<DittoRuntimeException> checkAclAbsenceInCreateThing(final CreateThing createThing) {
        if (createThing.getThing().getAccessControlList().isPresent()) {
            final DittoRuntimeException error = AclNotAllowedException.newBuilder(createThing.getThingId())
                    .dittoHeaders(createThing.getDittoHeaders())
                    .build();
            return Optional.of(error);
        } else {
            return Optional.empty();
        }
    }

    private static Optional<DittoRuntimeException> checkPolicyIdValidityForCreateThing(final CreateThing createThing) {
        final Thing thing = createThing.getThing();
        final Optional<String> policyIdOpt = thing.getPolicyId();
        final Optional<String> policyIdInPolicyOpt = createThing.getInitialPolicy()
                .flatMap(jsonObject -> jsonObject.getValue(Thing.JsonFields.POLICY_ID));

        final boolean isValid;
        if (policyIdOpt.isPresent()) {
            isValid = !policyIdInPolicyOpt.isPresent() || policyIdInPolicyOpt.equals(policyIdOpt);
        } else {
            isValid = true;
        }

        if (!isValid) {
            final DittoRuntimeException error = PolicyIdNotAllowedException.newBuilder(createThing.getThingId())
                    .dittoHeaders(createThing.getDittoHeaders())
                    .build();
            return Optional.of(error);
        } else {
            return Optional.empty();
        }
    }

    private static boolean shouldCreatePolicyForCreateThing(final CreateThing createThing) {
        final JsonSchemaVersion commandVersion =
                createThing.getDittoHeaders().getSchemaVersion().orElse(JsonSchemaVersion.LATEST);
        return createThing.getInitialPolicy().isPresent() ||
                (JsonSchemaVersion.V_1 != commandVersion && !createThing.getThing().getPolicyId().isPresent());
    }

    private static Optional<Policy> getDefaultPolicy(final AuthorizationContext authorizationContext,
            final CharSequence thingId) {

        final Optional<Subject> subjectOptional = authorizationContext.getFirstAuthorizationSubject()
                .map(AuthorizationSubject::getId)
                .map(SubjectId::newInstance)
                .map(Subject::newInstance);

        return subjectOptional.map(subject ->
                Policy.newBuilder(thingId)
                        .forLabel(DEFAULT_POLICY_ENTRY_LABEL)
                        .setSubject(subject)
                        .setGrantedPermissions(PoliciesResourceType.thingResource("/"),
                                org.eclipse.ditto.services.models.things.Permission.DEFAULT_THING_PERMISSIONS)
                        .setGrantedPermissions(PoliciesResourceType.policyResource("/"),
                                org.eclipse.ditto.services.models.policies.Permission.DEFAULT_POLICY_PERMISSIONS)
                        .setGrantedPermissions(PoliciesResourceType.messageResource("/"),
                                org.eclipse.ditto.services.models.policies.Permission.DEFAULT_POLICY_PERMISSIONS)
                        .build());
    }

    /**
     * Check if an enforcer key points to an access-control-list enforcer.
     *
     * @param enforcerKeyEntry cache key entry of an enforcer.
     * @return whether it is based on an access control list and requires special handling.
     */
    private static boolean isAclEnforcer(final Entry<EntityId> enforcerKeyEntry) {
        return enforcerKeyEntry.exists() &&
                Objects.equals(ThingCommand.RESOURCE_TYPE, enforcerKeyEntry.getValueOrThrow().getResourceType());
    }

    /**
     * A pair of {@code CreateThing} command with {@code Enforcer}.
     */
    private static final class CreateThingWithEnforcer {

        private final CreateThing createThing;
        private final Enforcer enforcer;

        private CreateThingWithEnforcer(final CreateThing createThing,
                final Enforcer enforcer) {
            this.createThing = createThing;
            this.enforcer = enforcer;
        }

    }

    /**
     * Provides {@link AbstractEnforcement} for commands of type {@link ThingCommand}.
     */
    public static final class Provider implements EnforcementProvider<ThingCommand> {

        private static final List<SubjectIssuer> DEFAULT_SUBJECT_ISSUERS_FOR_POLICY_MIGRATION =
                Collections.singletonList(SubjectIssuer.GOOGLE);
        private final ActorRef thingsShardRegion;
        private final ActorRef policiesShardRegion;
        private final Cache<EntityId, Entry<EntityId>> thingIdCache;
        private final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache;
        private final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache;
        private final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer;
        private final List<SubjectIssuer> subjectIssuersForPolicyMigration;

        /**
         * Constructor.
         *
         * @param thingsShardRegion the ActorRef to the Things shard region.
         * @param policiesShardRegion the ActorRef to the Policies shard region.
         * @param thingIdCache the thing-id-cache.
         * @param policyEnforcerCache the policy-enforcer cache.
         * @param aclEnforcerCache the acl-enforcer cache.
         * @param preEnforcer pre-enforcer function to block undesirable messages to policies shard region.
         */
        public Provider(final ActorRef thingsShardRegion,
                final ActorRef policiesShardRegion, final Cache<EntityId, Entry<EntityId>> thingIdCache,
                final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache,
                final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache,
                @Nullable final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer) {
            this(thingsShardRegion, policiesShardRegion, thingIdCache, policyEnforcerCache, aclEnforcerCache,
                    preEnforcer, DEFAULT_SUBJECT_ISSUERS_FOR_POLICY_MIGRATION);
        }

        /**
         * Constructor.
         *
         * @param thingsShardRegion the ActorRef to the Things shard region.
         * @param policiesShardRegion the ActorRef to the Policies shard region.
         * @param thingIdCache the thing-id-cache.
         * @param policyEnforcerCache the policy-enforcer cache.
         * @param aclEnforcerCache the acl-enforcer cache.
         * @param subjectIssuersForPolicyMigration a list of {@code SubjectIssuer}s for which a {@link Subject} will be
         * created per ACL SID. E.g. when {@link SubjectIssuer#GOOGLE} is specified, for the ACL SID "123", a {@link
         * Subject} "google:123" will be created.
         * @param preEnforcer pre-enforcer function to block undesirable messages to policies shard region.
         */
        public Provider(final ActorRef thingsShardRegion,
                final ActorRef policiesShardRegion, final Cache<EntityId, Entry<EntityId>> thingIdCache,
                final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache,
                final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache,
                @Nullable final Function<WithDittoHeaders, CompletionStage<WithDittoHeaders>> preEnforcer,
                final List<SubjectIssuer> subjectIssuersForPolicyMigration) {

            this.thingsShardRegion = requireNonNull(thingsShardRegion);
            this.policiesShardRegion = requireNonNull(policiesShardRegion);
            this.thingIdCache = requireNonNull(thingIdCache);
            this.policyEnforcerCache = requireNonNull(policyEnforcerCache);
            this.aclEnforcerCache = requireNonNull(aclEnforcerCache);
            this.preEnforcer = Optional.ofNullable(preEnforcer).orElse(CompletableFuture::completedFuture);
            this.subjectIssuersForPolicyMigration = requireNonNull(subjectIssuersForPolicyMigration);
        }

        @Override
        public Class<ThingCommand> getCommandClass() {
            return ThingCommand.class;
        }

        @Override
        public boolean isApplicable(final ThingCommand command) {
            // live commands are not applicable for thing command enforcement
            // because they should never be forwarded to things shard region
            return !LiveSignalEnforcement.isLiveSignal(command);
        }

        @Override
        public AbstractEnforcement<ThingCommand> createEnforcement(final Contextual<ThingCommand> context) {
            return new ThingCommandEnforcement(context, thingsShardRegion, policiesShardRegion, thingIdCache,
                    policyEnforcerCache, aclEnforcerCache, preEnforcer, subjectIssuersForPolicyMigration);
        }

    }

}
