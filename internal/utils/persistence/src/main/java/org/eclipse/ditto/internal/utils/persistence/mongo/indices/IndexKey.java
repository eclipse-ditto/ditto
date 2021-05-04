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
package org.eclipse.ditto.internal.utils.persistence.mongo.indices;

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
