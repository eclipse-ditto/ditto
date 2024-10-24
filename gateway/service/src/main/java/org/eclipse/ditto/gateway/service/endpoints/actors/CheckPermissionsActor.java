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
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.pattern.PatternsCS;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.endpoints.routes.checkpermissions.CheckPermissions;
import org.eclipse.ditto.gateway.service.endpoints.routes.checkpermissions.CheckPermissionsResponse;
import org.eclipse.ditto.gateway.service.endpoints.routes.checkpermissions.ImmutablePermissionCheck;
import org.eclipse.ditto.gateway.service.endpoints.routes.checkpermissions.PermissionCheckWrapper;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourcePermissionFactory;
import org.eclipse.ditto.policies.model.ResourcePermissions;
import org.eclipse.ditto.policies.api.commands.sudo.PolicyCheckPermissions;
import org.eclipse.ditto.policies.api.commands.sudo.PolicyCheckPermissionsResponse;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.ThingId;

final class CheckPermissionsActor extends AbstractActor {

    private final ThreadSafeDittoLogger logger = DittoLoggerFactory.getThreadSafeLogger(getClass());
    private final ActorRef edgeCommandForwarder;
    private final ActorRef sender;
    private final Duration defaultAskTimeout;

    public static final String ACTOR_NAME = "checkPermissionsActor";

    public CheckPermissionsActor(final ActorRef edgeCommandForwarder, final ActorRef sender,
            final Duration defaultTimeout) {
        this.edgeCommandForwarder = edgeCommandForwarder;
        this.sender = sender;
        this.defaultAskTimeout = defaultTimeout;
    }

    public static Props props(ActorRef edgeCommandForwarder, ActorRef sender, Duration defaultTimeout) {
        return Props.create(CheckPermissionsActor.class, edgeCommandForwarder, sender, defaultTimeout);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(CheckPermissions.class, this::handleCheckPermissions)
                .matchAny(msg -> logger.warn("Unknown message: <{}>", msg))
                .build();
    }

    private void handleCheckPermissions(CheckPermissions command) {
        // Initialize the results map with 'false' for each permission check
        Map<String, Boolean> permissionResults = initializePermissionResults(command);

        // Group permission checks by entity ID
        Map<String, Map<String, PermissionCheckWrapper>> groupedByEntityId = groupByEntityAndPolicyResource(command);

        CompletableFuture<Void> allPolicyRetrievals = CompletableFuture.allOf(
                groupedByEntityId.keySet().stream()
                        .map(entityId -> retrieveOrSetPolicyId(entityId, groupedByEntityId.get(entityId), command))
                        .toArray(CompletableFuture[]::new)
        );

        allPolicyRetrievals.thenRun(
                        () -> handlePolicyRetrievalCompletion(command, permissionResults, groupedByEntityId))
                .exceptionally(ex -> handleFailure(ex, command, permissionResults));
    }

    private Map<String, Boolean> initializePermissionResults(CheckPermissions command) {
        return command.getPermissionChecks().keySet().stream()
                .collect(Collectors.toMap(
                        check -> check,
                        check -> false,
                        (oldValue, newValue) -> oldValue,
                        LinkedHashMap::new));
    }

