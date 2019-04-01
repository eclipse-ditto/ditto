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
package org.eclipse.ditto.model.query.expression;

import org.eclipse.ditto.model.query.expression.visitors.FieldExpressionVisitor;
import org.eclipse.ditto.model.query.expression.visitors.FilterFieldExpressionVisitor;

/**
 * Field expression for global reads.
 */
class ThingsGlobalReadsFieldExpressionImpl implements FilterFieldExpression {

    @Override
    public <T> T acceptFilterVisitor(final FilterFieldExpressionVisitor<T> visitor) {
        return visitor.visitGlobalReads();
    }

    @Override
    public <T> T accept(final FieldExpressionVisitor<T> visitor) {
        return visitor.visitGlobalReads();
    }
}
