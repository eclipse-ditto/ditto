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
package org.eclipse.ditto.model.thingsearchparser.predicates.ast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Tests {@link SingleComparisonNode}.
 */
public class SingleComparisonNodeTest {

    /** */
    @Test
    public void hashcodeAndEquals() {
        EqualsVerifier.forClass(SingleComparisonNode.class)
                .usingGetClass()
                .suppress(Warning.REFERENCE_EQUALITY)
                .verify();
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void typeConstructorWithNullAsFilterProperty() {
        new SingleComparisonNode(SingleComparisonNode.Type.EQ, null, "test");
    }

    /** */
    @Test
    public void typeConstructorSuccess() {
        final SingleComparisonNode singleComparisonNode =
                new SingleComparisonNode(SingleComparisonNode.Type.EQ,
                        "propertyName", "propertyValue");
        assertThat(singleComparisonNode.getComparisonType()).isEqualTo(
                SingleComparisonNode.Type.EQ);
        assertThat(singleComparisonNode.getComparisonProperty()).isEqualTo("propertyName");
        assertThat(singleComparisonNode.getComparisonValue()).isEqualTo("propertyValue");
    }

    /** */
    @Test
    public void typeConstructorWithNullAsValue() {
        final SingleComparisonNode singleComparisonNode = new SingleComparisonNode(
                SingleComparisonNode.Type.EQ, "propertyName",
                null);
        assertThat(singleComparisonNode.getComparisonType()).isEqualTo(SingleComparisonNode.Type.EQ);
        assertThat(singleComparisonNode.getComparisonProperty()).isEqualTo("propertyName");
        assertThat(singleComparisonNode.getComparisonValue()).isNull();
    }

    @Test
    public void visitorGetsVisited() {
        final PredicateVisitor visitorMock = mock(PredicateVisitor.class);
        final SingleComparisonNode singleComparisonNode =
                new SingleComparisonNode(SingleComparisonNode.Type.EQ,
                        "propertyName", "propertyValue");
        singleComparisonNode.accept(visitorMock);
        verify(visitorMock).visit(singleComparisonNode);
    }

}
