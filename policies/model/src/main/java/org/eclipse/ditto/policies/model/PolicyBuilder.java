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

import java.time.Instant;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.json.JsonPointer;

/**
 * A mutable builder for a {@link Policy} with a fluent API.
 */
@NotThreadSafe
public interface PolicyBuilder {

    /**
     * Sub-Interface extending PolicyBuilder by awareness of the {@link Label} to use.
     *
     */
    interface LabelScoped extends PolicyBuilder {

        /**
         * Returns the label the LabelScoped builder is scoped to.
         *
         * @return the label.
         */
        Label getLabel();

        /**
         * Sets the given {@link Subjects} to the specified {@code label} to this builder. All previous entries with the
         * same
         * {@code subjectId} in the {@link Label} are replaced by the specified ones.
         *
         * @param subjects the Subjects to set for the PolicyEntry identified by the {@code label}.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default LabelScoped setSubjects(final Subjects subjects) {
            setSubjectsFor(getLabel(), subjects);
            return this;
        }

        /**
         * Sets the given {@link Subject} to the specified {@code label} to this builder. A previous entry with the same
         * {@code subjectId} in the {@link Label} is replaced by the specified one.
         *
         * @param issuer the SubjectId's {@code issuer}.
         * @param subject the character sequence for the SubjectId's {@code subject}.
         * @param subjectType the type of the subject to set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default LabelScoped setSubject(final SubjectIssuer issuer, final CharSequence subject,
                final SubjectType subjectType) {

            setSubjectFor(getLabel(), Subject.newInstance(issuer, subject, subjectType));
            return this;
        }

        /**
         * Sets the given {@link Subject} to the specified {@code label} to this builder. A previous entry with the same
         * {@code subjectId} in the {@link Label} is replaced by the specified one. Sets the subject type
         * to {@link SubjectType#GENERATED}.
         *
         * @param issuer the SubjectId's {@code issuer}.
         * @param subject the character sequence for the SubjectId's {@code subject}.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default LabelScoped setSubject(final SubjectIssuer issuer, final CharSequence subject) {

            setSubjectFor(getLabel(), Subject.newInstance(issuer, subject));
            return this;
        }


        /**
         * Sets the given {@link Subject} to the specified {@code label} to this builder. A previous entry with the same
         * {@code subjectIssuerWithId} in the {@link Label} is replaced by the specified one.
         *
         * @param subjectIssuerWithId the Subject issuer + Subject ID (separated with a "{@value
         * SubjectId#ISSUER_DELIMITER}") of the Subject to set.
         * @param subjectType the type of the subject to set.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default LabelScoped setSubject(final CharSequence subjectIssuerWithId, final SubjectType subjectType) {
            setSubjectFor(getLabel(), Subject.newInstance(subjectIssuerWithId, subjectType));
            return this;
        }

        /**
         * Sets the given {@link Subject} to the specified {@code label} to this builder. A previous entry with the same
         * {@code subjectId} in the {@link Label} is replaced by the specified one.
         *
         * @param subject the Subject to set for the PolicyEntry identified by the {@code label}.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default LabelScoped setSubject(final Subject subject) {
            setSubjectFor(getLabel(), subject);
            return this;
        }

        /**
         * Removes the subject identified by the specified {@code subjectIssuerWithId} from this builder.
         *
         * @param subjectIssuerWithId the Subject issuer + Subject ID (separated with a "{@value
         * SubjectId#ISSUER_DELIMITER}") of the Subject to remove from the PolicyEntry identified by the {@code label}.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default LabelScoped removeSubject(final CharSequence subjectIssuerWithId) {
            removeSubjectFor(getLabel(), SubjectId.newInstance(subjectIssuerWithId));
            return this;
        }

        /**
         * Removes the subject identified by the specified {@code issuer} and {@code subject} from this builder.
         *
         * @param issuer the SubjectId's {@code issuer} to remove from the PolicyEntry identified by the {@code label}.
         * @param subject the character sequence for the SubjectId's {@code subject} to remove from the PolicyEntry
         * identified by the {@code label}.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default LabelScoped removeSubject(final SubjectIssuer issuer, final CharSequence subject) {
            removeSubjectFor(getLabel(), SubjectId.newInstance(issuer, subject));
            return this;
        }

        /**
         * Removes the subject identified by the specified {@code subjectId} from this builder.
         *
         * @param subjectId the Subject ID to remove from the PolicyEntry identified by the {@code label}.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default LabelScoped removeSubject(final SubjectId subjectId) {
            removeSubjectFor(getLabel(), subjectId);
            return this;
        }

        /**
         * Removes the specified {@code subject} from this builder.
         *
         * @param subject the Subject to remove from the PolicyEntry identified by the {@code label}.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default LabelScoped removeSubject(final Subject subject) {
            removeSubjectFor(getLabel(), subject);
            return this;
        }

        /**
         * Sets the given {@link Resources} to the specified {@code label} to this builder. All previous entries with
         * the same {@code resourcePath} in the {@link Label} are replaced by the specified ones.
         *
         * @param resources the Resources to set for the PolicyEntry identified by the {@code label}.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default LabelScoped setResources(final Resources resources) {
            setResourcesFor(getLabel(), resources);
            return this;
        }

        /**
         * Sets the given {@link Resource} to the specified {@code label} to this builder. A previous entry with the
         * same {@code resourcePath} in the {@link Label} is replaced by the specified one.
         *
         * @param resource the Resource to set for the PolicyEntry identified by the {@code label}.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default LabelScoped setResource(final Resource resource) {
            setResourceFor(getLabel(), resource);
            return this;
        }

        /**
         * Removes the resource identified by the specified {@code resourceKey} from this builder.
         *
         * @param resourceKey the ResourceKey to remove from the PolicyEntry identified by the {@code label}.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default LabelScoped removeResource(final ResourceKey resourceKey) {
            removeResourceFor(getLabel(), resourceKey);
            return this;
        }

        /**
         * Removes the specified {@code resource} from this builder.
         *
         * @param resource the Resource to remove from the PolicyEntry identified by the {@code label}.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default LabelScoped removeResource(final Resource resource) {
            removeResourceFor(getLabel(), resource);
            return this;
        }

        /**
         * Set the given {@link EffectedPermissions} on the specified {@code resourceKey} to this builder.
         *
         * @param resourceKey the ResourceKey to set the effected permissions on.
         * @param effectedPermissions the EffectedPermissions to set on the resource in the label.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default LabelScoped setPermissions(final ResourceKey resourceKey,
                final EffectedPermissions effectedPermissions) {
            setPermissionsFor(getLabel(), resourceKey, effectedPermissions);
            return this;
        }

        /**
         * Set the given {@link Permissions} on the specified {@code resourceType} and {@code resourcePath}
         * as "granted" to this builder.
         *
         * @param resourceType the type of the Resource to set the permissions on.
         * @param resourcePath the path of the Resource to set the permissions on.
         * @param grantedPermissions the Permissions to set as "grant"ed on the Resource in the label.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default LabelScoped setGrantedPermissions(final String resourceType, final CharSequence resourcePath,
                final Permissions grantedPermissions) {
            setGrantedPermissions(resourceType, JsonPointer.of(resourcePath), grantedPermissions);
            return this;
        }

        /**
         * Set the given {@link Permissions} on the specified {@code resourceType} and {@code resourcePath} as "granted"
         * to this builder.
         *
         * @param resourceType the type of the Resource to set the permissions on.
         * @param resourcePath the path of the Resource to set the permissions on.
         * @param grantedPermissions the Permissions to set as "grant"ed on the Resource in the label.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         * @throws IllegalArgumentException if {@code resourceType} is empty.
         */
        default LabelScoped setGrantedPermissions(final String resourceType, final JsonPointer resourcePath,
                final Permissions grantedPermissions) {

            setGrantedPermissionsFor(getLabel(), resourceType, resourcePath, grantedPermissions);
            return this;
        }

