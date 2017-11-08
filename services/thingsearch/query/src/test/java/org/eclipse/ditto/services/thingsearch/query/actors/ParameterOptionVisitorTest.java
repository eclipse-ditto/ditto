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
package org.eclipse.ditto.services.thingsearch.query.actors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.eclipse.ditto.model.thingsearch.LimitOption;
import org.eclipse.ditto.model.thingsearch.SearchModelFactory;
import org.eclipse.ditto.model.thingsearch.SortOption;
import org.eclipse.ditto.model.thingsearch.SortOptionEntry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;

import org.eclipse.ditto.services.thingsearch.query.AnswerWithSelf;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FieldExpressionFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FieldExpressionUtil;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.SortFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.query.QueryBuilder;
import org.eclipse.ditto.services.thingsearch.querymodel.query.SortDirection;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link ParameterOptionVisitor}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ParameterOptionVisitorTest {

    private static final int KNOWN_LIMIT = 4;
    private static final int KNOWN_SKIP = 8;
    private static final String KNOWN_ATTR_KEY = "knownAttrKey";
    private QueryBuilder qbMock;
    @Mock
    private SortFieldExpression exprMock;
    @Mock
    private FieldExpressionFactory exprFactoryMock;

    private ParameterOptionVisitor visitor;

    /** */
    @Before
    public void before() {
        qbMock = mock(QueryBuilder.class, new AnswerWithSelf());
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
    public void visitSortOptionOwner() {
        // prepare
        SortOption sortOption = SearchModelFactory.newSortOption(Collections.emptyList());
        sortOption = sortOption.add(FieldExpressionUtil.FIELD_NAME_OWNER, SortOptionEntry.SortOrder.ASC);

        // test
        visitor.visitAll(Collections.singletonList(sortOption));

        // verify
        verify(exprFactoryMock).sortBy(FieldExpressionUtil.FIELD_NAME_OWNER);
        verify(qbMock).sort(
                Collections.singletonList(new org.eclipse.ditto.services.thingsearch.querymodel.query.SortOption(exprMock,
                        SortDirection.ASC)));
    }

    /** */
    @Test
    public void visitSortOptionThingId() {
        // prepare
        SortOption sortOption = SearchModelFactory.newSortOption(Collections.emptyList());
        sortOption = sortOption.add(FieldExpressionUtil.FIELD_NAME_THING_ID, SortOptionEntry.SortOrder.ASC);

        // test
        visitor.visitAll(Collections.singletonList(sortOption));

        // verify
        verify(exprFactoryMock).sortBy(FieldExpressionUtil.FIELD_NAME_THING_ID);
        verify(qbMock).sort(
                Collections.singletonList(
                        new org.eclipse.ditto.services.thingsearch.querymodel.query.SortOption(exprMock, SortDirection
                                .ASC)));
    }

    /** */
    @Test
    public void visitSortOptionAttribute() {
        // prepare
        SortOption sortOption = SearchModelFactory.newSortOption(Collections.emptyList());
        sortOption =
                sortOption.add(FieldExpressionUtil.addAttributesPrefix(KNOWN_ATTR_KEY), SortOptionEntry.SortOrder.ASC);

        // test
        visitor.visitAll(Collections.singletonList(sortOption));

        // verify
        verify(exprFactoryMock).sortBy(FieldExpressionUtil.addAttributesPrefix(KNOWN_ATTR_KEY));
        verify(qbMock).sort(
                Collections.singletonList(new org.eclipse.ditto.services.thingsearch.querymodel.query.SortOption(exprMock,
                        SortDirection.ASC)));
    }

    /** */
    @Test(expected = IllegalArgumentException.class)
    public void visitUnsupportedSortExpression() {
        // prepare
        final String unsupportedExpr = "unsupportedExpr";
        SortOption sortOption = SearchModelFactory.newSortOption(Collections.emptyList());
        sortOption = sortOption.add(unsupportedExpr, SortOptionEntry.SortOrder.ASC);

        when(exprFactoryMock.sortBy(unsupportedExpr)).thenThrow(new IllegalArgumentException("Unsupported expr"));

        // test
        visitor.visitAll(Collections.singletonList(sortOption));
    }

    /** */
    @Test
    public void visitSortOptionMultiple() {
        // prepare
        SortOption sortOption = SearchModelFactory.newSortOption(Collections.emptyList());
        sortOption = sortOption.add(FieldExpressionUtil.FIELD_NAME_THING_ID, SortOptionEntry.SortOrder.DESC);
        sortOption = sortOption.add(FieldExpressionUtil.FIELD_NAME_OWNER, SortOptionEntry.SortOrder.ASC);

        // test
        visitor.visitAll(Collections.singletonList(sortOption));

        // verify
        verify(exprFactoryMock).sortBy(FieldExpressionUtil.FIELD_NAME_THING_ID);
        verify(exprFactoryMock).sortBy(FieldExpressionUtil.FIELD_NAME_OWNER);
        verify(qbMock).sort(
                Arrays.asList(new org.eclipse.ditto.services.thingsearch.querymodel.query.SortOption(exprMock,
                                SortDirection.DESC),
                        new org.eclipse.ditto.services.thingsearch.querymodel.query.SortOption(exprMock,
                                SortDirection.ASC)));
    }


    /** */
    @Test
    public void visitLimitOptionAndSortOption() {
        // test
        final LimitOption limitOption = SearchModelFactory.newLimitOption(KNOWN_SKIP, KNOWN_LIMIT);
        SortOption sortOption = SearchModelFactory.newSortOption(Collections.emptyList());
        sortOption = sortOption.add(FieldExpressionUtil.FIELD_NAME_THING_ID, SortOptionEntry.SortOrder.ASC);

        // test
        visitor.visitAll(Arrays.asList(limitOption, sortOption));

        // verify
        verify(qbMock).skip(KNOWN_SKIP);
        verify(qbMock).limit(KNOWN_LIMIT);
        verify(exprFactoryMock).sortBy(FieldExpressionUtil.FIELD_NAME_THING_ID);
        verify(qbMock).sort(
                Collections.singletonList(new org.eclipse.ditto.services.thingsearch.querymodel.query.SortOption(exprMock,
                        SortDirection.ASC)));
    }

}
