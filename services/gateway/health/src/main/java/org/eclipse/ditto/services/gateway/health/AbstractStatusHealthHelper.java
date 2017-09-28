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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.services.gateway.starter.service.util.ConfigKeys;
import org.eclipse.ditto.services.utils.akka.SimpleCommand;
import org.eclipse.ditto.services.utils.akka.SimpleCommandResponse;
import org.eclipse.ditto.services.utils.health.HealthStatus;
import org.eclipse.ditto.services.utils.health.cluster.ClusterRoleStatus;
import org.eclipse.ditto.services.utils.health.cluster.ClusterStatus;
import org.eclipse.ditto.services.utils.health.status.StatusSupplierActor;

import akka.actor.ActorSystem;
import akka.pattern.PatternsCS;
import akka.util.Timeout;

/**
 * Abstract implementation of a {@link StatusHealthHelper}.
 */
public abstract class AbstractStatusHealthHelper implements StatusHealthHelper {

    private static final String JSON_KEY_MISSING_ROLES = "missing-roles";
    private static final String JSON_KEY_EXPECTED_ROLES = "expected-roles";

    private static final String STATUS_SUPPLIER_PATH = "/user/" + StatusSupplierActor.ACTOR_NAME;

    protected final ActorSystem actorSystem;
    protected final Supplier<ClusterStatus> clusterStateSupplier;

    protected AbstractStatusHealthHelper(final ActorSystem actorSystem,
            final Supplier<ClusterStatus> clusterStateSupplier) {
        this.actorSystem = actorSystem;
        this.clusterStateSupplier = clusterStateSupplier;
    }

