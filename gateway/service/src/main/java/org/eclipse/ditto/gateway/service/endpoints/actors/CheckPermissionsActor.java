/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.actors;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.pattern.Patterns;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.endpoints.routes.checkpermissions.CheckPermissions;
import org.eclipse.ditto.gateway.service.endpoints.routes.checkpermissions.CheckPermissionsResponse;
import org.eclipse.ditto.gateway.service.endpoints.routes.checkpermissions.ImmutablePermissionCheck;
import org.eclipse.ditto.gateway.service.endpoints.routes.checkpermissions.PermissionCheckWrapper;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.ResourcePermissionFactory;
import org.eclipse.ditto.policies.model.ResourcePermissions;
import org.eclipse.ditto.policies.api.commands.sudo.CheckPolicyPermissions;
import org.eclipse.ditto.policies.api.commands.sudo.CheckPolicyPermissionsResponse;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;

/**
 * Actor that handles permission checks for resources and entities.
 * <p>
 * This actor receives {@link CheckPermissions} messages, processes permission checks by
 * aggregating results based on policies and entities, and communicates with the Ditto Edge
 * and Things API to retrieve policy data and permissions.
 * It responds to the sender with a {@link CheckPermissionsResponse} after evaluating the
 * permission checks.
 * <p>
 * The actor forwards requests to the edge command forwarder to retrieve policies and permission
 * results, handling various entity and policy resources.
 *
 * @since 3.7.0
 */
final class CheckPermissionsActor extends AbstractActor {

    private final DittoLogger logger = DittoLoggerFactory.getLogger(CheckPermissionsActor.class);
    private final ActorRef edgeCommandForwarder;
    private final ActorRef sender;
    private final Duration defaultAskTimeout;

    /**
     * The actor name for this class.
     */
    public static final String ACTOR_NAME = "checkPermissionsActor";

    /**
     * Constructor to initialize the actor.
     *
     * @param edgeCommandForwarder the actor responsible for forwarding commands to edge services.
     * @param sender the actor reference to the original sender.
     * @param defaultTimeout the default timeout for async operations.
     */
    private CheckPermissionsActor(final ActorRef edgeCommandForwarder, final ActorRef sender,
            final Duration defaultTimeout) {
        this.edgeCommandForwarder = edgeCommandForwarder;
        this.sender = sender;
        this.defaultAskTimeout = defaultTimeout;
    }

    /**
     * Creates {@link Props} for the {@link CheckPermissionsActor}.
     *
     * @param edgeCommandForwarder the actor responsible for forwarding commands to edge services.
     * @param sender the actor reference to the original sender.
     * @param defaultTimeout the default timeout for async operations.
     * @return a {@link Props} object to create the actor.
     */
    public static Props props(final ActorRef edgeCommandForwarder, final ActorRef sender,
            final Duration defaultTimeout) {
        return Props.create(CheckPermissionsActor.class, edgeCommandForwarder, sender, defaultTimeout);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(CheckPermissions.class, this::handleCheckPermissions)
                .matchAny(msg -> logger.warn("Unknown message: <{}>", msg))
                .build();
    }

    /**
     * Handles the {@link CheckPermissions} message by initializing the permission results,
     * grouping permission checks by entity, and performing policy retrievals.
     *
     * @param command the {@link CheckPermissions} message to handle.
     */
    private void handleCheckPermissions(final CheckPermissions command) {
        final Map<String, Boolean> permissionResults = initializePermissionResults(command);
        final Map<String, Map<String, PermissionCheckWrapper>> groupedByEntityId =
                groupByEntityAndPolicyResource(command);

        CompletableFuture<Void> allPolicyRetrievals = CompletableFuture.allOf(
                groupedByEntityId.keySet().stream()
                        .map(entityId -> retrieveOrSetPolicyId(entityId, groupedByEntityId.get(entityId), command))
                        .toArray(CompletableFuture[]::new)
        );

        allPolicyRetrievals.thenRun(
                        () -> handlePolicyRetrievalCompletion(command, permissionResults, groupedByEntityId))
                .exceptionally(ex -> handleFailure(ex, command));
    }

