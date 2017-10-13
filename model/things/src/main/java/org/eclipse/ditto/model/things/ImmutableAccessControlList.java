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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;


/**
 * Immutable implementation of {@link AccessControlList}.
 */
@Immutable
final class ImmutableAccessControlList implements AccessControlList {

    private static final JsonFieldDefinition<Integer> JSON_SCHEMA_VERSION =
            JsonFactory.newIntFieldDefinition(JsonSchemaVersion.getJsonKey(), FieldType.SPECIAL, FieldType.HIDDEN,
                    JsonSchemaVersion.V_1);

    private final Map<AuthorizationSubject, AclEntry> entries;

    private ImmutableAccessControlList(final Map<AuthorizationSubject, AclEntry> theEntries) {
        entries = theEntries;
    }

    private ImmutableAccessControlList(final Collection<AclEntry> theEntries) {
        checkNotNull(theEntries, "ACL entries");

        entries = new HashMap<>();
        theEntries.forEach(e -> entries.put(e.getAuthorizationSubject(), e));
    }

    /**
     * Returns a new empty Access Control List (ACL).
     *
     * @return a new empty ACL.
     */
    public static AccessControlList empty() {
        return new ImmutableAccessControlList(Collections.emptySet());
    }

    /**
     * Returns a new Access Control List (ACL) which is initialised with the specified entries.
     *
     * @param entry the entries of the ACL.
     * @param furtherEntries additional entries of the ACL.
     * @return the new initialised Access Control List.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AccessControlList of(final AclEntry entry, final AclEntry... furtherEntries) {
        checkNotNull(entry, "mandatory ACL entry");
        checkNotNull(furtherEntries, "additional ACL entries");

        final Set<AclEntry> theEntries = new HashSet<>(1 + furtherEntries.length);
        theEntries.add(entry);
        Collections.addAll(theEntries, furtherEntries);

        return new ImmutableAccessControlList(theEntries);
    }

    /**
     * Returns a new Access Control List which is initialised with the specified entries.
     *
     * @param entries the entries of the ACL to be created.
     * @return a new initialised ACL.
     * @throws NullPointerException if {@code entries} is {@code null}.
     */
    public static AccessControlList of(final Iterable<AclEntry> entries) {
        checkNotNull(entries, "ACL entries");

        final Set<AclEntry> theEntries = new HashSet<>();
        entries.forEach(theEntries::add);

        return new ImmutableAccessControlList(theEntries);
    }

