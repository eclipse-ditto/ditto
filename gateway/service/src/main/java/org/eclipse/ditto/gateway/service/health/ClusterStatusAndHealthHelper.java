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
package org.eclipse.ditto.gateway.service.health;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.ditto.base.service.CompletableFutureUtils;
import org.eclipse.ditto.gateway.service.util.config.health.HealthCheckConfig;
import org.eclipse.ditto.internal.utils.akka.SimpleCommand;
import org.eclipse.ditto.internal.utils.akka.SimpleCommandResponse;
import org.eclipse.ditto.internal.utils.health.StatusDetailMessage;
import org.eclipse.ditto.internal.utils.health.StatusInfo;
import org.eclipse.ditto.internal.utils.health.cluster.ClusterRoleStatus;
import org.eclipse.ditto.internal.utils.health.cluster.ClusterStatus;
import org.eclipse.ditto.internal.utils.health.status.StatusSupplierActor;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

import akka.actor.ActorSystem;
import akka.pattern.Patterns;

/**
 * Helper for retrieving status and health information via the cluster.
 */
final class ClusterStatusAndHealthHelper {

    private static final String JSON_KEY_ROLES = "roles";
    private static final String JSON_KEY_EXPECTED_ROLES = "expected-roles";
    private static final String JSON_KEY_MISSING_ROLES = "missing-roles";
    private static final String JSON_KEY_EXTRA_ROLES = "extra-roles";

    private static final String STATUS_SUPPLIER_PATH = "/user/" + StatusSupplierActor.ACTOR_NAME;

    private final ActorSystem actorSystem;
    private final Supplier<ClusterStatus> clusterStateSupplier;
    private final HealthCheckConfig healthCheckConfig;

    private ClusterStatusAndHealthHelper(final ActorSystem actorSystem,
            final Supplier<ClusterStatus> clusterStateSupplier, final HealthCheckConfig healthCheckConfig) {

        this.actorSystem = checkNotNull(actorSystem, "ActorSystem");
        this.clusterStateSupplier = checkNotNull(clusterStateSupplier, "cluster state Supplier");
        this.healthCheckConfig = checkNotNull(healthCheckConfig, "HealthCheckConfig");
    }

