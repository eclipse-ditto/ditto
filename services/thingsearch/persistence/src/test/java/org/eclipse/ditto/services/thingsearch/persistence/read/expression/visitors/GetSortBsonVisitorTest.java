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
package org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors;

import java.util.Collections;

import org.bson.Document;
import org.eclipse.ditto.model.query.SortDirection;
import org.eclipse.ditto.model.query.SortOption;
import org.eclipse.ditto.model.query.expression.AttributeExpressionImpl;
import org.eclipse.ditto.model.query.expression.SortFieldExpression;
import org.junit.Test;

/**
 * Tests {@link org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors.GetSortBsonVisitor}.
 */
public final class GetSortBsonVisitorTest {

    @Test
    public void handlesNullValues() {
        final SortFieldExpression expression = new AttributeExpressionImpl("a/b/c/d/e/f/g");
        final SortOption sortOption = new SortOption(expression, SortDirection.ASC);

        GetSortBsonVisitor.sortValuesAsArray(new Document(), Collections.singletonList(sortOption));
    }
}