        /**
         * Set the given {@link Permissions} on the specified {@code resourceKey}
         * as "granted" to this builder.
         *
         * @param resourceKey the ResourceKey to set the permissions on.
         * @param grantedPermissions the Permissions to set as "grant"ed on the resource in the label.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default LabelScoped setGrantedPermissions(final ResourceKey resourceKey, final Permissions grantedPermissions) {
            setGrantedPermissionsFor(getLabel(), resourceKey, grantedPermissions);
            return this;
        }

        /**
         * Set the given {@link String}s on the specified {@code resourceKey}
         * as "granted" to this builder.
         *
         * @param resourceKey the ResourceKey to set the permissions on.
         * @param grantedPermission the Permission to set as "grant"ed on the Resource in the label.
         * @param furtherGrantedPermissions further Permissions to set as "grant"ed on the Resource in the label.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default LabelScoped setGrantedPermissions(final ResourceKey resourceKey, final String grantedPermission,
                final String... furtherGrantedPermissions) {

            setGrantedPermissionsFor(getLabel(), resourceKey, grantedPermission, furtherGrantedPermissions);
            return this;
        }

        /**
         * Set the given permissions on the specified {@code resourceType} and {@code resourcePath} as "granted" to
         * this builder.
         *
         * @param resourceType the type of the Resource to set the permissions on.
         * @param resourcePath the path of the Resource to set the permissions on.
         * @param grantedPermission the Permission to set as "grant"ed on the Resource in the label.
         * @param furtherGrantedPermissions further Permissions to set as "grant"ed on the Resource in the label.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         * @throws IllegalArgumentException if {@code resourceType} is empty.
         */
        default LabelScoped setGrantedPermissions(final String resourceType,
                final CharSequence resourcePath,
                final String grantedPermission,
                final String... furtherGrantedPermissions) {

            setGrantedPermissions(resourceType, JsonPointer.of(resourcePath), grantedPermission,
                    furtherGrantedPermissions);
            return this;
        }

