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

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.base.model.exceptions.DittoJsonException.wrapJsonRuntimeException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.Validator;
import org.eclipse.ditto.base.model.entity.validation.NoControlCharactersValidator;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * Factory that new {@link Policy} objects and other objects related to policies.
 */
@Immutable
public final class PoliciesModelFactory {

    public static final int DITTO_LIMITS_POLICY_IMPORTS_LIMIT = Integer.parseInt(System.getProperty("ditto.limits.policy.imports-limit", "10"));
    /*
     * Inhibit instantiation of this utility class.
     */
    private PoliciesModelFactory() {
        throw new AssertionError();
    }

    /**
     * Returns a {@link Label} for the given character sequence. If the given key value is already a Label, this is
     * immediately properly cast and returned.
     *
     * @param labelValue the character sequence value of the Label to be created.
     * @return a new Label with {@code labelValue} as its value.
     * @throws NullPointerException if {@code labelValue} is {@code null}.
     * @throws IllegalArgumentException if {@code labelValue} is empty.
     * @throws LabelInvalidException if the {@code labelValue} can not be used to blocklisted prefixes.
     */
    public static Label newLabel(final CharSequence labelValue) {
        if (labelValue instanceof Label) {
            return (Label) labelValue;
        }
        return ImmutableLabel.of(labelValue);
    }

    /**
     * Returns a {@link Label} for the given character sequence which is a label derived from a {@link PolicyImport}.
     *
     * @param importedFromPolicyId the Policy ID from where the label was imported from.
     * @param labelValue the character sequence value of the Label to be created.
     * @return a new Label with {@code labelValue} as its value.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code labelValue} is empty.
     * @since 3.1.0
     */
    public static Label newImportedLabel(final PolicyId importedFromPolicyId, final CharSequence labelValue) {
        return ImmutableImportedLabel.of(importedFromPolicyId, labelValue);
    }

    /**
     * Returns a new {@link SubjectIssuer} with the specified {@code subjectIssuer}.
     *
     * @param subjectIssuer the SubjectIssuer char sequence.
     * @return the new {@link SubjectIssuer}.
     * @throws NullPointerException if {@code subjectIssuer} is {@code null}.
     * @throws IllegalArgumentException if {@code subjectIssuer} is empty.
     */
    public static SubjectIssuer newSubjectIssuer(final CharSequence subjectIssuer) {
        return ImmutableSubjectIssuer.of(subjectIssuer);
    }

    /**
     * Returns a {@link SubjectId} for the given {@code issuer} and {@code subject} sequences.
     *
     * @param issuer the character sequence for the SubjectId's {@code issuer}.
     * @param subject the character sequence for the SubjectId's {@code subject}.
     * @return a new SubjectId.
     * @throws NullPointerException if {@code issuer} or {@code subject} is {@code null}.
     * @throws IllegalArgumentException if {@code issuer} or {@code subject} is empty.
     */
    public static SubjectId newSubjectId(final SubjectIssuer issuer, final CharSequence subject) {
        return ImmutableSubjectId.of(issuer, subject);
    }

    /**
     * Returns a {@link SubjectId} for the given character sequence. If the given key value is already a SubjectId, this
     * is immediately properly cast and returned.
     *
     * @param subjectIssuerWithId the Subject issuer + Subject ID (separated with a "{@value
     * SubjectId#ISSUER_DELIMITER}") of the SubjectId to be created.
     * @return a new SubjectId with {@code subjectIssuerWithId} as its value.
     * @throws NullPointerException if {@code subjectIssuerWithId} is {@code null}.
     * @throws IllegalArgumentException if {@code subjectIssuerWithId} is empty.
     */
    public static SubjectId newSubjectId(final CharSequence subjectIssuerWithId) {
        checkNotNull(subjectIssuerWithId, "subjectIssuerWithId");

        if (SubjectId.class.isAssignableFrom(subjectIssuerWithId.getClass())) {
            return (SubjectId) subjectIssuerWithId;
        }
        return ImmutableSubjectId.of(subjectIssuerWithId);
    }

