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

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;

/**
 * A mutable builder for a {@link AccessControlList} with a fluent API.
 */
@NotThreadSafe
public interface AccessControlListBuilder {

    /**
     * Sets the given entry to this builder. A previous entry with the same authorization subject is replaced.
     *
     * @param entry the entry to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code entry} is {@code null}.
     */
    AccessControlListBuilder set(AclEntry entry);

    /**
     * Sets the given entries to this builder. All previous entries with the same authorization subject are replaced.
     *
     * @param entries the entries to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code entries} is {@code null}.
     */
    AccessControlListBuilder setAll(Iterable<AclEntry> entries);

    /**
     * Removes the given entry from this builder. This is a convenience method for {@link
     * #remove(AuthorizationSubject)}.
     *
     * @param entry the entry to be removed.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code entry} is {@code null}.
     */
    AccessControlListBuilder remove(AclEntry entry);

    /**
     * Removes the entry from this builder which has the given authorization subject.
     *
     * @param authorizationSubject the authorization subject of the entry to be removed.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code authorizationSubject} is {@code null}.
     */
    AccessControlListBuilder remove(AuthorizationSubject authorizationSubject);

    /**
     * Removes all entries from this builder which have the same authorization subject like the given entries.
     *
     * @param entries the entries to be removed.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code entries} is {@code null}.
     */
    AccessControlListBuilder removeAll(Iterable<AclEntry> entries);

    /**
     * Returns a new immutable {@link AccessControlList} which contains all the entries which were set to this builder
     * beforehand.
     *
     * @return a new {@code AccessControlList}.
     */
    AccessControlList build();

}
