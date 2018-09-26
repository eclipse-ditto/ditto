/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.policies;

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