    /**
     * Returns a new {@link SubjectType} with the specified {@code subjectType}.
     *
     * @param subjectType the SubjectType char sequence.
     * @return the new {@link SubjectType}.
     * @throws NullPointerException if {@code subjectType} is {@code null}.
     */
    public static SubjectType newSubjectType(final CharSequence subjectType) {
        return ImmutableSubjectType.of(subjectType);
    }

    /**
     * Returns a new {@link SubjectExpiry} with the specified {@code expiry} CharSequence interpreted as
     * ISO-8601 timestamp.
     *
     * @param expiry the expiration timestamp as ISO-8601 formatted CharSequence.
     * @return the new {@link SubjectExpiry}.
     * @throws NullPointerException if {@code expiry} is {@code null}.
     * @throws SubjectExpiryInvalidException if the provided {@code expiry} could not be parsed.
     * @since 2.0.0
     */
    public static SubjectExpiry newSubjectExpiry(final CharSequence expiry) {
        return ImmutableSubjectExpiry.of(expiry);
    }

    /**
     * Returns a new {@link SubjectExpiry} with the specified {@code expiry} Instant.
     *
     * @param expiry the expiration timestamp.
     * @return the new {@link SubjectExpiry}.
     * @throws NullPointerException if {@code expiry} is {@code null}.
     * @since 2.0.0
     */
    public static SubjectExpiry newSubjectExpiry(final Instant expiry) {
        return ImmutableSubjectExpiry.of(expiry);
    }


    /**
     * Returns a new {@code Subject} object with the given {@code subjectId} and
     * subject type {@link SubjectType#GENERATED}.
     *
     * @param subjectId the ID of the new Subject.
     * @return a new {@code Subject} object.
     * @throws NullPointerException if {@code subjectId} is {@code null}.
     */
    public static Subject newSubject(final SubjectId subjectId) {
        return ImmutableSubject.of(subjectId);
    }

    /**
     * Returns a new {@link Subject} with the specified {@code subjectId} and {@code subjectType}.
     *
     * @param subjectId the ID of the new Subject to create.
     * @param subjectType the SubjectType of the new Subject to create.
     * @return the new {@link Subject}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Subject newSubject(final SubjectId subjectId, final SubjectType subjectType) {
        return ImmutableSubject.of(subjectId, subjectType);
    }

    /**
     * Returns a new {@link Subject} with the specified {@code subjectId} and {@code subjectType}.
     *
     * @param subjectId the ID of the new Subject to create.
     * @param subjectType the SubjectType of the new Subject to create.
     * @param subjectExpiry the expiry timestamp of the new Subject.
     * @return the new {@link Subject}.
     * @throws NullPointerException if the {@code subjectId} or {@code subjectType} argument is {@code null}.
     * @since 2.0.0
     */
    public static Subject newSubject(final SubjectId subjectId, final SubjectType subjectType,
            @Nullable final SubjectExpiry subjectExpiry) {
        return ImmutableSubject.of(subjectId, subjectType, subjectExpiry);
    }

    /**
     * Returns a new immutable {@link Subject} based on the given JSON object.
     *
     * @param subjectIssuerWithId the Subject issuer + Subject ID (separated with a "{@value
     * SubjectId#ISSUER_DELIMITER}") of the Subject to be created.
     * @param jsonObject provides the initial values for the result.
     * @return the new Subject.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if {@code jsonObject} cannot be parsed.
     * @throws SubjectExpiryInvalidException if the provided {@code expiry} could not be parsed as ISO-8601 timestamp.
     */
    public static Subject newSubject(final CharSequence subjectIssuerWithId, final JsonObject jsonObject) {
        return ImmutableSubject.fromJson(subjectIssuerWithId, jsonObject);
    }