    /**
     * Returns a new {@code ClusterStatusAndHealthHelper}.
     *
     * @param actorSystem the ActorSystem to use.
     * @param clusterStateSupplier the {@link ClusterStatus} supplier to use in order to find out the reachable cluster
     * nodes.
     * @param healthCheckConfig the config of for health checking.
     * @return the ClusterStatusAndHealthHelper.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static ClusterStatusAndHealthHelper of(final ActorSystem actorSystem,
            final Supplier<ClusterStatus> clusterStateSupplier, final HealthCheckConfig healthCheckConfig) {

        return new ClusterStatusAndHealthHelper(actorSystem, clusterStateSupplier, healthCheckConfig);
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

        final List<CompletableFuture<JsonObject>> statuses =
                clusterStateSupplier.get().getRoles().stream().map(clusterRoleStatus -> {
                    final String role = clusterRoleStatus.getRole();
                    final List<CompletableFuture<JsonObject>> statusList =
                            sendCommandToRemoteAddresses(actorSystem, command, clusterRoleStatus.getReachable(),
                                    (o, throwable, addressString) -> {
                                        if (throwable != null) {
                                            return JsonObject.newBuilder()
                                                    .set(addressString, throwable.getMessage()).build();
                                        } else {
                                            return ((SimpleCommandResponse) o).getPayload()
                                                    .map(JsonValue::asObject)
                                                    .orElse(JsonObject.newBuilder().build());
                                        }
                                    });

                    final CompletableFuture<JsonObject> roleStatusFuture =
                            CompletableFutureUtils.collectAsList(statusList).thenApply(statusObjects -> {
                                final JsonObjectBuilder roleStatusBuilder = JsonFactory.newObjectBuilder();
                                statusObjects.forEach(subStatusObj -> {
                                    String key = subStatusObj.getValue("instance")
                                            .map(JsonValue::asString)
                                            .orElse("?");
                                    final JsonObjectBuilder subBuilder = JsonObject.newBuilder();
                                    subStatusObj.forEach(subBuilder::set);
                                    if (roleStatusBuilder.build().contains(key)) {
                                        key = key + "_" + UUID.randomUUID().toString().hashCode();
                                    }
                                    roleStatusBuilder.set(key, subBuilder.build());
                                });
                                return roleStatusBuilder.build();
                            });

                    return roleStatusFuture.thenApply(rolesStatus ->
                            JsonObject.newBuilder().set(role, rolesStatus).build());

                }).toList();

        return CompletableFutureUtils.collectAsList(statuses).thenApply(roleStatuses -> {
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

        final HealthCheckConfig.ClusterRolesConfig clusterRolesConfig = healthCheckConfig.getClusterRolesConfig();
        if (clusterRolesConfig.isEnabled()) {
            final JsonArray expectedRoles = clusterRolesConfig.getExpectedClusterRoles()
                    .stream()
                    .sorted(Comparator.comparing(Function.identity()))
                    .map(JsonValue::of)
                    .collect(JsonCollectors.valuesToArray());

            final JsonArray actualRoles = clusterStatus.getRoles()
                    .stream()
                    .map(ClusterRoleStatus::getRole)
                    .sorted(Comparator.comparing(Function.identity()))
                    .map(JsonValue::of)
                    .collect(JsonCollectors.valuesToArray());

            final JsonArray missingRoles = expectedRoles.stream()
                    .filter(role -> !actualRoles.contains(role))
                    .collect(JsonCollectors.valuesToArray());

            final JsonArray extraRoles = actualRoles.stream()
                    .filter(role -> !expectedRoles.contains(role))
                    .collect(JsonCollectors.valuesToArray());

            final boolean allExpectedRolesPresent = missingRoles.isEmpty();
            final StatusInfo.Status status = allExpectedRolesPresent ? StatusInfo.Status.UP : StatusInfo.Status.DOWN;
            final StatusDetailMessage.Level level =
                    allExpectedRolesPresent ? StatusDetailMessage.Level.INFO : StatusDetailMessage.Level.ERROR;
            final StatusDetailMessage statusDetailMessage = StatusDetailMessage.of(level, JsonObject.newBuilder()
                    .set(JSON_KEY_MISSING_ROLES, missingRoles)
                    .set(JSON_KEY_EXTRA_ROLES, extraRoles)
                    .build());
            final StatusInfo statusInfo =
                    StatusInfo.fromStatus(status, Collections.singletonList(statusDetailMessage))
                            .label(JSON_KEY_EXPECTED_ROLES);

            healths.add(CompletableFuture.completedFuture(statusInfo));
        }

        final SimpleCommand command = SimpleCommand.of(StatusSupplierActor.SIMPLE_COMMAND_RETRIEVE_HEALTH, null, null);
        healths.addAll(clusterStatus.getRoles()
                .stream()
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
                                            StatusDetailMessage.Level.ERROR,
                                            "Role is not available on any remote address"));
                                } else {
                                    return StatusInfo.composite(remoteStatuses);
                                }
                            })
                            .thenApply(roleStatus -> roleStatus.label(roleLabel));
                })
                .toList());

        return CompletableFutureUtils.collectAsList(healths)
                .thenApply(statuses -> StatusInfo.composite(statuses).label(JSON_KEY_ROLES));
    }

    private <T> List<CompletableFuture<T>> sendCommandToRemoteAddresses(final ActorSystem actorSystem,
            final SimpleCommand command,
            final Collection<String> reachable,
            final RemoteResponseTransformer<Object, Throwable, T> responseTransformer) {

        final Duration healthCheckServiceTimeout = healthCheckConfig.getServiceTimeout();

        return reachable.stream()
                .map(addressString -> addressString + STATUS_SUPPLIER_PATH)
                .map(actorSystem::actorSelection)
                .map(selection -> {
                    final String addressString = selection.toSerializationFormat()
                            .substring(selection.toSerializationFormat().indexOf('@') + 1)
                            .replace(STATUS_SUPPLIER_PATH, "");
                    return Patterns.ask(selection, command, healthCheckServiceTimeout)
                            .handle((response, throwable) ->
                                    responseTransformer.apply(response, throwable, addressString))
                            .toCompletableFuture();
                })
                .toList();
    }

    @FunctionalInterface
    private interface RemoteResponseTransformer<T, U extends Throwable, R> {

        R apply(T responseToBeMapped, U throwable, String addressString);

    }

}
