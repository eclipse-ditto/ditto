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
import java.util.stream.Stream;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;

/**
 * A collection of {@link PolicyImport}s contained in a {@link Policy}.
 *
 * @since 3.1.0
 */
public interface PolicyImports extends Iterable<PolicyImport>, Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns a new {@code PolicyImports} containing no policyImports.
     *
     * @return the new {@code PolicyImports}.
     */
    static PolicyImports emptyInstance() {
        return PoliciesModelFactory.emptyPolicyImports();
    }

    /**
     * Returns a new {@code PolicyImports} containing the given policyImports.
     *
     * @param policyImports the {@link PolicyImport}s to be contained in the new PolicyImports.
     * @return the new {@code PolicyImports}.
     * @throws NullPointerException if {@code policyImports} is {@code null}.
     */
    static PolicyImports newInstance(final Iterable<PolicyImport> policyImports) {
        return PoliciesModelFactory.newPolicyImports(policyImports);
    }

    /**
     * Returns a new {@code PolicyImports} containing the given policyImport.
     *
     * @param policyImport the {@link PolicyImport} to be contained in the new PolicyImports.
     * @param furtherPolicyImports further {@link PolicyImport}s to be contained in the new PolicyImports.
     * @return the new {@code PolicyImports}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static PolicyImports newInstance(final PolicyImport policyImport, final PolicyImport... furtherPolicyImports) {
        return PoliciesModelFactory.newPolicyImports(policyImport, furtherPolicyImports);
    }

    /**
     * Returns the supported JSON schema version of this resources. <em>PolicyImports support only
     * {@link JsonSchemaVersion#V_2}.</em>
     *
     * @return the supported schema version.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Returns the PolicyImport with the given {@code importedPolicyId} or an empty optional.
     *
     * @param importedPolicyId the path of the PolicyImport to be retrieved.
     * @return the PolicyImport or an empty optional.
     * @throws NullPointerException if {@code importedPolicyId} is {@code null}.
     * @throws IllegalArgumentException if {@code importedPolicyId} is empty.
     */
    Optional<PolicyImport> getPolicyImport(CharSequence importedPolicyId);

    /**
     * Sets the given PolicyImport to a copy of this PolicyImports. A previous PolicyImport with the same identifier will be
     * overwritten.
     *
     * @param policyImport the PolicyImport to be set.
     * @return a copy of this PolicyImports with {@code policyImport} set.
     * @throws NullPointerException if {@code policyImport} is {@code null}.
     */
    PolicyImports setPolicyImport(PolicyImport policyImport);

    /**
     * Merges all given policyImports with self into a copy of this policy. A previous PolicyImport with the same identifier will be
     * overwritten.
     *
     * @param policyImports the PolicyImports to be set.
     * @return a copy of this PolicyImports with {@code policyImport} set.
     * @throws NullPointerException if {@code policyImport} is {@code null}.
     */
    PolicyImports setPolicyImports(PolicyImports policyImports);

    /**
     * Removes the PolicyImport with the given imported Policy ID.
     *
     * @param importedPolicyId the Policy ID identifying the PolicyImport to remove.
     * @return a copy of this PolicyImports with {@code importedPolicyId} removed.
     * @throws NullPointerException if {@code importedPolicyId} is {@code null}.
     */
    PolicyImports removePolicyImport(CharSequence importedPolicyId);

    /**
     * Returns the size of this PolicyImports, i.e. the number of contained values.
     *
     * @return the size.
     */
    int getSize();

    /**
     * Indicates whether this PolicyImports is empty.
     *
     * @return {@code true} if this PolicyImports does not contain any values, {@code false} else.
     */
    boolean isEmpty();

    /**
     * Returns a sequential {@code Stream} with the values of this PolicyImports as its source.
     *
     * @return a sequential stream of the PolicyImports of this container.
     */
    Stream<PolicyImport> stream();

    /**
     * Returns all non hidden marked fields of this PolicyImports.
     *
     * @return a JSON object representation of this PolicyImports including only non hidden marked fields.
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
