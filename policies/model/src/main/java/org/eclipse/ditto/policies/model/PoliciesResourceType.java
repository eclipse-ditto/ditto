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

import javax.annotation.concurrent.Immutable;

/**
 * Contains currently "known" policy resource types and convenience methods for creating {@link ResourceKey}s.
 */
@Immutable
public final class PoliciesResourceType {

    /**
     * Policy resource type.
     */
    public static final String POLICY = "policy";

    /**
     * Thing resource type.
     */
    public static final String THING = "thing";

    /**
     * Message resource type.
     */
    public static final String MESSAGE = "message";

    private PoliciesResourceType() {
        throw new AssertionError();
    }

    /**
     * Creates a {@link ResourceKey} for a Policy containing the passed {@code resourcePath}.
     *
     * @param resourcePath the Resource path to use for creating the ResourceKey.
     * @return the created ResourceKey for Policy.
     * @throws NullPointerException if {@code resourcePath} is {@code null}.
     */
    public static ResourceKey policyResource(final CharSequence resourcePath) {
        return PoliciesModelFactory.newResourceKey(POLICY, resourcePath);
    }

    /**
     * Creates a {@link ResourceKey} for a Thing containing the passed {@code resourcePath}.
     *
     * @param resourcePath the Resource path to use for creating the ResourceKey.
     * @return the created ResourceKey for Thing.
     * @throws NullPointerException if {@code resourcePath} is {@code null}.
     */
    public static ResourceKey thingResource(final CharSequence resourcePath) {
        return PoliciesModelFactory.newResourceKey(THING, resourcePath);
    }

    /**
     * Creates a {@link ResourceKey} for a Message containing the passed {@code resourcePath}.
     *
     * @param resourcePath the Resource path to use for creating the ResourceKey.
     * @return the created ResourceKey for Message.
     * @throws NullPointerException if {@code resourcePath} is {@code null}.
     */
    public static ResourceKey messageResource(final CharSequence resourcePath) {
        return PoliciesModelFactory.newResourceKey(MESSAGE, resourcePath);
    }

}
