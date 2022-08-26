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
package org.eclipse.ditto.wot.integration.generator;

import org.eclipse.ditto.wot.model.SingleUriAtContext;

/**
 * Contains the specifics of Ditto's WoT Extension Ontology.
 *
 * @see <a href="https://ditto.eclipseprojects.io/wot/ditto-extension#">Ditto - WoT Extension Ontology</a>
 * @since 3.0.0
 */
final class DittoWotExtension {

    /**
     * The {@code SingleUriAtContext} (being an IRI) of the Ditto WoT Extension.
     */
    static final SingleUriAtContext DITTO_WOT_EXTENSION = SingleUriAtContext.DITTO_WOT_EXTENSION;

    /**
     * Contains a category with which WoT property affordances may optionally be categorized.
     * This category will be used by Ditto in order to provide an additional "nesting level" for properties.
     *
     * @see <a href="https://ditto.eclipseprojects.io/wot/ditto-extension#category">Property category</a>
     */
    static final String DITTO_WOT_EXTENSION_CATEGORY = "category";


    private DittoWotExtension() {
        throw new AssertionError();
    }
}