    /**
     * Returns a new empty {@link Subjects}.
     *
     * @return the new {@code Subjects}.
     */
    public static Subjects emptySubjects() {
        return ImmutableSubjects.of(Collections.emptyList());
    }

    /**
     * Returns a new {@link Subjects} containing the given subjects.
     *
     * @param subjects the {@link Subject}s to be contained in the new Subjects.
     * @return the new {@code Subjects}.
     * @throws NullPointerException if {@code subjects} is {@code null}.
     */
    public static Subjects newSubjects(final Iterable<Subject> subjects) {
        if (subjects instanceof Subjects) {
            return (Subjects) subjects;
        }
        return ImmutableSubjects.of(subjects);
    }

    /**
     * Returns a new {@link Subjects} containing the given subjects.
     *
     * @param subject the {@link Subject} to be contained in the new Subjects.
     * @param furtherSubjects further {@link Subject}s to be contained in the new Subjects.
     * @return the new {@code Subjects}.
     */
    public static Subjects newSubjects(final Subject subject, final Subject... furtherSubjects) {
        checkNotNull(subject, "mandatory subject");
        checkNotNull(furtherSubjects, "additional subjects");

        final Collection<Subject> allSubjects = new ArrayList<>(1 + furtherSubjects.length);
        allSubjects.add(subject);
        Collections.addAll(allSubjects, furtherSubjects);

        return newSubjects(allSubjects);
    }

    /**
     * Returns a new immutable {@link Subjects} based on the given JSON object.
     *
     * @param jsonObject provides the initial values for the result.
     * @return the new Subjects.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if {@code jsonObject} cannot be parsed.
     */
    public static Subjects newSubjects(final JsonObject jsonObject) {
        return ImmutableSubjects.fromJson(jsonObject);
    }

    /**
     * Returns a {@link ResourceKey} for the given character sequence. The {@code typeWithPath} must contain a
     * "{@value ResourceKey#KEY_DELIMITER}" to separate Resource type and Resource path.
     * If the given key value is already a ResourceKey, this is immediately properly cast and returned.
     *
     * @param typeWithPath the character sequence value of the ResourceKey to be created.
     * @return a new ResourceKey.
     * @throws NullPointerException if {@code typeWithPath} is {@code null}.
     * @throws IllegalArgumentException if {@code typeWithPath} is empty.
     */
    public static ResourceKey newResourceKey(final CharSequence typeWithPath) {
        if (typeWithPath instanceof ResourceKey) {
            return (ResourceKey) typeWithPath;
        }

        argumentNotEmpty(typeWithPath, "typeWithPath");

        final Validator validator = NoControlCharactersValidator.getInstance(typeWithPath);
        if (!validator.isValid()) {
            throw PolicyEntryInvalidException.newBuilder()
                    .message("The Policy Resource " + typeWithPath + " is invalid")
                    .description(validator.getReason().orElse(null))
                    .build();
        }

        final String[] typeWithPathSplit = splitTypeWithPath(typeWithPath.toString());
        return ImmutableResourceKey.newInstance(typeWithPathSplit[0], JsonPointer.of(typeWithPathSplit[1]));
    }

    private static String[] splitTypeWithPath(final String typeWithPath) {
        final String[] split = typeWithPath.split(ResourceKey.KEY_DELIMITER, 2);
        if (split.length < 2) {
            throw new DittoJsonException(JsonParseException.newBuilder()
                    .message("The provided string was not in the expected format 'type:path'")
                    .build());
        }
        return split;
    }

    /**
     * Returns a {@link ResourceKey} for the given {@code resourceType} and {@code resourcePath}.
     *
     * @param resourceType the type value of the ResourceKey to be created.
     * @param resourcePath the path value of the ResourceKey to be created.
     * @return a new ResourceKey.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code resourceType} is empty.
     * @throws org.eclipse.ditto.json.JsonPointerInvalidException if the passed {@code resourcePath} contained double slashes.
     */
    public static ResourceKey newResourceKey(final CharSequence resourceType, final CharSequence resourcePath) {
        checkNotNull(resourcePath, "resource path");
        return ImmutableResourceKey.newInstance(resourceType, JsonPointer.of(resourcePath));
    }

