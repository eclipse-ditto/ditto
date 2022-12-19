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
package org.eclipse.ditto.policies.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.Entity;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;

/**
 * A Policy contains {@link PolicyEntry}s containing information about which {@link Subjects} are granted/revoked
 * which {@link Permissions} on which {@link Resources}.
 */
public interface Policy extends Iterable<PolicyEntry>, Entity<PolicyRevision> {

    /**
     * The name of the Json field when a policy is inlined in another Json object.
     */
    String INLINED_FIELD_NAME = "_policy";

    /**
     * Returns a mutable builder with a fluent API for an immutable {@code Policy}.
     *
     * @param policyId the ID of the new Policy.
     * @return the new builder.
     * @throws PolicyIdInvalidException if {@code id} is invalid.
     */
    static PolicyBuilder newBuilder(final PolicyId policyId) {
        return PoliciesModelFactory.newPolicyBuilder(policyId);
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@code Policy}.
     *
     * @return the new builder.
     */
    static PolicyBuilder newBuilder() {
        return PoliciesModelFactory.newPolicyBuilder();
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@code Policy}. The builder is initialised
     * with the Policy entries of this instance.
     *
     * @return the new builder.
     */
    default PolicyBuilder toBuilder() {
        return PoliciesModelFactory.newPolicyBuilder(this);
    }

    @Override
    Optional<PolicyId> getEntityId();

    /**
     * Policy is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions of Policy.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Returns the namespace of this Policy.
     *
     * @return the namespace of this Policy.
     */
    Optional<String> getNamespace();

    /**
     * Returns the current lifecycle of this Policy.
     *
     * @return the current lifecycle of this Policy.
     */
    Optional<PolicyLifecycle> getLifecycle();

    /**
     * Indicates whether this Policy has the given lifecycle.
     *
     * @param lifecycle the lifecycle to be checked for.
     * @return {@code true} if this Policy has {@code lifecycle} as its lifecycle, {@code false} else.
     */
    default boolean hasLifecycle(@Nullable final PolicyLifecycle lifecycle) {
        return getLifecycle()
                .filter(actualLifecycle -> Objects.equals(actualLifecycle, lifecycle))
                .isPresent();
    }

    /**
     * Returns all available {@link Label}s of this Policy.
     *
     * @return all available labels.
     */
    Set<Label> getLabels();

    /**
     * Indicates whether this Policy contains a PolicyEntry for the specified label.
     *
     * @param label the label to check if this Policy has a PolicyEntry for.
     * @return {@code true} if this Policy contains a PolicyEntry for {@code label}, {@code false} else.
     * @throws NullPointerException if {@code label} is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    boolean contains(CharSequence label);

    /**
     * Sets the specified entry to a copy of this Policy. A previous entry for the same label is replaced by
     * the specified one.
     *
     * @param entry the entry to be set to this Policy.
     * @return a copy of this Policy with {@code entry} set.
     * @throws NullPointerException if {@code entry} is {@code null}.
     */
    Policy setEntry(PolicyEntry entry);

    /**
     * Returns an Policy entry for the specified label.
     *
     * @param label the label to get the Policy entry for.
     * @return the Policy entry.
     * @throws NullPointerException if {@code label} is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    Optional<PolicyEntry> getEntryFor(CharSequence label);

    /**
     * Removes the entry identified by the specified label from this Policy.
     *
     * @param label the label identifying the entry to be removed from this Policy.
     * @return a copy of this Policy which does not contain the identified entry anymore.
     * @throws NullPointerException if {@code entry} is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    Policy removeEntry(CharSequence label);

    /**
     * Removes the entry identified by the specified labels from this Policy.
     *
     * @param labels the labels identifying the entries to be removed from this Policy.
     * @return a copy of this Policy which does not contain the identified entries anymore.
     * @throws NullPointerException if {@code labels} is {@code null}.
     * @throws IllegalArgumentException if {@code labels} is empty.
     * @since 3.1.0
     */
    Policy removeEntries(Iterable<CharSequence> labels);

    /**
     * Removes the specified entry from this Policy.
     *
     * @param entry the entry to be removed from this Policy.
     * @return a copy of this Policy which does not contain the specified entry anymore.
     * @throws NullPointerException if {@code entry} is {@code null}.
     */
    Policy removeEntry(PolicyEntry entry);

