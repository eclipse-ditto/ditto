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
package org.eclipse.ditto.services.gateway.health;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.services.gateway.starter.service.util.ConfigKeys;
import org.eclipse.ditto.services.utils.akka.SimpleCommand;
import org.eclipse.ditto.services.utils.akka.SimpleCommandResponse;
import org.eclipse.ditto.services.utils.health.StatusDetailMessage;
import org.eclipse.ditto.services.utils.health.StatusInfo;
import org.eclipse.ditto.services.utils.health.cluster.ClusterRoleStatus;
import org.eclipse.ditto.services.utils.health.cluster.ClusterStatus;
import org.eclipse.ditto.services.utils.health.status.StatusSupplierActor;

import akka.actor.ActorSystem;
import akka.pattern.PatternsCS;
import akka.util.Timeout;

/**
 * Helper for retrieving status and health information via the cluster.
 */
final class ClusterStatusAndHealthHelper {

    private static final String JSON_KEY_ROLES = "roles";
    private static final String JSON_KEY_MISSING_ROLES = "missing-roles";
    private static final String JSON_KEY_EXPECTED_ROLES = "expected-roles";

    private static final String STATUS_SUPPLIER_PATH = "/user/" + StatusSupplierActor.ACTOR_NAME;

    protected final ActorSystem actorSystem;
    protected final Supplier<ClusterStatus> clusterStateSupplier;

    private ClusterStatusAndHealthHelper(final ActorSystem actorSystem,
            final Supplier<ClusterStatus> clusterStateSupplier) {
        this.actorSystem = actorSystem;
        this.clusterStateSupplier = clusterStateSupplier;
    }

    /**
     * Returns a new {@link ClusterStatusAndHealthHelper}.
     *
     * @param actorSystem the ActorSystem to use.
     * @param clusterStateSupplier the {@link ClusterStatus} supplier to use in order to find out the reachable cluster
     * nodes.
     * @return the {@link ClusterStatusAndHealthHelper}.
     */
    static ClusterStatusAndHealthHelper of(final ActorSystem actorSystem,
            final Supplier<ClusterStatus> clusterStateSupplier) {
        requireNonNull(actorSystem);
        requireNonNull(clusterStateSupplier);

        return new ClusterStatusAndHealthHelper(actorSystem, clusterStateSupplier);
    }

    /**
     * Retrieves the static "status" information from each reachable cluster member in the cluster grouped by the
     * cluster {@code role} of the services and containing the address in the cluster as additional Json key nodes. The
     * result is wrapped as a JSON object with a single field with key {@link #JSON_KEY_ROLES}.
     *
     * @return a CompletionStage of a list of all "status" JsonObjects containing the cluster {@code role}s as keys.
     */
    CompletionStage<JsonObject> retrieveOverallRolesStatus() {
        final SimpleCommand command = SimpleCommand.of(StatusSupplierActor.SIMPLE_COMMAND_RETRIEVE_STATUS, null, null);

        final List<CompletableFuture<JsonObject>> statuses = clusterStateSupplier.get().getRoles().stream()
                .map(clusterRoleStatus -> {
                            final String role = clusterRoleStatus.getRole();
                            final List<CompletableFuture<JsonObject>> statusList =
                                    sendCommandToRemoteAddresses(
                                            actorSystem, command, clusterRoleStatus.getReachable(),
                                            (o, throwable, addressString) ->
                                            {
                                                if (throwable != null) {
                                                    return JsonObject.newBuilder()
                                                            .set(addressString, throwable.getMessage()).build();
                                                } else {
                                                    return ((SimpleCommandResponse) o).getPayload()
                                                            .map(JsonValue::asObject)
                                                            .orElse(JsonObject.newBuilder().build());
                                                }
                                            }
                                    );

                    final CompletableFuture<JsonObject> roleStatusFuture = CompletableFutureUtils.collectAsList(statusList)
                                    .thenApply(statusObjects -> {
                                        final JsonObjectBuilder roleStatusBuilder = JsonFactory.newObjectBuilder();
                                        statusObjects.forEach(
                                                subStatusObj -> subStatusObj.forEach(roleStatusBuilder::set));
                                        return roleStatusBuilder.build();
                                    });

                            return roleStatusFuture.thenApply(rolesStatus ->
                                    JsonObject.newBuilder().set(role, rolesStatus).build());
                        }
                )
                .collect(Collectors.toList());

        return CompletableFutureUtils.collectAsList(statuses)
                .thenApply(roleStatuses -> {
                    final JsonObjectBuilder rolesStatusBuilder = JsonFactory.newObjectBuilder();
                    roleStatuses.forEach(subStatusObj -> subStatusObj.forEach(rolesStatusBuilder::set));

                    final JsonObjectBuilder rolesStatusWrapperBuilder = JsonFactory.newObjectBuilder();
                    rolesStatusWrapperBuilder.set(JSON_KEY_ROLES, rolesStatusBuilder.build());

                    return rolesStatusWrapperBuilder.build();
                });
    }

