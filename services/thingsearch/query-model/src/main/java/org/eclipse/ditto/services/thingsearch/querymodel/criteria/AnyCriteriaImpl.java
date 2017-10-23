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
package org.eclipse.ditto.services.thingsearch.querymodel.criteria;

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.visitors.CriteriaVisitor;

/**
 * This Criteria matches any document.
 */
public class AnyCriteriaImpl implements Criteria {

    private static final AnyCriteriaImpl INSTANCE = new AnyCriteriaImpl();

    private AnyCriteriaImpl() {
    }

    /**
     * Gets the single instance of this class.
     *
     * @return the single instance.
     */
    public static AnyCriteriaImpl getInstance() {
        return INSTANCE;
    }

    @Override
    public <T> T accept(final CriteriaVisitor<T> visitor) {
        return visitor.visitAny();
    }
}
