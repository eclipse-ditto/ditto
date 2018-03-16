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
package org.eclipse.ditto.services.authorization.util.actors;

import static org.eclipse.ditto.model.things.Permission.ADMINISTRATE;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.enforcers.AclEnforcer;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.model.policies.Permissions;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.authorization.util.cache.entry.Entry;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.signals.commands.base.CommandToExceptionRegistry;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayServiceTimeoutException;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingCommandToAccessExceptionRegistry;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingCommandToModifyExceptionRegistry;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotModifiableException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommand;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;

import akka.actor.ActorRef;
import akka.pattern.AskTimeoutException;
import akka.pattern.PatternsCS;

/**
 * Mixin to authorize {@code ThingCommand}.
 * <p>
 */
// TODO: scrutinize CreateThing with policy ID and no inline policy
// TODO: migrate logging
// TODO: unit test
interface ThingCommandEnforcement extends CommandEnforcementSelfType {

    /**
     * Json fields that are always shown regardless of authorization.
     */
    static JsonFieldSelector THING_QUERY_COMMAND_RESPONSE_WHITELIST = JsonFactory.newFieldSelector(Thing.JsonFields.ID);

    /**
     * Mixin API: authorize a thing command. Either the command is forwarded to things-shard-region for execution or
     * the sender is told of an error.
     *
     * @param thingCommand the command to authorize.
     */
    default void enforceThingCommand(final ThingCommand thingCommand) {
        final ActorRef sender = sender();
        caches().retrieve(entityKey(), (enforcerKeyEntry, enforcerEntry) -> {
            if (!enforcerEntry.exists()) {
                enforceThingCommandByNonexistentEnforcer(enforcerKeyEntry, thingCommand, sender);
            } else if (isAclEnforcer(enforcerKeyEntry)) {
                enforceThingCommandByAclEnforcer(thingCommand, enforcerEntry.getValue(), sender);
            } else {
                enforceThingCommandByPolicyEnforcer(thingCommand, enforcerEntry.getValue(), sender);
            }
        });
    }

    /**
     * Mixin-private method: retrieve the things-shard-region from the entity region map.
     *
     * @return the things shard region if it exists in the entity region map, deadletters otherwise.
     */
    default ActorRef thingsShardRegion() {
        return entityRegionMap().lookup(ThingCommand.RESOURCE_TYPE).orElse(context().system().deadLetters());
    }

    /**
     * Mixin-private method: authorize a thing command in the absence of an enforcer. This happens when the thing did
     * not exist or when the policy of the thing does not exist.
     *
     * @param enforcerKeyEntry cache entry in the entity ID cache for the enforcer cache key.
     * @param thingCommand the command to authorize.
     * @param sender sender of the command.
     */
    default void enforceThingCommandByNonexistentEnforcer(final Entry<ResourceKey> enforcerKeyEntry,
            final ThingCommand thingCommand, final ActorRef sender) {
        if (enforcerKeyEntry.exists()) {
            // Thing exists but its policy is deleted.
            final String thingId = thingCommand.getThingId();
            final String policyId = getEntityIdFromCacheKey(enforcerKeyEntry.getValue());
            final DittoRuntimeException error = errorForExistingThingWithDeletedPolicy(thingId, policyId);
            sender.tell(error, self());
        } else {
            // Without prior enforcer in cache, enforce CreateThing by self.
            authorizeCreateThingBySelf(thingCommand)
                    .map(command -> forwardToThingsShardRegion(command, sender))
                    .orElseGet(respondWithError(thingCommand, sender));
        }
    }

    /**
     * Mixin-private method: authorize a thing command by ACL enforcer with special handling for the field "/acl".
     *
     * @param thingCommand the thing command.
     * @param enforcer the ACL enforcer.
     * @param sender sender of the command.
     */
    default void enforceThingCommandByAclEnforcer(final ThingCommand thingCommand, final Enforcer enforcer,
            final ActorRef sender) {
        authorizeByAcl(enforcer, thingCommand)
                .map(command -> forwardToThingsShardRegion(thingCommand, sender))
                .orElseGet(respondWithError(thingCommand, sender));
    }

    /**
     * Mixin-private method: authorize a thing command by policy enforcer with view restriction for query commands.
     *
     * @param thingCommand the thing command.
     * @param enforcer the policy enforcer.
     * @param sender sender of the command.
     */
    default void enforceThingCommandByPolicyEnforcer(final ThingCommand thingCommand, final Enforcer enforcer,
            final ActorRef sender) {
        authorizeByPolicy(enforcer, thingCommand)
                .map(commandWithReadSubjects -> {
                    if (commandWithReadSubjects instanceof ThingQueryCommand) {
                        final ThingQueryCommand thingQueryCommand = (ThingQueryCommand) commandWithReadSubjects;
                        return askThingsShardRegionAndBuildJsonView(thingQueryCommand, enforcer, sender);
                    } else {
                        return forwardToThingsShardRegion(commandWithReadSubjects, sender);
                    }
                })
                .orElseGet(respondWithError(thingCommand, sender));
    }

