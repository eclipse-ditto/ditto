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
import static org.eclipse.ditto.services.models.policies.Permission.MIN_REQUIRED_POLICY_PERMISSIONS;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonFieldSelectorBuilder;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.model.namespaces.NamespaceBlockedException;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyException;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingConstants;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.concierge.enforcement.placeholders.references.PolicyIdReferencePlaceholderResolver;
import org.eclipse.ditto.services.concierge.enforcement.placeholders.references.ReferencePlaceholder;
import org.eclipse.ditto.services.models.concierge.ConciergeMessagingConstants;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.services.models.policies.PoliciesValidator;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.EntityIdWithResourceType;
import org.eclipse.ditto.services.utils.cache.InvalidateCacheEntry;
import org.eclipse.ditto.services.utils.cache.entry.Entry;
import org.eclipse.ditto.services.utils.cacheloaders.IdentityCache;
import org.eclipse.ditto.services.utils.cacheloaders.PolicyEnforcer;
import org.eclipse.ditto.services.utils.cluster.DistPubSubAccess;
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
import org.eclipse.ditto.signals.commands.things.modify.MergeThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;

import akka.actor.ActorRef;
import akka.pattern.AskTimeoutException;
import akka.pattern.Patterns;

/**
 * Authorize {@code ThingCommand}.
 */
