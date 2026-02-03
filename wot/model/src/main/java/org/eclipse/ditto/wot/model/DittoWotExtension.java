/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
 * Contains the specifics of Ditto's WoT Extension Ontology.
 *
 * @see <a href="https://ditto.eclipseprojects.io/wot/ditto-extension#">Ditto - WoT Extension Ontology</a>
 * @since 3.0.0
 */
public final class DittoWotExtension {

    /**
     * The {@code SingleUriAtContext} (being an IRI) of the Ditto WoT Extension.
     */
    public static final SingleUriAtContext DITTO_WOT_EXTENSION = SingleUriAtContext.DITTO_WOT_EXTENSION;

    /**
     * Contains a category with which WoT property affordances may optionally be categorized.
     * This category will be used by Ditto in order to provide an additional "nesting level" for properties.
     *
     * @see <a href="https://ditto.eclipseprojects.io/wot/ditto-extension#category">Property category</a>
     */
    public static final String DITTO_WOT_EXTENSION_CATEGORY = "category";

    /**
     * Indicates that a WoT Thing (ThingModel/ThingDescription), or an affordance (property, action, or event)
     * is deprecated.
     * The value is a JSON object containing:
     * <ul>
     *   <li>{@code deprecated} - boolean (required): whether the Thing or affordance is deprecated</li>
     *   <li>{@code supersededBy} - string (optional): JSON Pointer to replacement affordance, or URL to
     *       replacement ThingModel</li>
     *   <li>{@code removalVersion} - string (required): SemVer version when the Thing or affordance will be
     *       removed</li>
     * </ul>
     *
     * @see <a href="https://ditto.eclipseprojects.io/wot/ditto-extension#deprecationNotice">Deprecation Notice</a>
     * @since 3.9.0
     */
    public static final String DITTO_WOT_EXTENSION_DEPRECATION_NOTICE = "deprecationNotice";


    private DittoWotExtension() {
        throw new AssertionError();
    }
}
