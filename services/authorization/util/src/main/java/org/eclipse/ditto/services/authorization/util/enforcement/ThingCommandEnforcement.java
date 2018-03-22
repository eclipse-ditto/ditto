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
package org.eclipse.ditto.services.authorization.util.enforcement;

import static org.eclipse.ditto.model.things.Permission.ADMINISTRATE;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.enforcers.AclEnforcer;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.authorization.util.cache.entry.Entry;
import org.eclipse.ditto.services.models.authorization.EntityId;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.signals.commands.base.CommandToExceptionRegistry;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayServiceTimeoutException;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicy;
import org.eclipse.ditto.signals.commands.policies.query.RetrievePolicyResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingCommandToAccessExceptionRegistry;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingCommandToModifyExceptionRegistry;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
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
import akka.pattern.AskTimeoutException;
import akka.pattern.PatternsCS;

/**
 * Mixin to authorize {@code ThingCommand}.
 */
// TODO: migrate logging
interface ThingCommandEnforcement extends Enforcement, InlinePolicyHandling {

    /**
     * Json fields that are always shown regardless of authorization.
     */
    JsonFieldSelector THING_QUERY_COMMAND_RESPONSE_WHITELIST = JsonFactory.newFieldSelector(Thing.JsonFields.ID);

    /**
     * Mixin API: authorize a thing command. Either the command is forwarded to things-shard-region for execution or
     * the sender is told of an error.
     *
     * @param thingCommand the command to authorize.
     */
    default void enforceThingCommand(final ThingCommand thingCommand) {
        final ActorRef sender = sender();
        caches().retrieve(entityId(), (enforcerKeyEntry, enforcerEntry) -> {
            if (!enforcerEntry.exists()) {
                enforceThingCommandByNonexistentEnforcer(enforcerKeyEntry, thingCommand, sender);
            } else if (isAclEnforcer(enforcerKeyEntry)) {
                enforceThingCommandByAclEnforcer(thingCommand, enforcerEntry.getValue(), sender);
            } else {
                final String policyId = enforcerKeyEntry.getValue().getId();
                enforceThingCommandByPolicyEnforcer(thingCommand, policyId, enforcerEntry.getValue(), sender);
            }
        });
    }

    /**
     * Mixin-private method: authorize a thing command in the absence of an enforcer. This happens when the thing did
     * not exist or when the policy of the thing does not exist.
     *
     * @param enforcerKeyEntry cache entry in the entity ID cache for the enforcer cache key.
     * @param thingCommand the command to authorize.
     * @param sender sender of the command.
     */
    default void enforceThingCommandByNonexistentEnforcer(final Entry<EntityId> enforcerKeyEntry,
            final ThingCommand thingCommand, final ActorRef sender) {
        if (enforcerKeyEntry.exists()) {
            // Thing exists but its policy is deleted.
            final String thingId = thingCommand.getThingId();
            final String policyId = enforcerKeyEntry.getValue().getId();
            final DittoRuntimeException error = errorForExistingThingWithDeletedPolicy(thingCommand, thingId, policyId);
            replyToSender(error, sender);
        } else {
            // Without prior enforcer in cache, enforce CreateThing by self.
            final boolean authorized = authorizeCreateThingBySelf(thingCommand)
                    .map(pair -> handleInitialCreateThing(pair.createThing, pair.enforcer, sender))
                    .isPresent();

            if (!authorized) {
                respondWithError(thingCommand, sender);
            }
        }
    }

    /**
     * Mixin-private method: authorize a thing command by ACL enforcer with special handling for the field "/acl".
     *
     * @param thingCommand the thing command.
     * @param enforcer the ACL enforcer.
     * @param sender sender of the command.
     */
    default void enforceThingCommandByAclEnforcer(final ThingCommand<?> thingCommand, final Enforcer enforcer,
            final ActorRef sender) {
        final boolean authorized = authorizeByAcl(enforcer, thingCommand)
                .map(command -> forwardToThingsShardRegion(thingCommand, sender))
                .isPresent();

        if (!authorized) {
            respondWithError(thingCommand, sender);
        }
    }

