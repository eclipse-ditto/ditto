/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.base.model.entity.type.EntityType;

/**
 * Constants to support working with {@link Policy}.
 *
 * @since 1.1.0
 */
@Immutable
public final class PolicyConstants {

    private PolicyConstants() {
        throw new AssertionError();
    }

    /**
     * The type of a Policy entity.
     */
    public static final EntityType ENTITY_TYPE = EntityType.of("policy");

}
