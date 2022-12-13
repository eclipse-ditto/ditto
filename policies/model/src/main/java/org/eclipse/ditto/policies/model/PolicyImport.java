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
package org.eclipse.ditto.policies.model;

import java.util.Optional;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;

/**
 * Represents a single import of another {@link Policy} based on its {@code policyId} and {@link EffectedImports}
 * containing optional includes and excludes.
 *
 * @since 3.1.0
 */
public interface PolicyImport extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns a new {@code PolicyImport} with the specified {@code resourceKey} and {@code effectedPermissions}.
     *
     * @param importedPolicyId the {@link PolicyId} of the imported policy.
     * @param effectedImports the EffectedImports of the new PolicyImport to create.
     * @return the new {@code PolicyImport}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static PolicyImport newInstance(final PolicyId importedPolicyId, final EffectedImports effectedImports) {
        return PoliciesModelFactory.newPolicyImport(importedPolicyId, effectedImports);
    }

    /**
     * Subject is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions of Subject.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Returns the Policy ID of the Policy to import.
     *
     * @return the Policy ID of the Policy to import.
     */
    PolicyId getImportedPolicyId();

    /**
     * Returns the optional {@link EffectedImports} for this PolicyImport.
     *
     * @return the effected imported entries.
     */
    Optional<EffectedImports> getEffectedImports();

    /**
     * Returns all non-hidden marked fields of this PolicyImport.
     *
     * @return a JSON object representation of this PolicyImport including only non-hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.regularOrSpecial()).get(fieldSelector);
    }

}
