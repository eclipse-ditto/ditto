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
 * Represents a {@link Subject} issuer who has issued the subject.
 */
public interface SubjectIssuer extends CharSequence {

    /**
     * The known issuer "Google" for JSON Web Tokens.
     */
    SubjectIssuer GOOGLE = PoliciesModelFactory.newSubjectIssuer("accounts.google.com");

    /**
     * The known issuer "Google" - more precisely its URL - for JSON Web Tokens.
     */
    SubjectIssuer GOOGLE_URL = PoliciesModelFactory.newSubjectIssuer("https://accounts.google.com");

    @Override
    String toString();

}
