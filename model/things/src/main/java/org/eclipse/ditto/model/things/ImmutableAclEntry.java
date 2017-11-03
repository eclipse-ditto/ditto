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

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * An immutable implementation of {@link AclEntry}.
 */
@Immutable
final class ImmutableAclEntry implements AclEntry {

    private static final JsonFieldDefinition<Integer> JSON_SCHEMA_VERSION =
            JsonFactory.newIntFieldDefinition(JsonSchemaVersion.getJsonKey(), FieldType.SPECIAL, FieldType.HIDDEN,
                    JsonSchemaVersion.V_1);

    private final AuthorizationSubject authSubject;
    private final Permissions permissions;

    private ImmutableAclEntry(final AuthorizationSubject theAuthSubject, final Set<Permission> thePermissions) {
        authSubject = checkNotNull(theAuthSubject, "authorization subject");
        checkNotNull(thePermissions, "permissions");

        permissions = thePermissions.isEmpty() ? AccessControlListModelFactory.noPermissions()
                : AccessControlListModelFactory.newPermissions(thePermissions);
    }

    /**
     * Returns a new {@code AclEntry} object of the given permission and Authorization Subject.
     *
     * @param authSubject the Authorization Subject of the new ACL entry.
     * @param permission the permission of the new ACL entry.
     * @param furtherPermissions additional permissions of the new ACL entry.
     * @return a new {@code AclEntry} object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AclEntry of(final AuthorizationSubject authSubject, final Permission permission,
            final Permission... furtherPermissions) {
        checkNotNull(permission, "permission of this entry");
        checkNotNull(furtherPermissions, "further permissions of this entry");

        final Set<Permission> permissions = EnumSet.of(permission);
        Collections.addAll(permissions, furtherPermissions);

        return of(authSubject, permissions);
    }

    /**
     * Returns a new {@code AclEntry} object of the given permission and Authorization Subject.
     *
     * @param authSubject the Authorization Subject of the new ACL entry.
     * @param permissions the ACL permissions of the new ACL entry.
     * @return a new {@code AclEntry} object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AclEntry of(final AuthorizationSubject authSubject, final Iterable<Permission> permissions) {
        if (permissions instanceof Set) {
            return new ImmutableAclEntry(authSubject, (Set<Permission>) permissions);
        }

        final Set<Permission> permissionSet = EnumSet.noneOf(Permission.class);
        permissions.forEach(permissionSet::add);

        return new ImmutableAclEntry(authSubject, permissionSet);
    }

    /**
     * Creates a new {@code AclEntry} object based on the specified JSON key and JSON value.
     *
     * @param jsonKey the JSON key which is assumed to be the ID of an Authorization Subject.
     * @param jsonValue the JSON value containing the permissions for the Authorization Subject denoted by {@code
     * jsonKey}. This value is supposed to be a {@link JsonObject}.
     * @return a new {@code AclEntry} object which is initialised with the values extracted from {@code jsonKey} and
     * {@code jsonValue}.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws JsonParseException if {@code jsonValue} is not a JSON object or the JSON has not the expected format.
     * @throws AclEntryInvalidException if the ACL entry does not contain any known permission which evaluates to {@code
     * true} or {@code false}.
     */
    public static AclEntry of(final CharSequence jsonKey, final JsonValue jsonValue) {
        validate(jsonKey, jsonValue);
        final JsonObject permissionsJsonObject = jsonValue.asObject();
        final AuthorizationSubject authorizationSubject = AuthorizationModelFactory.newAuthSubject(jsonKey);
        final Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        for (final Permission permission : Permission.values()) {
            final Optional<JsonValue> permissionValue = permissionsJsonObject.getValue(permission.toJsonKey());
            validate(jsonKey, permission, permissionValue);
            if (permissionValue.map(JsonValue::asBoolean).orElse(false)) {
                permissions.add(permission);
            }
        }

        final AclEntry result = ImmutableAclEntry.of(authorizationSubject, permissions);
        validate(result);
        return result;
    }

