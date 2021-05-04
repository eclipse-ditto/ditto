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
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.Jsonifiable;

/**
 * Holds the status of the akka cluster.
 */
@Immutable
public final class ClusterStatus implements Jsonifiable<JsonObject> {

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
     * JSON array of all addresses of members which have seen this cluster state.
     */
    public static final JsonFieldDefinition<JsonArray> JSON_KEY_SEEN_BY =
            JsonFactory.newJsonArrayFieldDefinition("seen-by");

    /**
     * JSON value of the leaders address.
     */
    public static final JsonFieldDefinition<String> JSON_KEY_LEADER = JsonFactory.newStringFieldDefinition("leader");

    /**
     * JSON array with all roles this cluster node has.
     */
    public static final JsonFieldDefinition<JsonArray> JSON_KEY_OWN_ROLES =
            JsonFactory.newJsonArrayFieldDefinition("own-roles");

    /**
     * JSON object with the cluster roles and their status.
     */
    public static final JsonFieldDefinition<JsonObject> JSON_KEY_ROLES =
            JsonFactory.newJsonObjectFieldDefinition("roles");

    private final Set<String> reachable;
    private final Set<String> unreachable;
    private final Set<String> seenBy;
    private final String leader;
    private final Set<String> ownRoles;
    private final Set<ClusterRoleStatus> roles;

    private ClusterStatus(final Set<String> reachable,
            final Set<String> unreachable,
            final Set<String> seenBy,
            final String leader,
            final Set<String> ownRoles,
            final Set<ClusterRoleStatus> roles) {

        this.reachable = Collections.unmodifiableSet(reachable);
        this.unreachable = Collections.unmodifiableSet(unreachable);
        this.seenBy = Collections.unmodifiableSet(seenBy);
        this.leader = leader;
        this.ownRoles = Collections.unmodifiableSet(ownRoles);
        this.roles = Collections.unmodifiableSet(roles);
    }

    /**
     * Returns a new {@code ClusterStatus} instance with the given parameters.
     *
     * @param reachable a list of all reachable members.
     * @param unreachable a list of all unreachable members.
     * @param seenBy a list of all members that have seen this cluster state.
     * @param leader the leader.
     * @param ownRoles a list of all roles this member has.
     * @param roles the status of each cluster roles members.
     * @return the ClusterState instance.
     */
    public static ClusterStatus of(final Set<String> reachable,
            final Set<String> unreachable,
            final Set<String> seenBy,
            final String leader,
            final Set<String> ownRoles,
            final Set<ClusterRoleStatus> roles) {

        return new ClusterStatus(reachable, unreachable, seenBy, leader, ownRoles, roles);
    }

    /**
     * Returns a list of all reachable members.
     *
     * @return a list of all reachable members.
     */
    public Set<String> getReachable() {
        return reachable;
    }

    /**
     * Returns a list of all unreachable members.
     *
     * @return a list of all unreachable members.
     */
    public Set<String> getUnreachable() {
        return unreachable;
    }

    /**
     * Returns a list of all members that have seen this cluster state.
     *
     * @return a list of all members that have seen this cluster state.
     */
    public Set<String> getSeenBy() {
        return seenBy;
    }

    /**
     * Returns the optional leader.
     *
     * @return the leader or an empty optional.
     */
    public Optional<String> getLeader() {
        return Optional.ofNullable(leader);
    }

    /**
     * Returns a list of the roles this member has.
     *
     * @return a list of the roles this member has.
     */
    public Set<String> getOwnRoles() {
        return ownRoles;
    }

    /**
     * Returns the status of each cluster roles members.
     *
     * @return the status of each cluster roles members.
     */
    public Set<ClusterRoleStatus> getRoles() {
        return roles;
    }

    @Override
    public JsonObject toJson() {
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(JSON_KEY_REACHABLE,
                reachable.stream().map(JsonValue::of).collect(JsonCollectors.valuesToArray()));
        jsonObjectBuilder.set(JSON_KEY_UNREACHABLE,
                unreachable.stream().map(JsonValue::of).collect(JsonCollectors.valuesToArray()));
        jsonObjectBuilder.set(JSON_KEY_SEEN_BY,
                seenBy.stream().map(JsonValue::of).collect(JsonCollectors.valuesToArray()));
        jsonObjectBuilder.set(JSON_KEY_LEADER, getLeader().orElse("<unknown>"));
        jsonObjectBuilder.set(JSON_KEY_OWN_ROLES,
                ownRoles.stream().map(JsonValue::of).collect(JsonCollectors.valuesToArray()));

        final JsonObjectBuilder rolesObjectBuilder = JsonFactory.newObjectBuilder();
        roles.forEach(roleStatus -> {
            final JsonKey key = JsonFactory.newKey(roleStatus.getRole());
            final JsonValue value = roleStatus.toJson();
            final JsonFieldDefinition<JsonObject> fieldDefinition =
                    JsonFactory.newJsonObjectFieldDefinition(key, FieldType.REGULAR);
            final JsonField field = JsonFactory.newField(key, value, fieldDefinition);
            rolesObjectBuilder.set(field);
        });
        jsonObjectBuilder.set(JSON_KEY_ROLES, rolesObjectBuilder.build());

        return jsonObjectBuilder.build();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClusterStatus)) {
            return false;
        }
        final ClusterStatus that = (ClusterStatus) o;
        return Objects.equals(reachable, that.reachable) && Objects.equals(unreachable, that.unreachable) && Objects
                .equals(seenBy, that.seenBy) && Objects.equals(leader, that.leader) &&
                Objects.equals(ownRoles, that.ownRoles)
                && Objects.equals(roles, that.roles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reachable, unreachable, seenBy, leader, ownRoles, roles);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + "reachable=" + reachable + ", unreachable=" + unreachable +
                ", seenBy=" + seenBy + ", leader=" + leader + ", ownRoles=" + ownRoles + ", roles=" + roles + ']';
    }

}
