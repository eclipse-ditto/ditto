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
package org.eclipse.ditto.internal.utils.health.cluster;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.json.Jsonifiable;

/**
 * Holds the status of a specific akka cluster role.
 */
@Immutable
public final class ClusterRoleStatus implements Jsonifiable<JsonObject> {

    /**
     * JSON array of reachable members.
     */
    public static final JsonFieldDefinition<JsonArray> JSON_KEY_REACHABLE =
            JsonFactory.newJsonArrayFieldDefinition("reachable");

    /**
     * JSON array of unreachable members.
     */
    public static final JsonFieldDefinition<JsonArray> JSON_KEY_UNREACHABLE =
            JsonFactory.newJsonArrayFieldDefinition("unreachable");

    /**
     * JSON value of the leaders address.
     */
    public static final JsonFieldDefinition<String> JSON_KEY_LEADER = JsonFactory.newStringFieldDefinition("leader");

    private final String role;
    private final Set<String> reachable;
    private final Set<String> unreachable;
    private final String leader;

    private ClusterRoleStatus(final String role,
            final Set<String> reachable,
            final Set<String> unreachable,
            final String leader) {

        this.role = role;
        this.reachable = Collections.unmodifiableSet(reachable);
        this.unreachable = Collections.unmodifiableSet(unreachable);
        this.leader = leader;
    }

    /**
     * Returns a new {@code ClusterRoleStatus} instance with the specified {@code leader}, {@code reachable} and {@code
     * unreachable} members.
     *
     * @param role the cluster role.
     * @param reachable a list of all reachable members.
     * @param unreachable a list of all unreachable members.
     * @param leader the leader.
     * @return the ClusterRoleStatus instance.
     */
    public static ClusterRoleStatus of(final String role, final Set<String> reachable, final Set<String> unreachable,
            final String leader) {
        return new ClusterRoleStatus(role, reachable, unreachable, leader);
    }

    /**
     * Returns the cluster role.
     *
     * @return the cluster role.
     */
    public String getRole() { return role; }

    /**
     * Returns a list of all reachable members for the given {@code role}.
     *
     * @return a list of all reachable members for the given {@code role}.
     */
    public Set<String> getReachable() {
        return reachable;
    }

    /**
     * Returns a list of all unreachable members for the given {@code role}.
     *
     * @return a list of all unreachable members for the given {@code role}.
     */
    public Set<String> getUnreachable() {
        return unreachable;
    }

    /**
     * Returns the optional leader for the given {@code role}.
     *
     * @return the leader or an empty optional.
     */
    public Optional<String> getLeader() {
        return Optional.ofNullable(leader);
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(JSON_KEY_REACHABLE,
                reachable.stream().map(JsonValue::of).collect(JsonCollectors.valuesToArray()));
        jsonObjectBuilder.set(JSON_KEY_UNREACHABLE,
                unreachable.stream().map(JsonValue::of).collect(JsonCollectors.valuesToArray()));
        jsonObjectBuilder.set(JSON_KEY_LEADER, getLeader().orElse("<unknown>"));

        return jsonObjectBuilder.build();
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClusterRoleStatus)) {
            return false;
        }
        final ClusterRoleStatus that = (ClusterRoleStatus) o;
        return Objects.equals(reachable, that.reachable) && Objects.equals(unreachable, that.unreachable) && Objects
                .equals(role, that.role) && Objects.equals(leader, that.leader);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, reachable, unreachable, leader);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + "role=" + role + ", reachable=" + reachable + ", unreachable="
                + unreachable + ", leader='" + leader + '\'' + ']';
    }

}
