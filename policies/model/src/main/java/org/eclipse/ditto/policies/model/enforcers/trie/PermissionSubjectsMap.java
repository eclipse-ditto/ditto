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
package org.eclipse.ditto.policies.model.enforcers.trie;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Weighted N-to-N relation (en.wikipedia.org/wiki/Binary_relation) between permissions and authorization subjects.
 * All uses of the words "relation" and "relate" on this page refers to binary relations in the mathematical sense as
 * defined in the linked Wikipedia page.
 */
@NotThreadSafe
final class PermissionSubjectsMap extends AbstractMap<String, Map<String, Integer>> {

    private final Map<String, Map<String, Integer>> data;

    /**
     * Constructs a new {@code PermissionSubjectsMap} object.
     */
    PermissionSubjectsMap() {
        data = new HashMap<>();
    }

    @Override
    public Set<Entry<String, Map<String, Integer>>> entrySet() {
        return data.entrySet();
    }

    @Override
    public Map<String, Integer> put(final String key, final Map<String, Integer> value) {
        return data.put(checkNotNull(key, "key"), checkNotNull(value, "value"));
    }

    /**
     * Augment this relation by a total relation between a set of permissions and a set of authorization subject IDs.
     * Every pair in the total relation has weight 0.
     *
     * @param permissions Left projection of the added total relation.
     * @param subjectIds Right projection of the added total relation.
     * @throws NullPointerException if any argument is {@code null}.
     */
    void addTotalRelationOfWeightZero(final Iterable<String> permissions, final Collection<String> subjectIds) {
        validatePermissions(permissions);
        validateSubjectIds(subjectIds);

        final Map<String, Integer> subjectsWithDefaultWeight = subjectIds.stream()
                .collect(Collectors.toMap(Function.identity(), subject -> 0));
        permissions.forEach(permission -> addPermissionSubjects(permission, subjectsWithDefaultWeight));
    }

    /**
     * If <em>some</em> of the given permissions are related to some of the given subject IDs, then return the maximum
     * weight of related permission-subject pairs among the given. Mathematically, intersect this relation with the
     * Cartesian product of {@code permissions} and {@code subjectIds}, then return the maximum weight in the resulting
     * relation.
     *
     * @param subjectIds The set of subject IDs to check.
     * @param permissions The set of permissions to check.
     * @return Either the maximum weight of given subject IDs related to the given permissions or
     * {@code Optional.empty()}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    Optional<Integer> getMaxWeightForAllPermissions(final Collection<String> subjectIds,
            final Collection<String> permissions) {

        validateSubjectIds(subjectIds);
        validatePermissions(permissions);

        return permissions.stream()
                .flatMap(permission -> {
                    final Map<String, Integer> permittedSubjects = getOrDefault(permission, Collections.emptyMap());
                    return intersect(subjectIds, permittedSubjects.keySet()).map(permittedSubjects::get);
                })
                .max(Comparator.naturalOrder());
    }

    /**
     * If <em>all</em>of the given permissions are related to some of the given subject IDs, then return the maximum
     * weight of the related subject IDs among the given subject IDs; otherwise return {@code Optional.empty()}.
     *
     * @param subjectIds The set of subject IDs to check.
     * @param permissions The set of permissions to check.
     * @return Either the maximum weight of given subject IDs related to the given permissions, or
     * {@code Optional.empty()}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    Optional<Integer> getMaxNonemptyWeightForAllPermissions(final Collection<String> subjectIds,
            final Collection<String> permissions) {

        validateSubjectIds(subjectIds);
        validatePermissions(permissions);

        final List<Optional<Integer>> permissionWeights = permissions.stream()
                .map(permission -> {
                    final Map<String, Integer> permittedSubjects = getOrDefault(permission, Collections.emptyMap());
                    return intersect(subjectIds, permittedSubjects.keySet())
                            .map(permittedSubjects::get)
                            .max(Comparator.naturalOrder());
                })
                .collect(Collectors.toList());
        if (permissionWeights.stream().anyMatch(maybeWeight -> !maybeWeight.isPresent())) {
            return Optional.empty();
        } else {
            return permissionWeights.stream().map(Optional::get).max(Comparator.naturalOrder());
        }
    }

    /**
     * Returns the set of subjects each of which is related to <em>some</em> permission among the given.
     *
     * @param permissions The set of permissions to check.
     * @return The set of subjects related to some permission among the given.
     * @throws NullPointerException if {@code permissions} is {@code null}.
     */
    Map<String, Integer> getSubjectUnion(final Set<String> permissions) {
        validatePermissions(permissions);

        final Map<String, Integer> subjectUnion = new HashMap<>();
        intersect(keySet(), permissions)
                .flatMap(permission -> get(permission).entrySet().stream())
                .forEach(entry -> subjectUnion.compute(entry.getKey(), (subject, weight) ->
                        weight == null ? entry.getValue() : Math.max(entry.getValue(), weight)));
        return subjectUnion;
    }