        /**
         * Set the given permissions on the specified {@code resourcePath} as "granted" to this
         * builder.
         *
         * @param resourceType the type of the Resource to set the permissions on.
         * @param resourcePath the path of the Resource to set the permissions on.
         * @param grantedPermission the Permission to set as "grant"ed on the Resource in the label.
         * @param furtherGrantedPermissions further Permissions to set as "grant"ed on the Resource in the label.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         * @throws IllegalArgumentException if {@code resourceType} is empty.
         */
        default LabelScoped setGrantedPermissions(final String resourceType,
                final JsonPointer resourcePath,
                final String grantedPermission,
                final String... furtherGrantedPermissions) {

            setGrantedPermissionsFor(getLabel(), resourceType, resourcePath, grantedPermission,
                    furtherGrantedPermissions);
            return this;
        }

        /**
         * Set the given permissions on the specified {@code resourceType} and {@code resourcePath} as "revoked" to this
         * builder.
         *
         * @param resourceType the type of the Resource to set the permissions on.
         * @param resourcePath the path of the Resource to set the permissions on.
         * @param revokedPermissions the Permissions to set as "revoke"ed on the Resource in the label.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default LabelScoped setRevokedPermissions(final String resourceType, final CharSequence resourcePath,
                final Permissions revokedPermissions) {

            setRevokedPermissions(resourceType, JsonPointer.of(resourcePath), revokedPermissions);
            return this;
        }

        /**
         * Set the given permissions on the specified {@code resourceType} and {@code resourcePath} as "revoked" to
         * this builder.
         *
         * @param resourceType the type of the Resource to set the permissions on.
         * @param resourcePath the path of the Resource to set the permissions on.
         * @param revokedPermissions the Permissions to set as "revoke"ed on the Resource in the label.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default LabelScoped setRevokedPermissions(final String resourceType, final JsonPointer resourcePath,
                final Permissions revokedPermissions) {
            setRevokedPermissionsFor(getLabel(), resourceType, resourcePath, revokedPermissions);
            return this;
        }

        /**
         * Set the given permissions on the specified {@code resourceKey} as "revoked" to this builder.
         *
         * @param resourceKey the ResourceKey to set the permissions on.
         * @param revokedPermissions the Permissions to set as "revoke"ed on the resource in the label.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default LabelScoped setRevokedPermissions(final ResourceKey resourceKey, final Permissions revokedPermissions) {
            setRevokedPermissionsFor(getLabel(), resourceKey, revokedPermissions);
            return this;
        }

        /**
         * Set the given permissions on the specified {@code resourceKey} as "revoked" to this builder.
         *
         * @param resourceKey the ResourceKey to set the permissions on.
         * @param revokedPermission the Permission to set as "revoke"ed on the Resource in the label.
         * @param furtherRevokedPermissions further Permissions to set as "revoke"ed on the Resource in the label.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default LabelScoped setRevokedPermissions(final ResourceKey resourceKey, final String revokedPermission,
                final String... furtherRevokedPermissions) {
            setRevokedPermissionsFor(getLabel(), resourceKey, revokedPermission, furtherRevokedPermissions);
            return this;
        }

        /**
         * Set the given permissions on the specified {@code resourceType} and {@code resourcePath} as "revoked" to this
         * builder.
         *
         * @param resourceType the type of the Resource to set the permissions on.
         * @param resourcePath the path of the Resource to set the permissions on.
         * @param revokedPermission the Permission to set as "revoke"ed on the Resource in the label.
         * @param furtherRevokedPermissions further Permissions to set as "revoke"ed on the Resource in the label.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default LabelScoped setRevokedPermissions(final String resourceType, final CharSequence resourcePath,
                final String revokedPermission, final String... furtherRevokedPermissions) {
            setRevokedPermissions(resourceType, JsonPointer.of(resourcePath), revokedPermission,
                    furtherRevokedPermissions);
            return this;
        }

        /**
         * Set the given permissions on the specified {@code resourceType} and {@code resourcePath} as "revoked"
         * to this builder.
         *
         * @param resourceType the type of the Resource to set the permissions on.
         * @param resourcePath the path of the Resource to set the permissions on.
         * @param revokedPermission the Permission to set as "revoke"ed on the Resource in the label.
         * @param furtherRevokedPermissions further Permissions to set as "revoke"ed on the Resource in the label.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         */
        default LabelScoped setRevokedPermissions(final String resourceType, final JsonPointer resourcePath,
                final String revokedPermission, final String... furtherRevokedPermissions) {
            setRevokedPermissionsFor(getLabel(), resourceType, resourcePath, revokedPermission,
                    furtherRevokedPermissions);
            return this;
        }

