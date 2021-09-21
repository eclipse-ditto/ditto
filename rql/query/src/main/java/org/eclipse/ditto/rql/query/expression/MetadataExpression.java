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
package org.eclipse.ditto.rql.query.expression;

/**
 * Expression for metadata.
 *
 * @since 2.1.0
 */
public interface MetadataExpression extends FilterFieldExpression, ExistsFieldExpression {

    /**
     * Creates an expression for metadata.
     *
     * @param key the metadata key.
     * @return the created MetadataExpression
     * @throws NullPointerException if {@code key} is {@code null}.
     */
    static MetadataExpression of(final String key) {
        return new MetadataExpressionImpl(key);
    }

    /**
     * Returns the metadata key.
     *
     * @return the metadata key
     */
    String getKey();

}
