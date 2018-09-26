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
package org.eclipse.ditto.model.query.criteria.visitors;

import java.util.List;

import org.eclipse.ditto.model.query.criteria.Predicate;

/**
 * Compositional interpreter of {@link Predicate}.
 */
public interface PredicateVisitor<T> {

    T visitEq(Object value);

    T visitGe(Object value);

    T visitGt(Object value);

    T visitIn(List<?> values);

    T visitLe(Object value);

    T visitLike(String value);

    T visitLt(Object value);

    T visitNe(Object value);

}
