/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.entity.id;

import java.text.MessageFormat;

import org.eclipse.ditto.base.model.entity.type.EntityType;

/**
 * This class exists merely to have an implementation of EntityId to rely on for unit tests as other implementations are
 * not necessarily on the classpath.
 */
@TypedEntityId(type = EntityIdForTests.ENTITY_TYPE_STRING)
final class EntityIdForTests extends AbstractEntityId {

    static final String ENTITY_TYPE_STRING = "garply";
    static final EntityType ENTITY_TYPE = EntityType.of(ENTITY_TYPE_STRING);
    static final String INVALID_ID = "qux";

    private EntityIdForTests(final EntityType entityType, final CharSequence id) {
        super(entityType, id);
    }

    public static EntityIdForTests of(final CharSequence id) {
        if (INVALID_ID.equals(id)) {
            throw new EntityIdForTestsInvalidException(MessageFormat.format("<{0}> is invalid.", id), null);
        }
        return new EntityIdForTests(ENTITY_TYPE, id);
    }

}
