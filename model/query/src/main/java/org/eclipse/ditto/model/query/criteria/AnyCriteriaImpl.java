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
package org.eclipse.ditto.model.query.criteria;

import org.eclipse.ditto.model.query.criteria.visitors.CriteriaVisitor;

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
