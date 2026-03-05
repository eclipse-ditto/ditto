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
package org.eclipse.ditto.edge.service.dispatching.checkpermissions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSelection;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.ReceiveTimeout;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.pattern.Patterns;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.messages.model.signals.commands.MessageCommand;
import org.eclipse.ditto.policies.api.commands.sudo.CheckPolicyPermissions;
import org.eclipse.ditto.policies.api.commands.sudo.CheckPolicyPermissionsResponse;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.ResourcePermissionFactory;
import org.eclipse.ditto.policies.model.ResourcePermissions;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.checkpermissions.CheckPermissions;
import org.eclipse.ditto.policies.model.signals.commands.checkpermissions.CheckPermissionsResponse;
import org.eclipse.ditto.policies.model.signals.commands.checkpermissions.ImmutablePermissionCheck;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;

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
public final class CheckPermissionsActor extends AbstractActor {

    private static final Set<String> SUPPORTED_RESOURCE_TYPES = Set.of(
            PolicyCommand.RESOURCE_TYPE,
            ThingCommand.RESOURCE_TYPE,
            MessageCommand.RESOURCE_TYPE
    );

    private final DittoLogger logger = DittoLoggerFactory.getLogger(CheckPermissionsActor.class);
    private final ActorSelection edgeCommandForwarder;
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
    private CheckPermissionsActor(final ActorSelection edgeCommandForwarder, final ActorRef sender,
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
    public static Props props(final ActorSelection edgeCommandForwarder, final ActorRef sender,
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
        for (final Map.Entry<String, ImmutablePermissionCheck> entry : command.getPermissionChecks().entrySet()) {
            try {
                validatePermissionCheck(entry.getKey(), entry.getValue());
            } catch (final Exception e) {
                final DittoRuntimeException dre = DittoRuntimeException.asDittoRuntimeException(e,
                        cause -> DittoInternalErrorException.newBuilder()
                                .dittoHeaders(command.getDittoHeaders())
                                .cause(cause)
                                .build());
                sender.tell(dre.setDittoHeaders(command.getDittoHeaders()), getSelf());
                stopSelf();
                return;
            }
        }

        final List<String> permissionOrder = new ArrayList<>(command.getPermissionChecks().keySet());
        final Map<String, Map<String, ImmutablePermissionCheck>> groupedByEntityId =
                groupByEntityAndPolicyResource(command);

        final List<Map.Entry<String, CompletableFuture<Map<String, PermissionCheckWrapper>>>> retrievalFutures =
                groupedByEntityId.entrySet().stream()
                        .map(entry -> Map.entry(entry.getKey(),
                                retrieveOrSetPolicyId(entry.getKey(), entry.getValue(), command)))
                        .toList();

        final CompletableFuture<Map<String, Map<String, PermissionCheckWrapper>>> allPolicyRetrievals =
                CompletableFuture.allOf(retrievalFutures.stream()
                                .map(Map.Entry::getValue)
                                .toArray(CompletableFuture[]::new))
                        .thenApply(v -> {
                            final Map<String, Map<String, PermissionCheckWrapper>> resultMap = new LinkedHashMap<>();
                            retrievalFutures.forEach(e -> resultMap.put(e.getKey(), e.getValue().join()));
                            return resultMap;
                        });

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

    private static void validatePermissionCheck(final String checkName,
            final ImmutablePermissionCheck permissionCheck) {

        final ResourceKey resourceKey = permissionCheck.getResource();

        final String resourceType = resourceKey.getResourceType();
        if (!SUPPORTED_RESOURCE_TYPES.contains(resourceType)) {
            throw new DittoJsonException(JsonParseException.newBuilder()
                    .message(String.format(
                            "Unsupported resource type '%s' in check '%s'. Supported resource types are: %s",
                            resourceType, checkName, SUPPORTED_RESOURCE_TYPES))
                    .build());
        }

        if (permissionCheck.isPolicyResource()) {
            PolicyId.of(permissionCheck.getEntityId());
        } else {
            ThingId.of(permissionCheck.getEntityId());
        }
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
                .thenApply(policyId -> {
                    if (null == policyId) {
                        logger.withCorrelationId(command.getDittoHeaders())
                                .debug("No policy ID could be resolved for entity '{}', all its permission checks " +
                                                "will be false.", entityId);
                        return Map.<String, PermissionCheckWrapper>of();
                    }
                    return permissionCheckMap.entrySet().stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> new PermissionCheckWrapper(entry.getValue(), policyId)
                            ));
                })
                .exceptionally(ex -> {
                    logger.withCorrelationId(command.getDittoHeaders())
                            .debug("Entity '{}' not accessible, all its permission checks will be false: {}",
                                    entityId, ex.getMessage());
                    return Map.of();
                })
                .toCompletableFuture();
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
     *
     * @param check the {@link ImmutablePermissionCheck} containing the permission data.
     * @return the constructed {@link ResourcePermissions}.
     */
    private ResourcePermissions createResourcePermissions(final ImmutablePermissionCheck check) {
        return ResourcePermissionFactory.newInstance(
                check.getResource(), check.getHasPermissions());
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
                                thingResponse.getThing().getPolicyId().orElseThrow(() ->
                                        DittoInternalErrorException.newBuilder()
                                                .dittoHeaders(headers)
                                                .message("Retrieved thing did not contain a policy ID: " + entityId)
                                                .build());
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
