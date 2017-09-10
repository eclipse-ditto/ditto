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

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;


/**
 * A mutable builder for a {@link ImmutableAccessControlList} with a fluent API.
 */
@NotThreadSafe
final class ImmutableAccessControlListBuilder implements AccessControlListBuilder {

    private final Map<AuthorizationSubject, AclEntry> entries;

    private ImmutableAccessControlListBuilder(final Map<AuthorizationSubject, AclEntry> theEntries) {
        entries = theEntries;
    }

    /**
     * Returns a new empty builder for a {@code AccessControlList}.
     *
     * @return the new builder.
     */
    public static AccessControlListBuilder newInstance() {
        return new ImmutableAccessControlListBuilder(new HashMap<>());
    }

    /**
     * Returns a new builder for a {@code AccessControlList} which is initialised with the given entries.
     *
     * @param aclEntries the initials entries of the new builder.
     * @return the new builder.
     * @throws NullPointerException if {@code aclEntries} is null;
     */
    public static AccessControlListBuilder of(final Iterable<AclEntry> aclEntries) {
        checkNotNull(aclEntries, "initial ACL entries");

        final Map<AuthorizationSubject, AclEntry> theEntries = new HashMap<>();
        aclEntries.forEach(e -> theEntries.put(e.getAuthorizationSubject(), e));

        return new ImmutableAccessControlListBuilder(theEntries);
    }

    @Override
    public AccessControlListBuilder set(final AclEntry entry) {
        checkNotNull(entry, "entry to be set");

        entries.put(entry.getAuthorizationSubject(), entry);

        return this;
    }

    @Override
    public AccessControlListBuilder setAll(final Iterable<AclEntry> entries) {
        checkNotNull(entries, "entries to be set");

        entries.forEach(e -> this.entries.put(e.getAuthorizationSubject(), e));

        return this;
    }

    @Override
    public AccessControlListBuilder remove(final AclEntry entry) {
        checkNotNull(entry, "entry to be removed");

        entries.remove(entry.getAuthorizationSubject());

        return this;
    }

    @Override
    public AccessControlListBuilder remove(final AuthorizationSubject authorizationSubject) {
        checkNotNull(authorizationSubject, "authorization subject of the entry to be removed");

        entries.remove(authorizationSubject);

        return this;
    }

    @Override
    public AccessControlListBuilder removeAll(final Iterable<AclEntry> entries) {
        checkNotNull(entries, "entries to be removed");

        entries.forEach(e -> this.entries.remove(e.getAuthorizationSubject()));

        return this;
    }

    @Override
    public AccessControlList build() {
        return AccessControlListModelFactory.newAcl(entries.values());
    }

}
