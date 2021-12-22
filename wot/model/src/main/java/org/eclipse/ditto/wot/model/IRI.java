/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.wot.model;

/**
 * An IRI (Internationalized Resource Identifier Reference) "value can be absolute or relative, and may have an
 * optional fragment identifier (i.e., it may be an IRI Reference)".
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#bib-rfc3987">RFC3987 - Internationalized Resource Identifiers (IRIs)</a>
 * @see <a href="https://www.w3.org/TR/2012/REC-xmlschema11-2-20120405/#anyURI">W3C XSD anyURI</a>
 * @since 2.4.0
 */
public interface IRI extends CharSequence {

    static IRI of(final CharSequence charSequence) {
        if (charSequence instanceof IRI) {
            return (IRI) charSequence;
        }
        return new ImmutableIRI(charSequence);
    }
}
