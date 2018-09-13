/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
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
