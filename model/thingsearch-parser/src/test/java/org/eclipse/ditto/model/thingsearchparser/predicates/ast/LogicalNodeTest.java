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

/**
 * Tests {@link LogicalNode}.
 */
public class LogicalNodeTest {

    /** */
    @Test
    public void hashcodeAndEquals() {
        EqualsVerifier.forClass(LogicalNode.class).usingGetClass().verify();
    }

    /** */
    @Test
    public void validToString() {
        assertThat(new LogicalNode("and").toString()).startsWith("LogicalNode ");
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void constructorWithNullAsName() {
        new LogicalNode((String) null);
    }

    /** */
    @Test(expected = IllegalArgumentException.class)
    public void constructorWithUnknownName() {
        new LogicalNode("unknownName");
    }

    /** */
    @Test
    public void nameConstructorSuccess() {
        final LogicalNode logicalNode = new LogicalNode("and");
        assertThat(logicalNode.getName()).isEqualTo("and");
        assertThat(logicalNode.getType()).isEqualTo(LogicalNode.Type.AND);
    }

    /** */
    @Test
    public void typeConstructorSuccess() {
        final LogicalNode logicalNode = new LogicalNode(LogicalNode.Type.AND);
        assertThat(logicalNode.getName()).isEqualTo("and");
        assertThat(logicalNode.getType()).isEqualTo(LogicalNode.Type.AND);
    }

    @Test
    public void visitorGetsVisited() {
        final PredicateVisitor visitorMock = mock(PredicateVisitor.class);
        final LogicalNode logicalNode = new LogicalNode(LogicalNode.Type.AND);
        logicalNode.accept(visitorMock);
        verify(visitorMock).visit(logicalNode);
    }

}
