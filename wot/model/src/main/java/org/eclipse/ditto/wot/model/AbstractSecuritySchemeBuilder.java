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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Abstract implementation of {@link org.eclipse.ditto.wot.model.SecurityScheme.Builder}.
 */
abstract class AbstractSecuritySchemeBuilder<B extends SecurityScheme.Builder<B, S>, S extends SecurityScheme>
        extends AbstractTypedJsonObjectBuilder<B, S>
        implements SecurityScheme.Builder<B, S> {

    protected final String securitySchemeName;

    protected AbstractSecuritySchemeBuilder(final String securitySchemeName,
            final JsonObjectBuilder wrappedObjectBuilder,
            final Class<?> selfType) {
        super(wrappedObjectBuilder, selfType);
        this.securitySchemeName = checkNotNull(securitySchemeName, "securitySchemeName");
        setScheme(getSecuritySchemeScheme());
    }

    abstract SecuritySchemeScheme getSecuritySchemeScheme();

    @Override
    public B setAtType(@Nullable final AtType atType) {
        if (atType != null) {
            if (atType instanceof MultipleAtType) {
                putValue(SecurityScheme.SecuritySchemeJsonFields.AT_TYPE_MULTIPLE, ((MultipleAtType) atType).toJson());
            } else if (atType instanceof SingleAtType) {
                putValue(SecurityScheme.SecuritySchemeJsonFields.AT_TYPE, atType.toString());
            } else {
                throw new IllegalArgumentException("Unsupported @type: " + atType.getClass().getSimpleName());
            }
        } else {
            remove(SecurityScheme.SecuritySchemeJsonFields.AT_TYPE);
        }
        return myself;
    }

    @Override
    public B setScheme(@Nullable final SecuritySchemeScheme scheme) {
        if (scheme != null) {
            putValue(SecurityScheme.SecuritySchemeJsonFields.SCHEME, scheme.toString());
        } else {
            remove(SecurityScheme.SecuritySchemeJsonFields.SCHEME);
        }
        return myself;
    }

    @Override
    public B setDescription(@Nullable final Description description) {
        if (description != null) {
            putValue(SecurityScheme.SecuritySchemeJsonFields.DESCRIPTION, description.toString());
        } else {
            remove(SecurityScheme.SecuritySchemeJsonFields.DESCRIPTION);
        }
        return myself;
    }

    @Override
    public B setDescriptions(@Nullable final Descriptions descriptions) {
        if (descriptions != null) {
            putValue(SecurityScheme.SecuritySchemeJsonFields.DESCRIPTIONS, descriptions.toJson());
        } else {
            remove(SecurityScheme.SecuritySchemeJsonFields.DESCRIPTIONS);
        }
        return myself;
    }

    @Override
    public B setProxy(@Nullable final IRI proxy) {
        if (proxy != null) {
            putValue(SecurityScheme.SecuritySchemeJsonFields.PROXY, proxy.toString());
        } else {
            remove(SecurityScheme.SecuritySchemeJsonFields.PROXY);
        }
        return myself;
    }

}