    /**
     * Sets the given {@link Subjects} to the specified label. All previous entries with the same subject ID in the
     * label are replaced by the specified ones.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param subjects the Subjects to set for the PolicyEntry identified by the {@code label}.
     * @return a copy of this Policy with the changed state.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    Policy setSubjectsFor(CharSequence label, Subjects subjects);

    /**
     * Sets the given {@link Subject} to the specified label. A previous entry with the same subject ID in the label is
     * replaced by the specified one.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param subject the Subject to set for the PolicyEntry identified by the {@code label}.
     * @return a copy of this Policy with the changed state.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    Policy setSubjectFor(CharSequence label, Subject subject);

    /**
     * Removes the subject identified by the specified subject ID from this Policy.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param subjectId the Subject ID to remove from the PolicyEntry identified by the {@code label}.
     * @return a copy of this Policy with the removed subject.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    Policy removeSubjectFor(CharSequence label, SubjectId subjectId);

    /**
     * Removes the specified subject from this Policy.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param subject the Subject to remove from the PolicyEntry identified by the {@code label}.
     * @return a copy of this Policy with the removed subject.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    default Policy removeSubjectFor(final CharSequence label, final Subject subject) {
        checkNotNull(subject, "subject to be removed");
        return removeSubjectFor(label, subject.getId());
    }

    /**
     * Sets the given {@link Resources} to the specified label. All previous entries with the same resource key in the
     * label are replaced by the specified ones.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param resources the Resources to set for the PolicyEntry identified by the {@code label}.
     * @return a copy of this Policy with the changed state.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    Policy setResourcesFor(CharSequence label, Resources resources);

    /**
     * Sets the given {@link Resource} to the specified label. A previous entry with the same resource key in the label
     * is replaced by the specified one.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param resource the Resource to set for the PolicyEntry identified by the {@code label}.
     * @return a copy of this Policy with the changed state.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    Policy setResourceFor(CharSequence label, Resource resource);

    /**
     * Removes the resource identified by the specified resource path from this Policy.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param resourceType the type of the Resource to remove from the PolicyEntry identified by the {@code label}.
     * @param resourcePath the path of the Resource to remove from the PolicyEntry identified by the {@code label}.
     * @return a copy of this Policy with the removed subject.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} or {@code resourceType} is empty.
     */
    default Policy removeResourceFor(final CharSequence label, final String resourceType,
            final CharSequence resourcePath) {

        return removeResourceFor(label, PoliciesModelFactory.newResourceKey(resourceType, resourcePath));
    }

    /**
     * Removes the resource identified by the specified resource key from this Policy.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param resourceKey the ResourceKey of the Resource to remove from the PolicyEntry identified by the
     * {@code label}.
     * @return a copy of this Policy with the removed subject.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    Policy removeResourceFor(CharSequence label, ResourceKey resourceKey);

    /**
     * Removes the specified Resource from this Policy.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param resource the Resource to remove from the PolicyEntry identified by the {@code label}.
     * @return a copy of this Policy with the removed subject.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    default Policy removeResourceFor(final CharSequence label, final Resource resource) {
        return removeResourceFor(label, checkNotNull(resource, "resource").getResourceKey());
    }

    /**
     * Set the given {@link EffectedPermissions} on the specified resource key in the specified label.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param resourceKey the ResourceKey to set the effected permissions on.
     * @param effectedPermissions the EffectedPermissions to set on the resource in the label.
     * @return a copy of this Policy with the changed state.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    default Policy setEffectedPermissionsFor(final CharSequence label, final ResourceKey resourceKey,
            final EffectedPermissions effectedPermissions) {

        return setResourceFor(label, PoliciesModelFactory.newResource(resourceKey, effectedPermissions));
    }

    /**
     * Returns the {@link EffectedPermissions} for the specified subject ID on the passed resource type and resource
     * path.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param subjectId the Subject ID to get the effected permissions for.
     * @param resourceType the Resource type to get the effected permissions for.
     * @param resourcePath the Resource path to get the effected permissions for.
     * @return the effected permissions which are associated with {@code subject} on the passed {@code resourceType} and
     * {@code resourcePath}. The returned set is mutable but disjoint from this Policy; thus modifying the set does not
     * have an impact on this Policy.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} or {@code resourceType} is empty.
     */
    default Optional<EffectedPermissions> getEffectedPermissionsFor(final CharSequence label,
            final SubjectId subjectId,
            final CharSequence resourceType,
            final CharSequence resourcePath) {

        return getEffectedPermissionsFor(label, subjectId,
                PoliciesModelFactory.newResourceKey(resourceType, resourcePath));
    }

    /**
     * Returns the {@link EffectedPermissions} for the specified subject ID on the passed resource key.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param subjectId the Subject ID to get the effected permissions for.
     * @param resourceKey the ResourceKey to get the effected permissions for.
     * @return the effected permissions which are associated with {@code subject} on the passed {@code resourceKey}. The
     * returned set is mutable but disjoint from this Policy; thus modifying the set does not have an impact on this
     * Policy.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    Optional<EffectedPermissions> getEffectedPermissionsFor(CharSequence label, SubjectId subjectId,
            ResourceKey resourceKey);


    /**
     * Sets the importable flag for the specified label.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param importableType the importable type.
     * @return a copy of this Policy with the changed state.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     * @since 3.1.0
     */
    Policy setImportableFor(CharSequence label, ImportableType importableType);

    /**
     * Indicates whether this Policy is empty.
     *
     * @return {@code true} if this Policy does not contain any entry, {@code false} else.
     */
    boolean isEmpty();

