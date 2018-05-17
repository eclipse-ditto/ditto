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
package org.eclipse.ditto.services.concierge.enforcement;

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.model.things.Permission.ADMINISTRATE;
import static org.eclipse.ditto.services.models.policies.Permission.MIN_REQUIRED_POLICY_PERMISSIONS;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonFieldSelectorBuilder;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.enforcers.AclEnforcer;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclInvalidException;
import org.eclipse.ditto.model.things.AclNotAllowedException;
import org.eclipse.ditto.model.things.AclValidator;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.concierge.cache.IdentityCache;
import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.models.concierge.cache.Entry;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.services.models.policies.PoliciesAclMigrations;
import org.eclipse.ditto.services.models.policies.PoliciesValidator;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.signals.commands.base.CommandToExceptionRegistry;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyConflictException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyUnavailableException;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicy;
import org.eclipse.ditto.signals.commands.policies.modify.CreatePolicyResponse;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicy;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyIdNotAllowedException;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyInvalidException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingCommandToAccessExceptionRegistry;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingCommandToModifyExceptionRegistry;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotCreatableException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotModifiableException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingUnavailableException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;

import akka.actor.ActorRef;
import akka.event.DiagnosticLoggingAdapter;
import akka.pattern.AskTimeoutException;
import akka.pattern.PatternsCS;

/**
 * Authorize {@code ThingCommand}.
 */
public final class ThingCommandEnforcement extends AbstractEnforcement<ThingCommand> {

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
    private final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache;

    private ThingCommandEnforcement(final Context data, final ActorRef thingsShardRegion,
            final ActorRef policiesShardRegion, final Cache<EntityId, Entry<EntityId>> thingIdCache,
            final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache,
            final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache,
            final List<SubjectIssuer> subjectIssuersForPolicyMigration) {

        super(data);
        this.thingsShardRegion = requireNonNull(thingsShardRegion);
        this.policiesShardRegion = requireNonNull(policiesShardRegion);
        this.subjectIssuersForPolicyMigration = requireNonNull(subjectIssuersForPolicyMigration);

        this.thingIdCache = requireNonNull(thingIdCache);
        this.policyEnforcerCache = requireNonNull(policyEnforcerCache);
        this.aclEnforcerCache = requireNonNull(aclEnforcerCache);
        thingEnforcerRetriever =
                PolicyOrAclEnforcerRetrieverFactory.create(thingIdCache, policyEnforcerCache, aclEnforcerCache);
        policyEnforcerRetriever = new EnforcerRetriever(IdentityCache.INSTANCE, policyEnforcerCache);
    }

