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
package org.eclipse.ditto.model.things;


import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.model.base.exceptions.DittoJsonException.wrapJsonRuntimeException;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;

/**
 * Factory that creates new {@code org.eclipse.ditto.model.things.AccessControlList} objects.
 */
@Immutable
public final class AccessControlListModelFactory {

    /*
     * Inhibit instantiation of this utility class.
     */
    private AccessControlListModelFactory() {
        throw new AssertionError();
    }

    /**
     * Returns a new empty <em>mutable</em> {@link Permissions}.
     *
     * @return the new {@code Permissions}.
     */
    public static Permissions noPermissions() {
        return MutablePermissions.none();
    }

    /**
     * Returns a new <em>mutable</em> {@link Permissions} containing all available permissions.
     *
     * @return the new {@code Permissions}.
     * @see Permission#values()
     */
    public static Permissions allPermissions() {
        return MutablePermissions.all();
    }

    /**
     * Returns a new <em>mutable</em> {@link Permissions} containing the given permissions.
     *
     * @param permissions the permissions to initialise the result with.
     * @return the new {@code Permissions}.
     * @throws NullPointerException if {@code permissions} is {@code null};
     */
    public static Permissions newPermissions(final Collection<Permission> permissions) {
        return new MutablePermissions(permissions);
    }

    /**
     * Returns a new <em>mutable</em> {@link Permissions} containing the given permissions.
     *
     * @param permission the mandatory permission to be contained in the result.
     * @param furtherPermissions additional permissions to be contained in the result.
     * @return the new {@code Permissions}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Permissions newPermissions(final Permission permission, final Permission... furtherPermissions) {
        return MutablePermissions.of(permission, furtherPermissions);
    }

    /**
     * Returns a new unmodifiable {@link Permissions} containing the given permissions.
     *
     * @param permission the mandatory permission to be contained in the result.
     * @param furtherPermissions additional permissions to be contained in the result.
     * @return the new {@code Permissions}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static Permissions newUnmodifiablePermissions(final Permission permission,
            final Permission... furtherPermissions) {
        return new MutablePermissions(Collections.unmodifiableSet(EnumSet.of(permission, furtherPermissions)));
    }

    /**
     * Returns a new immutable {@link AclEntry} with the given authorization subject and permissions.
     *
     * @param authorizationSubject the authorization subject of the new ACL entry.
     * @param permission the permission of the new ACL entry.
     * @param furtherPermissions additional permission of the new ACL entry.
     * @return the new ACL entry.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AclEntry newAclEntry(final AuthorizationSubject authorizationSubject, final Permission permission,
            final Permission... furtherPermissions) {
        return ImmutableAclEntry.of(authorizationSubject, permission, furtherPermissions);
    }

    /**
     * Returns a new immutable {@link AclEntry} with the given authorization subject and permissions.
     *
     * @param authorizationSubject the authorization subject of the new ACL entry.
     * @param permissions the permissions of the new ACL entry.
     * @return the new ACL entry.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AclEntry newAclEntry(final AuthorizationSubject authorizationSubject,
            final Iterable<Permission> permissions) {
        return ImmutableAclEntry.of(authorizationSubject, permissions);
    }

    /**
     * Returns a new immutable {@link AclEntry} based on the given JSON object.
     *
     * @param jsonObject provides the initial values for the result.
     * @return the new ACL entry.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.model.base.exceptions.DittoJsonException if {@code jsonObject} cannot be parsed.
     */
    public static AclEntry newAclEntry(final JsonObject jsonObject) {
        return ImmutableAclEntry.fromJson(jsonObject);
    }

    /**
     * Returns a new immutable {@link AclEntry} with the given authorization subject identifier and the given JSON value
     * which provides the permissions.
     *
     * @param authorizationSubjectId the identifier of the authorization subject of the new ACL entry.
     * @param permissionsValue a JSON value which represents the permissions of the authorization subject of the new ACL
     * entry. This value is supposed to be a JSON object.
     * @return the new ACL entry.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.model.base.exceptions.DittoJsonException if {@code permissionsValue} is not a JSON object.
     * @throws AclEntryInvalidException if {@code permissionsValue} does not contain a
     * {@code boolean} value for the required permissions.
     */
    public static AclEntry newAclEntry(final CharSequence authorizationSubjectId, final JsonValue permissionsValue) {
        return ImmutableAclEntry.of(authorizationSubjectId, permissionsValue);
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@link AccessControlList}.
     *
     * @return the new builder.
     */
    public static AccessControlListBuilder newAclBuilder() {
        return ImmutableAccessControlListBuilder.newInstance();
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@link AccessControlList}. The builder is
     * initialised with the given ACL entries.
     *
     * @param aclEntries the initial entries of the new builder.
     * @return the new builder.
     * @throws NullPointerException if {@code aclEntries} is {@code null}.
     */
    public static AccessControlListBuilder newAclBuilder(final Iterable<AclEntry> aclEntries) {
        return ImmutableAccessControlListBuilder.of(aclEntries);
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@link AccessControlList}. The builder is
     * initialised with the given ACL entries.
     *
     * @param aclEntries the initial entries of the new builder.
     * @return the new builder.
     * @throws NullPointerException if {@code aclEntries} is {@code null}.
     */
    public static AccessControlListBuilder newAclBuilder(final Optional<? extends Iterable<AclEntry>> aclEntries) {
        checkNotNull(aclEntries, "optional ACL entries");
        return aclEntries.map(AccessControlListModelFactory::newAclBuilder)
                .orElseGet(AccessControlListModelFactory::newAclBuilder);
    }

    /**
     * Returns a new empty immutable {@link AccessControlList}.
     *
     * @return the new ACL.
     */
    public static AccessControlList emptyAcl() {
        return ImmutableAccessControlList.empty();
    }

    /**
     * Returns a new immutable Access Control List (ACL) which is initialised with the specified entries.
     *
     * @param entry the mandatory entry of the ACL.
     * @param furtherEntries additional entries of the ACL.
     * @return the new initialised Access Control List.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AccessControlList newAcl(final AclEntry entry, final AclEntry... furtherEntries) {
        return ImmutableAccessControlList.of(entry, furtherEntries);
    }

    /**
     * Returns a new immutable Access Control List (ACL) which is initialised with the specified entries.
     *
     * @param entries the entries of the ACL.
     * @return the new initialised Access Control List.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AccessControlList newAcl(final Iterable<AclEntry> entries) {
        return ImmutableAccessControlList.of(entries);
    }

    /**
     * Returns a new immutable Access Control List (ACL) based on the given JSON object.
     *
     * @param jsonObject the JSON object representation of an ACL.
     * @return the new initialised {@code AccessControlList}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.model.base.exceptions.DittoJsonException if {@code jsonObject} cannot be parsed to {@link
     * AccessControlList}.
     */
    public static AccessControlList newAcl(final JsonObject jsonObject) {
        return ImmutableAccessControlList.fromJson(jsonObject);
    }

    /**
     * Returns a new immutable Access Control List (ACL) based on the given JSON string.
     *
     * @param jsonString the JSON object representation of an ACL.
     * @return the new initialised {@code AccessControlList}.
     * @throws org.eclipse.ditto.model.base.exceptions.DittoJsonException if {@code jsonString} cannot be parsed to {@link
     * AccessControlList}.
     */
    public static AccessControlList newAcl(final String jsonString) {
        final JsonObject jsonObject = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return ImmutableAccessControlList.fromJson(jsonObject);
    }
}