    /**
     * Returns the set of subjects each of which is related to <em>all</em> given permissions.
     *
     * @param permissions The set of permissions to check.
     * @return The set of subjects each of which is related to all given permissions.
     * @throws NullPointerException if {@code permissions} is {@code null}.
     */
    Map<String, Integer> getSubjectIntersect(final Set<String> permissions) {
        validatePermissions(permissions);

        final Stream<Map<String, Integer>> subjectsOfPermissions = intersect(keySet(), permissions).map(this::get);

        final Optional<Map<String, Integer>> reduceResult = subjectsOfPermissions.reduce((map1, map2) ->
                intersect(map1.keySet(), map2.keySet())
                        .collect(Collectors.toMap(Function.identity(), key -> Math.max(map1.get(key), map2.get(key)))));

        return reduceResult.orElse(Collections.emptyMap());
    }

    /**
     * Returns a copy of this relation.
     *
     * @return The copy.
     */
    PermissionSubjectsMap copy() {
        final PermissionSubjectsMap copy = new PermissionSubjectsMap();
        forEach((permission, subjectMap) -> copy.put(permission, new HashMap<>(subjectMap)));
        return copy;
    }

    /**
     * Returns a copy of this relation where the weight of each permission-subject pair is increased by 1.
     *
     * @return The copy with incremented weight.
     */
    PermissionSubjectsMap copyWithIncrementedWeight() {
        return copyWithWeightAdjustment(1);
    }

    /**
     * Returns a copy of this relation where the weight of each permission-subject pair is decreased by 1.
     *
     * @return The copy with decremented weight.
     */
    PermissionSubjectsMap copyWithDecrementedWeight() {
        return copyWithWeightAdjustment(-1);
    }

    /**
     * Removes all permission-subject pairs in the given relation {@code update}.
     *
     * @param update The relation to delete from this.
     * @return This object after the mutation.
     * @throws NullPointerException if {@code update} is {@code null}.
     */
    PermissionSubjectsMap removeAllEntriesFrom(final PermissionSubjectsMap update) {
        checkNotNull(update, "relation to be deleted");
        update.forEach((permission, subjectMap) -> removePermissionSubjects(permission, subjectMap.keySet()));
        return this;
    }

    private void removePermissionSubjects(final String permission, final Iterable<String> subjectIds) {
        compute(permission, (p, subjectMap) -> {
            if (subjectMap == null) {
                return null;
            } else {
                subjectIds.forEach(subjectMap::remove);
                return subjectMap;
            }
        });
    }

    /**
     * Add all permission-subject pairs in the given relation to this relation such that the weight of each pair is
     * the maximum weight of the pair in both relations. Mathematically, compute the union of this relation with the
     * relation {@code other} such that pairs are assigned their maximum weight in both relations, then replace this
     * object by the union.
     *
     * @param other The relation to add to this.
     * @return This object after the mutation.
     * @throws NullPointerException if {@code other} is {@code null}.
     */
    PermissionSubjectsMap addAllEntriesFrom(final PermissionSubjectsMap other) {
        checkNotNull(other, "relation to be added");
        other.forEach(this::addPermissionSubjects);
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final PermissionSubjectsMap that = (PermissionSubjectsMap) o;
        return Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), data);
    }

    private static void validateSubjectIds(final Collection<String> subjectIds) {
        checkNotNull(subjectIds, "subject IDs to check");
    }

    private static void validatePermissions(final Object permissions) {
        checkNotNull(permissions, "permissions to check");
    }

    private void addPermissionSubjects(final String permission, final Map<String, Integer> subjects) {
        compute(permission, (p, subjectMap) -> {
            if (subjectMap == null) {
                return new HashMap<>(subjects);
            } else {
                subjects.forEach((subject, thatWeight) ->
                        subjectMap.compute(subject, (s, thisWeight) ->
                            thisWeight == null ? thatWeight : Math.max(thisWeight, thatWeight))
                );
                return subjectMap;
            }
        });
    }

    private static <T> Stream<T> intersect(final Collection<T> set1, final Collection<T> set2) {
        return set1.size() <= set2.size()
                ? set1.stream().filter(set2::contains)
                : set2.stream().filter(set1::contains);
    }

    private PermissionSubjectsMap copyWithWeightAdjustment(final int adjustment) {
        final PermissionSubjectsMap copy = new PermissionSubjectsMap();
        forEach((permission, subjectMap) -> {
            final Map<String, Integer> adjustedSubjectMap = subjectMap.entrySet()
                    .stream()
                    .collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue() + adjustment));
            copy.put(permission, adjustedSubjectMap);
        });
        return copy;
    }

}
