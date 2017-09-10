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
package org.eclipse.ditto.model.policies;

/**
 * Represents a {@link Subject} type used in a subject for documenting what type it is.
 */
public interface SubjectType extends CharSequence {

    /**
     * Json Web Token.
     */
    SubjectType JWT = PoliciesModelFactory.newSubjectType("jwt");

    @Override
    String toString();

}