    /**
     * Returns a {@link ResourceKey} for the given {@link JsonPointer}.
     *
     * @param pointer the json pointer representing a resource key e.g. /thing:/path1/path2/...
     * @return a new ResourceKey.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     */
    public static ResourceKey newResourceKey(final JsonPointer pointer) {
        checkNotNull(pointer, "pointer");
        checkNotEmpty(pointer, "pointer");
        // omit leading slash
        final String typeWithPath = pointer.toString().substring(1);
        return newResourceKey(typeWithPath);
    }

    /**
     * Returns a new {@link Resource} with the specified {@code resourceType}, {@code resourcePath} and
     * {@code effectedPermissions}.
     *
     * @param resourceType the type of the new Resource to create.
     * @param resourcePath the path of the new Resource to create.
     * @param effectedPermissions the EffectedPermissions of the new Resource to create.
     * @return the new {@link Resource}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code resourceType} is empty.
     */
    public static Resource newResource(final CharSequence resourceType, final CharSequence resourcePath,
            final EffectedPermissions effectedPermissions) {

        return newResource(newResourceKey(resourceType, resourcePath), effectedPermissions);
    }

    /**
     * Returns a new {@link Resource} with the specified {@code resourceKey} and {@code effectedPermissions}.
     *
     * @param resourceKey the JSON key which is assumed to be the path of the new Resource to create prefixed with a
     * type.
     * @param effectedPermissions the EffectedPermissions of the new Resource to create.
     * @return the new {@link Resource}.
     */
    public static Resource newResource(final ResourceKey resourceKey, final JsonValue effectedPermissions) {
        return ImmutableResource.of(resourceKey, effectedPermissions);
    }

    /**
     * Returns a new {@link Resource} with the specified {@code resourceKey} and {@code effectedPermissions}.
     *
     * @param resourceKey the JSON key which is assumed to be the path of the new Resource to create prefixed with a
     * type.
     * @param effectedPermissions the EffectedPermissions of the new Resource to create.
     * @return the new {@link Resource}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Resource newResource(final ResourceKey resourceKey, final EffectedPermissions effectedPermissions) {
        return ImmutableResource.of(resourceKey, effectedPermissions);
    }

    /**
     * Returns a new empty {@link Resources}.
     *
     * @return the new {@code Resources}.
     */
    public static Resources emptyResources() {
        return ImmutableResources.of(Collections.emptyList());
    }

    /**
     * Returns a new {@link Resources} containing the given resources.
     *
     * @param resources the resource iterator to use
     * @return the new {@code Resources}.
     * @throws NullPointerException if {@code resources} is {@code null}.
     */
    public static Resources newResources(final Iterable<Resource> resources) {
        if (resources instanceof Resources) {
            return (Resources) resources;
        }
        return ImmutableResources.of(resources);
    }

    /**
     * Returns a new {@link Resources} containing the given resource.
     *
     * @param resource the {@link Resource} to be contained in the new Resources.
     * @param furtherResources further {@link Resource}s to be contained in the new Resources.
     * @return the new {@code Resources}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Resources newResources(final Resource resource, final Resource... furtherResources) {
        checkNotNull(resource, "mandatory resource");
        checkNotNull(furtherResources, "additional resources");

        final Collection<Resource> allResources = new ArrayList<>(1 + furtherResources.length);
        allResources.add(resource);
        Collections.addAll(allResources, furtherResources);

        return newResources(allResources);
    }

    /**
     * Returns a new immutable {@link Resources} based on the given JSON object.
     *
     * @param jsonObject provides the initial values for the result.
     * @return the new Resources.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if {@code jsonObject} cannot be parsed.
     */
    public static Resources newResources(final JsonObject jsonObject) {
        return ImmutableResources.fromJson(jsonObject);
    }

