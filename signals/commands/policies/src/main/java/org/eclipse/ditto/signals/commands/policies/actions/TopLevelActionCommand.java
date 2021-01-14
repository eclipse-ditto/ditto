/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.policies.actions;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.io.NotSerializableException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommand;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;

/**
 * This command executes a policy action on every authorized entries.
 *
 * @since 2.0.0
 */
@Immutable
@JsonParsableCommand(typePrefix = PolicyCommand.TYPE_PREFIX, name = TopLevelActionCommand.NAME)
public final class TopLevelActionCommand extends AbstractCommand<TopLevelActionCommand>
        implements PolicyActionCommand<TopLevelActionCommand> {

    /**
     * NAME of this command.
     */
    public static final String NAME = "topLevelAction";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_ACTION =
            JsonFactory.newJsonObjectFieldDefinition("action", FieldType.REGULAR, JsonSchemaVersion.V_2);

    static final JsonFieldDefinition<JsonArray> JSON_LABELS =
            JsonFactory.newJsonArrayFieldDefinition("labels", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private final PolicyActionCommand<?> policyActionCommand;
    private final List<Label> authorizedLabels;

    private TopLevelActionCommand(final PolicyActionCommand<?> policyActionCommand,
            final List<Label> authorizedLabels) {
        super(TYPE, policyActionCommand.getDittoHeaders());
        // Null check and copying in the factory method in order to share known unmodifiable fields between instances.
        this.authorizedLabels = authorizedLabels;
        this.policyActionCommand = policyActionCommand;
    }

    /**
     * Creates a command for executing a policy action on all authorized entries.
     *
     * @param policyActionCommand the action to execute.
     * @param authorizedLabels labels of policy entries authorized to execute the action.
     * @return the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static TopLevelActionCommand of(final PolicyActionCommand<?> policyActionCommand,
            final List<Label> authorizedLabels) {

        return new TopLevelActionCommand(
                policyActionCommand,
                Collections.unmodifiableList(new ArrayList<>(checkNotNull(authorizedLabels, "labels")))
        );
    }

    /**
     * Creates a command for executing a policy action on all authorized entries.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders headers of the command.
     * @param parseInnerJson function to parse the inner JSON of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static TopLevelActionCommand fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders,
            final JsonParsable.ParseInnerJson parseInnerJson) {

        return new CommandJsonDeserializer<TopLevelActionCommand>(TYPE, jsonObject).deserialize(() -> {
            try {
                final JsonObject commandJson = jsonObject.getValueOrThrow(JSON_ACTION);
                final PolicyActionCommand<?> policyActionCommand =
                        (PolicyActionCommand<?>) parseInnerJson.parseInnerJson(commandJson);
                final List<Label> labels = Collections.unmodifiableList(
                        jsonObject.getValueOrThrow(JSON_LABELS)
                                .stream()
                                .map(JsonValue::asString)
                                .map(Label::of)
                                .collect(Collectors.toList())
                );
                return new TopLevelActionCommand(policyActionCommand.setDittoHeaders(dittoHeaders), labels);
            } catch (final NotSerializableException e) {
                throw new JsonParseException(e.getMessage());
            }
        });
    }

    @Override
    public SubjectId getSubjectId() {
        return policyActionCommand.getSubjectId();
    }

    @Override
    public TopLevelActionCommand setLabel(final Label label) {
        return this;
    }

    @Override
    public boolean isApplicable(final PolicyEntry policyEntry) {
        return false;
    }

    /**
     * Returns the labels of policy entries authorized to execute the action.
     *
     * @return the policy entry labels.
     */
    public List<Label> getAuthorizedLabels() {
        return authorizedLabels;
    }

    /**
     * Returns the policy action command to execute on each authorized entry.
     *
     * @return the policy action command.
     */
    public PolicyActionCommand<?> getPolicyActionCommand() {
        return policyActionCommand;
    }

    @Override
    public PolicyId getEntityId() {
        return policyActionCommand.getEntityId();
    }

    @Override
    public JsonPointer getResourcePath() {
        // actions/<ACTION-NAME>
        return RESOURCE_PATH_ACTIONS.addLeaf(JsonKey.of(policyActionCommand.getName()));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObject policyActionCommandJson = policyActionCommand.toJson(schemaVersion, thePredicate);
        final JsonArray authorizedLabelsJson =
                authorizedLabels.stream().map(JsonValue::of).collect(JsonCollectors.valuesToArray());
        jsonObjectBuilder.set(JSON_ACTION, policyActionCommandJson, predicate);
        jsonObjectBuilder.set(JSON_LABELS, authorizedLabelsJson, predicate);
    }

    @Override
    public TopLevelActionCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new TopLevelActionCommand(policyActionCommand.setDittoHeaders(dittoHeaders), authorizedLabels);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof TopLevelActionCommand;
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (null == obj || getClass() != obj.getClass()) {
            return false;
        }
        final TopLevelActionCommand that = (TopLevelActionCommand) obj;
        return Objects.equals(policyActionCommand, that.policyActionCommand) &&
                Objects.equals(authorizedLabels, that.authorizedLabels) &&
                super.equals(obj);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), policyActionCommand, authorizedLabels);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", policyActionCommand=" + policyActionCommand +
                ", labels=" + authorizedLabels +
                "]";
    }

}