    private CompletableFuture<Void> retrieveOrSetPolicyId(String entityId, Map<String, PermissionCheckWrapper> wrappers,
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

    private void handlePolicyRetrievalCompletion(CheckPermissions command, Map<String, Boolean> permissionResults,
            Map<String, Map<String, PermissionCheckWrapper>> groupedByEntityId) {
        // Aggregate permissions by PolicyId and Resource
        Map<PolicyId, Map<String, ResourcePermissions>> aggregatedPermissions =
                groupPermissionsByPolicyId(groupedByEntityId);

        List<CompletionStage<Void>> permissionCheckFutures = aggregatedPermissions.entrySet().stream()
                .map(aggregateEntry -> checkPermissionsForPolicy(aggregateEntry, command, permissionResults))
                .collect(Collectors.toList());

        CompletableFuture.allOf(permissionCheckFutures.toArray(new CompletableFuture[0])).thenRun(() -> {
            CheckPermissionsResponse response =
                    CheckPermissionsResponse.of(permissionResults, command.getDittoHeaders());
            sender.tell(response, getSelf());
        }).exceptionally(ex -> handleFailure(ex, command, permissionResults));
    }

    private Map<PolicyId, Map<String, ResourcePermissions>> groupPermissionsByPolicyId(
            Map<String, Map<String, PermissionCheckWrapper>> groupedByEntityId) {
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

    private CompletionStage<Void> checkPermissionsForPolicy(
            Map.Entry<PolicyId, Map<String, ResourcePermissions>> aggregateEntry,
            CheckPermissions command,
            Map<String, Boolean> permissionResults) {
        PolicyCheckPermissions policyCommand = PolicyCheckPermissions.of(
                aggregateEntry.getKey(), aggregateEntry.getValue(), command.getDittoHeaders());

        return Patterns.ask(edgeCommandForwarder, policyCommand, defaultAskTimeout)
                .thenAccept(result -> processPolicyCommandResult(result, aggregateEntry.getValue(), permissionResults))
                .exceptionally(ex -> {
                    logError(ex, aggregateEntry.getKey().toString(), aggregateEntry.getValue().toString());
                    return null;
                });
    }


    private Void handleFailure(Throwable ex, CheckPermissions command, Map<String, Boolean> permissionResults) {
        logError(ex, command.getPermissionChecks());
        CheckPermissionsResponse response = CheckPermissionsResponse.of(permissionResults, command.getDittoHeaders());
        sender.tell(response, getSelf());
        return null;
    }

    private Map<String, Map<String, PermissionCheckWrapper>> groupByEntityAndPolicyResource(CheckPermissions command) {
        return command.getPermissionChecks().entrySet().stream()
                .collect(Collectors.groupingBy(
                        entry -> entry.getValue().getEntityId(),
                        Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> new PermissionCheckWrapper(entry.getValue())
                        )
                ));
    }

    private void processPolicyCommandResult(Object result,
            Map<String, ResourcePermissions> resourcePermissions,
            Map<String, Boolean> permissionResults) {
        if (result instanceof PolicyCheckPermissionsResponse response) {
            permissionResults.putAll(PolicyCheckPermissionsResponse.toMap(response.getPermissionsResults()));
        } else {
            setPermissionCheckFailure(resourcePermissions, permissionResults);
        }
    }

    private void setPermissionCheckFailure(Map<String, ResourcePermissions> resourcePermissions,
            Map<String, Boolean> permissionResults) {
        resourcePermissions.forEach((key, value) -> permissionResults.put(key, false));
    }

    private void logError(Throwable ex, String entityId, String resource) {
        logger.error("Permission check failed for entity: {} and resource: {}. Error: {}", entityId, resource,
                ex.getMessage());
    }

    private void logError(Throwable ex, LinkedHashMap<String, ImmutablePermissionCheck> permissionChecks) {
        logger.error("Permission check failed for request: {}. Error: {}", permissionChecks, ex.getMessage());
    }

    private ResourcePermissions createResourcePermissions(ImmutablePermissionCheck check) {
        String resourceType = check.getResource().split(":")[0];
        String resourcePath = check.getResource();
        List<String> permissions = check.getHasPermissions();

        return ResourcePermissionFactory.newInstance(resourceType, resourcePath, permissions);
    }

    private CompletionStage<PolicyId> retrievePolicyIdForEntity(String entityId, boolean isPolicyResource,
            DittoHeaders headers) {
        if (isPolicyResource) {
            return CompletableFuture.completedFuture(PolicyId.of(entityId));
        } else {
            SudoRetrieveThing retrieveThing =
                    SudoRetrieveThing.of(ThingId.of(entityId), JsonFieldSelector.newInstance("policyId"), headers);
            return PatternsCS.ask(edgeCommandForwarder, retrieveThing, defaultAskTimeout)
                    .thenApply(result -> {
                        if (result instanceof SudoRetrieveThingResponse thingResponse) {
                            return thingResponse.getThing().getPolicyId().orElse(null);
                        }
                        return null;
                    });
        }
    }
}