    /**
     * Initializes the permission results map with 'false' for each permission check.
     *
     * @param command the {@link CheckPermissions} command containing the permission checks.
     * @return a map with the permission check results, initialized to {@code false}.
     */
    private Map<String, Boolean> initializePermissionResults(final CheckPermissions command) {
        return command.getPermissionChecks().keySet().stream()
                .collect(Collectors.toMap(
                        check -> check,
                        check -> false,
                        (oldValue, newValue) -> oldValue,
                        LinkedHashMap::new));
    }

    /**
     * Groups permission checks by entity ID and policy resource.
     *
     * @param command the {@link CheckPermissions} command.
     * @return a map grouped by entity ID, containing permission check wrappers.
     */
    private Map<String, Map<String, PermissionCheckWrapper>> groupByEntityAndPolicyResource(
            final CheckPermissions command) {
        return command.getPermissionChecks().entrySet().stream()
                .collect(Collectors.groupingBy(
                        entry -> entry.getValue().getEntityId(),
                        Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> new PermissionCheckWrapper(entry.getValue())
                        )
                ));
    }

    /**
     * Retrieves or sets the {@link PolicyId} for a given entity.
     * <p>
     * If the entity is a policy resource, it directly sets the {@link PolicyId} for the corresponding wrappers.
     * If the entity is not a policy resource, it retrieves the {@link PolicyId} from the entity using the
     *
     * @param entityId the ID of the entity for which the {@link PolicyId} is being retrieved or set.
     * @param wrappers the wrappers containing the permission checks for the entity.
     * @param command the {@link CheckPermissions} command containing the headers for the request.
     * @return a {@link CompletableFuture} that completes when the {@link PolicyId} is set for all wrappers.
     */
    private CompletableFuture<Void> retrieveOrSetPolicyId(final String entityId,
            final Map<String, PermissionCheckWrapper> wrappers,
            CheckPermissions command) {
        if (wrappers.values().stream().findFirst().get().getPermissionCheck().isPolicyResource()) {
            wrappers.values().forEach(wrapper -> wrapper.setPolicyId(PolicyId.of(entityId)));
            return CompletableFuture.completedFuture(null);
        } else {
            return retrievePolicyIdForEntity(entityId, false, command.getDittoHeaders())
                    .thenAccept(policyId -> wrappers.values().forEach(wrapper -> wrapper.setPolicyId(policyId)))
                    .toCompletableFuture();
        }
    }

    /**
     * Handles the completion of policy retrieval by checking the permissions for each policy.
     *
     * @param command the {@link CheckPermissions} command.
     * @param permissionResults the map to store the permission check results.
     * @param groupedByEntityId the permission checks grouped by entity ID.
     */
    private void handlePolicyRetrievalCompletion(final CheckPermissions command,
            final Map<String, Boolean> permissionResults,
            Map<String, Map<String, PermissionCheckWrapper>> groupedByEntityId) {
        // Aggregate permissions by PolicyId and Resource
        final Map<PolicyId, Map<String, ResourcePermissions>> aggregatedPermissions =
                groupPermissionsByPolicyId(groupedByEntityId);

        List<CompletionStage<Void>> permissionCheckFutures = aggregatedPermissions.entrySet().stream()
                .map(aggregateEntry -> checkPermissionsForPolicy(aggregateEntry, command, permissionResults))
                .toList();

        CompletableFuture.allOf(permissionCheckFutures.toArray(new CompletableFuture[0])).thenRun(() -> {
            final CheckPermissionsResponse response =
                    CheckPermissionsResponse.of(permissionResults, command.getDittoHeaders());
            sender.tell(response, getSelf());
        }).exceptionally(ex -> handleFailure(ex, command));
    }

    /**
     * Groups permission checks by policy ID, aggregating the results.
     *
     * @param groupedByEntityId the permission checks grouped by entity ID.
     * @return a map grouped by {@link PolicyId}, containing resource permissions.
     */
    private Map<PolicyId, Map<String, ResourcePermissions>> groupPermissionsByPolicyId(
            final Map<String, Map<String, PermissionCheckWrapper>> groupedByEntityId) {
        return groupedByEntityId.values().stream()
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.groupingBy(
                        entry -> entry.getValue().getPolicyId(),
                        Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> createResourcePermissions(entry.getValue().getPermissionCheck())
                        )
                ));
    }

    /**
     * Checks permissions for a given policy and aggregates the results.
     *
     * @param aggregateEntry the entry containing the policy ID and resource permissions.
     * @param command the {@link CheckPermissions} command.
     * @param permissionResults the map to store the permission check results.
     * @return a {@link CompletionStage} representing the result of the permission check.
     */
    private CompletionStage<Void> checkPermissionsForPolicy(
            final Map.Entry<PolicyId, Map<String, ResourcePermissions>> aggregateEntry,
            final CheckPermissions command,
            final Map<String, Boolean> permissionResults) {
        CheckPolicyPermissions policyCommand = CheckPolicyPermissions.of(
                aggregateEntry.getKey(), aggregateEntry.getValue(), command.getDittoHeaders());


        return Patterns.ask(edgeCommandForwarder, policyCommand, defaultAskTimeout)
                .thenAccept(result -> processPolicyCommandResult(result, aggregateEntry.getValue(), permissionResults))
                .exceptionally(ex -> {
                    logError(ex, aggregateEntry.getKey().toString(), aggregateEntry.getValue().toString(),
                            command.getDittoHeaders());

                    throw DittoInternalErrorException.newBuilder()
                            .dittoHeaders(command.getDittoHeaders())
                            .cause(ex)
                            .message("Failed to check permissions for policy ID: " + aggregateEntry.getKey())
                            .build();
                });
    }

    /**
     * Processes the result of a policy command by updating the permission results.
     *
     * @param result the result of the policy command.
     * @param resourcePermissions the resource permissions to check.
     * @param permissionResults the map to store the permission check results.
     */
    private void processPolicyCommandResult(final Object result,
            final Map<String, ResourcePermissions> resourcePermissions,
            final Map<String, Boolean> permissionResults) {
        if (result instanceof CheckPolicyPermissionsResponse response) {
            permissionResults.putAll(CheckPolicyPermissionsResponse.toMap(response.getPermissionsResults()));
        } else {
            setPermissionCheckFailure(resourcePermissions, permissionResults);
        }
    }

    /**
     * Marks the permission check as failed by setting all results for the given resources to {@code false}.
     *
     * @param resourcePermissions the map of resource permissions that failed.
     * @param permissionResults the map of permission results where the failure should be reflected.
     */
    private void setPermissionCheckFailure(Map<String, ResourcePermissions> resourcePermissions,
            Map<String, Boolean> permissionResults) {
        resourcePermissions.forEach((key, value) -> permissionResults.put(key, false));
    }

    /**
     * Handles a failure during the permission checking process by logging the error and sending a
     * {@link DittoInternalErrorException} back to the sender.
     *
     * @param ex the exception that occurred during the permission check.
     * @param command the {@link CheckPermissions} command containing permission checks.
     * @return null, as the error is propagated via an exception and response is sent.
     */
    private Void handleFailure(Throwable ex, CheckPermissions command) {
        logError(ex, command.getPermissionChecks(), command.getDittoHeaders());

        final DittoInternalErrorException errorResponse = DittoInternalErrorException.newBuilder()
                .dittoHeaders(command.getDittoHeaders())
                .cause(ex)
                .message("Internal error while processing permission checks for command: " + command)
                .build();
        sender.tell(errorResponse, getSelf());
        throw new CompletionException(ex);
    }


    /**
     * Logs an error message when a permission check fails for a given request.
     * <p>
     * This method logs the failure of a permission check operation using a {@code logger} with correlation ID
     * extracted from the provided {@code DittoHeaders}. The logged message includes the details of the failed
     * permission check request and the associated exception message.
     * </p>
     *
     * @param ex the exception that occurred during the permission check.
     * @param permissionChecks the map containing the permission checks that failed.
     * @param headers the {@code DittoHeaders} associated with the command, used for logging context.
     */
    private void logError(Throwable ex, LinkedHashMap<String, ImmutablePermissionCheck> permissionChecks,
            DittoHeaders headers) {
        logger.withCorrelationId(headers)
                .warn("Permission check failed for request: {}. Error: {}", permissionChecks, ex.getMessage());
    }

    /**
     * Logs an error related to a failed permission check.
     *
     * @param ex the exception that occurred.
     * @param entityId the ID of the entity being checked.
     * @param resource the resource being checked.
     */
    private void logError(Throwable ex, String entityId, String resource, final DittoHeaders dittoHeaders) {
        logger.withCorrelationId(dittoHeaders)
                .warn("Permission check failed for entityId: {} and resource: {}. Error: {}", entityId, resource,
                        ex.getMessage());
    }

    /**
     * Creates a {@link ResourcePermissions} object from the {@link ImmutablePermissionCheck}.
     * <p>
     * This method constructs a {@link ResourcePermissions} object for a specific resource by
     * extracting the resource type, resource path, and the list of permissions.
     *
     * @param check the {@link ImmutablePermissionCheck} containing the permission data.
     * @return the constructed {@link ResourcePermissions}.
     */
    private ResourcePermissions createResourcePermissions(ImmutablePermissionCheck check) {
        ResourceKey resourceKey = check.getResourceKey();
        List<String> permissions = check.getHasPermissions();

        return ResourcePermissionFactory.newInstance(resourceKey, permissions);
    }

    /**
     * Retrieves the {@link PolicyId} for a given entity by sending a {@link SudoRetrieveThing} command.
     * <p>
     * If the entity is a policy resource, it immediately creates a {@link PolicyId} from the entity ID.
     * Otherwise, it retrieves the {@link PolicyId} using the Ditto Things API by sending a {@link SudoRetrieveThing} command.
     * <p>
     * If an unexpected response type is received a {@link DittoRuntimeException}
     * or a {@link DittoInternalErrorException} will be thrown to indicate an internal processing error.
     * <p>
     * For cases where the response is a {@link DittoRuntimeException}, the exception is rethrown directly.
     * If the response is not of the expected type or if the {@link PolicyId} is absent, an {@link IllegalArgumentException}
     * wrapped inside a {@link DittoInternalErrorException} is thrown, ensuring proper error handling.
     *
     * @param entityId the ID of the entity for which the {@link PolicyId} is being retrieved.
     * @param isPolicyResource flag indicating whether the entity is a policy resource.
     * @param headers the {@link DittoHeaders} associated with the request.
     * @return a {@link CompletionStage} that completes with the {@link PolicyId} once the operation is finished.
     * @throws DittoRuntimeException if an unexpected response is received, or the {@link PolicyId} is missing in the response.
     * @throws DittoInternalErrorException if an unexpected response type is encountered, or if the {@link PolicyId} is missing.
     */
    private CompletionStage<PolicyId> retrievePolicyIdForEntity(String entityId, boolean isPolicyResource,
            DittoHeaders headers) {
        if (isPolicyResource) {
            return CompletableFuture.completedFuture(PolicyId.of(entityId));
        } else {
            final SudoRetrieveThing retrieveThing = SudoRetrieveThing.of(
                    ThingId.of(entityId),
                    JsonFieldSelector.newInstance(Thing.JsonFields.POLICY_ID.getPointer()),
                    headers);

            return Patterns.ask(edgeCommandForwarder, retrieveThing, defaultAskTimeout)
                    .thenApply(result -> {
                        if (result instanceof SudoRetrieveThingResponse thingResponse) {
                            return thingResponse.getThing().getPolicyId().orElse(null);
                        } else if (result instanceof DittoRuntimeException) {
                            throw (DittoRuntimeException) result;
                        } else {
                            throw DittoInternalErrorException.newBuilder()
                                    .dittoHeaders(headers)
                                    .cause(new IllegalArgumentException(
                                            "Unexpected response type: " + result.getClass().getSimpleName()))
                                    .build();
                        }
                    }).handle((policyId, throwable) -> {
                        if (throwable != null) {
                            logError(throwable, entityId, "retrievePolicyIdForEntity", headers);
                            sender.tell(DittoInternalErrorException.newBuilder()
                                    .dittoHeaders(headers)
                                    .cause(throwable)
                                    .message("Unexpected error while handling the entity: " + entityId)
                                    .build(), getSelf());
                            throw new CompletionException(throwable);
                        }
                        return policyId;
                    });
        }
    }
}