    /**
     * Retrieves the "health" information from each reachable cluster member in the ditto-cluster grouped by the cluster
     * {@code role} of the services and containing the address in the cluster as additional Json key.
     *
     * @return a CompletionStage of a list of all "health" JsonObjects containing the cluster {@code role}s as keys.
     */
    CompletionStage<StatusInfo> retrieveOverallRolesHealth() {
        final ClusterStatus clusterStatus = clusterStateSupplier.get();
        final List<CompletableFuture<StatusInfo>> healths = new ArrayList<>();

        final boolean healthClusterRolesEnabled =
                actorSystem.settings().config().getBoolean(ConfigKeys.HEALTH_CHECK_CLUSTER_ROLES_ENABLED);

        if (healthClusterRolesEnabled) {
            final List<String> healthClusterRolesExpected =
                    actorSystem.settings().config().getStringList(ConfigKeys.HEALTH_CHECK_CLUSTER_ROLES_EXPECTED);

            healths.add(CompletableFuture.supplyAsync(() -> {
                final List<String> existingClusterRoles = clusterStatus
                        .getRoles()
                        .stream()
                        .map(ClusterRoleStatus::getRole)
                        .collect(Collectors.toList());
                healthClusterRolesExpected.removeAll(existingClusterRoles);
                final boolean expectedRolesAreAvailable = healthClusterRolesExpected.isEmpty();
                if (expectedRolesAreAvailable) {
                    return StatusInfo.fromStatus(StatusInfo.Status.UP);
                } else {
                    final JsonObject missingRolesJson = JsonObject.newBuilder()
                            .set(JSON_KEY_MISSING_ROLES, healthClusterRolesExpected.stream()
                                    .map(JsonValue::of)
                                    .collect(JsonCollectors.valuesToArray())
                            )
                            .build();
                    final StatusDetailMessage missingRolesMessage =
                            StatusDetailMessage.of(StatusDetailMessage.Level.ERROR, missingRolesJson);
                    return StatusInfo.fromDetail(missingRolesMessage).label(JSON_KEY_EXPECTED_ROLES);
                }
            }));
        }

        final SimpleCommand command = SimpleCommand.of(StatusSupplierActor.SIMPLE_COMMAND_RETRIEVE_HEALTH, null, null);
        healths.addAll(clusterStatus.getRoles().stream()
                .map(clusterRoleStatus -> {
                            final String roleLabel = clusterRoleStatus.getRole();

                            final Set<String> reachable = clusterRoleStatus.getReachable();
                            final List<CompletableFuture<StatusInfo>> remoteStatusFutures =
                                    sendCommandToRemoteAddresses(
                                            actorSystem, command, reachable,
                                            (o, throwable, addressString) -> {
                                                final StatusInfo statusInfo;
                                                if (throwable != null) {
                                                    statusInfo =
                                                            StatusInfo.fromDetail(StatusDetailMessage.of(
                                                                    StatusDetailMessage.Level.ERROR, "Exception: " +
                                                                            throwable.getMessage()));
                                                } else {
                                                    statusInfo = (StatusInfo) o;
                                                }
                                                return statusInfo.label(addressString);
                                            });

                    return CompletableFutureUtils.collectAsList(remoteStatusFutures)
                                    .thenApply(remoteStatuses -> {
                                        if (reachable.isEmpty()) {
                                            return StatusInfo.fromDetail(StatusDetailMessage.of(
                                                    StatusDetailMessage.Level.ERROR, "Role is not available on any " +
                                                            "remote address"));
                                        } else {
                                            return StatusInfo.composite(remoteStatuses);
                                        }
                                    })
                                    .thenApply(roleStatus -> roleStatus.label(roleLabel));
                        }
                )
                .collect(Collectors.toList()));

        return CompletableFutureUtils.collectAsList(healths)
                .thenApply(statuses -> StatusInfo.composite(statuses).label(JSON_KEY_ROLES));
    }

    private static <T> List<CompletableFuture<T>> sendCommandToRemoteAddresses(final ActorSystem actorSystem,
            final SimpleCommand command,
            final Set<String> reachable,
            final RemoteResponseTransformer<Object, Throwable, T> responseTransformer) {

        final Duration healthCheckServiceTimeout =
                actorSystem.settings().config().getDuration(ConfigKeys.HEALTH_CHECK_SERVICE_TIMEOUT);
        return reachable.stream()
                .map(addressString -> addressString + STATUS_SUPPLIER_PATH)
                .map(actorSystem::actorSelection)
                .map(selection -> {
                    final String addressString = selection.toSerializationFormat()
                            .substring(selection.toSerializationFormat().indexOf('@') + 1)
                            .replace(STATUS_SUPPLIER_PATH, "");
                    return PatternsCS.ask(selection, command,
                            Timeout.apply(healthCheckServiceTimeout.getSeconds(), TimeUnit.SECONDS))
                            .handle((response, throwable) ->
                                    responseTransformer.apply(response, throwable, addressString))
                            .toCompletableFuture();
                })
                .collect(Collectors.toList());
    }

    @FunctionalInterface
    private interface RemoteResponseTransformer<T, U extends Throwable, R> {

        R apply(T responseToBeMapped, U throwable, String addressString);

    }
}
