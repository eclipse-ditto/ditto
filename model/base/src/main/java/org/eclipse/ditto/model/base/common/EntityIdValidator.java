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
