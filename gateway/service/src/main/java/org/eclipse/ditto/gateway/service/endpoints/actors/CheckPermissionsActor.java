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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.ReceiveTimeout;
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
import org.eclipse.ditto.policies.api.commands.sudo.CheckPolicyPermissions;
import org.eclipse.ditto.policies.api.commands.sudo.CheckPolicyPermissionsResponse;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourcePermissionFactory;
import org.eclipse.ditto.policies.model.ResourcePermissions;
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
     * Constructor to initialize the actor.
     *
     * @param edgeCommandForwarder the actor responsible for forwarding commands to edge services.
     * @param sender the actor reference to the original sender.
     * @param defaultTimeout the default timeout for async operations.
     */
    @SuppressWarnings("unused")
    private CheckPermissionsActor(final ActorRef edgeCommandForwarder, final ActorRef sender,
            final Duration defaultTimeout) {
        this.edgeCommandForwarder = edgeCommandForwarder;
        this.sender = sender;
        this.defaultAskTimeout = defaultTimeout;
        getContext().setReceiveTimeout(defaultTimeout);
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
                .match(ReceiveTimeout.class, this::handleReceiveTimeout)
                .matchAny(msg -> logger.warn("Unknown message: <{}>", msg))
                .build();
    }

    private void handleReceiveTimeout(final ReceiveTimeout receiveTimeout) {
        logger.warn("CheckPermissionsActor timed out: {}. Shutting down due to inactivity.", receiveTimeout);
        stopSelf();
    }

    /**
     * Handles the {@link CheckPermissions} message by initializing the permission results,
     * grouping permission checks by entity, and performing policy retrievals.
     *
     * @param command the {@link CheckPermissions} message to handle.
     */
    private void handleCheckPermissions(final CheckPermissions command) {
        final List<String> permissionOrder = new ArrayList<>(command.getPermissionChecks().keySet());
        final Map<String, Map<String, ImmutablePermissionCheck>> groupedByEntityId =
                groupByEntityAndPolicyResource(command);

        final CompletableFuture<Map<String, Map<String, PermissionCheckWrapper>>> allPolicyRetrievals =
                groupedByEntityId.entrySet().stream()
                        .reduce(
                                CompletableFuture.completedFuture(new LinkedHashMap<>()),
                                (future, entry) -> future.thenCompose(resultMap ->
                                        retrieveOrSetPolicyId(entry.getKey(), entry.getValue(), command)
                                                .thenApply(retrieved -> {
                                                    resultMap.put(entry.getKey(), retrieved);
                                                    return resultMap;
                                                })
                                ),
                                (f1, f2) -> f1.thenCombine(f2, (map1, map2) -> {
                                    map1.putAll(map2);
                                    return map1;
                                })
                        );

        allPolicyRetrievals
                .thenCompose(enrichedGroupedByEntityId ->
                        handlePolicyRetrievalCompletion(command, permissionOrder, enrichedGroupedByEntityId)
                )
                .whenComplete((result, throwable) -> {
                    if (null != throwable) {
                        handleFailure(throwable, command);
                    }
                    stopSelf();
                });
    }


    private void stopSelf() {
        final var context = getContext();
        context.cancelReceiveTimeout();
        context.stop(getSelf());
    }

    /**
     * Groups permission checks by entity ID and policy resource.
     *
     * @param command the {@link CheckPermissions} command.
     * @return a map grouped by entity ID, containing permission check wrappers.
     */
    private Map<String, Map<String, ImmutablePermissionCheck>> groupByEntityAndPolicyResource(
            final CheckPermissions command) {
        return command.getPermissionChecks().entrySet().stream()
                .collect(Collectors.groupingBy(
                        entry -> entry.getValue().getEntityId(),
                        Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue
                        )
                ));
    }

    private CompletableFuture<Map<String, PermissionCheckWrapper>> retrieveOrSetPolicyId(
            final String entityId,
            final Map<String, ImmutablePermissionCheck> permissionCheckMap,
            final CheckPermissions command) {

        final Optional<ImmutablePermissionCheck> first = permissionCheckMap.values().stream().findFirst();
        if (first.isPresent() && first.get().isPolicyResource()) {
            return CompletableFuture.completedFuture(
                    permissionCheckMap.entrySet().stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> new PermissionCheckWrapper(entry.getValue(), PolicyId.of(entityId))
                            ))
            );
        }

        return retrievePolicyIdForEntity(entityId, command.getDittoHeaders())
                .thenApply(policyId ->
                        permissionCheckMap.entrySet().stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        entry -> new PermissionCheckWrapper(entry.getValue(), policyId)
                                ))
                ).toCompletableFuture();

    }


    /**
     * Handles the completion of policy retrieval by checking the permissions for each policy.
     *
     * @param command the {@link CheckPermissions} command.
     * @param permissionOrder the list of permissions which retains the order.
     * @param enrichedGroupedByEntityId the permission checks grouped by entity ID and enriched with policyId.
     */
    private CompletionStage<Void> handlePolicyRetrievalCompletion(
            final CheckPermissions command,
            final List<String> permissionOrder,
            final Map<String, Map<String, PermissionCheckWrapper>> enrichedGroupedByEntityId) {
        final Map<PolicyId, Map<String, ResourcePermissions>> aggregatedPermissions =
                groupPermissionsByPolicyId(enrichedGroupedByEntityId);

        final List<CompletableFuture<Map<String, Boolean>>> permissionCheckFutures =
                aggregatedPermissions.entrySet().stream()
                        .map(aggregateEntry -> checkPermissionsForPolicy(aggregateEntry, command).toCompletableFuture())
                        .toList();

        final ActorRef self = getSelf();
        return CompletableFuture.allOf(permissionCheckFutures.toArray(new CompletableFuture[0]))
                .thenApply(value ->
                        permissionCheckFutures.stream()
                                .map(CompletableFuture::join)
                                .flatMap(map -> map.entrySet().stream())
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        Map.Entry::getValue,
                                        Boolean::logicalOr,
                                        () -> permissionOrder.stream()
                                                .collect(Collectors.toMap(key -> key, key -> false, (v1, v2) -> v1,
                                                        LinkedHashMap::new))
                                ))
                )
                .thenAccept(aggregatedResults -> {
                    final CheckPermissionsResponse response = CheckPermissionsResponse.of(aggregatedResults,
                            command.getDittoHeaders());
                    sender.tell(response, self);
                });
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
                        entry -> entry.getValue().policyId(),
                        Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> createResourcePermissions(entry.getValue().permissionCheck())
                        )
                ));
    }

    private CompletionStage<Map<String, Boolean>> checkPermissionsForPolicy(
            final Map.Entry<PolicyId, Map<String, ResourcePermissions>> aggregateEntry,
            final CheckPermissions command) {

        final CheckPolicyPermissions policyCommand = CheckPolicyPermissions.of(
                aggregateEntry.getKey(), aggregateEntry.getValue(), command.getDittoHeaders());

        return Patterns.ask(edgeCommandForwarder, policyCommand, defaultAskTimeout)
                .thenApply(result -> processPolicyCommandResult(result, aggregateEntry.getValue()))
                .exceptionally(ex -> {
                    throw DittoInternalErrorException.newBuilder()
                            .dittoHeaders(command.getDittoHeaders())
                            .cause(ex)
                            .message("Failed to check permissions for policy ID: " + aggregateEntry.getKey())
                            .build();
                });
    }

    private Map<String, Boolean> processPolicyCommandResult(final Object result,
            final Map<String, ResourcePermissions> resourcePermissions) {
        if (result instanceof CheckPolicyPermissionsResponse response) {
            return CheckPolicyPermissionsResponse.toMap(response.getPermissionsResults());
        } else {
            return resourcePermissions.keySet().stream()
                    .collect(Collectors.toMap(key -> key, key -> false));
        }
    }

    /**
     * Handles a failure during the permission checking process by logging the error and sending a
     * {@link DittoInternalErrorException} back to the sender.
     *
     * @param ex the exception that occurred during the permission check.
     * @param command the {@link CheckPermissions} command containing permission checks.
     */
    private void handleFailure(final Throwable ex, final CheckPermissions command) {
        logWarn(ex, command.getPermissionChecks(), command.getDittoHeaders());

        final DittoInternalErrorException errorResponse = DittoInternalErrorException.newBuilder()
                .dittoHeaders(command.getDittoHeaders())
                .cause(ex)
                .build();
        sender.tell(errorResponse, getSelf());
    }


    /**
     * Logs a warn message when a permission check fails for a given request.
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
    private void logWarn(final Throwable ex, final Map<String, ImmutablePermissionCheck> permissionChecks,
            final DittoHeaders headers) {
        logger.withCorrelationId(headers)
                .warn("Permission check failed for request: {}. Error: {}", permissionChecks, ex.getMessage());
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
    private ResourcePermissions createResourcePermissions(final ImmutablePermissionCheck check) {
        return ResourcePermissionFactory.newInstance(check.getResourceKey(), check.getHasPermissions());
    }

    private CompletionStage<PolicyId> retrievePolicyIdForEntity(final String entityId, DittoHeaders headers) {
        final SudoRetrieveThing retrieveThing = SudoRetrieveThing.of(
                ThingId.of(entityId),
                JsonFieldSelector.newInstance(Thing.JsonFields.POLICY_ID.getPointer()),
                headers);

        return Patterns.ask(edgeCommandForwarder, retrieveThing, defaultAskTimeout)
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        throw DittoInternalErrorException.newBuilder()
                                .dittoHeaders(headers)
                                .cause(throwable)
                                .message("Unexpected error while handling the entity: " + entityId)
                                .build();
                    }
                    return switch (result) {
                        case SudoRetrieveThingResponse thingResponse ->
                                thingResponse.getThing().getPolicyId().orElse(null);
                        case DittoRuntimeException dre -> throw dre;
                        default -> throw DittoInternalErrorException.newBuilder()
                                .dittoHeaders(headers)
                                .cause(new IllegalArgumentException(
                                        "Unexpected response type: " + result.getClass().getSimpleName()))
                                .build();
                    };
                });

    }
}
