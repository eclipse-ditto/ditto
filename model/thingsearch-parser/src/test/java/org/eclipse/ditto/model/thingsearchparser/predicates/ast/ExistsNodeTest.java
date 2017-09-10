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
 * Tests {@link ExistsNode}.
 */
public class ExistsNodeTest {

    /** */
    @Test
    public void hashcodeAndEquals() {
        EqualsVerifier.forClass(ExistsNode.class).usingGetClass().verify();
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void typeConstructorWithNull() {
        new ExistsNode(null);
    }

    /** */
    @Test
    public void constructorSuccess() {
        final ExistsNode existsNode = new ExistsNode("propertyName");
        assertThat(existsNode.getProperty()).isEqualTo("propertyName");
    }

    @Test
    public void visitorGetsVisited() {
        final PredicateVisitor visitorMock = mock(PredicateVisitor.class);
        final ExistsNode existsNode = new ExistsNode("propertyName");
        existsNode.accept(visitorMock);
        verify(visitorMock).visit(existsNode);
    }

}