    /**
     * Mixin-private method: authorize a thing command by policy enforcer with view restriction for query commands.
     *
     * @param thingCommand the thing command.
     * @param policyId Id of the thing's policy.
     * @param enforcer the policy enforcer.
     * @param sender sender of the command.
     */
    default void enforceThingCommandByPolicyEnforcer(final ThingCommand<?> thingCommand,
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
            respondWithError(thingCommand, sender);
        }
    }

    /**
     * Mixin-private method: reply with an error.
     *
     * @param thingCommand command that generated the error.
     * @param sender sender of the command.
     */
    default void respondWithError(final ThingCommand thingCommand, final ActorRef sender) {
        sender.tell(errorForThingCommand(thingCommand), self());
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
    default boolean askThingsShardRegionAndBuildJsonView(
            final ThingQueryCommand commandWithReadSubjects,
            final Enforcer enforcer,
            final ActorRef sender) {

        PatternsCS.ask(thingsShardRegion(), commandWithReadSubjects, getAskTimeout().toMillis())
                .handleAsync((response, error) -> {
                    if (error != null) {
                        reportUnexpectedError("before building JsonView", sender, error);
                    } else if (response instanceof ThingQueryCommandResponse) {
                        reportJsonViewForThingQuery(sender, (ThingQueryCommandResponse) response, enforcer);
                    } else if (response instanceof DittoRuntimeException) {
                        replyToSender(response, sender);
                    } else if (response instanceof AskTimeoutException) {
                        reportTimeoutForThingQuery(sender, (AskTimeoutException) response);
                    } else {
                        reportUnknownResponse("before building JsonView", sender, response);
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
    default boolean retrieveThingAndPolicy(
            final RetrieveThing retrieveThing,
            final String policyId,
            final Enforcer enforcer,
            final ActorRef sender) {

        final Optional<RetrievePolicy> retrievePolicyOptional =
                authorizePolicyCommand(RetrievePolicy.of(policyId, retrieveThing.getDittoHeaders()), enforcer);

        if (retrievePolicyOptional.isPresent()) {
            retrieveThingBeforePolicy(retrieveThing, sender).thenAccept(thingResponse ->
                    thingResponse.ifPresent(retrieveThingResponse -> {
                        final RetrievePolicy retrievePolicy = retrievePolicyOptional.get();
                        retrieveInlinedPolicyForThing(retrieveThing, retrievePolicy).thenAccept(policyResponse -> {
                            if (policyResponse.isPresent()) {
                                reportAggregatedThingAndPolicy(retrieveThing, retrieveThingResponse,
                                        policyResponse.get(), enforcer, sender);
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
    default CompletionStage<Optional<RetrieveThingResponse>> retrieveThingBeforePolicy(
            final RetrieveThing retrieveThing,
            final ActorRef sender) {

        return PatternsCS.ask(thingsShardRegion(), retrieveThing, getAskTimeout().toMillis())
                .handleAsync((response, error) -> {
                    if (response instanceof RetrieveThingResponse) {
                        return Optional.of((RetrieveThingResponse) response);
                    } else if (response instanceof ThingErrorResponse || response instanceof DittoRuntimeException) {
                        replyToSender(response, sender);
                    } else if (error instanceof AskTimeoutException) {
                        final ThingUnavailableException thingUnavailableException =
                                ThingUnavailableException.newBuilder(retrieveThing.getThingId()).build();
                        replyToSender(thingUnavailableException, sender);
                    } else {
                        reportUnexpectedErrorOrResponse("retrieving thing before inlined policy",
                                sender, response, error);
                    }
                    return Optional.empty();
                });
    }

    /**
     * Retrieve inlined policy after retrieving a thing. Do not report errors.
     *
     * @param retrieveThing the original command.
     * @param retrievePolicy the command to retrieve the thing's policy.
     * @return future response from policies-shard-region.
     */
    default CompletionStage<Optional<RetrievePolicyResponse>> retrieveInlinedPolicyForThing(
            final RetrieveThing retrieveThing,
            final RetrievePolicy retrievePolicy) {

        return PatternsCS.ask(policiesShardRegion(), retrievePolicy, getAskTimeout().toMillis())
                .handleAsync((response, error) -> {
                    if (response instanceof RetrievePolicyResponse) {
                        return Optional.of((RetrievePolicyResponse) response);
                    } else if (error != null) {
                        log().error(error, "retrieving inlined policy after RetrieveThing");
                    } else {
                        log().info("No authorized response when retrieving inlined policy <{}> for thing <{}>: {}",
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
    default void reportAggregatedThingAndPolicy(
            final RetrieveThing retrieveThing,
            final RetrieveThingResponse retrieveThingResponse,
            final RetrievePolicyResponse retrievePolicyResponse,
            final Enforcer enforcer,
            final ActorRef sender) {

        final RetrieveThingResponse limitedView =
                buildJsonViewForThingQueryCommandResponse(retrieveThingResponse, enforcer);

        final JsonObject inlinedPolicy = retrievePolicyResponse.getPolicy()
                .toInlinedJson(retrieveThing.getImplementedSchemaVersion(), FieldType.notHidden());

        final JsonObject thingWithInlinedPolicy = limitedView.getEntity().asObject().toBuilder()
                .setAll(inlinedPolicy)
                .build();

        replyToSender(limitedView.setEntity(thingWithInlinedPolicy), sender);
    }

    /**
     * Mixin-private: report timeout of {@code ThingQueryComand}.
     *
     * @param sender sender of the command.
     * @param askTimeoutException the timeout exception.
     */
    default void reportTimeoutForThingQuery(final ActorRef sender, final AskTimeoutException askTimeoutException) {
        log().error(askTimeoutException, "Timeout before building JsonView");
        replyToSender(GatewayServiceTimeoutException.newBuilder().build(), sender);
    }

    /**
     * Mixin-private: report thing query response with view on entity restricted by enforcer.
     *
     * @param sender sender of the command.
     * @param thingQueryCommandResponse response of query.
     * @param enforcer the enforcer.
     */
    default void reportJsonViewForThingQuery(final ActorRef sender,
            final ThingQueryCommandResponse<?> thingQueryCommandResponse,
            final Enforcer enforcer) {

        try {
            final ThingQueryCommandResponse responseWithLimitedJsonView =
                    buildJsonViewForThingQueryCommandResponse(thingQueryCommandResponse, enforcer);
            replyToSender(responseWithLimitedJsonView, sender);
        } catch (final DittoRuntimeException e) {
            log().error(e, "Error after building JsonView");
            replyToSender(e, sender);
        }
    }

    @Override
    default boolean enforceCreateThingForNonexistentThingWithPolicyId(final CreateThing createThing,
            final String policyId,
            final ActorRef sender) {
        final EntityId policyEntityId = EntityId.of(PolicyCommand.RESOURCE_TYPE, policyId);
        caches().retrieve(policyEntityId, (policyIdEntry, policyEnforcerEntry) -> {
            if (policyEnforcerEntry.exists()) {
                enforceThingCommandByPolicyEnforcer(createThing, policyId, policyEnforcerEntry.getValue(), sender);
            } else {
                final DittoRuntimeException error =
                        errorForExistingThingWithDeletedPolicy(createThing, createThing.getThingId(), policyId);
                replyToSender(error, sender);
            }
        });
        return true;
    }

    /**
     * Mixin-private: limit view on entity of {@code ThingQueryCommandResponse} by enforcer.
     *
     * @param response the response.
     * @param enforcer the enforcer.
     * @return response with view on entity restricted by enforcer..
     */
    static <T extends ThingQueryCommandResponse> T buildJsonViewForThingQueryCommandResponse(
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
     * Mixin-private: restrict view on a JSON object by enforcer.
     *
     * @param responseEntity the JSON object to restrict view on.
     * @param response the response containing the object.
     * @param enforcer the enforcer.
     * @return JSON object with view restricted by enforcer.
     */
    static JsonObject getJsonViewForThingQueryCommandResponse(final JsonObject responseEntity,
            final ThingQueryCommandResponse response,
            final Enforcer enforcer) {


        final ResourceKey resourceKey = ResourceKey.newInstance(ThingCommand.RESOURCE_TYPE, response.getResourcePath());
        final AuthorizationContext authorizationContext = response.getDittoHeaders().getAuthorizationContext();

        return enforcer.buildJsonView(resourceKey, responseEntity, authorizationContext,
                THING_QUERY_COMMAND_RESPONSE_WHITELIST, Permissions.newInstance(Permission.READ));
    }


    /**
     * Mixin-private: create error for commands to an existing thing whose policy is deleted.
     *
     * @param thingCommand the triggering command.
     * @param thingId ID of the thing.
     * @param policyId ID of the deleted policy.
     * @return an appropriate error.
     */
    static DittoRuntimeException errorForExistingThingWithDeletedPolicy(
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
                    .build();
        } else {
            return ThingNotAccessibleException.newBuilder(thingId)
                    .message(message)
                    .description(description)
                    .build();
        }
    }

    /**
     * Mixin-private: create error due to failing to execute a thing-command in the expected way.
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
     * Mixin-private: check if an enforcer key points to an access-control-list enforcer.
     *
     * @param enforcerKeyEntry cache key entry of an enforcer.
     * @return whether it is based on an access control list and requires special handling.
     */
    static boolean isAclEnforcer(final Entry<EntityId> enforcerKeyEntry) {
        return enforcerKeyEntry.exists() &&
                Objects.equals(ThingCommand.RESOURCE_TYPE, enforcerKeyEntry.getValue().getResourceType());
    }

    /**
     * Mixin-private: authorize a thing-command by authorization information contained in itself. Only {@code
     * CreateThing} commands are authorized in this manner in the absence of an existing enforcer. {@code
     * ModifyThing} commands are transformed to {@code CreateThing} commands before being processed.
     *
     * @param receivedThingCommand the command to authorize.
     * @return optionally the authorized command extended by  read subjects.
     */
    static Optional<CreateThingWithEnforcer> authorizeCreateThingBySelf(final ThingCommand receivedThingCommand) {
        final ThingCommand thingCommand = transformModifyThingToCreateThing(receivedThingCommand);
        if (thingCommand instanceof CreateThing) {
            final CreateThing createThing = (CreateThing) thingCommand;
            final Optional<JsonObject> initialPolicyOptional = createThing.getInitialPolicy();
            if (initialPolicyOptional.isPresent()) {
                final Policy initialPolicy = PoliciesModelFactory.newPolicy(initialPolicyOptional.get());
                final Enforcer initialEnforcer = PolicyEnforcers.defaultEvaluator(initialPolicy);
                return authorizeByPolicy(initialEnforcer, createThing)
                        .map(command -> new CreateThingWithEnforcer(command, initialEnforcer));
            } else {
                final Optional<AccessControlList> aclOptional =
                        createThing.getThing().getAccessControlList().filter(acl -> !acl.isEmpty());
                if (aclOptional.isPresent()) {
                    final Enforcer initialEnforcer = AclEnforcer.of(aclOptional.get());
                    return authorizeByAcl(initialEnforcer, createThing)
                            .map(command -> new CreateThingWithEnforcer(command, initialEnforcer));
                } else {
                    // Command without authorization information is authorized by default.
                    final Set<String> authorizedSubjects = createThing.getDittoHeaders()
                            .getAuthorizationContext()
                            .getFirstAuthorizationSubject()
                            .map(subject -> Collections.singleton(subject.getId()))
                            .orElse(Collections.emptySet());
                    final CreateThing command =
                            Enforcement.addReadSubjectsToCommand(createThing, authorizedSubjects);
                    final Enforcer enforcer = new AuthorizedSubjectsEnforcer(authorizedSubjects);
                    return Optional.of(new CreateThingWithEnforcer(command, enforcer));
                }
            }
        } else {
            // Other commands cannot be authorized by ACL or policy contained in self.
            return Optional.empty();
        }
    }

    /**
     * Mixin-private: Transform a {@code ModifyThing} command sent to nonexistent thing to {@code CreateThing}
     * command if it is sent to a nonexistent thing.
     *
     * @param receivedCommand the command to transform.
     * @return {@code CreateThing} command containing the same information if the argument is a {@code ModifyThing}
     * command. Otherwise return the command itself.
     */
    static ThingCommand transformModifyThingToCreateThing(final ThingCommand receivedCommand) {
        if (receivedCommand instanceof ModifyThing) {
            final ModifyThing modifyThing = (ModifyThing) receivedCommand;
            final JsonObject initialPolicy = modifyThing.getInitialPolicy().orElse(null);
            return CreateThing.of(modifyThing.getThing(), initialPolicy, modifyThing.getDittoHeaders());
        } else {
            return receivedCommand;
        }
    }

    /**
     * Mixin-private: authorize a thing-command by a policy enforcer.
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
                ? Optional.of(Enforcement.addReadSubjectsToCommand(command, policyEnforcer))
                : Optional.empty();
    }

    /**
     * Mixin-private: authorize a thing-command by an ACL enforcer.
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
                ? Optional.of(Enforcement.addReadSubjectsToCommand(command, aclEnforcer))
                : Optional.empty();
    }

    /**
     * Mixin-private: compute ACL permissions relevant for a {@code ThingModifyCommand}. The field "/acl" is handled
     * specially with the "ADMINISTRATE" permission.
     *
     * @param command the command.
     * @return permissions needed to execute the command.
     */
    static Permissions computeAclPermissions(final ThingModifyCommand command) {
        return affectsAcl(command)
                ? Permissions.newInstance(Permission.WRITE, ADMINISTRATE.name())
                : Permissions.newInstance(Permission.WRITE);
    }

    /**
     * Mixin-private: decide whether a command affects the ACL.
     *
     * @param command the command.
     * @return whether it affects the ACL.
     */
    static boolean affectsAcl(final ThingModifyCommand command) {
        return command instanceof DeleteThing || resourcePathIntersectsAcl(command) || entityIntersectsAcl(command);
    }

    /**
     * Mixin-private: decide whether a command's resource path intersects with the ACL.
     *
     * @param command the command.
     * @return whether its resource path intersects with the ACL.
     */
    static boolean resourcePathIntersectsAcl(final ThingModifyCommand command) {
        return command.getResourcePath().getRoot()
                .flatMap(root -> Thing.JsonFields.ACL.getPointer()
                        .getRoot()
                        .map(aclRoot -> Objects.equals(root, aclRoot)))
                .orElse(true);
    }

    /**
     * Mixin-private: decide whether a command's entity intersects with the ACL.
     *
     * @param command the command.
     * @return whether its entity intersects with the ACL.
     */
    static boolean entityIntersectsAcl(final ThingModifyCommand command) {
        return (command instanceof ModifyThing || command instanceof CreateThing) &&
                command.getEntity()
                        .filter(JsonValue::isObject)
                        .map(jsonValue -> jsonValue.asObject().contains(Thing.JsonFields.ACL.getPointer()))
                        .isPresent();
    }

    /**
     * Check if inlined policy should be retrieved together with the thing.
     *
     * @param command the thing query command.
     * @return whether it is necessary to retrieve the thing's policy.
     */
    static boolean shouldRetrievePolicyWithThing(final ThingQueryCommand command) {
        final RetrieveThing retrieveThing = (RetrieveThing) command;
        return retrieveThing.getSelectedFields().filter(selector ->
                selector.getPointers().stream().anyMatch(jsonPointer ->
                        jsonPointer.getRoot()
                                .filter(jsonKey -> Policy.INLINED_FIELD_NAME.equals(jsonKey.toString()))
                                .isPresent()))
                .isPresent();
    }

    /**
     * A pair of {@code CreateThing} command with {@code Enforcer}.
     */
    final class CreateThingWithEnforcer {

        private final CreateThing createThing;
        private final Enforcer enforcer;

        private CreateThingWithEnforcer(final CreateThing createThing,
                final Enforcer enforcer) {
            this.createThing = createThing;
            this.enforcer = enforcer;
        }
    }
}
