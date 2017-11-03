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

import java.util.Collection;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;


/**
 * Represents a single entry of an {@link AccessControlList} consisting of an Authorization Subject and one or more
 * permissions.
 */
public interface AclEntry extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns a new immutable {@code AclEntry} with the given authorization subject and permissions.
     *
     * @param authorizationSubject the authorization subject of the new ACL entry.
     * @param permission the permission of the new ACL entry.
     * @param furtherPermissions additional permission of the new ACL entry.
     * @return the new ACL entry.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static AclEntry newInstance(final AuthorizationSubject authorizationSubject, final Permission permission,
            final Permission... furtherPermissions) {
        return AccessControlListModelFactory.newAclEntry(authorizationSubject, permission, furtherPermissions);
    }

    /**
     * Returns a new immutable {@code AclEntry} with the given authorization subject and permissions.
     *
     * @param authorizationSubject the authorization subject of the new ACL entry.
     * @param permissions the permissions of the new ACL entry.
     * @return the new ACL entry.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static AclEntry newInstance(final AuthorizationSubject authorizationSubject,
            final Iterable<Permission> permissions) {
        return AccessControlListModelFactory.newAclEntry(authorizationSubject, permissions);
    }

    /**
     * AclEntry is only available in JsonSchemaVersion V_1.
     *
     * @return the supported JsonSchemaVersions of AclEntry.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_1};
    }


    /**
     * Returns the Authorization Subject of this entry.
     *
     * @return this entry's Authorization Subject.
     */
    AuthorizationSubject getAuthorizationSubject();

    /**
     * Indicates whether this ACL entry contains the specified permission or in other words if the Authorization Subject
     * of this entry is granted the specified permission.
     *
     * @param permission the permission to be checked for.
     * @return {@code true} if this entry contains {@code permission}, {@code false} else.
     */
    boolean contains(Permission permission);

    /**
     * Indicates whether this ACL entry contains the specified permissions or in other words if the Authorization
     * Subject of this entry is granted each of the specified permissions.
     *
     * @param permissions the permissions to be checked for.
     * @return {@code true} if this entry contains all specified permission, {@code false} else.
     */
    boolean containsAll(@Nullable Collection<Permission> permissions);

    /**
     * Returns the permissions of this entry.
     *
     * @return this entry's permissions. The returned set is sorted with the natural order of {@link Permission}'s
     * constants.
     */
    Permissions getPermissions();

    /**
     * Returns all non hidden marked fields of this ACL entry.
     *
     * @return a JSON object representation of this ACL entry including only non hidden marked fields.
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