        /**
         * Set the importable flag on this builder.
         *
         * @param importableType the importable type.
         * @return this builder to allow method chaining.
         * @throws NullPointerException if any argument is {@code null}.
         * @since 3.1.0
         */
        default LabelScoped setImportable(final ImportableType importableType) {
            setImportableFor(getLabel(), importableType);
            return this;
        }

        /**
         * Exits the currently provided {@link Label} to the {@link PolicyBuilder} level again where a new
         * {@link #forLabel(Label)} can be done.
         *
         * @return the non-Label scoped PolicyBuilder.
         */
        PolicyBuilder exitLabel();
    }

    /**
     * Returns a {@code label} scoped PolicyBuilder where the {@link Label} can be omitted in the builder methods.
     *
     * @param label the Label to scope to.
     * @return the label scoped PolicyBuilder.
     */
    default LabelScoped forLabel(final CharSequence label) {
        return forLabel(Label.of(label));
    }

    /**
     * Returns a {@code label} scoped PolicyBuilder where the {@link Label} can be omitted in the builder methods.
     *
     * @param label the Label to scope to.
     * @return the label scoped PolicyBuilder.
     */
    LabelScoped forLabel(Label label);

    /**
     * Sets the Policy ID. The previous ID is overwritten.
     *
     * @param id the Policy ID to set.
     * @return this builder to allow method chaining.
     */
    PolicyBuilder setId(PolicyId id);

    /**
     * Sets the given lifecycle to this builder.
     *
     * @param lifecycle the lifecycle to be set.
     * @return this builder to allow method chaining.
     */
    PolicyBuilder setLifecycle(@Nullable PolicyLifecycle lifecycle);

    /**
     * Sets the given revision number to this builder.
     *
     * @param revisionNumber the revision number to be set.
     * @return this builder to allow method chaining.
     */
    default PolicyBuilder setRevision(final long revisionNumber) {
        return setRevision(PolicyRevision.newInstance(revisionNumber));
    }

    /**
     * Sets the given revision to this builder.
     *
     * @param revision the revision to be set.
     * @return this builder to allow method chaining.
     */
    PolicyBuilder setRevision(@Nullable PolicyRevision revision);

    /**
     * Sets the given modified timestamp to this builder.
     *
     * @param modified the timestamp to be set.
     * @return this builder to allow method chaining.
     */
    PolicyBuilder setModified(@Nullable Instant modified);

    /**
     * Sets the given created timestamp to this builder.
     *
     * @param created the created timestamp to be set.
     * @return this builder to allow method chaining.
     * @since 1.2.0
     */
    PolicyBuilder setCreated(@Nullable Instant created);

    /**
     * Sets the given metadata to this builder.
     *
     * @param metadata the metadata to be set.
     * @return this builder to allow method chaining.
     * @since 2.0.0
     */
    PolicyBuilder setMetadata(@Nullable Metadata metadata);