    /**
     * Creates a new {@code AccessControlList} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the ACL to be created.
     * @return a new ACL which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws AclEntryInvalidException if an ACL entry does not contain any known
     * permission which evaluates to {@code true} or {@code false}.
     */
    public static AccessControlList fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "JSON object");

        final Set<AclEntry> aclEntries = new HashSet<>();
        for (final JsonField jsonField : jsonObject) {
            final JsonKey jsonKey = jsonField.getKey();
            if (!jsonKey.equals(JsonSchemaVersion.getJsonKey())) {
                aclEntries.add(AccessControlListModelFactory.newAclEntry(jsonKey, jsonField.getValue()));
            }
        }
        return new ImmutableAccessControlList(aclEntries);
    }

    @Override
    public AccessControlList merge(final AclEntry entry) {
        checkNotNull(entry, "ACL entry to be added");

        final AuthorizationSubject authSubject = entry.getAuthorizationSubject();
        final Permissions permissions = getPermissionsOf(authSubject);
        permissions.addAll(entry.getPermissions());

        final Map<AuthorizationSubject, AclEntry> entriesCopy = copyEntries();
        entriesCopy.put(authSubject, AccessControlListModelFactory.newAclEntry(authSubject, permissions));

        return new ImmutableAccessControlList(entriesCopy);
    }

    @Override
    public AccessControlList merge(final AuthorizationSubject authorizationSubject, final Permission permission,
            final Permission... furtherPermissions) {
        checkNotNull(authorizationSubject, "authorization subject to be added");
        checkNotNull(permission, "permission of the authorization subject to be added");
        checkNotNull(furtherPermissions, "additional permissions to be added");

        final Permissions permissions = getPermissionsOf(authorizationSubject);
        permissions.add(permission);
        Collections.addAll(permissions, furtherPermissions);

        final Map<AuthorizationSubject, AclEntry> entriesCopy = copyEntries();
        entriesCopy.put(authorizationSubject,
                AccessControlListModelFactory.newAclEntry(authorizationSubject, permissions));

        return new ImmutableAccessControlList(entriesCopy);
    }

    @Override
    public AccessControlList merge(final Permission permission, final AuthorizationSubject authorizationSubject,
            final AuthorizationSubject... furtherAuthorizationSubjects) {
        checkNotNull(permission, "permission to be added");
        checkNotNull(authorizationSubject, "authorization subject to be added");
        checkNotNull(furtherAuthorizationSubjects, "additional authorization subjects to be added");

        final Collection<AuthorizationSubject> allAuthSubjects = new HashSet<>(1 + furtherAuthorizationSubjects.length);
        allAuthSubjects.add(authorizationSubject);
        Collections.addAll(allAuthSubjects, furtherAuthorizationSubjects);

        final Map<AuthorizationSubject, AclEntry> entriesCopy = copyEntries();

        allAuthSubjects.forEach(authSubject -> {
            final Permissions permissions = getPermissionsOf(authSubject);
            permissions.add(permission);

            entriesCopy.put(authSubject, AccessControlListModelFactory.newAclEntry(authSubject, permissions));
        });

        return new ImmutableAccessControlList(entriesCopy);
    }

    @Override
    public AccessControlList setEntry(final AclEntry aclEntry) {
        checkNotNull(aclEntry, "entry to be set to this ACL");

        final AccessControlList result;

        final AclEntry existingAclEntry = entries.get(aclEntry.getAuthorizationSubject());
        if (null != existingAclEntry) {
            if (existingAclEntry.equals(aclEntry)) {
                result = this;
            } else {
                final Map<AuthorizationSubject, AclEntry> entriesCopy = copyEntries();
                entriesCopy.put(aclEntry.getAuthorizationSubject(), aclEntry);
                result = new ImmutableAccessControlList(entriesCopy);
            }
        } else {
            final Map<AuthorizationSubject, AclEntry> entriesCopy = copyEntries();
            entriesCopy.put(aclEntry.getAuthorizationSubject(), aclEntry);
            result = new ImmutableAccessControlList(entriesCopy);
        }

        return result;
    }

    @Override
    public AccessControlList setForAllAuthorizationSubjects(final Permissions permissions) {
        checkNotNull(permissions, "set with the permissions to set");

        final AccessControlList result;

        if (entries.isEmpty()) {
            result = this;
        } else if (permissions.isEmpty()) {
            result = ImmutableAccessControlList.empty();
        } else {
            final Set<AclEntry> newAclEntries = stream() //
                    .map(AclEntry::getAuthorizationSubject) //
                    .map(authSubject -> AccessControlListModelFactory.newAclEntry(authSubject, permissions)) //
                    .collect(Collectors.toSet());
            result = of(newAclEntries);
        }

        return result;
    }

    @Override
    public Set<AuthorizationSubject> getAuthorizedSubjectsFor(final Permission permission,
            final Permission... furtherPermissions) {
        checkNotNull(permission, "permission to get the Authorized Subjects for");
        checkNotNull(furtherPermissions, "further permissions to get the authorization subjects for");

        return getAuthorizedSubjectsFor(AccessControlListModelFactory.newPermissions(permission, furtherPermissions));
    }

    @Override
    public boolean hasPermission(final AuthorizationContext authorizationContext, final Permission permission,
            final Permission... furtherPermissions) {
        checkNotNull(authorizationContext, "authorization context in which to check for the permissions");
        checkNotNull(permission, "permission to check for in the authorization context");
        checkNotNull(furtherPermissions, "further permissions to check for in the authorization context");

        // find out all the AuthorizationSubjects having the permissions asked for:
        final Set<AuthorizationSubject> authSubjectsHavingPermissions =
                getAuthorizedSubjectsFor(permission, furtherPermissions);

        return authorizationContext.getAuthorizationSubjects() //
                .stream() //
                .anyMatch(authSubjectsHavingPermissions::contains);
    }

    @Override
    public boolean hasPermission(final AuthorizationSubject authorizationSubject, final Permission permission,
            final Permission... furtherPermissions) {
        checkNotNull(authorizationSubject, "authorization subject of which the permissions are checked");
        checkNotNull(permission, "permission to check for in the authorization subject");
        checkNotNull(furtherPermissions, "further permissions to check for in the authorization subject");

        return getPermissionsOf(authorizationSubject).contains(permission, furtherPermissions);
    }

    @Override
    public Set<AuthorizationSubject> getAuthorizedSubjectsFor(final Permissions expectedPermissions) {
        checkNotNull(expectedPermissions, "expected permissions");

        return stream() //
                .filter(aclEntry -> aclEntry.containsAll(expectedPermissions)) //
                .map(AclEntry::getAuthorizationSubject) //
                .collect(Collectors.toSet());
    }

    @Override
    public boolean contains(final AuthorizationSubject authorizationSubject) {
        return entries.containsKey(authorizationSubject);
    }

    @Override
    public Optional<AclEntry> getEntryFor(final AuthorizationSubject authorizationSubject) {
        checkNotNull(authorizationSubject, "authorization subject to get as ACL entry");

        return Optional.ofNullable(entries.get(authorizationSubject));
    }

    @Override
    public Permissions getPermissionsOf(final AuthorizationSubject authorizationSubject) {
        checkNotNull(authorizationSubject, "authorization subject to get the permissions for");

        return getEntryFor(authorizationSubject) //
                .map(AclEntry::getPermissions) //
                .orElseGet(AccessControlListModelFactory::noPermissions);
    }

    @Override
    public AccessControlList removeEntry(final AclEntry entry) {
        checkNotNull(entry, "ACL entry to be removed");

        if (!entries.containsKey(entry.getAuthorizationSubject())) {
            return this;
        }

        final Map<AuthorizationSubject, AclEntry> entriesCopy = copyEntries();
        entriesCopy.remove(entry.getAuthorizationSubject());

        return new ImmutableAccessControlList(entriesCopy);
    }

    @Override
    public AccessControlList removePermission(final AuthorizationSubject authorizationSubject,
            final Permission permission, final Permission... furtherPermissions) {
        checkNotNull(authorizationSubject, "authorization subject");
        checkNotNull(permission, "permission");
        checkNotNull(furtherPermissions, "further permissions");

        final AclEntry existingEntry = entries.get(authorizationSubject);
        if (null != existingEntry) {
            final Permissions permissions = existingEntry.getPermissions();
            permissions.remove(permission);
            Stream.of(furtherPermissions).forEach(permissions::remove);

            final Map<AuthorizationSubject, AclEntry> entriesCopy = copyEntries();
            if (permissions.isEmpty()) {
                entriesCopy.remove(authorizationSubject);
            } else {
                entriesCopy.put(authorizationSubject,
                        AccessControlListModelFactory.newAclEntry(authorizationSubject, permissions));
            }
            return new ImmutableAccessControlList(entriesCopy);
        }

        return this;
    }

    @Override
    public AccessControlList removeAllPermissionsOf(final AuthorizationSubject authorizationSubject) {
        checkNotNull(authorizationSubject, "authorization subject to remove all permissions of");

        if (!entries.containsKey(authorizationSubject)) {
            return this;
        }

        final Map<AuthorizationSubject, AclEntry> entriesCopy = copyEntries();
        entriesCopy.remove(authorizationSubject);

        return new ImmutableAccessControlList(entriesCopy.values());
    }

    @Override
    public boolean isEmpty() {
        return 0 == getSize();
    }

    @Override
    public int getSize() {
        return entries.size();
    }

    @Override
    public Set<AclEntry> getEntriesSet() {
        return stream().collect(Collectors.toSet());
    }

    @Override
    public Stream<AclEntry> stream() {
        return entries.values().stream();
    }

    @Override
    public Iterator<AclEntry> iterator() {
        final Set<AclEntry> aclEntries = getEntriesSet();
        return aclEntries.iterator();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        return JsonFactory.newObjectBuilder() //
                .set(JSON_SCHEMA_VERSION, schemaVersion.toInt(), predicate) //
                .setAll(stream() //
                        .map(aclEntry -> aclEntry.toJson(schemaVersion, thePredicate)) //
                        .collect(JsonCollectors.objectsToObject()), predicate) //
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
        final ImmutableAccessControlList that = (ImmutableAccessControlList) o;
        return Objects.equals(entries, that.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entries);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "entries=" + entries + "]";
    }

    private Map<AuthorizationSubject, AclEntry> copyEntries() {
        return new HashMap<>(entries);
    }

}
