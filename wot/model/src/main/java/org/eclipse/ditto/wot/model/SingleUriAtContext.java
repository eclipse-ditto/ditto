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
 * A SingleUriAtContext is a single IRI {@link AtContext}.
 *
 * @see <a href="https://www.w3.org/TR/wot-thing-description11/#sec-context">WoT TD @context</a>
 * @since 2.4.0
 */
public interface SingleUriAtContext extends SingleAtContext, IRI {

    /**
     * The W3C WoT Thing Description 1.0 context URI.
     */
    SingleUriAtContext W3ORG_2019_WOT_TD_V1 = of("https://www.w3.org/2019/wot/td/v1");

    /**
     * The W3C WoT Thing Description 1.1 context URI.
     */
    SingleUriAtContext W3ORG_2022_WOT_TD_V11 = of("https://www.w3.org/2022/wot/td/v1.1");

    /**
     * The W3C WoT namespace URI for Thing Descriptions.
     */
    SingleUriAtContext W3ORG_NS_TD = of("http://www.w3.org/ns/td");

    /**
     * The Eclipse Ditto WoT extension context URI.
     */
    SingleUriAtContext DITTO_WOT_EXTENSION = of("https://ditto.eclipseprojects.io/wot/ditto-extension#");

    /**
     * Creates a SingleUriAtContext from the specified context IRI.
     *
     * @param context the context IRI.
     * @return the SingleUriAtContext.
     */
    static SingleUriAtContext of(final CharSequence context) {
        if (context instanceof SingleUriAtContext) {
            return (SingleUriAtContext) context;
        }
        return new ImmutableSingleUriAtContext(context);
    }
}