    /**
     * Sets the PolicyImport to this builder.
     *
     * @param policyImport the PolicyImport to be set.
     * @return this builder to allow method chaining.
     * @since 3.1.0
     */
    PolicyBuilder setPolicyImport(PolicyImport policyImport);


    /**
     * Sets the PolicyImports to this builder.
     *
     * @param imports the PolicyImports to be set.
     * @return this builder to allow method chaining.
     * @since 3.1.0
     */
    PolicyBuilder setPolicyImports(PolicyImports imports);

    /**
     * Sets the given entry to this builder. A previous entry with the same {@link Label} as the one of the
     * provided {@code entry} is replaced.
     *
     * @param entry the entry to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code entry} is {@code null}.
     */
    PolicyBuilder set(PolicyEntry entry);

    /**
     * Sets the given entries to this builder. All previous entries with the same {@link Label}s as the ones of the
     * provided {@code entries} are replaced. Be aware: if there are several entries with the same {@link Label} in the
     * given Iterable, later entries will replace earlier ones.
     *
     * @param entries the entries to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code entries} is {@code null}.
     */
    PolicyBuilder setAll(Iterable<PolicyEntry> entries);

    /**
     * Removes the entry identified by the passed {@code label} from this builder.
     *
     * @param label the label to be removed.
     * @return label builder to allow method chaining.
     * @throws NullPointerException if {@code label} is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    PolicyBuilder remove(CharSequence label);

    /**
     * Removes the given entry from this builder.
     *
     * @param entry the entry to be removed.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code entry} is {@code null}.
     */
    default PolicyBuilder remove(final PolicyEntry entry) {
        return remove(checkNotNull(entry, "entry to be removed").getLabel());
    }

    /**
     * Removes all entries from this builder which have the same {@link Label} like the given entries.
     *
     * @param entries the entries to be removed.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code entries} is {@code null}.
     */
    PolicyBuilder removeAll(Iterable<PolicyEntry> entries);

    /**
     * Sets the given {@link Subjects} to the specified {@code label} to this builder. All previous entries with the
     * same {@code subjectId} in the {@link Label} are replaced by the specified ones.
     *
     * @param label the Label identifying the PolicyEntry to modify.
     * @param subjects the Subjects to set for the PolicyEntry identified by the {@code label}.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    PolicyBuilder setSubjectsFor(CharSequence label, Subjects subjects);

    /**
     * Sets the given {@link Subject} to the specified {@code label} to this builder. A previous entry with the same
     * {@code issuer} and {@code subject} in the {@link Label} is replaced by the specified one.
     *
     * @param label the Label identifying the PolicyEntry to modify.
     * @param issuer the SubjectId's {@code issuer}.
     * @param subject the character sequence for the SubjectId's {@code subject}.
     * @param subjectType the type of the subject to set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    default PolicyBuilder setSubjectFor(final CharSequence label,
            final SubjectIssuer issuer,
            final CharSequence subject,
            final SubjectType subjectType) {

        return setSubjectFor(label, Subject.newInstance(issuer, subject, subjectType));
    }

    /**
     * Sets the given {@link Subject} to the specified {@code label} to this builder. A previous entry with the same
     * {@code subjectIssuerWithId} in the {@link Label} is replaced by the specified one.
     *
     * @param label the Label identifying the PolicyEntry to modify.
     * @param subjectIssuerWithId the Subject issuer + Subject ID (separated with a "{@value
     * SubjectId#ISSUER_DELIMITER}") of the Subject to set.
     * @param subjectType the type of the subject to set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    default PolicyBuilder setSubjectFor(final CharSequence label, final CharSequence subjectIssuerWithId,
            final SubjectType subjectType) {

        return setSubjectFor(label, Subject.newInstance(subjectIssuerWithId, subjectType));
    }

    /**
     * Sets the given {@link Subject} to the specified {@code label} to this builder. A previous entry with the same
     * {@code subjectId} in the {@link Label} is replaced by the specified one.
     *
     * @param label the Label identifying the PolicyEntry to modify.
     * @param subject the Subject to set for the PolicyEntry identified by the {@code label}.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    PolicyBuilder setSubjectFor(CharSequence label, Subject subject);

    /**
     * Removes the subject identified by the specified {@code subjectIssuerWithId} from this builder.
     *
     * @param label the Label identifying the PolicyEntry to modify.
     * @param subjectIssuerWithId the Subject issuer + Subject ID (separated with a "{@value
     * SubjectId#ISSUER_DELIMITER}") of the Subject to remove from the PolicyEntry identified by the {@code label}.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if any argument is empty.
     */
    PolicyBuilder removeSubjectFor(CharSequence label, CharSequence subjectIssuerWithId);

