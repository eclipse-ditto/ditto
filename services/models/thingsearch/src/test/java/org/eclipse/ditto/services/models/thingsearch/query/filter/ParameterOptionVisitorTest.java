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
package org.eclipse.ditto.services.models.thingsearch.query.filter;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.query.QueryBuilder;
import org.eclipse.ditto.model.query.SortDirection;
import org.eclipse.ditto.model.query.expression.FieldExpressionFactory;
import org.eclipse.ditto.model.query.expression.SortFieldExpression;
import org.eclipse.ditto.model.thingsearch.LimitOption;
import org.eclipse.ditto.model.thingsearch.SearchModelFactory;
import org.eclipse.ditto.model.thingsearch.SortOption;
import org.eclipse.ditto.model.thingsearch.SortOptionEntry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link ParameterOptionVisitor}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ParameterOptionVisitorTest {

    private static final JsonPointer POINTER_2 = JsonPointer.of("/jsonpointer2");
    private static final JsonPointer POINTER_1 = JsonPointer.of("/jsonpointer1");

    private static final int KNOWN_LIMIT = 4;
    private static final int KNOWN_SKIP = 8;

    private QueryBuilder qbMock;
    @Mock
    private SortFieldExpression exprMock;
    @Mock
    private FieldExpressionFactory exprFactoryMock;

    private ParameterOptionVisitor visitor;

    /** */
    @Before
    public void before() {
        qbMock = Mockito.mock(QueryBuilder.class, new AnswerWithSelf());
        visitor = new ParameterOptionVisitor(exprFactoryMock, qbMock);

        when(exprFactoryMock.sortBy(Mockito.any(String.class))).thenReturn(exprMock);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void constructorWithNullExpressionFactory() {
        new ParameterOptionVisitor(null, qbMock);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void constructorWithNullQueryBuilder() {
        new ParameterOptionVisitor(exprFactoryMock, null);
    }

    /** */
    @Test
    public void visitLimitOption() {
        // test
        visitor.visitAll(Collections.singletonList(SearchModelFactory.newLimitOption(KNOWN_SKIP, KNOWN_LIMIT)));

        // verify
        verify(qbMock).skip(KNOWN_SKIP);
        verify(qbMock).limit(KNOWN_LIMIT);
    }

    /** */
    @Test
    public void visitSortOptionWithSingleSortOption() {
        // prepare
        final SortOption sortOption = SearchModelFactory.newSortOption(SortOptionEntry.SortOrder.ASC, POINTER_1);

        // test
        visitor.visitAll(Collections.singletonList(sortOption));

        // verify
        verify(exprFactoryMock).sortBy(POINTER_1.toString());
        verify(qbMock).sort(
                Collections.singletonList(new org.eclipse.ditto.model.query.SortOption(exprMock,
                        SortDirection.ASC)));
    }

    /** */
    @Test(expected = IllegalArgumentException.class)
    public void visitUnsupportedSortExpression() {
        // prepare
        final JsonPointer unsupportedProperty = JsonPointer.of("unsupportedExpr");
        final SortOption sortOption = SearchModelFactory.newSortOption(SortOptionEntry.SortOrder.ASC, unsupportedProperty);

        when(exprFactoryMock.sortBy(unsupportedProperty.toString()))
                .thenThrow(new IllegalArgumentException("Unsupported expr"));

        // test
        visitor.visitAll(Collections.singletonList(sortOption));
    }

    /** */
    @Test
    public void visitSortOptionMultiple() {
        // prepare
        SortOption sortOption = SearchModelFactory.newSortOption(Collections.emptyList());
        sortOption = sortOption.add(POINTER_2, SortOptionEntry.SortOrder.DESC);
        sortOption = sortOption.add(POINTER_1, SortOptionEntry.SortOrder.ASC);

        // test
        visitor.visitAll(Collections.singletonList(sortOption));

        // verify
        verify(exprFactoryMock).sortBy(POINTER_2.toString());
        verify(exprFactoryMock).sortBy(POINTER_1.toString());
        verify(qbMock).sort(
                Arrays.asList(new org.eclipse.ditto.model.query.SortOption(exprMock,
                                SortDirection.DESC),
                        new org.eclipse.ditto.model.query.SortOption(exprMock,
                                SortDirection.ASC)));
    }


    /** */
    @Test
    public void visitLimitOptionAndSortOption() {
        // test
        final LimitOption limitOption = SearchModelFactory.newLimitOption(KNOWN_SKIP, KNOWN_LIMIT);
        SortOption sortOption = SearchModelFactory.newSortOption(Collections.emptyList());
        sortOption = sortOption.add(POINTER_1, SortOptionEntry.SortOrder.ASC);

        // test
        visitor.visitAll(Arrays.asList(limitOption, sortOption));

        // verify
        verify(qbMock).skip(KNOWN_SKIP);
        verify(qbMock).limit(KNOWN_LIMIT);
        verify(exprFactoryMock).sortBy(POINTER_1.toString());
        verify(qbMock).sort(
                Collections.singletonList(new org.eclipse.ditto.model.query.SortOption(exprMock,
                        SortDirection.ASC)));
    }

}