    /**
     * Returns the amount of entries this Policy has.
     *
     * @return this Policy's entries amount.
     */
    int getSize();

    /**
     * Returns the PolicyImports this Policy has.
     *
     * @return the PolicyImports of this Policy.
     * @since 3.1.0
     */
    PolicyImports getPolicyImports();

    /**
     * Returns the entries of this Policy as set. The returned set is modifiable but disjoint from this Policy; thus
     * modifying the entry set has no impact on this Policy.
     *
     * @return an unsorted set of this Policy's entries.
     */
    Set<PolicyEntry> getEntriesSet();

    /**
     * Returns a sequential {@code Stream} with the entries of this Policy as its source.
     *
     * @return a sequential stream of the entries of this Policy.
     */
    Stream<PolicyEntry> stream();

    /**
     * Checks if the passed {@code otherPolicy} is semantically the same as this policy.
     * I.e. that those contain the same policy entries with the same subject ids having the same resources.
     *
     * @param otherPolicy the other policy to check against.
     * @return {@code true} if the other policy is semantically the same as this policy.
     * @since 3.0.0
     */
    boolean isSemanticallySameAs(Policy otherPolicy);

    /**
     * Returns a JSON object representation of this policy to embed in another JSON object.
     *
     * @param schemaVersion the JsonSchemaVersion in which to return the JSON.
     * @param predicate determines the content of the result.
     * @return a JSON object representation of this policy to embed in another JSON object.
     * @throws NullPointerException if {@code predicate} is {@code null}.
     */
    default JsonObject toInlinedJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        return JsonFactory.newObjectBuilder()
                .set(INLINED_FIELD_NAME, toJson(schemaVersion, predicate))
                .build();
    }

    /**
     * Returns a JSON object representation of this policy to embed in another JSON object.
     *
     * @param schemaVersion the JsonSchemaVersion in which to return the JSON.
     * @param fieldSelector determines the content of the result.
     * @return a JSON object representation of this policy to embed in another JSON object.
     * @throws NullPointerException if {@code predicate} is {@code null}.
     */
    default JsonObject toInlinedJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return JsonFactory.newObjectBuilder()
                .set(INLINED_FIELD_NAME, toJson(schemaVersion, fieldSelector))
                .build();
    }

    default CompletionStage<Policy> withResolvedImports(
            final Function<PolicyId, CompletionStage<Optional<Policy>>> policyResolver) {
        final CompletionStage<Policy> result;
        final PolicyImports imports = getPolicyImports();
        if (!imports.isEmpty()) {
            result = PolicyImporter.mergeImportedPolicyEntries(this, policyResolver)
                    .thenApply(mergedEntries -> this.toBuilder().setAll(mergedEntries).build());
        } else {
            result = CompletableFuture.completedFuture(this);
        }
        return result;
    }

    /**
     * An enumeration of the known {@link JsonField}s of a Policy.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the Policy's lifecycle.
         */
        public static final JsonFieldDefinition<String> LIFECYCLE = JsonFactory.newStringFieldDefinition("__lifecycle",
                FieldType.SPECIAL,
                FieldType.HIDDEN,
                JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Policy's namespace.
         */
        public static final JsonFieldDefinition<String> NAMESPACE = JsonFactory.newStringFieldDefinition("_namespace",
                FieldType.SPECIAL,
                FieldType.HIDDEN,
                JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Policy's revision.
         */
        public static final JsonFieldDefinition<Long> REVISION = JsonFactory.newLongFieldDefinition("_revision",
                FieldType.SPECIAL,
                FieldType.HIDDEN,
                JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Policy's modified timestamp in ISO-8601 format.
         */
        public static final JsonFieldDefinition<String> MODIFIED = JsonFactory.newStringFieldDefinition("_modified",
                FieldType.SPECIAL,
                FieldType.HIDDEN,
                JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Policy's created timestamp in ISO-8601 format.
         *
         * @since 1.2.0
         */
        public static final JsonFieldDefinition<String> CREATED = JsonFactory.newStringFieldDefinition("_created",
                FieldType.SPECIAL,
                FieldType.HIDDEN,
                JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Policy's ID.
         */
        public static final JsonFieldDefinition<String> ID =
                JsonFactory.newStringFieldDefinition("policyId", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Policy's imports.
         *
         * @since 3.1.0
         */
        public static final JsonFieldDefinition<JsonObject> IMPORTS =
                JsonFactory.newJsonObjectFieldDefinition("imports", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Policy's entries.
         */
        public static final JsonFieldDefinition<JsonObject> ENTRIES =
                JsonFactory.newJsonObjectFieldDefinition("entries", FieldType.REGULAR, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the Policy's metadata.
         *
         * @since 2.0.0
         */
        public static final JsonFieldDefinition<JsonObject> METADATA = JsonFactory.newJsonObjectFieldDefinition(
                "_metadata",
                FieldType.SPECIAL,
                FieldType.HIDDEN,
                JsonSchemaVersion.V_2
        );

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