    /**
     * Removes the subject identified by the specified {@code issuer} and {@code subject} from this builder.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param issuer the SubjectId's {@code issuer} to remove from the PolicyEntry identified by the {@code label}.
     * @param subject the character sequence for the SubjectId's {@code subject} to remove from the PolicyEntry
     * identified by the {@code label}.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    default PolicyBuilder removeSubjectFor(final CharSequence label, final SubjectIssuer issuer,
            final CharSequence subject) {

        return removeSubjectFor(label, SubjectId.newInstance(issuer, subject));
    }

    /**
     * Removes the specified {@code subject} from this builder.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param subject the Subject to remove from the PolicyEntry identified by the {@code label}.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    PolicyBuilder removeSubjectFor(CharSequence label, Subject subject);

    /**
     * Sets the given {@link Resources} to the specified {@code label} to this builder. All previous entries with the
     * same {@code resourceKey} in the label are replaced by the specified ones.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param resources the Resources to set for the PolicyEntry identified by the {@code label}.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    PolicyBuilder setResourcesFor(CharSequence label, Resources resources);

    /**
     * Sets the given {@link Resource} to the specified {@code label} to this builder. A previous entry with the same
     * {@code resourceKey} in the label is replaced by the specified one.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param resource the Resource to set for the PolicyEntry identified by the {@code label}.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    PolicyBuilder setResourceFor(CharSequence label, Resource resource);

    /**
     * Removes the resource identified by the specified {@code resourceKey} from this builder.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param resourceKey the ResourceKey to remove from the PolicyEntry identified by the {@code label}.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    PolicyBuilder removeResourceFor(CharSequence label, ResourceKey resourceKey);

    /**
     * Removes the specified {@code resource} from this builder.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param resource the Resource to remove from the PolicyEntry identified by the {@code label}.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    PolicyBuilder removeResourceFor(CharSequence label, Resource resource);

    /**
     * Set the given {@link EffectedPermissions} on the specified {@code resourceKey} in the specified {@code label}
     * to this builder.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param resourceKey the ResourceKey to set the effected permissions on.
     * @param effectedPermissions the EffectedPermissions to set on the resource in the label.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    PolicyBuilder setPermissionsFor(CharSequence label, ResourceKey resourceKey,
            EffectedPermissions effectedPermissions);

    /**
     * Set the given permissions on the specified {@code resourcePath} in the specified {@code label}
     * as "granted" to this builder.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param resourceType the type of the Resource to set the permissions on.
     * @param resourcePath the path of the Resource to set the permissions on.
     * @param grantedPermissions the Permissions to set as "grant"ed on the Resource in the label.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} or {@code resourceType} is empty.
     */
    default PolicyBuilder setGrantedPermissionsFor(final CharSequence label,
            final String resourceType,
            final CharSequence resourcePath,
            final Permissions grantedPermissions) {

        return setGrantedPermissionsFor(label, ResourceKey.newInstance(resourceType, resourcePath), grantedPermissions);
    }

    /**
     * Set the given permissions on the specified {@code resourceKey} in the specified {@code label}
     * as "granted" to this builder.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param resourceKey the ResourceKey to set the permissions on.
     * @param grantedPermissions the Permissions to set as "grant"ed on the resource in the label.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     */
    PolicyBuilder setGrantedPermissionsFor(CharSequence label, ResourceKey resourceKey, Permissions grantedPermissions);

    /**
     * Set the given permissions on the specified {@code resourceType} and {@code resourcePath} in the specified
     * {@code label} as "granted" to this builder.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param resourceType the type of the Resource to set the permissions on.
     * @param resourcePath the path of the Resource to set the permissions on.
     * @param grantedPermission the Permission to set as "grant"ed on the Resource in the label.
     * @param furtherGrantedPermissions further Permissions to set as "grant"ed on the Resource in the label.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} or {@code resourceType} is empty.
     */
    default PolicyBuilder setGrantedPermissionsFor(final CharSequence label,
            final String resourceType,
            final CharSequence resourcePath,
            final String grantedPermission,
            final String... furtherGrantedPermissions) {

        return setGrantedPermissionsFor(label, ResourceKey.newInstance(resourceType, resourcePath),
                Permissions.newInstance(grantedPermission, furtherGrantedPermissions));
    }

