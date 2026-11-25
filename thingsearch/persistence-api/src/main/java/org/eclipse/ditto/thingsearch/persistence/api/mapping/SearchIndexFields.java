/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.persistence.api.mapping;

/**
 * Field-name constants that are part of the backend-neutral search-index document contract, shared by the
 * neutral builder classes in this package.
 * <p>
 * These values mirror the corresponding constants in the service's {@code PersistenceConstants} (which stays
 * the single source of truth for the Mongo encoder) but are duplicated here so the neutral persistence-api
 * module does not depend on the service. They describe the raw-key auth-tree markers ({@code ·g}/{@code ·r})
 * and the feature-array key names as they appear in the neutral {@code SearchIndexDocument}.
 * <p>
 * <strong>Drift risk:</strong> these values are hand-duplicated, not derived from a shared constant, so
 * {@code SearchIndexFields} and the service's {@code PersistenceConstants} can silently diverge if one is
 * edited without the other. This is intentionally NOT unified across modules (persistence-api must not depend
 * on the service). {@code EnforcedThingMapperGoldenTest} is the guard against drift: it pins the encoder's
 * emitted Mongo document shape against a golden fixture, so a divergence here that changes the emitted keys
 * will fail that test.
 */
final class SearchIndexFields {

    /**
     * Field name for the {@code features} object of a thing.
     */
    static final String FIELD_FEATURES = "features";

    /**
     * Field name carrying a feature's ID within a feature index entry.
     */
    static final String FIELD_FEATURE_ID = "id";

    /**
     * Special character used as prefix for grant ({@code g}) and revoke ({@code r}) fields in the auth tree to
     * avoid conflicts with actual thing fields. The character is part of the restricted fields (see
     * {@code org.eclipse.ditto.base.model.entity.id.RegexPatterns#CONTROL_CHARS}).
     */
    private static final String FIELD_PERMISSION_PREFIX = Character.valueOf((char) 183).toString();

    /**
     * Field name for policy read grants.
     */
    static final String FIELD_GRANTED = FIELD_PERMISSION_PREFIX + "g";

    /**
     * Field name for policy read revokes.
     */
    static final String FIELD_REVOKED = FIELD_PERMISSION_PREFIX + "r";

    private SearchIndexFields() {
        throw new AssertionError();
    }
}
