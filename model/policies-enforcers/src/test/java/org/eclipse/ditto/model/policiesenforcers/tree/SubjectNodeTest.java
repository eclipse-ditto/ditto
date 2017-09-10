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
package org.eclipse.ditto.model.policiesenforcers.tree;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SubjectNode}.
 */
public class SubjectNodeTest {

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SubjectNode.class) //
                .withRedefinedSuperclass() //
                .verify();
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullName() {
        SubjectNode.of(null);
    }

}
