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
package org.eclipse.ditto.model.base.common;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.Entity;

/**
 * Validates an ID against {@link Entity#ID_REGEX}.
 * If the ID is invalid an {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException} is thrown.
 */
@Immutable
public abstract class EntityIdValidator extends AbstractIdValidator {

    protected EntityIdValidator() {
        super(Entity.ID_REGEX);
    }
}