    /**
     * Mixin-private method: forward a command to things-shard-region.
     * Do not call {@code Actor.forward(Object, ActorContext)} because it is not thread-safe.
     *
     * @param thingCommand command to forward.
     * @param sender sender of the command.
     * @return null.
     */
    default Void forwardToThingsShardRegion(final ThingCommand thingCommand, final ActorRef sender) {
        thingsShardRegion().tell(thingCommand, sender);
        return null;
    }

    /**
     * Mixin-private method: reply with an error.
     *
     * @param thingCommand command that generated the error.
     * @param sender sender of the command.
     * @return null.
     */
    default Supplier<Void> respondWithError(final ThingCommand thingCommand, final ActorRef sender) {
        sender.tell(errorForThingCommand(thingCommand), self());
        return null;
    }

    /**
     * Ask things-shard-region for response of a query command and limit the response according to a policy enforcer.
     *
     * @param commandWithReadSubjects the command to ask.
     * @param enforcer enforcer to build JsonView with.
     * @param sender sender of the command.
     * @return null.
     */
    default Void askThingsShardRegionAndBuildJsonView(
            final ThingQueryCommand commandWithReadSubjects,
            final Enforcer enforcer,
            final ActorRef sender) {

        PatternsCS.ask(thingsShardRegion(), commandWithReadSubjects, getAskTimeout().toMillis())
                .handleAsync((response, error) -> {
                    if (error != null) {
                        reportUnknownErrorForThingQuery(sender, error, response,
                                "Unexpected error before building JsonView");
                    } else if (response instanceof ThingQueryCommandResponse) {
                        reportJsonViewForThingQuery(sender, (ThingQueryCommandResponse) response, enforcer);
                    } else if (response instanceof DittoRuntimeException) {
                        sender.tell(response, self());
                    } else if (response instanceof AskTimeoutException) {
                        reportTimeoutForThingQuery(sender, (AskTimeoutException) response);
                    } else {
                        reportUnknownErrorForThingQuery(sender, null, response,
                                "Unexpected message before building JsonView");
                    }
                    return null;
                });
        return null;
    }

    /**
     * Mixin-private: report timeout of {@code ThingQueryComand}.
     *
     * @param sender sender of the command.
     * @param askTimeoutException the timeout exception.
     */
    default void reportTimeoutForThingQuery(final ActorRef sender, final AskTimeoutException askTimeoutException) {
        log().error(askTimeoutException, "Timeout before building JsonView");
        sender.tell(GatewayServiceTimeoutException.newBuilder().build(), self());
    }

    /**
     * Mixin-private: report thing query response with view on entity restricted by enforcer.
     *
     * @param sender sender of the command.
     * @param thingQueryCommandResponse response of query.
     * @param enforcer the enforcer.
     */
    default void reportJsonViewForThingQuery(final ActorRef sender,
            final ThingQueryCommandResponse thingQueryCommandResponse,
            final Enforcer enforcer) {

        try {
            final ThingQueryCommandResponse responseWithLimitedJsonView =
                    buildJsonViewForThingQueryCommandResponse(thingQueryCommandResponse, enforcer);
            sender.tell(responseWithLimitedJsonView, self());
        } catch (final DittoRuntimeException e) {
            log().error(e, "Error after building JsonView");
            sender.tell(e, self());
        }
    }

    /**
     * Mixin-private: report unknown error processing a {@code ThingQueryCommand}.
     */
    default void reportUnknownErrorForThingQuery(final ActorRef sender,
            @Nullable final Throwable error, @Nullable final Object response,
            final String extraInformation) {
        if (error != null) {
            log().error(error, extraInformation);
        } else {
            log().error(extraInformation + ": <{}>", response);
        }
        sender.tell(GatewayInternalErrorException.newBuilder().build(), self());
    }

