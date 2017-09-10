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

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Represents an association between for {@link AuthorizationSubject}s and {@link Permission}s.
 */
public interface AccessControlList extends Iterable<AclEntry>, Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns a mutable builder with a fluent API for an immutable {@code AccessControlList}.
     *
     * @return the new builder.
     */
    static AccessControlListBuilder newBuilder() {
        return AccessControlListModelFactory.newAclBuilder();
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@code AccessControlList}. The builder is
     * initialised with the ACL entries of this instance.
     *
     * @return the new builder.
     */
    default AccessControlListBuilder toBuilder() {
        return AccessControlListModelFactory.newAclBuilder(this.getEntriesSet());
    }

    /**
     * AccessControlList is only available in JsonSchemaVersion V_1.
     *
     * @return the supported JsonSchemaVersions of AccessControlList.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_1};
    }

    /**
     * Adds the specified entry to this ACL. The permissions of this entry are merged with possibly existing permissions
     * in this ACL for the authorization subject of the specified entry.
     *
     * @param entry the entry to be added.
     * @return a copy of this ACL which additionally contains the specified entry.
     * @throws NullPointerException if {@code entryToAdd} is {@code null}.
     */
    AccessControlList merge(AclEntry entry);

    /**
     * Creates an association between the specified authorization subject and one or more permissions. If there are
     * already permissions for the subject, the specified permissions are merged with them.
     *
     * @param authorizationSubject the authorization subject to be associated with the specified permission(s).
     * @param permission the permission to be associated with {@code authorizationSubject}.
     * @param furtherPermissions additional permissions to be associated with {@code authorizationSubject}.
     * @return a copy of this ACL which additionally contains at least one entry for the specified authorization subject
     * and permission(s).
     * @throws NullPointerException if any argument is {@code null}.
     */
    AccessControlList merge(AuthorizationSubject authorizationSubject, Permission permission,
            Permission... furtherPermissions);

    /**
     * Creates an associated between the specified permission and one or more authorization subjects. The specified
     * arguments are merged with existing associations.
     *
     * @param permission the permission to be associated with the specified authorization subject(s).
     * @param authorizationSubject the authorization subject to be associated with {@code permission}.)
     * @param furtherAuthorizationSubjects additional authorization subjects to be associated with {@code permission}.
     * @return a copy of this ACL which additionally contains at least one entry for the specified Access Control
     * Permission and authorization subject(s).
     * @throws NullPointerException if any argument is {@code null}.
     */
    AccessControlList merge(Permission permission, AuthorizationSubject authorizationSubject,
            AuthorizationSubject... furtherAuthorizationSubjects);

    /**
     * Sets the specified entry to a copy this ACL. A previous entry for the same authorization subject is replaced by
     * the specified one.
     *
     * @param aclEntry the entry to be set to this ACL.
     * @return a copy of this ACL with {@code aclEntry} set.
     * @throws NullPointerException if {@code aclEntry} is {@code null}.
     */
    AccessControlList setEntry(AclEntry aclEntry);

    /**
     * Sets the specified permissions to all authorization subjects of this ACL. All previous settings are overwritten.
     * Thus if the specified set of permissions is empty this ACL <em>is cleared completely.</em>
     *
     * @param permissions the permissions to set for all authorization subjects of this ACL.
     * @return a copy of this ACL with the {@code permissions} set for all authorization subjects or an empty ACL if
     * {@code permissions} is empty.
     * @throws NullPointerException if {@code permissions} is {@code null}.
     */
    AccessControlList setForAllAuthorizationSubjects(Permissions permissions);

    /**
     * Returns all authorization subjects for the specified permission(s), i. e. each returned subject has at least all
     * specified permissions.
     *
     * @param permission the permission to get all associated authorization subjects for.
     * @param furtherPermissions additional permissions to get all associated authorization subjects for.
     * @return an unsorted set containing all authorization subjects which are associated with {@code permission}. The
     * returned set is mutable but disjoint from this ACL; thus modifying the set does not have an impact on this ACL.
     * @throws NullPointerException if any argument is {@code null}.
     */
    Set<AuthorizationSubject> getAuthorizedSubjectsFor(Permission permission, Permission... furtherPermissions);

    /**
     * Indicates whether any {@link AuthorizationSubject} contained in the passed {@link AuthorizationContext} has at
     * least all of the specified permissions.
     *
     * @param authorizationContext the Authorization Context whose authorization subjects are to be searched for the
     * specified permissions.
     * @param permission the supposed permission of any authorization subject of {@code authorizationContext}.
     * @param furtherPermissions additional supposed permissions of any authorization subject of {@code
     * authorizationContext}.
     * @return {@code true} if the Authorization Context contains an authorization subject which has all of the
     * specified permissions.
     * @throws NullPointerException if any argument is {@code null}.
     */
    boolean hasPermission(AuthorizationContext authorizationContext, Permission permission,
            Permission... furtherPermissions);

    /**
     * Indicates whether the given {@link AuthorizationSubject} has at least all of the specified permissions.
     *
     * @param authorizationSubject the authorization subjects of which the permissions are checked.
     * @param permission the supposed permission.
     * @param furtherPermissions additional supposed permissions.
     * @return {@code true} if the authorization subject has all of the specified permissions.
     * @throws NullPointerException if any argument is {@code null}.
     */
    boolean hasPermission(AuthorizationSubject authorizationSubject, Permission permission,
            Permission... furtherPermissions);

    /**
     * Returns all authorization subjects for the specified permissions, i. e. each returned subject has at least all
     * specified permissions.
     *
     * @param expectedPermissions the permissions to get all associated authorization subjects for.
     * @return an unsorted set containing all authorization subjects which are associated with {@code permission}. The
     * returned set is mutable but disjoint from this ACL; thus modifying the set does not have an impact on this ACL.
     * @throws NullPointerException if {@code expectedPermissions} is {@code null}.
     */
    Set<AuthorizationSubject> getAuthorizedSubjectsFor(Permissions expectedPermissions);

    /**
     * Indicates whether this ACL contains permissions for the specified authorization subject.
     *
     * @param authorizationSubject the authorization subject to check if this ACL has permissions for.
     * @return {@code true} if this ACL contains permissions for {@code authorizationSubject}, {@code false} else.
     */
    boolean contains(AuthorizationSubject authorizationSubject);

    /**
     * Returns an ACL entry for the specified authorization subject.
     *
     * @param authorizationSubject the authorization subject get the ACL entry for.
     * @return the ACL entry.
     * @throws NullPointerException if {@code authorizationSubject} is {@code null}.
     */
    Optional<AclEntry> getEntryFor(AuthorizationSubject authorizationSubject);

    /**
     * Returns all permissions for the specified authorization subject.
     *
     * @param authorizationSubject the authorization subject to get all associated permissions for.
     * @return all permissions which are associated with {@code authorizationSubject}. The returned set is mutable but
     * disjoint from this ACL; thus modifying the set does not have an impact on this ACL.
     * @throws NullPointerException if {@code authorizationSubject} is {@code null}.
     */
    Permissions getPermissionsOf(AuthorizationSubject authorizationSubject);

    /**
     * Removes the specified entry from this ACL.
     *
     * @param entry the entry to be removed from this ACL.
     * @return a copy of this ACL which does not contain the specified entry anymore.
     * @throws NullPointerException if {@code entry} is {@code null}.
     */
    AccessControlList removeEntry(AclEntry entry);

    /**
     * Removes the given permission(s) from the entry of this ACL which has the given authorization subject. If after
     * removal the entry has no permissions left, it will be entirely removed from this ACL.
     *
     * @param authorizationSubject the authorization subject of the entry.
     * @param permission the permission of the entry to be removed.
     * @param furtherPermissions additional permissions to be removed.
     * @return a copy of this ACL with the changed state.
     * @throws NullPointerException if any argument is {@code null}.
     */
    AccessControlList removePermission(AuthorizationSubject authorizationSubject, Permission permission,
            Permission... furtherPermissions);

    /**
     * Removes all permissions which are associated to the specified authorization subject in this ACL.
     *
     * @param authorizationSubject the authorization subject of which all permissions are to be removed.
     * @return a copy of this ACL which does not contain any entries which are associated with the specified
     * authorization subject.
     * @throws NullPointerException if {@code authorizationSubject} is {@code null}.
     */
    AccessControlList removeAllPermissionsOf(AuthorizationSubject authorizationSubject);

    /**
     * Indicates whether this ACL is empty.
     *
     * @return {@code true} if this ACL does not contain any entry, {@code false} else.
     */
    boolean isEmpty();

    /**
     * Returns the amount of entries this ACL has.
     *
     * @return this ACL's entries amount.
     */
    int getSize();

    /**
     * Returns the entries of this ACL as set. The returned set is modifiable but disjoint from this ACL; thus modifying
     * the entry set has no impact on this ACL.
     *
     * @return an unsorted set of this ACL's entries.
     */
    Set<AclEntry> getEntriesSet();

    /**
     * Returns a sequential {@code Stream} with the entries of this ACL as its source.
     *
     * @return a sequential stream of the entries of this ACL.
     */
    Stream<AclEntry> stream();

    /**
     * Returns all non hidden marked fields of this ACL.
     *
     * @return a JSON object representation of this ACL including only non hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.regularOrSpecial()).get(fieldSelector);
    }

}