    private static void validate(final CharSequence jsonKey, final JsonValue jsonValue) {
        checkNotNull(jsonKey, "JSON key");
        checkNotNull(jsonValue, "JSON value");

        final String msgTemplate =
                "Expected for Authorization Subject ''{0}'' a JSON object containing all of {1}" + " but got <{2}>!";
        final Supplier<String> descriptionSupplier =
                () -> MessageFormat.format(msgTemplate, jsonKey, Permission.allToString(), jsonValue);
        if (!jsonValue.isObject()) {
            throw new DittoJsonException(JsonParseException.newBuilder() //
                    .description(descriptionSupplier) //
                    .build());
        }
        final JsonObject permissionsJsonObject = jsonValue.asObject();
        if (permissionsJsonObject.isEmpty()) {
            throw AclEntryInvalidException.newBuilder() //
                    .description(descriptionSupplier) //
                    .build();
        }
    }

    private static void validate(final CharSequence authSubjectId, final Permission permission,
            final Optional<JsonValue> permissionValue) {

        if (permissionValue.isPresent()) {
            final JsonValue permissionJsonValue = permissionValue.get();
            if (!permissionJsonValue.isBoolean()) {
                final String descTemplate = "Expected for permission ''{0}'' of Authorization Subject ''{1}''"
                        + " the value <true> or <false> but got <{2}>!";

                throw AclEntryInvalidException.newBuilder()
                        .message(MessageFormat.format(descTemplate, permission, authSubjectId, permissionValue.get()))
                        .build();
            }
        } else {
            final String descTemplate = "Expected for Authorization Subject ''{0}'' the permission ''{1}''"
                    + " with value <true> or <false> but the permission is absent at all!";

            throw AclEntryInvalidException.newBuilder()
                    .message(MessageFormat.format(descTemplate, authSubjectId, permission))
                    .build();
        }
    }

    private static void validate(final AclEntry aclEntry) {
        final Permissions entryPermissions = aclEntry.getPermissions();
        if (entryPermissions.isEmpty()) {
            final String descTemplate =
                    "The ACL entry for ''{0}'' did not contain any permission of {1} which evaluates to <true>!";
            final AuthorizationSubject authorizationSubject = aclEntry.getAuthorizationSubject();
            final String allPermissions = Permission.allToString();

            throw AclEntryInvalidException.newBuilder()
                    .message(MessageFormat.format(descTemplate, authorizationSubject.getId(), allPermissions))
                    .build();
        }
    }

    /**
     * Creates a new {@code AclEntry} object from the specified JSON object. If, for any reason, the specified JSON
     * object contains more than one field with Authorization Subject/permissions pairs only the first field is used
     * while all remaining fields are ignored.
     *
     * @param jsonObject a JSON object which provides the data for the ACL entry to be created.
     * @return a new ACL entry which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws DittoJsonException if {@code jsonObject} <ul> <li>is empty,</li> <li>contains only a field with the schema
     * version</li> <li>or it contains more than two fields.</li> </ul>
     */
    public static AclEntry fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object");

        return jsonObject.stream() //
                .filter(field -> !Objects.equals(field.getKey(), JsonSchemaVersion.getJsonKey())) //
                .findFirst() //
                .map(field -> ImmutableAclEntry.of(field.getKey(), field.getValue())) //
                .orElseThrow(() -> new DittoJsonException(JsonMissingFieldException.newBuilder() //
                        .message("The JSON object is either empty or contains only fields with the schema version.") //
                        .build()));
    }

    @Override
    public AuthorizationSubject getAuthorizationSubject() {
        return authSubject;
    }

    @Override
    public boolean contains(final Permission permission) {
        return permissions.contains(permission);
    }

    @Override
    public boolean containsAll(@Nullable final Collection<Permission> permissions) {
        return (null != permissions) && this.permissions.containsAll(permissions);
    }

    @Override
    public Permissions getPermissions() {
        return AccessControlListModelFactory.newPermissions(permissions);
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        return JsonFactory.newObjectBuilder()
                .set(JSON_SCHEMA_VERSION, schemaVersion.toInt(), predicate)
                // Explicitly DON'T pass the predicate to permissions!
                .set(authSubject.getId(), permissions.toJson(schemaVersion))
                .build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableAclEntry aclEntry = (ImmutableAclEntry) o;
        return Objects.equals(authSubject, aclEntry.authSubject) && Objects.equals(permissions, aclEntry.permissions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(authSubject, permissions);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "authSubject=" + authSubject + ", permissions=" + permissions + "]";
    }

}