    /**
     * Set the given permissions on the specified {@code resourceKey} in the specified {@code label}
     * as "granted" to this builder.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param resourceKey the ResourceKey to set the permissions on.
     * @param grantedPermission the Permission to set as "grant"ed on the resource in the label.
     * @param furtherGrantedPermissions further Permissions to set as "grant"ed on the resource in the label.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    default PolicyBuilder setGrantedPermissionsFor(final CharSequence label,
            final ResourceKey resourceKey,
            final String grantedPermission,
            final String... furtherGrantedPermissions) {

        return setGrantedPermissionsFor(label, resourceKey, Permissions.newInstance(grantedPermission,
                furtherGrantedPermissions));
    }

    /**
     * Set the given permissions on the specified {@code resourceType} and {@code resourcePath} in the specified
     * {@code label} as "revoked" to this builder.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param resourceType the type of the Resource to set the permissions on.
     * @param resourcePath the path of the Resource to set the permissions on.
     * @param revokedPermissions the Permissions to set as "revoke"ed on the Resource in the label.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} or {@code resourceType} is empty.
     */
    default PolicyBuilder setRevokedPermissionsFor(final CharSequence label,
            final String resourceType,
            final CharSequence resourcePath,
            final Permissions revokedPermissions) {

        return setRevokedPermissionsFor(label, ResourceKey.newInstance(resourceType, resourcePath), revokedPermissions);
    }

    /**
     * Set the given permissions on the specified {@code resourceKey} in the specified {@code label}
     * as "revoked" to this builder.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param resourceKey the ResourceKey to set the permissions on.
     * @param revokedPermissions the Permissions to set as "revoke"ed on the resource in the label.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     */
    PolicyBuilder setRevokedPermissionsFor(CharSequence label, ResourceKey resourceKey, Permissions revokedPermissions);

    /**
     * Set the given permissions on the specified {@code resourceType} and {@code resourcePath} in the specified
     * {@code label} as "revoked" to this builder.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param resourceType the type of the Resource to set the permissions on.
     * @param resourcePath the path of the Resource to set the permissions on.
     * @param revokedPermission the Permission to set as "revoke"ed on the Resource in the label.
     * @param furtherRevokedPermissions further Permissions to set as "revoke"ed on the Resource in the label.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} or {@code resourceType} is empty.
     */
    default PolicyBuilder setRevokedPermissionsFor(final CharSequence label,
            final String resourceType,
            final CharSequence resourcePath,
            final String revokedPermission,
            final String... furtherRevokedPermissions) {

        return setRevokedPermissionsFor(label,
                ResourceKey.newInstance(resourceType, JsonPointer.of(resourcePath)),
                Permissions.newInstance(revokedPermission, furtherRevokedPermissions));
    }

    /**
     * Set the given permissions on the specified {@code resourceKey} in the specified {@code label}
     * as "revoked" to this builder.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param resourceKey the ResourceKey to set the permissions on.
     * @param revokedPermission the Permission to set as "revoke"ed on the resource in the label.
     * @param furtherRevokedPermissions further Permissions to set as "revoke"ed on the resource in the label.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     */
    default PolicyBuilder setRevokedPermissionsFor(final CharSequence label,
            final ResourceKey resourceKey,
            final String revokedPermission,
            final String... furtherRevokedPermissions) {

        return setRevokedPermissionsFor(label, resourceKey,
                Permissions.newInstance(revokedPermission, furtherRevokedPermissions));
    }

    /**
     * Sets the importable flag for the entry specified by {@code label} to this builder.
     *
     * @param label the label identifying the PolicyEntry to modify.
     * @param importableType whether/how the entry is importable by others.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code label} is empty.
     * @since 3.1.0
     */
    PolicyBuilder setImportableFor(CharSequence label, ImportableType importableType);

    /**
     * Returns a new immutable {@link Policy} which contains all the entries which were set to this builder beforehand.
     *
     * @return a new {@code Policy}.
     */
    Policy build();

}