    /**
     * Mixin-private: limit view on entity of {@code ThingQueryCommandResponse} by enforcer.
     *
     * @param response the response.
     * @param enforcer the enforcer.
     * @return response with view on entity restricted by enforcer..
     */
    static ThingQueryCommandResponse buildJsonViewForThingQueryCommandResponse(
            final ThingQueryCommandResponse response,
            final Enforcer enforcer) {

        final JsonValue entity = response.getEntity();
        if (entity.isObject()) {
            final JsonObject filteredView =
                    getJsonViewForThingQueryCommandResponse(entity.asObject(), response, enforcer);
            return response.setEntity(filteredView);
        } else {
            return response;
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
     * Mixin-private: extend a thing command by read-subjects header given by an enforcer.
     *
     * @param thingCommand the command to extend.
     * @param enforcer the enforcer.
     * @return the extended command.
     */
    static ThingCommand addReadSubjectsToThingCommand(final ThingCommand thingCommand, final Enforcer enforcer) {
        return addReadSubjectsToThingCommand(thingCommand, getReadSubjects(thingCommand, enforcer));
    }

    /**
     * Mixin-private: extend a thing command by read-subjects header given explicitly.
     *
     * @param thingCommand the command to extend.
     * @param readSubjects explicitly-given read subjects.
     * @return the extended command.
     */
    static ThingCommand addReadSubjectsToThingCommand(final ThingCommand thingCommand, final Set<String> readSubjects) {
        final DittoHeaders newHeaders = thingCommand.getDittoHeaders()
                .toBuilder()
                .readSubjects(readSubjects)
                .build();
        return thingCommand.setDittoHeaders(newHeaders);
    }

    /**
     * Mixin-private: get read subjects from an enforcer.
     *
     * @param thingCommand the command to get read subjects for.
     * @param enforcer the enforcer.
     * @return read subjects of the command.
     */
    static Set<String> getReadSubjects(final ThingCommand thingCommand, final Enforcer enforcer) {
        final ResourceKey resourceKey =
                ResourceKey.newInstance(ThingCommand.RESOURCE_TYPE, thingCommand.getResourcePath());
        return enforcer.getSubjectIdsWithPermission(resourceKey, Permission.READ).getGranted();
    }

    /**
     * Mixin-private: create error for commands to an existing thing whose policy is deleted.
     *
     * @param thingId ID of the thing.
     * @param policyId ID of the deleted policy.
     * @return an appropriate error.
     */
    static DittoRuntimeException errorForExistingThingWithDeletedPolicy(final String thingId, final String policyId) {
        final String message =
                "The Thing with ID ''%s'' could not be accessed as its Policy with ID ''%s'' is not or no longer existing.";
        final String description =
                "Recreate/create the Policy with ID ''%s'' in order to get access to the Thing again.";
        return ThingNotModifiableException.newBuilder(thingId)
                .message(String.format(message, thingId, policyId))
                .description(String.format(description, policyId))
                .build();
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
    static boolean isAclEnforcer(final Entry<ResourceKey> enforcerKeyEntry) {
        return enforcerKeyEntry.exists() &&
                Objects.equals(ThingCommand.RESOURCE_TYPE, enforcerKeyEntry.getValue().getResourceType());
    }

    /**
     * Mixin-private: authorize a thing-command by authorization information contained in itself. Only {@code
     * CreateThing} commands are authorized in this manner in the absence of an existing enforcer.
     *
     * @param thingCommand the command to authorize.
     * @return optionally the authorized command extended by  read subjects.
     */
    static Optional<ThingCommand> authorizeCreateThingBySelf(final ThingCommand thingCommand) {
        if (thingCommand instanceof CreateThing) {
            final CreateThing createThing = (CreateThing) thingCommand;
            if (createThing.getInitialPolicy().isPresent()) {
                final Policy initialPolicy = PoliciesModelFactory.newPolicy(createThing.getInitialPolicy().get());
                final Enforcer initialEnforcer = PolicyEnforcers.defaultEvaluator(initialPolicy);
                return authorizeByPolicy(initialEnforcer, createThing);
            } else if (createThing.getThing().getAccessControlList().isPresent()) {
                final Enforcer initialEnforcer = AclEnforcer.of(createThing.getThing().getAccessControlList().get());
                return authorizeByAcl(initialEnforcer, createThing);
            } else {
                // Command without authorization information is authorized by default.
                final Set<String> readSubjects = createThing.getDittoHeaders()
                        .getAuthorizationContext()
                        .getFirstAuthorizationSubject()
                        .map(subject -> Collections.singleton(subject.getId()))
                        .orElse(Collections.emptySet());
                return Optional.of(addReadSubjectsToThingCommand(createThing, readSubjects));
            }
        } else {
            // Other commands cannot be authorized by ACL or policy contained in self.
            return Optional.empty();
        }
    }

    /**
     * Mixin-private: authorize a thing-command by a policy enforcer.
     *
     * @param policyEnforcer the policy enforcer.
     * @param command the command to authorize.
     * @return optionally the authorized command extended by read subjects.
     */
    static Optional<ThingCommand> authorizeByPolicy(final Enforcer policyEnforcer, final ThingCommand command) {
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
                ? Optional.of(addReadSubjectsToThingCommand(command, policyEnforcer))
                : Optional.empty();
    }

    /**
     * Mixin-private: authorize a thing-command by an ACL enforcer.
     *
     * @param aclEnforcer the ACL enforcer.
     * @param command the command to authorize.
     * @return optionally the authorized command extended by read subjects.
     */
    static Optional<ThingCommand> authorizeByAcl(final Enforcer aclEnforcer, final ThingCommand command) {
        final ResourceKey thingResourceKey = PoliciesResourceType.thingResource(command.getResourcePath());
        final AuthorizationContext authorizationContext = command.getDittoHeaders().getAuthorizationContext();
        final Permissions permissions = command instanceof ThingModifyCommand
                ? computeAclPermissions((ThingModifyCommand) command)
                : Permissions.newInstance(Permission.READ);
        return aclEnforcer.hasUnrestrictedPermissions(thingResourceKey, authorizationContext, permissions)
                ? Optional.of(addReadSubjectsToThingCommand(command, aclEnforcer))
                : Optional.empty();
    }

    /**
     * Mixin-private: compute ACL permissions relevant for a {@code ThingModifyCommand}. The field "/acl" is handled
     * especially with the "ADMINISTRATE" permission.
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
                .orElse(false);
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
                        .orElse(false);
    }
}
