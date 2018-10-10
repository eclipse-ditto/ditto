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
package org.eclipse.ditto.services.utils.persistence.mongo.indices;

import org.bson.BsonValue;

/**
 * Interface for a MongoDB index key.
 */
public interface IndexKey {

    /**
     * Gets the field name.
     * @return the field name.
     */
    String getFieldName();

    /**
     * Gets the {@link BsonValue} defining the type of the key.
     * @return the {@link BsonValue}.
     */
    BsonValue getBsonValue();
}