    /**
     * Returns a new {@link EffectedPermissions} containing the given {@code grantedPermissions} and
     * {@code revokedPermissions}.
     *
     * @param grantedPermissions the Permissions which should be granted - may be {@code null}.
     * @param revokedPermissions the Permissions which should be revoked - may be {@code null}.
     * @return the new {@code EffectedPermissions}.
     */
    public static EffectedPermissions newEffectedPermissions(@Nullable final Iterable<String> grantedPermissions,
            @Nullable final Iterable<String> revokedPermissions) {

        return ImmutableEffectedPermissions.of(getOrEmptyCollection(grantedPermissions),
                getOrEmptyCollection(revokedPermissions));
    }

    private static <T> Iterable<T> getOrEmptyCollection(@Nullable final Iterable<T> iterable) {
        return (null != iterable) ? iterable : Collections.emptySet();
    }

    /**
     * Returns a new immutable {@link PolicyRevision} which is initialised with the given revision number.
     *
     * @param revisionNumber the {@code long} value of the revision.
     * @return the new immutable {@code PolicyRevision}.
     */
    public static PolicyRevision newPolicyRevision(final long revisionNumber) {
        return ImmutablePolicyRevision.of(revisionNumber);
    }

    /**
     * Returns a new empty immutable instance of {@link Permissions}.
     *
     * @return the new {@code Permissions}.
     */
    public static Permissions noPermissions() {
        return ImmutablePermissions.none();
    }

    /**
     * Returns a new immutable instance of {@link Permissions} containing the given permissions.
     *
     * @param permissions the permissions to initialise the result with.
     * @return the new {@code Permissions}.
     * @throws NullPointerException if {@code permissions} is {@code null};
     */
    public static Permissions newPermissions(final Collection<String> permissions) {
        return ImmutablePermissions.of(permissions);
    }

    /**
     * Returns a new immutable instance of {@link Permissions} containing the given permissions.
     *
     * @param permission the mandatory permission to be contained in the result.
     * @param furtherPermissions additional permissions to be contained in the result.
     * @return the new {@code Permissions}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Permissions newPermissions(final String permission, final String... furtherPermissions) {
        return ImmutablePermissions.of(permission, furtherPermissions);
    }

    /**
     * Returns a new immutable {@link PolicyEntry} with the given authorization subject and permissions.
     *
     * @param label the Label of the PolicyEntry to create.
     * @param subjects the Subjects contained in the PolicyEntry to create.
     * @param resources the Resources of the PolicyEntry to create.
     * @return the new Policy entry.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    public static PolicyEntry newPolicyEntry(final CharSequence label, final Iterable<Subject> subjects,
            final Iterable<Resource> resources) {
        return ImmutablePolicyEntry.of(Label.of(label), newSubjects(subjects), newResources(resources));
    }

    /**
     * Returns a new immutable {@link PolicyEntry} with the given authorization subject and permissions.
     *
     * @param label the Label of the PolicyEntry to create.
     * @param subjects the Subjects contained in the PolicyEntry to create.
     * @param resources the Resources of the PolicyEntry to create.
     * @param importable whether and how the entry is importable by others.
     * @return the new Policy entry.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     * @since 3.1.0
     */
    public static PolicyEntry newPolicyEntry(final CharSequence label, final Iterable<Subject> subjects,
            final Iterable<Resource> resources, final ImportableType importable) {
        return ImmutablePolicyEntry.of(Label.of(label), newSubjects(subjects), newResources(resources), importable);
    }

    /**
     * Returns a new immutable {@link PolicyEntry} based on the given JSON object.
     *
     * @param label the Label for the PolicyEntry to create.
     * @param jsonObject the JSON object representation of a PolicyEntry.
     * @return the new Policy entry.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if {@code jsonObject} cannot be parsed.
     */
    public static PolicyEntry newPolicyEntry(final CharSequence label, final JsonObject jsonObject) {
        return ImmutablePolicyEntry.fromJson(label, jsonObject);
    }

