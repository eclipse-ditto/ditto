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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.policies.model.PoliciesModelFactory.DITTO_LIMITS_POLICY_IMPORTS_LIMIT;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyImportsTooLargeException;

/**
 * An immutable implementation of {@link PolicyImports}.
 */
@Immutable
final class ImmutablePolicyImports implements PolicyImports {

    private final Map<CharSequence, PolicyImport> policyImports;
    public static final String POLICY_IMPORTS = "policyImports";

    private ImmutablePolicyImports() {
        this.policyImports = Collections.emptyMap();
    }

    private ImmutablePolicyImports(final Map<CharSequence, PolicyImport> policyImports) {
        checkNotNull(policyImports, POLICY_IMPORTS);
        this.policyImports = Collections.unmodifiableMap(new HashMap<>(policyImports));
    }

    /**
     * Returns a new instance of {@code PolicyImports} with the given policyImports.
     *
     * @param policyImports the {@link PolicyImport}s of the new PolicyImports.
     * @return the new {@code PolicyImports}.
     * @throws NullPointerException if {@code policyImports} is {@code null}.
     */
    public static ImmutablePolicyImports of(final Iterable<PolicyImport> policyImports) {
        checkNotNull(policyImports, POLICY_IMPORTS);

        final Map<CharSequence, PolicyImport> resourcesMap = new HashMap<>();
        policyImports.forEach(policyImport -> {
            final PolicyImport existingPolicyImport =
                    resourcesMap.put(policyImport.getImportedPolicyId(), policyImport);
            if (null != existingPolicyImport) {
                final String msgTemplate = "There is more than one PolicyImport with the imported Policy ID <{0}>!";
                throw new IllegalArgumentException(
                        MessageFormat.format(msgTemplate, policyImport.getImportedPolicyId()));
            }
        });

        return new ImmutablePolicyImports(resourcesMap);
    }

    /**
     * Returns a new instance of {@code PolicyImports} with no policyImports.
     *
     * @return the new empty {@code PolicyImports}.
     */
    public static ImmutablePolicyImports empty() {
        return new ImmutablePolicyImports();
    }

    /**
     * Creates a new {@code PolicyImports} from the specified JSON object.
     *
     * @param jsonObject the JSON object of which a new PolicyImports instance is to be created.
     * @return the {@code PolicyImports} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'PolicyImports' format.
     */
    public static PolicyImports fromJson(final JsonObject jsonObject) {
        final List<PolicyImport> thePolicyImports = jsonObject.stream()
                .filter(field -> !Objects.equals(field.getKey(), JsonSchemaVersion.getJsonKey()))
                .map(field -> ImmutablePolicyImport.fromJson(PolicyId.of(field.getKeyName()), field.getValue()))
                .collect(Collectors.toList());

        return of(thePolicyImports);
    }

    @Override
    public Optional<PolicyImport> getPolicyImport(final CharSequence importedPolicyId) {
        checkNotNull(importedPolicyId, "importedPolicyId");
        return Optional.ofNullable(policyImports.get(importedPolicyId));
    }

    @Override
    public PolicyImports setPolicyImport(final PolicyImport policyImport) {
        checkNotNull(policyImport, "policyImport");

        PolicyImports result = this;

        final PolicyImport existingPolicyImport = policyImports.get(policyImport.getImportedPolicyId());
        if (!Objects.equals(existingPolicyImport, policyImport)) {
            result = createNewPolicyImportsWithNewPolicyImport(policyImport);
        }

        return result;
    }

    @Override
    public PolicyImports setPolicyImports(final PolicyImports policyImports) {
        checkNotNull(policyImports, POLICY_IMPORTS);

        PolicyImports result = this;
        for (PolicyImport policyImport : policyImports) {
            result = result.setPolicyImport(policyImport);
        }
        return result;
    }


    private PolicyImports createNewPolicyImportsWithNewPolicyImport(final PolicyImport newPolicyImport) {
        final Map<CharSequence, PolicyImport> resourcesCopy = copyPolicyImports();
        resourcesCopy.put(newPolicyImport.getImportedPolicyId(), newPolicyImport);
        if (resourcesCopy.size() > DITTO_LIMITS_POLICY_IMPORTS_LIMIT) {
            throw PolicyImportsTooLargeException.newBuilder(newPolicyImport.getImportedPolicyId()).build();
        }
        return new ImmutablePolicyImports(resourcesCopy);
    }

    private Map<CharSequence, PolicyImport> copyPolicyImports() {
        return new HashMap<>(policyImports);
    }

    @Override
    public PolicyImports removePolicyImport(final CharSequence importedPolicyId) {
        checkNotNull(importedPolicyId, "importedPolicyId");

        if (!policyImports.containsKey(importedPolicyId)) {
            return this;
        }

        final Map<CharSequence, PolicyImport> resourcesCopy = copyPolicyImports();
        resourcesCopy.remove(importedPolicyId);

        return new ImmutablePolicyImports(resourcesCopy);
    }

    @Override
    public int getSize() {
        return policyImports.size();
    }

    @Override
    public boolean isEmpty() {
        return policyImports.isEmpty();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        return JsonFactory.newObjectBuilder()
                .setAll(resourcesToJson(schemaVersion, thePredicate), predicate)
                .build();
    }

    private JsonObject resourcesToJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        policyImports.values().forEach(policyImport -> {
            final JsonKey key = JsonKey.of(policyImport.getImportedPolicyId());
            final JsonValue value = policyImport.toJson(schemaVersion, thePredicate);
            final JsonFieldDefinition<JsonObject> fieldDefinition =
                    JsonFactory.newJsonObjectFieldDefinition(key, FieldType.REGULAR, JsonSchemaVersion.V_2);
            final JsonField field = JsonFactory.newField(key, value, fieldDefinition);

            jsonObjectBuilder.set(field, predicate);
        });

        return jsonObjectBuilder.build();
    }

    @Override
    public Iterator<PolicyImport> iterator() {
        return new HashSet<>(policyImports.values()).iterator();
    }

    @Override
    public Stream<PolicyImport> stream() {
        return policyImports.values().stream();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutablePolicyImports that = (ImmutablePolicyImports) o;
        return Objects.equals(policyImports, that.policyImports);
    }

    @Override
    public int hashCode() {
        return Objects.hash(policyImports);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [policyImports=" + policyImports + "]";
    }

}
