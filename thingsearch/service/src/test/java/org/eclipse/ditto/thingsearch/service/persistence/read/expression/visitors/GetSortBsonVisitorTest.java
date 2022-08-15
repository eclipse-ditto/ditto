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
package org.eclipse.ditto.thingsearch.service.persistence.read.expression.visitors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.bson.Document;
import org.eclipse.ditto.rql.query.SortDirection;
import org.eclipse.ditto.rql.query.SortOption;
import org.eclipse.ditto.rql.query.expression.AttributeExpression;
import org.eclipse.ditto.rql.query.expression.SortFieldExpression;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;
import org.junit.Test;

/**
 * Tests {@link GetSortBsonVisitor}.
 */
public final class GetSortBsonVisitorTest {

    @Test
    public void handlesNullValues() {
        final SortFieldExpression expression = AttributeExpression.of("a/b/c/d/e/f/g");
        final SortOption sortOption = new SortOption(expression, SortDirection.ASC);

        GetSortBsonVisitor.sortValuesAsArray(new Document(), List.of(sortOption));
    }

    @Test
    public void documentSortValuesAsArray() {
        final var document = new Document().append(PersistenceConstants.FIELD_THING,
                new Document().append("attributes",
                        new Document().append("sortKey", new Document().append("key", "value"))));
        final SortFieldExpression expression = AttributeExpression.of("sortKey");
        final SortOption sortOption = new SortOption(expression, SortDirection.ASC);

        final var result = GetSortBsonVisitor.sortValuesAsArray(document, List.of(sortOption));
        assertThat(result.toString()).hasToString("[{\"key\":\"value\"}]");
    }

}