    /**
     * Returns a new immutable {@link PolicyEntry} based on the given JSON value.
     *
     * @param label the Label for the PolicyEntry to create.
     * @param jsonValue the JSON value representation of a PolicyEntry.
     * @return the new Policy entry.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if {@code jsonObject} cannot be parsed.
     * @throws PolicyIdInvalidException if the parsed policy ID did not comply to
     * {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#ID_REGEX}.
     */
    public static PolicyEntry newPolicyEntry(final CharSequence label, final JsonValue jsonValue) {
        final JsonObject jsonObject = wrapJsonRuntimeException(jsonValue::asObject);
        return ImmutablePolicyEntry.fromJson(label, jsonObject);
    }

    /**
     * Returns a new immutable {@link PolicyEntry} based on the given JSON string.
     *
     * @param label the Label for the PolicyEntry to create.
     * @param jsonString the JSON object representation as String of a PolicyEntry.
     * @return the new Policy entry.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if {@code jsonObject} cannot be parsed.
     */
    public static PolicyEntry newPolicyEntry(final CharSequence label, final String jsonString) {
        final JsonObject jsonObject = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return ImmutablePolicyEntry.fromJson(label, jsonObject);
    }

    /**
     * Returns a new immutable Iterable of Policy entries based on the given JSON object.
     *
     * @param jsonObject the JSON object representation of Policy entries.
     * @return the new initialised {@code Iterable} of {@code PolicyEntry}s.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if {@code jsonObject} cannot be parsed to
     * {@link Iterable} of {@link PolicyEntry}s.
     */
    public static Iterable<PolicyEntry> newPolicyEntries(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object");
        return jsonObject.stream()
                .map(jsonField -> newPolicyEntry(jsonField.getKey(), jsonField.getValue()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Returns a new immutable Iterable of Policy entries based on the given JSON string.
     *
     * @param jsonString the JSON object representation as String of Policy entries.
     * @return the new initialised {@code Policy}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if {@code jsonObject} cannot be parsed to
     * {@link Iterable} of {@link PolicyEntry}s.
     */
    public static Iterable<PolicyEntry> newPolicyEntries(final String jsonString) {
        final JsonObject jsonObject = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return newPolicyEntries(jsonObject);
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@link Policy}.
     *
     * @return the new builder.
     */
    public static PolicyBuilder newPolicyBuilder() {
        return ImmutablePolicyBuilder.newInstance();
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@link Policy}.
     *
     * @param id the ID of the new Policy.
     * @return the new builder.
     */
    public static PolicyBuilder newPolicyBuilder(final PolicyId id) {
        return ImmutablePolicyBuilder.of(id);
    }

    /**
     * Returns a mutable builder for a {@code Policy} based on the given {@code existingPolicy}.
     *
     * @param existingPolicy the existing Policy to instantiate the builder with.
     * @return the new builder.
     */
    public static PolicyBuilder newPolicyBuilder(final Policy existingPolicy) {
        return ImmutablePolicyBuilder.of(existingPolicy);
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@link Policy}. The builder is initialised
     * with the given Policy entries.
     *
     * @param id the ID of the new Policy.
     * @param policyEntries the initial entries of the new builder.
     * @return the new builder.
     * @throws NullPointerException if {@code policyEntries} is {@code null}.
     */
    public static PolicyBuilder newPolicyBuilder(final PolicyId id, final Iterable<PolicyEntry> policyEntries) {
        return ImmutablePolicyBuilder.of(id, policyEntries);
    }

    /**
     * Returns a new immutable Policy which is initialised with the specified entries.
     *
     * @param id the ID of the new Policy.
     * @param entry the mandatory entry of the Policy.
     * @param furtherEntries additional entries of the Policy.
     * @return the new initialised Policy.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Policy newPolicy(final PolicyId id, final PolicyEntry entry,
            final PolicyEntry... furtherEntries) {

        checkNotNull(entry, "mandatory entry");
        checkNotNull(furtherEntries, "additional policy entries");

        final Collection<PolicyEntry> allEntries = new HashSet<>(1 + furtherEntries.length);
        allEntries.add(entry);
        Collections.addAll(allEntries, furtherEntries);

        return ImmutablePolicy.of(id, PolicyLifecycle.ACTIVE, PolicyRevision.newInstance(1),
                null, null, null, emptyPolicyImports(), allEntries);
    }

    /**
     * Returns a new immutable Policy which is initialised with the specified entries.
     *
     * @param id the ID of the new Policy.
     * @param entries the entries of the Policy.
     * @return the new initialised Policy.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Policy newPolicy(final PolicyId id, final Iterable<PolicyEntry> entries) {
        return ImmutablePolicy.of(id, PolicyLifecycle.ACTIVE, PolicyRevision.newInstance(1),
                null, null, null, emptyPolicyImports(), entries);
    }

    /**
     * Returns a new immutable Policy based on the given JSON object.
     *
     * @param jsonObject the JSON object representation of a Policy.
     * @return the new initialised {@code Policy}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if {@code jsonObject} cannot be parsed to
     * {@link Policy}.
     */
    public static Policy newPolicy(final JsonObject jsonObject) {
        return ImmutablePolicy.fromJson(jsonObject);
    }

    /**
     * Returns a new immutable Policy based on the given JSON string.
     *
     * @param jsonString the JSON object representation as String of a Policy.
     * @return the new initialised {@code Policy}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if {@code jsonString} cannot be parsed to
     * {@link Policy}.
     */
    public static Policy newPolicy(final String jsonString) {
        final JsonObject jsonObject = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return newPolicy(jsonObject);
    }

    /**
     * Returns a new {@link EffectedImports} containing the optionally passed policy entry labels.
     *
     * @param importedLabels the labels of the policy entries which should be imported.
     * @return the new {@code EffectedImports}.
     * @since 3.1.0
     */
    public static EffectedImports newEffectedImportedLabels(@Nullable final Iterable<Label> importedLabels) {

        return ImmutableEffectedImports.of(getOrEmptyCollection(importedLabels));
    }

    /**
     * Returns a new immutable instance of {@link ImportedLabels} containing the given entry labels.
     *
     * @param entryLabels the entryLabels to initialise the result with.
     * @return the new {@code ImportedLabels}.
     * @throws NullPointerException if {@code entryLabels} is {@code null};
     * @since 3.1.0
     */
    public static ImportedLabels newImportedEntries(final Collection<Label> entryLabels) {
        return ImmutableImportedLabels.of(entryLabels);
    }

    /**
     * Returns a new immutable instance of {@link ImportedLabels} containing the given entry labels.
     *
     * @param entryLabel the mandatory entryLabel to be contained in the result.
     * @param furtherEntryLabels additional entryLabels to be contained in the result.
     * @return the new {@code ImportedLabels}.
     * @throws NullPointerException if any argument is {@code null}.
    * @since 3.1.0
     */
    public static ImportedLabels newImportedEntries(final CharSequence entryLabel,
            final CharSequence... furtherEntryLabels) {
        return ImmutableImportedLabels.of(entryLabel, furtherEntryLabels);
    }

    /**
     * Returns a new immutable instance of {@link ImportedLabels} containing no labels.
     *
     * @return the new {@code ImportedLabels}.
    * @since 3.1.0
     */
    public static ImportedLabels noImportedEntries() {
        return ImmutableImportedLabels.none();
    }

    /**
     * Returns a new {@link PolicyImport} with the specified {@code importedPolicyId} and {@code effectedImportedEntries}.
     *
     * @param importedPolicyId The {@link PolicyId} of the imported policy
     * @return the new {@link PolicyImport}.
     * @throws NullPointerException if any argument is {@code null}.
    * @since 3.1.0
     */
    public static PolicyImport newPolicyImport(final PolicyId importedPolicyId) {
        return ImmutablePolicyImport.of(importedPolicyId);
    }

    /**
     * Returns a new {@link PolicyImport} with the specified {@code importedPolicyId} and {@code effectedImports}.
     *
     * @param importedPolicyId the {@link PolicyId} of the imported policy.
     * @param effectedImports lists every {@code PolicyEntry} label from the imported {@code Policy} that will be included - if {@code null}, all policy entries will be imported.
     * @return the new {@link PolicyImport}.
     * @throws NullPointerException if {@code importedPolicyId} is {@code null}.
    * @since 3.1.0
     */
    public static PolicyImport newPolicyImport(final PolicyId importedPolicyId,
            @Nullable final EffectedImports effectedImports) {
        return ImmutablePolicyImport.of(importedPolicyId, effectedImports);
    }

    /**
     * Returns a new immutable {@link PolicyImport} based on the given JSON object.
     *
     * @param importedPolicyId The {@link PolicyId} of the imported policy
     * @param jsonObject the JSON object representation of a PolicyImport.
     * @return the new Policy import.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if {@code jsonObject} cannot be parsed.
    * @since 3.1.0
     */
    public static PolicyImport newPolicyImport(final PolicyId importedPolicyId, final JsonObject jsonObject) {
        return ImmutablePolicyImport.fromJson(importedPolicyId, jsonObject);
    }

    /**
     * Returns an empty {@link EffectedImports} meaning that all entries of an imported policy will be imported.
     *
     * @return the empty effected imports.
    * @since 3.1.0
     */
    public static EffectedImports emptyEffectedImportedEntries() {
        return ImmutableEffectedImports.of(Collections.emptyList());
    }

    /**
     * Returns a new {@link PolicyImports} containing no policyImports.
     *
     * @return the new empty {@code PolicyImports}.
    * @since 3.1.0
     */
    public static PolicyImports emptyPolicyImports() {
        return ImmutablePolicyImports.empty();
    }

    /**
     * Returns a new {@link PolicyImports} containing the given policyImports.
     *
     * @param policyImports the PolicyImport iterator to use
     * @return the new {@code PolicyImports}.
     * @throws NullPointerException if {@code policyImports} is {@code null}.
    * @since 3.1.0
     */
    public static PolicyImports newPolicyImports(final Iterable<PolicyImport> policyImports) {
        if (policyImports instanceof PolicyImports) {
            return (PolicyImports) policyImports;
        }
        return ImmutablePolicyImports.of(policyImports);
    }

    /**
     * Returns a new {@link PolicyImport} containing the given policyImport.
     *
     * @param policyImport the {@link Resource} to be contained in the new Resources.
     * @param furtherPolicyImports further {@link Resource}s to be contained in the new Resources.
     * @return the new {@code Resources}.
     * @throws NullPointerException if any argument is {@code null}.
    * @since 3.1.0
     */
    public static PolicyImports newPolicyImports(final PolicyImport policyImport,
            final PolicyImport... furtherPolicyImports) {
        checkNotNull(policyImport, "policyImport");
        checkNotNull(furtherPolicyImports, "furtherPolicyImports");

        final Collection<PolicyImport> allPolicyImports =
                new ArrayList<>(1 + furtherPolicyImports.length);
        allPolicyImports.add(policyImport);
        Collections.addAll(allPolicyImports, furtherPolicyImports);

        return newPolicyImports(allPolicyImports);
    }

    /**
     * Returns a new immutable {@link PolicyImports} based on the given JSON object.
     *
     * @param jsonObject provides the initial values for the result.
     * @return the new PolicyImports.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if {@code jsonObject} cannot be parsed.
    * @since 3.1.0
     */
    public static PolicyImports newPolicyImports(final JsonObject jsonObject) {
        return ImmutablePolicyImports.fromJson(jsonObject);
    }
}