    /**
     * Authorize a thing command. Either the command is forwarded to things-shard-region for execution or
     * the sender is told of an error.
     *
     * @param signal the command to authorize.
     * @param sender of the command.
     * @param log the logger to use for logging.
     */
    @Override
    public void enforce(final ThingCommand signal, final ActorRef sender, final DiagnosticLoggingAdapter log) {
        thingEnforcerRetriever.retrieve(entityId(), (enforcerKeyEntry, enforcerEntry) -> {
            if (!enforcerEntry.exists()) {
                enforceThingCommandByNonexistentEnforcer(enforcerKeyEntry, signal, sender);
            } else if (isAclEnforcer(enforcerKeyEntry)) {
                enforceThingCommandByAclEnforcer(signal, enforcerEntry.getValue(), sender);
            } else {
                final String policyId = enforcerKeyEntry.getValue().getId();
                enforceThingCommandByPolicyEnforcer(signal, policyId, enforcerEntry.getValue(), sender);
            }
        });
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
        private final List<SubjectIssuer> subjectIssuersForPolicyMigration;

        /**
         * Constructor.
         *
         * @param thingsShardRegion the ActorRef to the Things shard region.
         * @param policiesShardRegion the ActorRef to the Policies shard region.
         * @param thingIdCache the thing-id-cache.
         * @param policyEnforcerCache the policy-enforcer cache.
         * @param aclEnforcerCache the acl-enforcer cache.
         */
        public Provider(final ActorRef thingsShardRegion,
                final ActorRef policiesShardRegion, final Cache<EntityId, Entry<EntityId>> thingIdCache,
                final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache,
                final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache) {
            this(thingsShardRegion, policiesShardRegion, thingIdCache, policyEnforcerCache, aclEnforcerCache,
                    DEFAULT_SUBJECT_ISSUERS_FOR_POLICY_MIGRATION);
        }

        /**
         * Constructor.
         *
         * @param thingsShardRegion the ActorRef to the Things shard region.
         * @param policiesShardRegion the ActorRef to the Policies shard region.
         * @param thingIdCache the thing-id-cache.
         * @param policyEnforcerCache the policy-enforcer cache.
         * @param aclEnforcerCache the acl-enforcer cache.
         * @param subjectIssuersForPolicyMigration a list of {@link SubjectIssuer}s for which a {@link Subject} will
         * be created per ACL SID. E.g. when {@link SubjectIssuer#GOOGLE} is specified, for the ACL SID "123", a
         * {@link Subject} "google:123" will be created.
         */
        public Provider(final ActorRef thingsShardRegion,
                final ActorRef policiesShardRegion, final Cache<EntityId, Entry<EntityId>> thingIdCache,
                final Cache<EntityId, Entry<Enforcer>> policyEnforcerCache,
                final Cache<EntityId, Entry<Enforcer>> aclEnforcerCache,
                final List<SubjectIssuer> subjectIssuersForPolicyMigration) {
            this.thingsShardRegion = requireNonNull(thingsShardRegion);
            this.policiesShardRegion = requireNonNull(policiesShardRegion);
            this.thingIdCache = requireNonNull(thingIdCache);
            this.policyEnforcerCache = requireNonNull(policyEnforcerCache);
            this.aclEnforcerCache = requireNonNull(aclEnforcerCache);
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
        public AbstractEnforcement<ThingCommand> createEnforcement(final AbstractEnforcement.Context context) {
            return new ThingCommandEnforcement(context, thingsShardRegion, policiesShardRegion, thingIdCache,
                    policyEnforcerCache, aclEnforcerCache, subjectIssuersForPolicyMigration
            );
        }
    }

    /**
     * Authorize a thing command in the absence of an enforcer. This happens when the thing did
     * not exist or when the policy of the thing does not exist.
     *
     * @param enforcerKeyEntry cache entry in the entity ID cache for the enforcer cache key.
     * @param thingCommand the command to authorize.
     * @param sender sender of the command.
     */
    private void enforceThingCommandByNonexistentEnforcer(final Entry<EntityId> enforcerKeyEntry,
            final ThingCommand thingCommand, final ActorRef sender) {
        if (enforcerKeyEntry.exists()) {
            // Thing exists but its policy is deleted.
            final String thingId = thingCommand.getThingId();
            final String policyId = enforcerKeyEntry.getValue().getId();
            final DittoRuntimeException error = errorForExistingThingWithDeletedPolicy(thingCommand, thingId, policyId);
            log(thingCommand).info("Enforcer was not existing for Thing <{}>, responding with: {}", thingId, error);
            replyToSender(error, sender);
        } else {
            // Without prior enforcer in cache, enforce CreateThing by self.
            enforceCreateThingBySelf(thingCommand, sender).ifPresent(pair ->
                    handleInitialCreateThing(pair.createThing, pair.enforcer, sender));
        }
    }

    /**
     * Authorize a thing command by ACL enforcer with special handling for the field "/acl".
     *
     * @param thingCommand the thing command.
     * @param enforcer the ACL enforcer.
     * @param sender sender of the command.
     */
    private void enforceThingCommandByAclEnforcer(final ThingCommand<?> thingCommand, final Enforcer enforcer,
            final ActorRef sender) {
        final Optional<? extends ThingCommand> authorizedCommand = authorizeByAcl(enforcer, thingCommand);

        if (authorizedCommand.isPresent()) {
            final ThingCommand commandWithReadSubjects = authorizedCommand.get();
            if (commandWithReadSubjects instanceof RetrieveThing &&
                    shouldRetrievePolicyWithThing(commandWithReadSubjects)) {
                final RetrieveThing retrieveThing = (RetrieveThing) commandWithReadSubjects;
                retrieveThingAclAndMigrateToPolicy(retrieveThing, enforcer, sender);
            } else {
                forwardToThingsShardRegion(commandWithReadSubjects, sender);
            }
        } else {
            respondWithError(thingCommand, sender, self());
        }
    }

    private void retrieveThingAclAndMigrateToPolicy(final RetrieveThing retrieveThing,
            final Enforcer enforcer,
            final ActorRef sender) {
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
        PatternsCS.ask(thingsShardRegion, retrieveThingV1, getAskTimeout().toMillis())
                .handleAsync((response, error) -> {
                    if (response instanceof RetrieveThingResponse) {
                        final RetrieveThingResponse retrieveThingResponse = (RetrieveThingResponse) response;
                        final Optional<AccessControlList> aclOptional =
                                retrieveThingResponse.getThing().getAccessControlList();
                        if (aclOptional.isPresent()) {
                            final Policy policy =
                                    PoliciesAclMigrations.accessControlListToPolicyEntries(aclOptional.get(),
                                            retrieveThing.getThingId(), subjectIssuersForPolicyMigration);
                            reportAggregatedThingAndPolicy(retrieveThing,
                                    retrieveThingResponse.setDittoHeaders(retrieveThing.getDittoHeaders()),
                                    policy, enforcer, sender);
                        } else {
                            replyToSender(retrieveThingResponse.setDittoHeaders(retrieveThing.getDittoHeaders()),
                                    sender);
                        }
                    } else if (response instanceof WithDittoHeaders) {
                        final WithDittoHeaders withDittoHeaders = (WithDittoHeaders) response;
                        replyToSender(withDittoHeaders.setDittoHeaders(retrieveThing.getDittoHeaders()), sender);
                    } else if (isAskTimeoutException(response, error)) {
                        reportThingUnavailable(retrieveThing.getThingId(), retrieveThing.getDittoHeaders(), sender);
                    } else {
                        reportUnexpectedErrorOrResponse("retrieving thing for ACL migration",
                                sender, response, error, retrieveThing.getDittoHeaders());
                    }
                    return null;
                });
    }

    /**
     * Authorize a thing command by policy enforcer with view restriction for query commands.
     *
     * @param thingCommand the thing command.
     * @param policyId Id of the thing's policy.
     * @param enforcer the policy enforcer.
     * @param sender sender of the command.
     */
    private void enforceThingCommandByPolicyEnforcer(final ThingCommand<?> thingCommand,
            final String policyId,
            final Enforcer enforcer,
            final ActorRef sender) {
        final boolean authorized = authorizeByPolicy(enforcer, thingCommand)
                .map(commandWithReadSubjects -> {
                    if (commandWithReadSubjects instanceof ThingQueryCommand) {
                        final ThingQueryCommand thingQueryCommand = (ThingQueryCommand) commandWithReadSubjects;
                        if (thingQueryCommand instanceof RetrieveThing &&
                                shouldRetrievePolicyWithThing(thingQueryCommand)) {

                            final RetrieveThing retrieveThing = (RetrieveThing) thingQueryCommand;
                            return retrieveThingAndPolicy(retrieveThing, policyId, enforcer, sender);
                        } else {
                            return askThingsShardRegionAndBuildJsonView(thingQueryCommand, enforcer, sender);
                        }
                    } else {
                        return forwardToThingsShardRegion(commandWithReadSubjects, sender);
                    }
                })
                .isPresent();

        if (!authorized) {
            respondWithError(thingCommand, sender, self());
        }
    }

    /**
     * Responds to the passed {@code sender} with an error based on the type of the passed in {@code thingCommand}.
     *
     * @param thingCommand the ThingCommand to use for determining which error to send back
     * @param sender the sender to send back the error to
     * @param self the self reference
     */
    static void respondWithError(final ThingCommand thingCommand, final ActorRef sender, final ActorRef self) {
        sender.tell(errorForThingCommand(thingCommand), self);
    }

    /**
     * Retrieve for response of a query command and limit the response
     * according to a policy
     * enforcer.
     *
     * @param commandWithReadSubjects the command to ask.
     * @param enforcer enforcer to build JsonView with.
     * @param sender sender of the command.
     * @return always {@code true}.
     */
    private boolean askThingsShardRegionAndBuildJsonView(
            final ThingQueryCommand commandWithReadSubjects,
            final Enforcer enforcer,
            final ActorRef sender) {

        PatternsCS.ask(thingsShardRegion, commandWithReadSubjects, getAskTimeout().toMillis())
                .handleAsync((response, error) -> {
                    if (response instanceof ThingQueryCommandResponse) {
                        reportJsonViewForThingQuery(sender, (ThingQueryCommandResponse) response, enforcer);
                    } else if (response instanceof DittoRuntimeException) {
                        replyToSender(response, sender);
                    } else if (isAskTimeoutException(response, error)) {
                        final AskTimeoutException askTimeoutException = error instanceof AskTimeoutException
                                ? (AskTimeoutException) error
                                : (AskTimeoutException) response;
                        reportTimeoutForThingQuery(commandWithReadSubjects, sender, askTimeoutException);
                    } else if (error != null) {
                        reportUnexpectedError("before building JsonView", sender, error,
                                commandWithReadSubjects.getDittoHeaders());
                    } else {
                        reportUnknownResponse("before building JsonView", sender, response,
                                commandWithReadSubjects.getDittoHeaders());
                    }
                    return null;
                });
        return true;
    }

    /**
     * Retrieve a thing and its policy and combine them into a response.
     *
     * @param retrieveThing the retrieve-thing command.
     * @param policyId ID of the thing's policy.
     * @param enforcer the enforcer for the command.
     * @param sender sender of the command.
     * @return always {@code true}.
     */
    private boolean retrieveThingAndPolicy(
            final RetrieveThing retrieveThing,
            final String policyId,
            final Enforcer enforcer,
            final ActorRef sender) {

        final Optional<RetrievePolicy> retrievePolicyOptional = PolicyCommandEnforcement.authorizePolicyCommand(
                RetrievePolicy.of(policyId, retrieveThing.getDittoHeaders()), enforcer);

        if (retrievePolicyOptional.isPresent()) {
            retrieveThingBeforePolicy(retrieveThing, sender).thenAccept(thingResponse ->
                    thingResponse.ifPresent(retrieveThingResponse -> {
                        final RetrievePolicy retrievePolicy = retrievePolicyOptional.get();
                        retrieveInlinedPolicyForThing(retrieveThing, retrievePolicy).thenAccept(policyResponse -> {
                            if (policyResponse.isPresent()) {
                                final RetrievePolicyResponse filteredPolicyResponse =
                                        PolicyCommandEnforcement.buildJsonViewForPolicyQueryCommandResponse(
                                                policyResponse.get(), enforcer);
                                reportAggregatedThingAndPolicyResponse(retrieveThing, retrieveThingResponse,
                                        filteredPolicyResponse, enforcer, sender);
                            } else {
                                replyToSender(retrieveThingResponse, sender);
                            }
                        });
                    }));
            return true;
        } else {
            // sender is not authorized to view the policy, ignore the request to embed policy.
            return askThingsShardRegionAndBuildJsonView(retrieveThing, enforcer, sender);
        }
    }

    /**
     * Retrieve a thing before retrieving its inlined policy. Report errors to sender.
     *
     * @param retrieveThing the command.
     * @param sender whom to report errors to.
     * @return future response from things-shard-region.
     */
    private CompletionStage<Optional<RetrieveThingResponse>> retrieveThingBeforePolicy(
            final RetrieveThing retrieveThing,
            final ActorRef sender) {

        return PatternsCS.ask(thingsShardRegion, retrieveThing, getAskTimeout().toMillis())
                .handleAsync((response, error) -> {
                    if (response instanceof RetrieveThingResponse) {
                        return Optional.of((RetrieveThingResponse) response);
                    } else if (response instanceof ThingErrorResponse || response instanceof DittoRuntimeException) {
                        replyToSender(response, sender);
                    } else if (isAskTimeoutException(response, error)) {
                        reportThingUnavailable(retrieveThing.getThingId(), retrieveThing.getDittoHeaders(), sender);
                    } else {
                        reportUnexpectedErrorOrResponse("retrieving thing before inlined policy",
                                sender, response, error, retrieveThing.getDittoHeaders());
                    }
                    return Optional.empty();
                });
    }

    private void reportThingUnavailable(final String thingId, final DittoHeaders dittoHeaders, final ActorRef sender) {
        final ThingUnavailableException thingUnavailableException =
                ThingUnavailableException.newBuilder(thingId).dittoHeaders(dittoHeaders).build();
        replyToSender(thingUnavailableException, sender);
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

        return PatternsCS.ask(policiesShardRegion, retrievePolicy, getAskTimeout().toMillis())
                .handleAsync((response, error) -> {
                    if (response instanceof RetrievePolicyResponse) {
                        return Optional.of((RetrievePolicyResponse) response);
                    } else if (error != null) {
                        log(error).error(error, "retrieving inlined policy after RetrieveThing");
                    } else {
                        log(response).info("No authorized response when retrieving inlined policy <{}> for thing <{}>: {}",
                                retrievePolicy.getId(), retrieveThing.getThingId(), response);
                    }
                    return Optional.empty();
                });
    }

    /**
     * Put thing and policy together as response to the sender.
     *
     * @param retrieveThing the original command.
     * @param retrieveThingResponse response from things-shard-region.
     * @param retrievePolicyResponse response from policies-shard-region.
     * @param enforcer enforcer to bulid the Json view.
     * @param sender sender of the original command.
     */
    private void reportAggregatedThingAndPolicyResponse(
            final RetrieveThing retrieveThing,
            final RetrieveThingResponse retrieveThingResponse,
            final RetrievePolicyResponse retrievePolicyResponse,
            final Enforcer enforcer,
            final ActorRef sender) {

        reportAggregatedThingAndPolicy(retrieveThing, retrieveThingResponse, retrievePolicyResponse.getPolicy(),
                enforcer, sender);
    }

    private void reportAggregatedThingAndPolicy(
            final RetrieveThing retrieveThing,
            final RetrieveThingResponse retrieveThingResponse,
            final Policy policy,
            final Enforcer enforcer,
            final ActorRef sender) {

        final RetrieveThingResponse limitedView =
                buildJsonViewForThingQueryCommandResponse(retrieveThingResponse, enforcer);

        final JsonObject inlinedPolicy =
                policy.toInlinedJson(retrieveThing.getImplementedSchemaVersion(), FieldType.notHidden());

        final JsonObject thingWithInlinedPolicy = limitedView.getEntity().asObject().toBuilder()
                .setAll(inlinedPolicy)
                .build();

        replyToSender(limitedView.setEntity(thingWithInlinedPolicy), sender);
    }

    /**
     * Report timeout of {@code ThingQueryComand}.
     *
     * @param command the original command.
     * @param sender sender of the command.
     * @param askTimeoutException the timeout exception.
     */
    private void reportTimeoutForThingQuery(
            final ThingQueryCommand command,
            final ActorRef sender,
            final AskTimeoutException askTimeoutException) {
        log(command).error(askTimeoutException, "Timeout before building JsonView");
        replyToSender(ThingUnavailableException.newBuilder(command.getThingId())
                .dittoHeaders(command.getDittoHeaders())
                .build(), sender);
    }

    /**
     * Mixin-private: report thing query response with view on entity restricted by enforcer.
     *
     * @param sender sender of the command.
     * @param thingQueryCommandResponse response of query.
     * @param enforcer the enforcer.
     */
    private void reportJsonViewForThingQuery(final ActorRef sender,
            final ThingQueryCommandResponse<?> thingQueryCommandResponse,
            final Enforcer enforcer) {

        try {
            final ThingQueryCommandResponse responseWithLimitedJsonView =
                    buildJsonViewForThingQueryCommandResponse(thingQueryCommandResponse, enforcer);
            replyToSender(responseWithLimitedJsonView, sender);
        } catch (final RuntimeException e) {
            reportError("Error after building JsonView", sender, e, thingQueryCommandResponse.getDittoHeaders());
        }
    }

    /**
     * Query caches again to authorize a {@code CreateThing} command with explicit policy ID and no inline policy.
     *
     * @param createThing the command.
     * @param policyId the policy ID.
     * @param sender sender of the command.
     */
    private void enforceCreateThingForNonexistentThingWithPolicyId(final CreateThing createThing,
            final String policyId,
            final ActorRef sender) {
        final EntityId policyEntityId = EntityId.of(PolicyCommand.RESOURCE_TYPE, policyId);
        policyEnforcerRetriever.retrieve(policyEntityId, (policyIdEntry, policyEnforcerEntry) -> {
            if (policyEnforcerEntry.exists()) {
                enforceThingCommandByPolicyEnforcer(createThing, policyId, policyEnforcerEntry.getValue(), sender);
            } else {
                final DittoRuntimeException error =
                        errorForExistingThingWithDeletedPolicy(createThing, createThing.getThingId(), policyId);
                replyToSender(error, sender);
            }
        });
    }

    /**
     * Limit view on entity of {@code ThingQueryCommandResponse} by enforcer.
     *
     * @param response the response.
     * @param enforcer the enforcer.
     * @return response with view on entity restricted by enforcer..
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
     * Do not call {@code Actor.forward(Object, ActorContext)} because it is not thread-safe.
     *
     * @param command command to forward.
     * @param sender sender of the command.
     * @return true.
     */
    private boolean forwardToThingsShardRegion(final ThingCommand command, final ActorRef sender) {
        thingsShardRegion.tell(command, sender);
        if (command instanceof ThingModifyCommand &&
                (affectsAcl((ThingModifyCommand) command) || affectsPolicyId((ThingModifyCommand) command))) {
            invalidateCaches(command.getThingId());
        }
        return true;
    }

    /**
     * Whenever a Command changed the authorization, the caches must be invalidated - otherwise a directly following
     * Command targeted for the same entity will probably fail as the enforcer was not yet updated.
     *
     * @param thingId the ID of the Thing to invalidate caches for.
     */
    private void invalidateCaches(final String thingId) {
        final EntityId entityId = EntityId.of(ThingCommand.RESOURCE_TYPE, thingId);
        thingIdCache.invalidate(entityId);
        aclEnforcerCache.invalidate(entityId);
        policyEnforcerCache.invalidate(entityId);
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
                "The Thing with ID ''%s'' could not be accessed as its Policy with ID ''%s'' is not or no longer existing.",
                thingId, policyId);
        final String description = String.format(
                "Recreate/create the Policy with ID ''%s'' in order to get access to the Thing again.",
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
    private static DittoRuntimeException errorForThingCommand(final ThingCommand thingCommand) {
        final CommandToExceptionRegistry<ThingCommand, DittoRuntimeException> registry =
                thingCommand instanceof ThingModifyCommand
                        ? ThingCommandToModifyExceptionRegistry.getInstance()
                        : ThingCommandToAccessExceptionRegistry.getInstance();
        return registry.exceptionFrom(thingCommand);
    }

    /**
     * Check if an enforcer key points to an access-control-list enforcer.
     *
     * @param enforcerKeyEntry cache key entry of an enforcer.
     * @return whether it is based on an access control list and requires special handling.
     */
    private static boolean isAclEnforcer(final Entry<EntityId> enforcerKeyEntry) {
        return enforcerKeyEntry.exists() &&
                Objects.equals(ThingCommand.RESOURCE_TYPE, enforcerKeyEntry.getValue().getResourceType());
    }

    /**
     * Authorize a thing-command by authorization information contained in itself. Only {@code
     * CreateThing} commands are authorized in this manner in the absence of an existing enforcer. {@code
     * ModifyThing} commands are transformed to {@code CreateThing} commands before being processed.
     *
     * @param receivedThingCommand the command to authorize.
     * @return optionally the authorized command extended by  read subjects.
     */
    private Optional<CreateThingWithEnforcer> enforceCreateThingBySelf(
            final ThingCommand receivedThingCommand, final ActorRef sender) {

        final ThingCommand thingCommand = transformModifyThingToCreateThing(receivedThingCommand);
        final Optional<CreateThingWithEnforcer> result;
        if (thingCommand instanceof CreateThing) {
            final CreateThing createThing = (CreateThing) thingCommand;
            final Optional<JsonObject> initialPolicyOptional = createThing.getInitialPolicy();
            if (initialPolicyOptional.isPresent()) {
                result = enforceCreateThingByOwnInlinedPolicy(createThing, initialPolicyOptional.get(), sender);
            } else {
                final Optional<AccessControlList> aclOptional =
                        createThing.getThing().getAccessControlList().filter(acl -> !acl.isEmpty());
                if (aclOptional.isPresent()) {
                    result = enforceCreateThingByOwnAcl(createThing, aclOptional.get(), sender);
                } else {
                    result = enforceCreateThingByAuthorizationContext(createThing);
                }
            }
        } else {
            // Other commands cannot be authorized by ACL or policy contained in self.
            final DittoRuntimeException error =
                    ThingNotAccessibleException.newBuilder(thingCommand.getThingId())
                            .dittoHeaders(thingCommand.getDittoHeaders())
                            .build();
            log(thingCommand).info("Enforcer was not existing for Thing <{}> and no auth info was inlined, " +
                            "responding with: {}", thingCommand.getThingId(), error);
            replyToSender(error, sender);
            result = Optional.empty();
        }
        return result;
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
            final JsonObject inlinedPolicy, final ActorRef sender) {
        final Policy initialPolicy = PoliciesModelFactory.newPolicy(inlinedPolicy);
        if (PoliciesValidator.newInstance(initialPolicy).isValid()) {
            final Enforcer initialEnforcer = PolicyEnforcers.defaultEvaluator(initialPolicy);
            return attachEnforcerOrReplyWithError(createThing, initialEnforcer,
                    ThingCommandEnforcement::authorizeByPolicy, sender);
        } else {
            final DittoRuntimeException error =
                    PolicyInvalidException.newBuilder(MIN_REQUIRED_POLICY_PERMISSIONS, createThing.getThingId())
                            .dittoHeaders(createThing.getDittoHeaders())
                            .build();
            replyToSender(error, sender);
            return Optional.empty();
        }
    }

    private Optional<CreateThingWithEnforcer> enforceCreateThingByOwnAcl(final CreateThing createThing,
            final AccessControlList acl, final ActorRef sender) {
        if (AclValidator.newInstance(acl, Thing.MIN_REQUIRED_PERMISSIONS).isValid()) {
            final Enforcer initialEnforcer = AclEnforcer.of(acl);
            return attachEnforcerOrReplyWithError(createThing, initialEnforcer,
                    ThingCommandEnforcement::authorizeByAcl, sender);
        } else {
            final DittoRuntimeException error = AclInvalidException.newBuilder(createThing.getThingId())
                    .dittoHeaders(createThing.getDittoHeaders())
                    .build();
            replyToSender(error, sender);
            return Optional.empty();
        }

    }

    private Optional<CreateThingWithEnforcer> attachEnforcerOrReplyWithError(final CreateThing command,
            final Enforcer enforcer,
            final BiFunction<Enforcer, ThingCommand<CreateThing>, Optional<CreateThing>> authorization,
            final ActorRef sender) {

        final Optional<CreateThing> authorizedCommand = authorization.apply(enforcer, command);
        if (authorizedCommand.isPresent()) {
            return authorizedCommand.map(cmd -> new CreateThingWithEnforcer(cmd, enforcer));
        } else {
            respondWithError(command, sender, self());
            return Optional.empty();
        }
    }

    /**
     * Transform a {@code ModifyThing} command sent to nonexistent thing to {@code CreateThing}
     * command if it is sent to a nonexistent thing.
     *
     * @param receivedCommand the command to transform.
     * @return {@code CreateThing} command containing the same information if the argument is a {@code ModifyThing}
     * command. Otherwise return the command itself.
     */
    private static ThingCommand transformModifyThingToCreateThing(final ThingCommand receivedCommand) {
        if (receivedCommand instanceof ModifyThing) {
            final ModifyThing modifyThing = (ModifyThing) receivedCommand;
            final JsonObject initialPolicy = modifyThing.getInitialPolicy().orElse(null);
            return CreateThing.of(modifyThing.getThing(), initialPolicy, modifyThing.getDittoHeaders());
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
     * Compute ACL permissions relevant for a {@code ThingModifyCommand}. The field "/acl" is handled
     * specially with the "ADMINISTRATE" permission.
     *
     * @param command the command.
     * @return permissions needed to execute the command.
     */
    private static Permissions computeAclPermissions(final ThingModifyCommand command) {
        return affectsAcl(command)
                ? Permissions.newInstance(Permission.WRITE, ADMINISTRATE.name())
                : Permissions.newInstance(Permission.WRITE);
    }

    /**
     * Decide whether a command affects the ACL.
     *
     * @param command the command.
     * @return whether it affects the ACL.
     */
    private static boolean affectsAcl(final ThingModifyCommand command) {
        return command instanceof DeleteThing || resourcePathIntersectsAcl(command) || entityIntersectsAcl(command);
    }

    /**
     * Decide whether a command affects the Policy ID (e.g. changes it).
     *
     * @param command the command.
     * @return whether it affects the Policy ID.
     */
    private static boolean affectsPolicyId(final ThingModifyCommand command) {
        return command instanceof DeleteThing || resourcePathIntersectsPolicyId(command) || entityIntersectsPolicyId(command);
    }

    /**
     * Decide whether a command's resource path intersects with the ACL.
     *
     * @param command the command.
     * @return whether its resource path intersects with the ACL.
     */
    private static boolean resourcePathIntersectsAcl(final ThingModifyCommand command) {
        return command.getResourcePath().getRoot()
                .flatMap(root -> Thing.JsonFields.ACL.getPointer()
                        .getRoot()
                        .map(aclRoot -> Objects.equals(root, aclRoot)))
                .orElse(true);
    }

    /**
     * Decide whether a command's resource path intersects with the Policy ID.
     *
     * @param command the command.
     * @return whether its resource path intersects with the Policy ID.
     */
    private static boolean resourcePathIntersectsPolicyId(final ThingModifyCommand command) {
        return command.getResourcePath().getRoot()
                .flatMap(root -> Thing.JsonFields.POLICY_ID.getPointer()
                        .getRoot()
                        .map(aclRoot -> Objects.equals(root, aclRoot)))
                .orElse(true);
    }

    /**
     * Decide whether a command's entity intersects with the ACL.
     *
     * @param command the command.
     * @return whether its entity intersects with the ACL.
     */
    private static boolean entityIntersectsAcl(final ThingModifyCommand command) {
        return (command instanceof ModifyThing || command instanceof CreateThing) &&
                command.getEntity()
                        .filter(JsonValue::isObject)
                        .map(jsonValue -> jsonValue.asObject().contains(Thing.JsonFields.ACL.getPointer()))
                        .isPresent();
    }

    /**
     * Decide whether a command's entity intersects with the Policy ID (e.g. changes it).
     *
     * @param command the command.
     * @return whether its entity intersects with the Policy ID.
     */
    private static boolean entityIntersectsPolicyId(final ThingModifyCommand command) {
        return (command instanceof ModifyThing || command instanceof CreateThing) &&
                command.getEntity()
                        .filter(JsonValue::isObject)
                        .map(jsonValue -> jsonValue.asObject().contains(Thing.JsonFields.POLICY_ID.getPointer()))
                        .isPresent();
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

    private void handleInitialCreateThing(final CreateThing createThing, final Enforcer enforcer,
            final ActorRef sender) {
        if (shouldCreatePolicyForCreateThing(createThing)) {
            final Optional<DittoRuntimeException> errorOpt = checkForErrorsInCreateThingWithPolicy(createThing);
            if (errorOpt.isPresent()) {
                replyToSender(errorOpt.get(), sender);
            } else {
                createThingWithInitialPolicy(createThing, enforcer, sender);
            }
        } else if (createThing.getThing().getPolicyId().isPresent()) {
            final String policyId = createThing.getThing().getPolicyId().orElseThrow(IllegalStateException::new);
            final Optional<DittoRuntimeException> errorOpt = checkForErrorsInCreateThingWithPolicy(createThing);
            if (errorOpt.isPresent()) {
                replyToSender(errorOpt.get(), sender);
            } else {
                enforceCreateThingForNonexistentThingWithPolicyId(createThing, policyId, sender);
            }
        } else {
            // nothing to do with policy, simply forward the command
            forwardToThingsShardRegion(createThing, sender);
        }
    }

    private void createThingWithInitialPolicy(final CreateThing createThing,
            final Enforcer enforcer,
            final ActorRef sender) {

        try {
            final Optional<Policy> policy =
                    getInlinedOrDefaultPolicyForCreateThing(createThing);

            if (policy.isPresent()) {

                final CreatePolicy createPolicy = CreatePolicy.of(policy.get(), createThing.getDittoHeaders());
                final Optional<CreatePolicy> authorizedCreatePolicy =
                        PolicyCommandEnforcement.authorizePolicyCommand(createPolicy, enforcer);

                // CreatePolicy is rejected; abort CreateThing.
                final boolean created = authorizedCreatePolicy
                        .filter(cmd -> createPolicyAndThing(cmd, createThing, sender))
                        .isPresent();

                if (!created) {
                    final DittoRuntimeException error = errorForThingCommand(createThing);
                    replyToSender(error, sender);
                }
            } else {
                // cannot create policy.
                final String thingId = createThing.getThingId();
                final String message = String.format("The Thing with ID ''%s'' could not be created with implicit " +
                        "Policy because no authorization subject is present.", thingId);
                final ThingNotCreatableException error =
                        ThingNotCreatableException.newBuilderForPolicyMissing(thingId, thingId)
                                .message(message)
                                .description(() -> null)
                                .dittoHeaders(createThing.getDittoHeaders())
                                .build();
                replyToSender(error, sender);
            }
        } catch (final RuntimeException error) {
            reportError("error before creating thing with initial policy", sender, error,
                    createThing.getDittoHeaders());
        }
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

    private static Optional<DittoRuntimeException> checkForErrorsInCreateThingWithPolicy(
            final CreateThing createThing) {
        return checkAclAbsenceInCreateThing(createThing)
                .map(Optional::of)
                .orElseGet(() -> checkPolicyIdValidityForCreateThing(createThing));
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
        final Optional<String> thingIdOpt = thing.getId();
        final Optional<String> policyIdOpt = thing.getPolicyId();
        final Optional<String> policyIdInPolicyOpt = createThing.getInitialPolicy()
                .flatMap(jsonObject -> jsonObject.getValue(Thing.JsonFields.POLICY_ID));

        final boolean isValid;
        if (policyIdOpt.isPresent()) {
            isValid = !policyIdInPolicyOpt.isPresent() || policyIdInPolicyOpt.equals(policyIdOpt);
        } else {
            isValid = !policyIdInPolicyOpt.isPresent() || policyIdInPolicyOpt.equals(thingIdOpt);
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

    private boolean createPolicyAndThing(final CreatePolicy createPolicy,
            final CreateThing createThingWithoutPolicyId,
            final ActorRef sender) {

        final long timeout = getAskTimeout().toMillis();

        final CreateThing createThing = CreateThing.of(
                createThingWithoutPolicyId.getThing().setPolicyId(createPolicy.getId()),
                null,
                createThingWithoutPolicyId.getDittoHeaders());

        PatternsCS.ask(policiesShardRegion, createPolicy, timeout).handleAsync((policyResponse, policyError) -> {

            final Optional<CreateThing> nextStep =
                    handlePolicyResponseForCreateThing(createPolicy, createThing, policyResponse, policyError, sender);

            nextStep.ifPresent(cmd -> PatternsCS.ask(thingsShardRegion, cmd, timeout)
                    .handleAsync((thingResponse, thingError) ->
                            handleThingResponseForCreateThing(createThing, thingResponse, thingError, sender)));

            return null;
        });

        return true;
    }

    private Optional<CreateThing> handlePolicyResponseForCreateThing(
            final CreatePolicy createPolicy,
            final CreateThing createThing,
            final Object policyResponse,
            final Throwable policyError,
            final ActorRef sender) {

        if (policyResponse instanceof CreatePolicyResponse) {

            return Optional.of(createThing);

        } else if (policyResponse instanceof PolicyConflictException ||
                policyResponse instanceof PolicyNotAccessibleException) {

            reportInitialPolicyCreationFailure(createPolicy.getId(), createThing, sender);

        } else if (isAskTimeoutException(policyResponse, policyError)) {

            replyToSender(PolicyUnavailableException.newBuilder(createThing.getThingId())
                    .dittoHeaders(createThing.getDittoHeaders())
                    .build(), sender);

        } else {

            final String hint =
                    String.format("creating initial policy during creation of Thing <%s>",
                            createThing.getThingId());
            reportUnexpectedErrorOrResponse(hint, sender, policyResponse, policyError, createThing.getDittoHeaders());
        }

        return Optional.empty();
    }

    private Void handleThingResponseForCreateThing(
            final CreateThing createThing,
            final Object thingResponse,
            final Throwable thingError,
            final ActorRef sender) {

        if (thingResponse instanceof ThingCommandResponse || thingResponse instanceof DittoRuntimeException) {

            replyToSender(thingResponse, sender);

        } else if (isAskTimeoutException(thingResponse, thingError)) {

            replyToSender(ThingUnavailableException.newBuilder(createThing.getThingId())
                    .dittoHeaders(createThing.getDittoHeaders())
                    .build(), sender);

        } else {

            final String hint =
                    String.format("after creating initial policy during creation of Thing <%s>",
                            createThing.getThingId());
            reportUnexpectedErrorOrResponse(hint, sender, thingResponse, thingError, createThing.getDittoHeaders());
        }

        return null;
    }

    private void reportInitialPolicyCreationFailure(final String policyId,
            final CreateThing command,
            final ActorRef sender) {

        log(command).info("The Policy with ID '{}' is already existing, the CreateThing " +
                "command which would have created an implicit Policy for the Thing with ID '{}' " +
                "is therefore not handled", policyId, command.getThingId());
        final ThingNotCreatableException error =
                ThingNotCreatableException.newBuilderForPolicyExisting(command.getThingId(), policyId)
                        .dittoHeaders(command.getDittoHeaders())
                        .build();
        replyToSender(error, sender);
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
}