    @Override
    public CompletionStage<List<JsonObject>> retrieveOverallRolesStatus() {
        final SimpleCommand command = SimpleCommand.of(StatusSupplierActor.SIMPLE_COMMAND_RETRIEVE_STATUS, null, null);

        final List<CompletableFuture<JsonObject>> statuses = clusterStateSupplier.get().getRoles().stream()
                .map(clusterRoleStatus -> {
                            final String role = clusterRoleStatus.getRole();
                            final List<CompletableFuture<JsonObject>> statusList =
                                    sendCommandToRemoteAddresses(
                                            actorSystem, command, clusterRoleStatus.getReachable(), simpleResponse ->
                                                    ((SimpleCommandResponse) simpleResponse).getPayload()
                                                            .map(JsonValue::asObject)
                                                            .orElse(JsonObject.newBuilder()
                                                                    .build())
                                    );

                            final CompletableFuture<JsonObject> roleStatusFuture = sequence(statusList)
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

        return sequence(statuses);
    }

    @Override
    public boolean checkIfAllSubStatusAreUp(final JsonObject subSystemsStatus) {
        return subSystemsStatus.stream()
                .map(JsonField::getValue)
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .allMatch(statusObject -> statusObject.getValue(HealthStatus.JSON_KEY_STATUS)
                        .filter(JsonValue::isString)
                        .map(JsonValue::asString)
                        .map(statusStr -> statusStr.equalsIgnoreCase(HealthStatus.Status.UP.toString()) || statusStr
                                .equalsIgnoreCase(HealthStatus.Status.UNKNOWN.toString()))
                        .orElse(false));
    }

    /**
     * Retrieves the "health" information from each reachable cluster member in the ditto-cluster grouped by the cluster
     * {@code role} of the services and containing the address in the cluster as additional Json key.
     *
     * @param actorSystem the ActorSystem to use
     * @param clusterStateSupplier the {@link ClusterStatus} supplier to use in order to find out the reachable cluster
     * nodes
     * @return a CompletionStage of a list of all "health" JsonObjects containing the cluster {@code role}s as keys
     */
    protected CompletionStage<List<JsonObject>> retrieveOverallRolesHealth(final ActorSystem actorSystem,
            final Supplier<ClusterStatus> clusterStateSupplier) {
        final ClusterStatus clusterStatus = clusterStateSupplier.get();
        final List<CompletableFuture<JsonObject>> healths = new ArrayList<>();

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
                final boolean expectedRolesStatus = healthClusterRolesExpected.isEmpty();
                final JsonObject expectedRolesStatusJson = JsonObject.newBuilder()
                        .set(HealthStatus.JSON_KEY_STATUS, expectedRolesStatus ?
                                HealthStatus.Status.UP.toString() : HealthStatus.Status.DOWN.toString())
                        .set(JSON_KEY_MISSING_ROLES, healthClusterRolesExpected.stream()
                                .map(JsonValue::of)
                                .collect(JsonCollectors.valuesToArray())
                        )
                        .build();
                return JsonObject.newBuilder().set(JSON_KEY_EXPECTED_ROLES, expectedRolesStatusJson).build();
            }));
        }

        final SimpleCommand command = SimpleCommand.of(StatusSupplierActor.SIMPLE_COMMAND_RETRIEVE_HEALTH, null, null);
        healths.addAll(clusterStatus.getRoles().stream()
                .map(clusterRoleStatus -> {
                            final String role = clusterRoleStatus.getRole();

                            final Set<String> reachable = clusterRoleStatus.getReachable();
                            final List<CompletableFuture<JsonObject>> healthList = sendCommandToRemoteAddresses(
                                    actorSystem, command, reachable,
                                    health -> ((Jsonifiable<JsonObject>) health).toJson());

                            final CompletableFuture<JsonObject> roleHealthFuture = sequence(healthList)
                                    .thenApply(statusObjects -> {
                                        final JsonObjectBuilder roleStatusBuilder = JsonFactory.newObjectBuilder();
                                        statusObjects.forEach(subStatusObj -> subStatusObj.forEach(roleStatusBuilder::set));
                                        final JsonObject roleStatus = roleStatusBuilder.build();
                                        final String roleHealth =
                                                !reachable.isEmpty() && checkIfAllSubStatusAreUp(roleStatus) ?
                                                        HealthStatus.Status.UP.toString() :
                                                        HealthStatus.Status.DOWN.toString();

                                        return JsonObject.newBuilder()
                                                .set(HealthStatus.JSON_KEY_STATUS, roleHealth)
                                                .setAll(roleStatus)
                                                .build();
                                    });

                            return roleHealthFuture.thenApply(rolesStatus -> JsonObject.newBuilder()
                                    .set(role, rolesStatus).build());
                        }
                )
                .collect(Collectors.toList()));

        return sequence(healths);
    }

    protected JsonObject combineHealth(final List<JsonObject> statusObjects) {
        final JsonObjectBuilder rolesHealthBuilder = JsonFactory.newObjectBuilder();
        statusObjects.forEach(subStatusObj -> subStatusObj.forEach(rolesHealthBuilder::set));
        final JsonObject allHealth = rolesHealthBuilder.build();
        final boolean allServicesUp = checkIfAllSubStatusAreUp(allHealth);
        return JsonObject.newBuilder()
                .set(JSON_KEY_ROLES, JsonFactory.newObjectBuilder()
                        .set(HealthStatus.JSON_KEY_STATUS,
                                allServicesUp ? HealthStatus.Status.UP.toString() :
                                        HealthStatus.Status.DOWN.toString())
                        .setAll(allHealth).build()
                ).build();
    }

    protected JsonObject setOverallHealth(final JsonObject combinedHealth) {
        final boolean allUp = combinedHealth.stream()
                .map(field -> Optional.of(field.getValue())
                        .filter(JsonValue::isObject)
                        .map(JsonValue::asObject)
                        .flatMap(obj -> obj.getValue(HealthStatus.JSON_KEY_STATUS))
                )
                .allMatch(statusValue -> statusValue
                        .filter(JsonValue::isString)
                        .map(JsonValue::asString)
                        .filter(status -> HealthStatus.Status.UP.toString().equals(status) ||
                                HealthStatus.Status.UNKNOWN.toString().equals(status))
                        .isPresent());
        return JsonObject.newBuilder()
                .set(HealthStatus.JSON_KEY_STATUS,
                        allUp ? HealthStatus.Status.UP.toString() : HealthStatus.Status.DOWN.toString())
                .setAll(combinedHealth)
                .build();
    }

    protected List<CompletableFuture<JsonObject>> sendCommandToRemoteAddresses(final ActorSystem actorSystem,
            final SimpleCommand command, final Set<String> reachable,
            final Function<Object, JsonObject> responseFunction) {
        final Duration healthCheckServiceTimeout =
                actorSystem.settings().config().getDuration(ConfigKeys.HEALTH_CHECK_SERVICE_TIMEOUT);
        return reachable.stream()
                .map(addressString -> addressString + STATUS_SUPPLIER_PATH)
                .map(actorSystem::actorSelection)
                .map(selection -> {
                    final String addressString = selection.toSerializationFormat()
                            .substring(selection.toSerializationFormat()
                                    .indexOf("@") + 1)
                            .replace(STATUS_SUPPLIER_PATH, "");
                    return PatternsCS.ask(selection, command,
                            Timeout.apply(healthCheckServiceTimeout.getSeconds(), TimeUnit.SECONDS))
                            .thenApply(responseFunction)
                            .thenApply(response -> JsonObject.newBuilder()
                                    .set(addressString, response)
                                    .build()
                            )
                            .exceptionally(throwable -> JsonObject.newBuilder()
                                    .set(addressString, throwable.getMessage()).build())
                            .toCompletableFuture();
                })
                .collect(Collectors.toList());
    }

    protected <T> CompletableFuture<List<T>> sequence(final List<CompletableFuture<T>> com) {
        return CompletableFuture.allOf(com.toArray(new CompletableFuture[com.size()]))
                .thenApply(v -> com.stream().map(CompletableFuture::join).collect(Collectors.toList()));
    }
}
