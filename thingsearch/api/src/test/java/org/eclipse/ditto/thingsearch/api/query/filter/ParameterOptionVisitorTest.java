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
package org.eclipse.ditto.thingsearch.api.query.filter;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.rql.query.QueryBuilder;
import org.eclipse.ditto.rql.query.SortDirection;
import org.eclipse.ditto.rql.query.expression.FieldExpressionFactory;
import org.eclipse.ditto.rql.query.expression.SortFieldExpression;
import org.eclipse.ditto.thingsearch.model.LimitOption;
import org.eclipse.ditto.thingsearch.model.Option;
import org.eclipse.ditto.thingsearch.model.SearchModelFactory;
import org.eclipse.ditto.thingsearch.model.SortOption;
import org.eclipse.ditto.thingsearch.model.SortOptionEntry;
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


    @Before
    public void before() {
        qbMock = Mockito.mock(QueryBuilder.class, new AnswerWithSelf());
        visitor = new ParameterOptionVisitor(exprFactoryMock, qbMock);

        when(exprFactoryMock.sortBy(Mockito.any(String.class))).thenReturn(exprMock);
    }


    @Test(expected = NullPointerException.class)
    public void constructorWithNullExpressionFactory() {
        new ParameterOptionVisitor(null, qbMock);
    }


    @Test(expected = NullPointerException.class)
    public void constructorWithNullQueryBuilder() {
        new ParameterOptionVisitor(exprFactoryMock, null);
    }


    @Test
    public void visitLimitOption() {
        // test
        visitor.visitAll(Collections.singletonList(SearchModelFactory.newLimitOption(KNOWN_SKIP, KNOWN_LIMIT)));

        // verify
        verify(qbMock).skip(KNOWN_SKIP);
        verify(qbMock).limit(KNOWN_LIMIT);
    }


    @Test
    public void visitSortOptionWithSingleSortOption() {
        // prepare
        final SortOption sortOption = SearchModelFactory.newSortOption(POINTER_1, SortOptionEntry.SortOrder.ASC);

        // test
        visitor.visitAll(Collections.singletonList(sortOption));

        // verify
        verify(exprFactoryMock).sortBy(POINTER_1.toString());
        verify(qbMock).sort(
                Collections.singletonList(new org.eclipse.ditto.rql.query.SortOption(exprMock,
                        SortDirection.ASC)));
    }


    @Test(expected = IllegalArgumentException.class)
    public void visitUnsupportedSortExpression() {
        // prepare
        final JsonPointer unsupportedProperty = JsonPointer.of("unsupportedExpr");
        final SortOption sortOption = SearchModelFactory.newSortOption(unsupportedProperty,
                SortOptionEntry.SortOrder.ASC);

        when(exprFactoryMock.sortBy(unsupportedProperty.toString()))
                .thenThrow(new IllegalArgumentException("Unsupported expr"));

        // test
        visitor.visitAll(Collections.singletonList(sortOption));
    }


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
                Arrays.asList(new org.eclipse.ditto.rql.query.SortOption(exprMock,
                                SortDirection.DESC),
                        new org.eclipse.ditto.rql.query.SortOption(exprMock,
                                SortDirection.ASC)));
    }



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
                Collections.singletonList(new org.eclipse.ditto.rql.query.SortOption(exprMock,
                        SortDirection.ASC)));
    }

    @Test
    public void visitCursorAndSizeOptions() {
        final Option cursorOption = SearchModelFactory.newCursorOption("cursor");
        final Option sizeOption = SearchModelFactory.newSizeOption(123);

        visitor.visitAll(Arrays.asList(cursorOption, sizeOption));

        verify(qbMock).skip(0);
        verify(qbMock).size(123);
        verifyNoMoreInteractions(qbMock);
    }

}