public final class ThingCommandEnforcement
        extends AbstractEnforcementWithAsk<ThingCommand<?>, ThingQueryCommandResponse<?>> {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(ThingCommandEnforcement.class);

    /**
     * Label of default policy entry in default policy.
     */
    private static final String DEFAULT_POLICY_ENTRY_LABEL = "DEFAULT";

    /**
     * Json fields that are always shown regardless of authorization.
     */
    private static final JsonFieldSelector THING_QUERY_COMMAND_RESPONSE_ALLOWLIST =
            JsonFactory.newFieldSelector(Thing.JsonFields.ID);

    private final ActorRef thingsShardRegion;
    private final ActorRef policiesShardRegion;
    private final EnforcerRetriever<Enforcer> thingEnforcerRetriever;
    private final EnforcerRetriever<Enforcer> policyEnforcerRetriever;
    private final Cache<EntityIdWithResourceType, Entry<EntityIdWithResourceType>> thingIdCache;
    private final Cache<EntityIdWithResourceType, Entry<Enforcer>> policyEnforcerCache;
    private final PreEnforcer preEnforcer;
    private final PolicyIdReferencePlaceholderResolver policyIdReferencePlaceholderResolver;

    private ThingCommandEnforcement(final Contextual<ThingCommand<?>> data,
            final ActorRef thingsShardRegion,
            final ActorRef policiesShardRegion,
            final Cache<EntityIdWithResourceType, Entry<EntityIdWithResourceType>> thingIdCache,
            final Cache<EntityIdWithResourceType, Entry<Enforcer>> policyEnforcerCache,
            final PreEnforcer preEnforcer) {

        super(data, ThingQueryCommandResponse.class);
        this.thingsShardRegion = requireNonNull(thingsShardRegion);
        this.policiesShardRegion = requireNonNull(policiesShardRegion);

        this.thingIdCache = requireNonNull(thingIdCache);
        this.policyEnforcerCache = requireNonNull(policyEnforcerCache);
        this.preEnforcer = preEnforcer;
        thingEnforcerRetriever = PolicyEnforcerRetrieverFactory.create(thingIdCache, policyEnforcerCache);
        policyEnforcerRetriever = new EnforcerRetriever<Enforcer>(IdentityCache.INSTANCE, policyEnforcerCache);
        policyIdReferencePlaceholderResolver =
                PolicyIdReferencePlaceholderResolver.of(conciergeForwarder(), getAskTimeout());
    }

    @Override
    public CompletionStage<Contextual<WithDittoHeaders>> enforce() {
        return thingEnforcerRetriever.retrieve(entityId(), (enforcerKeyEntry, enforcerEntry) -> {
            try {
                return doEnforce(enforcerKeyEntry, enforcerEntry);
            } catch (final RuntimeException e) {
                return CompletableFuture.failedStage(e);
            }
        });
    }

    private CompletionStage<Contextual<WithDittoHeaders>> doEnforce(
            final Entry<EntityIdWithResourceType> enforcerKeyEntry, final Entry<Enforcer> enforcerEntry) {

        if (!enforcerEntry.exists()) {
            return enforceThingCommandByNonexistentEnforcer(enforcerKeyEntry);
        } else {
            final EntityId policyId = enforcerKeyEntry.getValueOrThrow().getId();
            final Contextual<WithDittoHeaders> enforcementResult = enforceThingCommandByPolicyEnforcer(signal(),
                    PolicyId.of(policyId),
                    enforcerEntry.getValueOrThrow());
            return CompletableFuture.completedFuture(enforcementResult);
        }
    }

    /**
     * Authorize a thing command in the absence of an enforcer. This happens when the thing did not exist or when the
     * policy of the thing does not exist.
     *
     * @param enforcerKeyEntry cache entry in the entity ID cache for the enforcer cache key.
     * @return the completionStage of the contextual including message and receiver
     */
    private CompletionStage<Contextual<WithDittoHeaders>> enforceThingCommandByNonexistentEnforcer(
            final Entry<EntityIdWithResourceType> enforcerKeyEntry) {

        if (enforcerKeyEntry.exists()) {
            // Thing exists but its policy is deleted.
            final ThingId thingId = signal().getThingEntityId();
            final EntityId policyId = enforcerKeyEntry.getValueOrThrow().getId();
            final DittoRuntimeException error = errorForExistingThingWithDeletedPolicy(signal(), thingId, policyId);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.withCorrelationId(dittoHeaders())
                        .info("Enforcer was not existing for Thing <{}>, responding with: {}", thingId,
                                error.toString());
            }
            throw error;
        } else {
            // Without prior enforcer in cache, enforce CreateThing by self.
            // DO NOT use Contextual.askFuture to handle the ask-steps of a CreateThing command! Otherwise
            // the query- and modify-commands sent immediately after may be processed before the thing is created.
            return enforceCreateThingBySelf()
                    .thenCompose(pair ->
                            handleInitialCreateThing(pair.createThing, pair.enforcer)
                                    .thenApply(create -> create.withReceiver(thingsShardRegion))
                    )
                    .exceptionally(throwable -> {
                        final ThreadSafeDittoLogger l = LOGGER.withCorrelationId(dittoHeaders());

                        final DittoRuntimeException dittoRuntimeException =
                                DittoRuntimeException.asDittoRuntimeException(throwable, cause -> {
                                    l.warn("Error during thing by itself enforcement - {}: {}",
                                            cause.getClass().getSimpleName(), cause.getMessage());
                                    throw GatewayInternalErrorException.newBuilder()
                                            .cause(cause)
                                            .build();
                                });

                        l.debug("DittoRuntimeException during enforceThingCommandByNonexistentEnforcer - {}: {}",
                                dittoRuntimeException.getClass().getSimpleName(), dittoRuntimeException.getMessage());
                        throw dittoRuntimeException;
                    });
        }
    }

    private static boolean isResponseRequired(final WithDittoHeaders withDittoHeaders) {
        final DittoHeaders dittoHeaders = withDittoHeaders.getDittoHeaders();
        return dittoHeaders.isResponseRequired();
    }

    /**
     * Authorize a thing command by policy enforcer with view restriction for query commands.
     *
     * @param policyId Id of the thing's policy.
     * @param enforcer the policy enforcer.
     * @return the contextual including message and receiver
     */
    private Contextual<WithDittoHeaders> enforceThingCommandByPolicyEnforcer(
            final ThingCommand<?> thingCommand, final PolicyId policyId, final Enforcer enforcer) {

        final ThingCommand<?> commandWithReadSubjects = authorizeByPolicyOrThrow(enforcer, thingCommand);

        final Contextual<WithDittoHeaders> result;
        if (commandWithReadSubjects instanceof ThingQueryCommand) {
            final ThingQueryCommand<?> thingQueryCommand = (ThingQueryCommand<?>) commandWithReadSubjects;
            if (!isResponseRequired(thingQueryCommand)) {
                // drop query command with response-required=false
                result = withMessageToReceiver(null, ActorRef.noSender());
            } else if (thingQueryCommand instanceof RetrieveThing && shouldRetrievePolicyWithThing(thingQueryCommand)) {
                final RetrieveThing retrieveThing = (RetrieveThing) thingQueryCommand;
                result = withMessageToReceiverViaAskFuture(retrieveThing, sender(),
                        () -> retrieveThingAndPolicy(retrieveThing, policyId, enforcer));
            } else {
                result = withMessageToReceiverViaAskFuture(thingQueryCommand, sender(),
                        () -> askAndBuildJsonView(thingsShardRegion, thingQueryCommand, enforcer));
            }
        } else {
            result = forwardToThingsShardRegion(commandWithReadSubjects);
        }
        return result;
    }

    /**
     * Retrieve a thing and its policy and combine them into a response.
     *
     * @param retrieveThing the retrieve-thing command.
     * @param policyId ID of the thing's policy.
     * @param enforcer the enforcer for the command.
     * @return always {@code true}.
     */
    private CompletionStage<ThingQueryCommandResponse<?>> retrieveThingAndPolicy(final RetrieveThing retrieveThing,
            final PolicyId policyId, final Enforcer enforcer) {

        final DittoHeaders dittoHeadersWithoutPreconditionHeaders =
                DittoHeaders.newBuilder(retrieveThing.getDittoHeaders())
                        .removePreconditionHeaders()
                        .build();

        final Optional<RetrievePolicy> retrievePolicyOptional = PolicyCommandEnforcement.authorizePolicyCommand(
                RetrievePolicy.of(policyId, dittoHeadersWithoutPreconditionHeaders), PolicyEnforcer.of(enforcer));

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
            return askAndBuildJsonView(thingsShardRegion, retrieveThing, enforcer);
        }
    }

    /**
     * Retrieve a thing before retrieving its inlined policy. Report errors to sender.
     *
     * @param command the command.
     * @return future response from things-shard-region.
     */
    private CompletionStage<ThingQueryCommandResponse<?>> retrieveThingBeforePolicy(final RetrieveThing command) {
        return ask(thingsShardRegion, command, "retrieving thing before inlined policy");
    }

    private ThingUnavailableException reportThingUnavailable() {
        return ThingUnavailableException.newBuilder(signal().getThingEntityId()).dittoHeaders(dittoHeaders()).build();
    }

    /**
     * Retrieve inlined policy after retrieving a thing. Do not report errors.
     *
     * @param retrieveThing the original command.
     * @param retrievePolicy the command to retrieve the thing's policy.
     * @return future response from policies-shard-region.
     */
    private CompletionStage<Optional<RetrievePolicyResponse>> retrieveInlinedPolicyForThing(
            final RetrieveThing retrieveThing, final RetrievePolicy retrievePolicy) {

        return preEnforcer.apply(retrievePolicy)
                .thenCompose(msg -> Patterns.ask(policiesShardRegion, msg, getAskTimeout()))
                .handle((response, error) -> {
                    LOGGER.debug("Response of policiesShardRegion: <{}>", response);
                    if (response instanceof RetrievePolicyResponse) {
                        return Optional.of((RetrievePolicyResponse) response);
                    } else if (error != null) {
                        LOGGER.withCorrelationId(getCorrelationIdOrNull(error, retrieveThing))
                                .error("Retrieving inlined policy after RetrieveThing", error);
                    } else {
                        LOGGER.withCorrelationId(getCorrelationIdOrNull(response, retrieveThing))
                                .info("No authorized response when retrieving inlined policy <{}> for thing <{}>: {}",
                                        retrievePolicy.getEntityId(), retrieveThing.getThingEntityId(), response);
                    }
                    return Optional.empty();
                });
    }

    @Nullable
    private static CharSequence getCorrelationIdOrNull(final Object signal, final WithDittoHeaders fallBackSignal) {
        final WithDittoHeaders withDittoHeaders;
        if (isWithDittoHeaders(signal)) {
            withDittoHeaders = (WithDittoHeaders) signal;
        } else {
            withDittoHeaders = fallBackSignal;
        }
        final DittoHeaders dittoHeaders = withDittoHeaders.getDittoHeaders();
        return dittoHeaders.getCorrelationId().orElse(null);
    }

    private static boolean isWithDittoHeaders(final Object o) {
        return o instanceof WithDittoHeaders;
    }

    /**
     * Put thing and policy together as response to the sender.
     *
     * @param retrieveThing the original command.
     * @param thingResponse response from things-shard-region.
     * @param policyResponse response from policies-shard-region.
     * @param enforcer enforcer to build the JSON view.
     */
    private static RetrieveThingResponse reportAggregatedThingAndPolicyResponse(final RetrieveThing retrieveThing,
            final RetrieveThingResponse thingResponse,
            final RetrievePolicyResponse policyResponse,
            final Enforcer enforcer) {

        return reportAggregatedThingAndPolicy(retrieveThing, thingResponse, policyResponse.getPolicy(), enforcer);
    }

    private static RetrieveThingResponse reportAggregatedThingAndPolicy(final RetrieveThing retrieveThing,
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
     * @param askTimeout the timeout exception.
     */
    @Override
    protected DittoRuntimeException handleAskTimeoutForCommand(final ThingCommand<?> command,
            final AskTimeoutException askTimeout) {
        LOGGER.withCorrelationId(dittoHeaders()).error("Timeout before building JsonView", askTimeout);
        return ThingUnavailableException.newBuilder(command.getThingEntityId())
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    /**
     * Mixin-private: report thing query response with view on entity restricted by enforcer.
     *
     * @param commandResponse response of query.
     * @param enforcer the enforcer.
     */
    @Override
    protected ThingQueryCommandResponse<?> filterJsonView(final ThingQueryCommandResponse<?> commandResponse,
            final Enforcer enforcer) {
        try {
            return buildJsonViewForThingQueryCommandResponse(commandResponse, enforcer);
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
            final CreateThing command, final PolicyId policyId) {

        final EntityIdWithResourceType
                policyEntityId = EntityIdWithResourceType.of(PolicyCommand.RESOURCE_TYPE, policyId);
        return policyEnforcerRetriever.retrieve(policyEntityId, (policyIdEntry, policyEnforcerEntry) -> {
            if (policyEnforcerEntry.exists()) {
                final Contextual<WithDittoHeaders> enforcementResult =
                        enforceThingCommandByPolicyEnforcer(command, policyId, policyEnforcerEntry.getValueOrThrow());
                return CompletableFuture.completedFuture(enforcementResult);
            } else {
                throw errorForExistingThingWithDeletedPolicy(command, command.getThingEntityId(), policyId);
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
    private static <T extends ThingQueryCommandResponse<T>> T buildJsonViewForThingQueryCommandResponse(
            final ThingQueryCommandResponse<T> response, final Enforcer enforcer) {

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
    private Contextual<WithDittoHeaders> forwardToThingsShardRegion(final ThingCommand<?> command) {
        if (command instanceof ThingModifyCommand && ((ThingModifyCommand<?>) command).changesAuthorization()) {
            invalidateThingCaches(command.getThingEntityId());
        }
        return withMessageToReceiver(command, thingsShardRegion);
    }

    /**
     * Whenever a Command changed the authorization, the caches must be invalidated - otherwise a directly following
     * Command targeted for the same entity will probably fail as the enforcer was not yet updated.
     *
     * @param thingId the ID of the Thing to invalidate caches for.
     */
    private void invalidateThingCaches(final ThingId thingId) {
        final EntityIdWithResourceType entityId = EntityIdWithResourceType.of(ThingCommand.RESOURCE_TYPE, thingId);
        thingIdCache.invalidate(entityId);
        pubSubMediator().tell(DistPubSubAccess.sendToAll(
                ConciergeMessagingConstants.ENFORCER_ACTOR_PATH,
                InvalidateCacheEntry.of(entityId),
                true),
                self());
    }

    private void invalidatePolicyCache(final PolicyId policyId) {
        final EntityIdWithResourceType entityId = EntityIdWithResourceType.of(PolicyCommand.RESOURCE_TYPE, policyId);
        policyEnforcerCache.invalidate(entityId);
        pubSubMediator().tell(DistPubSubAccess.sendToAll(
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
            final ThingQueryCommandResponse<?> response, final Enforcer enforcer) {


        final ResourceKey resourceKey = ResourceKey.newInstance(ThingConstants.ENTITY_TYPE, response.getResourcePath());
        final AuthorizationContext authorizationContext = response.getDittoHeaders().getAuthorizationContext();

        return enforcer.buildJsonView(resourceKey, responseEntity, authorizationContext,
                THING_QUERY_COMMAND_RESPONSE_ALLOWLIST, Permissions.newInstance(Permission.READ));
    }

    /**
     * Create error for commands to an existing thing whose policy is deleted.
     *
     * @param thingCommand the triggering command.
     * @param thingId ID of the thing.
     * @param policyId ID of the deleted policy.
     * @return an appropriate error.
     */
    private static DittoRuntimeException errorForExistingThingWithDeletedPolicy(final ThingCommand<?> thingCommand,
            final ThingId thingId, final CharSequence policyId) {

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
    static DittoRuntimeException errorForThingCommand(final ThingCommand<?> thingCommand) {
        final CommandToExceptionRegistry<ThingCommand<?>, DittoRuntimeException> registry =
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
        final ThingCommand<?> thingCommand = transformModifyThingToCreateThing(signal());
        if (thingCommand instanceof CreateThing) {
            return replaceInitialPolicyWithCopiedPolicyIfPresent((CreateThing) thingCommand)
                    .thenApply(createThing -> {
                        final Optional<JsonObject> initialPolicyOptional = createThing.getInitialPolicy();
                        return initialPolicyOptional.map(
                                initialPolicy -> enforceCreateThingByOwnInlinedPolicyOrThrow(createThing,
                                        initialPolicy))
                                .orElseGet(() -> enforceCreateThingByAuthorizationContext(createThing));
                    });
        } else {
            // Other commands cannot be authorized by policy contained in self.
            final DittoRuntimeException error = ThingNotAccessibleException.newBuilder(thingCommand.getThingEntityId())
                    .dittoHeaders(thingCommand.getDittoHeaders())
                    .build();
            LOGGER.withCorrelationId(dittoHeaders())
                    .info("Enforcer was not existing for Thing <{}> and no auth info was inlined, responding with: {} - {}",
                            thingCommand.getThingEntityId(), error.getClass().getSimpleName(), error.getMessage());
            throw error;
        }
    }

    private CompletionStage<CreateThing> replaceInitialPolicyWithCopiedPolicyIfPresent(final CreateThing createThing) {
        return getInitialPolicyOrCopiedPolicy(createThing)
                .thenApply(initialPolicyOrCopiedPolicy ->
                        CreateThing.of(createThing.getThing(), initialPolicyOrCopiedPolicy,
                                createThing.getDittoHeaders()));
    }

    private CompletionStage<JsonObject> getInitialPolicyOrCopiedPolicy(final CreateThing createThing) {
        final ThreadSafeDittoLogger l = LOGGER.withCorrelationId(createThing);
        return createThing.getPolicyIdOrPlaceholder()
                .flatMap(ReferencePlaceholder::fromCharSequence)
                .map(referencePlaceholder -> {
                    l.debug("CreateThing command contains a reference placeholder for the policy it wants to copy: {}",
                            referencePlaceholder);
                    final DittoHeaders dittoHeadersWithoutPreconditionHeaders = dittoHeaders().toBuilder()
                            .removePreconditionHeaders()
                            .responseRequired(true)
                            .build();
                    return policyIdReferencePlaceholderResolver.resolve(referencePlaceholder,
                            dittoHeadersWithoutPreconditionHeaders);
                })
                .orElseGet(() -> CompletableFuture.completedFuture(createThing.getPolicyIdOrPlaceholder().orElse(null)))
                .thenCompose(policyId -> {
                    if (policyId != null) {
                        l.debug("CreateThing command wants to use a copy of Policy <{}>", policyId);
                        return retrievePolicyWithEnforcement(PolicyId.of(policyId))
                                .thenApply(policy -> policy.toJson(JsonSchemaVersion.V_2).remove("policyId"));
                    } else {
                        l.debug("CreateThing command did not contain a policy that should be copied.");
                        return CompletableFuture.completedFuture(createThing.getInitialPolicy().orElse(null));
                    }
                });
    }

    private CompletionStage<Policy> retrievePolicyWithEnforcement(final PolicyId policyId) {
        final DittoHeaders adjustedHeaders = dittoHeaders().toBuilder()
                .removePreconditionHeaders()
                .responseRequired(true)
                .build();

        return Patterns.ask(conciergeForwarder(), RetrievePolicy.of(policyId, adjustedHeaders), getAskTimeout())
                .thenApply(response -> {
                    if (response instanceof RetrievePolicyResponse) {
                        return ((RetrievePolicyResponse) response).getPolicy();
                    } else if (response instanceof PolicyErrorResponse) {
                        throw ((PolicyErrorResponse) response).getDittoRuntimeException();
                    } else if (response instanceof DittoRuntimeException) {
                        throw (DittoRuntimeException) response;
                    } else {
                        LOGGER.withCorrelationId(adjustedHeaders)
                                .error("Got an unexpected response while retrieving a Policy that should be copied" +
                                        " during Thing creation: {}", response);
                        throw GatewayInternalErrorException.newBuilder().build();
                    }
                });

    }

    private static CreateThingWithEnforcer enforceCreateThingByAuthorizationContext(final CreateThing createThing) {

        // Command without authorization information is authorized by default.
        final DittoHeaders dittoHeaders = createThing.getDittoHeaders();
        final AuthorizationContext authorizationContext = dittoHeaders.getAuthorizationContext();
        final Set<AuthorizationSubject> authorizedSubjects = authorizationContext.getFirstAuthorizationSubject()
                .map(Collections::singleton)
                .orElse(Collections.emptySet());
        final Enforcer enforcer = new AuthorizedSubjectsEnforcer(
                AuthorizationContext.newInstance(authorizationContext.getType(), authorizedSubjects));
        final CreateThing command = AbstractEnforcement.addEffectedReadSubjectsToThingSignal(createThing, enforcer);
        return new CreateThingWithEnforcer(command, enforcer);
    }

    private CreateThingWithEnforcer enforceCreateThingByOwnInlinedPolicyOrThrow(final CreateThing createThing,
            final JsonObject inlinedPolicy) {

        final Policy initialPolicy = getInitialPolicy(createThing, inlinedPolicy);
        final PoliciesValidator policiesValidator = PoliciesValidator.newInstance(initialPolicy);
        if (policiesValidator.isValid()) {
            final Enforcer initialEnforcer = PolicyEnforcers.defaultEvaluator(initialPolicy);
            return attachEnforcerOrThrow(createThing, initialEnforcer,
                    ThingCommandEnforcement::authorizeByPolicyOrThrow);
        } else {
            throw PolicyInvalidException.newBuilder(MIN_REQUIRED_POLICY_PERMISSIONS, createThing.getThingEntityId())
                    .dittoHeaders(createThing.getDittoHeaders())
                    .build();
        }
    }

    @SuppressWarnings("java:S1193")
    private Policy getInitialPolicy(final CreateThing createThing, final JsonObject inlinedPolicy) {
        try {
            // Java doesn't permit conversion of this early return into assignment to final variable.
            return PoliciesModelFactory.newPolicy(inlinedPolicy);
        } catch (final JsonRuntimeException | DittoJsonException e) {
            final ThingId thingId = createThing.getThingEntityId();
            throw PolicyInvalidException.newBuilderForCause(e, thingId)
                    .dittoHeaders(createThing.getDittoHeaders())
                    .build();
        } catch (final DittoRuntimeException e) {
            final DittoHeaders dittoHeaders = createThing.getDittoHeaders();
            // PolicyException is an interface!
            if (e instanceof PolicyException) {
                // user error; no need to log stack trace.
                throw e.setDittoHeaders(dittoHeaders);
            } else {
                throw reportError("Error during creation of inline policy from JSON", e);
            }
        }
    }

    private static CreateThingWithEnforcer attachEnforcerOrThrow(final CreateThing command, final Enforcer enforcer,
            final BiFunction<Enforcer, ThingCommand<CreateThing>, CreateThing> authorization) {

        final CreateThing authorizedCommand = authorization.apply(enforcer, command);
        return new CreateThingWithEnforcer(authorizedCommand, enforcer);
    }

    /**
     * Transform a {@code ModifyThing} command sent to nonexistent thing to {@code CreateThing} command if it is sent to
     * a nonexistent thing.
     *
     * @param receivedCommand the command to transform.
     * @return {@code CreateThing} command containing the same information if the argument is a {@code ModifyThing}
     * command. Otherwise return the command itself.
     */
    private static ThingCommand<?> transformModifyThingToCreateThing(final ThingCommand<?> receivedCommand) {
        if (receivedCommand instanceof ModifyThing) {
            final ModifyThing modifyThing = (ModifyThing) receivedCommand;
            final JsonObject initialPolicy = modifyThing.getInitialPolicy().orElse(null);
            final String policyIdOrPlaceholder = modifyThing.getPolicyIdOrPlaceholder().orElse(null);
            final Thing newThing = modifyThing.getThing().toBuilder()
                    .setId(modifyThing.getThingEntityId())
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
    static <T extends ThingCommand<T>> T authorizeByPolicyOrThrow(final Enforcer policyEnforcer,
            final ThingCommand<T> command) {

        final ResourceKey thingResourceKey = PoliciesResourceType.thingResource(command.getResourcePath());
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        final AuthorizationContext authorizationContext = dittoHeaders.getAuthorizationContext();

        final boolean authorized;
        if (command instanceof MergeThing) {
            authorized = enforceMergeThingCommand(policyEnforcer, (MergeThing) command, thingResourceKey,
                    authorizationContext);
        } else if (command instanceof ThingModifyCommand) {
            authorized = policyEnforcer.hasUnrestrictedPermissions(thingResourceKey, authorizationContext,
                    Permission.WRITE);
        } else {
            authorized = policyEnforcer.hasPartialPermissions(thingResourceKey, authorizationContext, Permission.READ);
        }

        if (authorized) {
            return AbstractEnforcement.addEffectedReadSubjectsToThingSignal(command, policyEnforcer);
        } else {
            throw errorForThingCommand(command);
        }
    }

    private static boolean enforceMergeThingCommand(final Enforcer policyEnforcer,
            final MergeThing command, final ResourceKey thingResourceKey,
            final AuthorizationContext authorizationContext) {
        if (policyEnforcer.hasUnrestrictedPermissions(thingResourceKey, authorizationContext, Permission.WRITE)) {
            // unrestricted permissions at thingResourceKey level
            return true;
        } else if (policyEnforcer.hasPartialPermissions(thingResourceKey, authorizationContext, Permission.WRITE)) {
            // in case of partial permissions at thingResourceKey level check all leaves of merge patch for
            // unrestricted permissions
            final Set<ResourceKey> resourceKeys = calculateLeaves(command.getPath(), command.getValue());
            return policyEnforcer.hasUnrestrictedPermissions(resourceKeys, authorizationContext, Permission.WRITE);
        } else {
            // not even partial permission
            return false;
        }
    }

    private static Set<ResourceKey> calculateLeaves(final JsonPointer path, final JsonValue value) {
        if (value.isObject()) {
            return value.asObject().stream()
                    .map(f -> calculateLeaves(path.append(f.getKey().asPointer()), f.getValue()))
                    .reduce(new HashSet<>(), ThingCommandEnforcement::addAll, ThingCommandEnforcement::addAll);
        } else {
            return Set.of(PoliciesResourceType.thingResource(path));
        }
    }

    private static Set<ResourceKey> addAll(final Set<ResourceKey> result, final Set<ResourceKey> toBeAdded) {
        result.addAll(toBeAdded);
        return result;
    }

    /**
     * Check if inlined policy should be retrieved together with the thing.
     *
     * @param command the thing query command.
     * @return whether it is necessary to retrieve the thing's policy.
     */
    private static boolean shouldRetrievePolicyWithThing(final ThingCommand<?> command) {
        final RetrieveThing retrieveThing = (RetrieveThing) command;
        return retrieveThing.getSelectedFields()
                .filter(selector -> selector.getPointers()
                        .stream()
                        .anyMatch(jsonPointer -> jsonPointer.getRoot()
                                .filter(jsonKey -> Policy.INLINED_FIELD_NAME.equals(jsonKey.toString()))
                                .isPresent()))
                .isPresent();
    }

    private CompletionStage<Contextual<WithDittoHeaders>> handleInitialCreateThing(
            final CreateThing createThing, final Enforcer enforcer) {

        final CompletionStage<Contextual<WithDittoHeaders>> result;
        if (shouldCreatePolicyForCreateThing(createThing)) {
            checkForErrorsInCreateThingWithPolicy(createThing);
            result = createThingWithInitialPolicy(createThing, enforcer).thenApply(this::forwardToThingsShardRegion);
        } else if (createThing.getThing().getPolicyEntityId().isPresent()) {
            final PolicyId policyId = createThing.getThing()
                    .getPolicyEntityId()
                    .orElseThrow(IllegalStateException::new);
            checkForErrorsInCreateThingWithPolicy(createThing);
            result = enforceCreateThingForNonexistentThingWithPolicyId(createThing, policyId);
        } else {
            // nothing to do with policy, simply forward the command
            result = CompletableFuture.completedFuture(forwardToThingsShardRegion(createThing));
        }
        return result;
    }

    private static boolean shouldCreatePolicyForCreateThing(final CreateThing createThing) {
        final JsonSchemaVersion commandVersion =
                createThing.getDittoHeaders().getSchemaVersion().orElse(JsonSchemaVersion.LATEST);
        return createThing.getInitialPolicy().isPresent() || createThing.getThing().getPolicyEntityId().isEmpty();
    }

    private static void checkForErrorsInCreateThingWithPolicy(final CreateThing command) {
        validatePolicyIdForCreateThing(command);
    }

    private static void validatePolicyIdForCreateThing(final CreateThing createThing) {
        final Thing thing = createThing.getThing();
        final Optional<String> policyIdOpt = thing.getPolicyEntityId().map(String::valueOf);
        final Optional<String> policyIdInPolicyOpt = createThing.getInitialPolicy()
                .flatMap(jsonObject -> jsonObject.getValue(Thing.JsonFields.POLICY_ID));

        final boolean isValid;
        if (policyIdOpt.isPresent()) {
            isValid = policyIdInPolicyOpt.isEmpty() || policyIdInPolicyOpt.equals(policyIdOpt);
        } else {
            isValid = true;
        }

        if (!isValid) {
            throw PolicyIdNotAllowedException.newBuilder(createThing.getThingEntityId())
                    .dittoHeaders(createThing.getDittoHeaders())
                    .build();
        }
    }

    private CompletionStage<CreateThing> createThingWithInitialPolicy(final CreateThing createThing,
            final Enforcer enforcer) {

        try {
            final Optional<Policy> policy =
                    getInlinedOrDefaultPolicyForCreateThing(createThing);

            if (policy.isPresent()) {

                final DittoHeaders dittoHeadersForCreatePolicy = DittoHeaders.newBuilder(createThing.getDittoHeaders())
                        .removePreconditionHeaders()
                        .responseRequired(true)
                        .build();

                final CreatePolicy createPolicy = CreatePolicy.of(policy.get(), dittoHeadersForCreatePolicy);
                final Optional<CreatePolicy> authorizedCreatePolicy =
                        PolicyCommandEnforcement.authorizePolicyCommand(createPolicy, PolicyEnforcer.of(enforcer));

                // CreatePolicy is rejected; abort CreateThing.
                return authorizedCreatePolicy
                        .map(cmd -> createPolicyAndThing(cmd, createThing))
                        .orElseThrow(() -> errorForThingCommand(createThing));
            } else {
                // cannot create policy.
                final ThingId thingId = createThing.getThingEntityId();
                final String message = String.format("The Thing with ID '%s' could not be created with implicit " +
                        "Policy because no authorization subject is present.", thingId);
                throw ThingNotCreatableException.newBuilderForPolicyMissing(thingId, PolicyId.of(thingId))
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
                createThingWithoutPolicyId.getThing().setPolicyId(createPolicy.getEntityId()),
                null,
                createThingWithoutPolicyId.getDittoHeaders());

        invalidatePolicyCache(createPolicy.getEntityId());

        return preEnforcer.apply(createPolicy)
                .thenCompose(msg -> Patterns.ask(policiesShardRegion, msg, getAskTimeout()))
                .thenApply(policyResponse -> {
                    handlePolicyResponseForCreateThing(createPolicy, createThing, policyResponse);

                    invalidateThingCaches(createThing.getThingEntityId());

                    return createThing;
                });
    }

    private void handlePolicyResponseForCreateThing(final CreatePolicy createPolicy, final CreateThing createThing,
            final Object policyResponse) {

        if (!(policyResponse instanceof CreatePolicyResponse)) {
            if (shouldReportInitialPolicyCreationFailure(policyResponse)) {

                throw reportInitialPolicyCreationFailure(createPolicy.getEntityId(), createThing);
            } else if (isAskTimeoutException(policyResponse, null)) {

                throw PolicyUnavailableException.newBuilder(createPolicy.getEntityId())
                        .dittoHeaders(createThing.getDittoHeaders())
                        .build();
            } else {

                final String hint =
                        String.format("creating initial policy during creation of Thing <%s>",
                                createThing.getThingEntityId());
                throw reportErrorOrResponse(hint, policyResponse, null);
            }
        }
    }

    private static boolean shouldReportInitialPolicyCreationFailure(final Object policyResponse) {
        return policyResponse instanceof PolicyConflictException ||
                policyResponse instanceof PolicyNotAccessibleException ||
                policyResponse instanceof NamespaceBlockedException;
    }

    private static ThingNotCreatableException reportInitialPolicyCreationFailure(final PolicyId policyId,
            final CreateThing command) {

        LOGGER.withCorrelationId(command)
                .info("Failed to create Policy with ID <{}> because it already exists." +
                        " The CreateThing command which would have created a Policy for the Thing with ID <{}>" +
                        " is therefore not handled.", policyId, command.getThingEntityId());
        return ThingNotCreatableException.newBuilderForPolicyExisting(command.getThingEntityId(), policyId)
                .dittoHeaders(command.getDittoHeaders())
                .build();
    }

    private static Optional<Policy> getInlinedOrDefaultPolicyForCreateThing(final CreateThing createThing) {
        final Optional<JsonObject> initialPolicy = createThing.getInitialPolicy();
        if (initialPolicy.isPresent()) {
            final JsonObject policyJson = initialPolicy.get();
            final JsonObjectBuilder policyJsonBuilder = policyJson.toBuilder();
            final Thing thing = createThing.getThing();
            if (thing.getPolicyEntityId().isPresent() || !policyJson.contains(Policy.JsonFields.ID.getPointer())) {
                final String policyId = thing.getPolicyEntityId()
                        .map(String::valueOf)
                        .orElse(createThing.getThingEntityId().toString());
                policyJsonBuilder.set(Policy.JsonFields.ID, policyId);
            }
            return Optional.of(PoliciesModelFactory.newPolicy(policyJsonBuilder.build()));
        } else {
            return getDefaultPolicy(createThing.getDittoHeaders().getAuthorizationContext(),
                    createThing.getThingEntityId());
        }
    }

    private static Optional<Policy> getDefaultPolicy(final AuthorizationContext authorizationContext,
            final ThingId thingId) {

        final Optional<Subject> subjectOptional = authorizationContext.getFirstAuthorizationSubject()
                .map(AuthorizationSubject::getId)
                .map(SubjectId::newInstance)
                .map(Subject::newInstance);

        return subjectOptional.map(subject ->
                Policy.newBuilder(PolicyId.of(thingId))
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
     * A pair of {@code CreateThing} command with {@code Enforcer}.
     */
    private static final class CreateThingWithEnforcer {

        private final CreateThing createThing;
        private final Enforcer enforcer;

        private CreateThingWithEnforcer(final CreateThing createThing, final Enforcer enforcer) {
            this.createThing = createThing;
            this.enforcer = enforcer;
        }

    }

    /**
     * Provides {@link AbstractEnforcement} for commands of type {@link ThingCommand}.
     */
    public static final class Provider implements EnforcementProvider<ThingCommand<?>> {

        private static final List<SubjectIssuer> DEFAULT_SUBJECT_ISSUERS_FOR_POLICY_MIGRATION =
                Collections.singletonList(SubjectIssuer.GOOGLE);
        private final ActorRef thingsShardRegion;
        private final ActorRef policiesShardRegion;
        private final Cache<EntityIdWithResourceType, Entry<EntityIdWithResourceType>> thingIdCache;
        private final Cache<EntityIdWithResourceType, Entry<Enforcer>> policyEnforcerCache;
        private final PreEnforcer preEnforcer;

        /**
         * Constructor.
         *
         * @param thingsShardRegion the ActorRef to the Things shard region.
         * @param policiesShardRegion the ActorRef to the Policies shard region.
         * @param thingIdCache the thing-id-cache.
         * @param policyEnforcerCache the policy-enforcer cache.
         * @param preEnforcer pre-enforcer function to block undesirable messages to policies shard region.
         */
        public Provider(final ActorRef thingsShardRegion,
                final ActorRef policiesShardRegion,
                final Cache<EntityIdWithResourceType, Entry<EntityIdWithResourceType>> thingIdCache,
                final Cache<EntityIdWithResourceType, Entry<Enforcer>> policyEnforcerCache,
                @Nullable final PreEnforcer preEnforcer) {

            this.thingsShardRegion = requireNonNull(thingsShardRegion);
            this.policiesShardRegion = requireNonNull(policiesShardRegion);
            this.thingIdCache = requireNonNull(thingIdCache);
            this.policyEnforcerCache = requireNonNull(policyEnforcerCache);
            this.preEnforcer = Optional.ofNullable(preEnforcer).orElse(CompletableFuture::completedFuture);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Class<ThingCommand<?>> getCommandClass() {
            return (Class) ThingCommand.class;
        }

        @Override
        public boolean isApplicable(final ThingCommand<?> command) {
            // live commands are not applicable for thing command enforcement
            // because they should never be forwarded to things shard region
            return !LiveSignalEnforcement.isLiveSignal(command);
        }

        @Override
        public boolean changesAuthorization(final ThingCommand<?> signal) {
            return signal instanceof ThingModifyCommand && ((ThingModifyCommand<?>) signal).changesAuthorization();
        }

        @Override
        public AbstractEnforcement<ThingCommand<?>> createEnforcement(final Contextual<ThingCommand<?>> context) {
            return new ThingCommandEnforcement(context, thingsShardRegion, policiesShardRegion, thingIdCache,
                    policyEnforcerCache, preEnforcer);
        }

    }

}
