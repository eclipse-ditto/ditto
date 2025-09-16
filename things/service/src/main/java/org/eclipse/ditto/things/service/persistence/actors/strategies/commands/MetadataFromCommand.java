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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.metadata.Metadata;
import org.eclipse.ditto.base.model.entity.metadata.MetadataBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeader;
import org.eclipse.ditto.base.model.headers.metadata.MetadataHeaderKey;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandSizeValidator;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;

/**
 * Creates or extends/modifies Metadata of an entity based on {@link MetadataHeader}s of a {@link Command}'s
 * DittoHeaders.
 *
 * @since 1.3.0
 */
@Immutable
final class MetadataFromCommand implements Supplier<Metadata> {

    private static final Predicate<JsonField> FIELDS_NOT_HIDDEN_OR_METADATA = FieldType.notHidden()
            .or(jsonField -> Objects.equals(Thing.JsonFields.METADATA.getPointer(), jsonField.getKey().asPointer()));

    private final Command<?> command;
    private final Thing mergedThing;

    @Nullable
    private final Metadata existingMetadata;

    private MetadataFromCommand(final Command<?> command,
            final Thing mergedThing,
            @Nullable final Metadata existingMetadata) {
        this.command = checkNotNull(command, "command");
        this.mergedThing = checkNotNull(mergedThing, "mergedThing");
        this.existingMetadata = existingMetadata;
    }

    /**
     * Returns a new {@code MetadataFromCommand}.
     *
     * @param command provides modified paths and headers.
     * @param existingThing provides the existing thing.
     * @param existingRelativeMetadata provides existing metadata relative to the command's resource path to be extended.
     * @throws NullPointerException if {@code command} or {@code withOptionalEntity} is {@code null}.
     */
    static MetadataFromCommand of(final Command<?> command,
            @Nullable final Thing existingThing,
            @Nullable final Metadata existingRelativeMetadata
    ) {
        final Thing existingOrEmptyThing =
                Objects.requireNonNullElseGet(existingThing, () -> Thing.newBuilder().build());

        if (command instanceof WithOptionalEntity<?> withOptionalEntity) {
            final var mergedThing = withOptionalEntity.getEntity()
                    .map(entity -> {
                        final var resourcePath = command.getResourcePath();
                        if (command instanceof MergeThing) {
                            return ThingsModelFactory.newThing(
                                    JsonObject.newBuilder().set(resourcePath, entity).build()
                            );
                        } else if (resourcePath.isEmpty() && entity.isObject()) {
                            return ThingsModelFactory.newThing(entity.asObject());
                        } else {
                            return ThingsModelFactory.newThing(
                                    JsonObject.newBuilder().set(resourcePath, entity).build()
                            );
                        }
                    })
                    .orElse(existingOrEmptyThing);
            return new MetadataFromCommand(command, mergedThing, existingRelativeMetadata);
        } else {
            return new MetadataFromCommand(command, existingOrEmptyThing, existingRelativeMetadata);
        }
    }

    /**
     * Builds Metadata based on the Command and the existing metadata of this instance. If the existing metadata is
     * {@code null} and the command does not contain an entity for its implemented schema version, the result of this
     * method is {@code null}. Otherwise, either a new Metadata is created or the existing metadata gets modified and/or
     * extended. The values of the returned metadata are obtained from the metadata headers within the DittoHeaders of
     * this instance's command.
     *
     * @return the created, modified or extended Metadata or {@code null}.
     * @throws IllegalArgumentException if the key of a metadata header
     * <ul>
     *     <li>is empty,</li>
     *     <li>starts with an asterisk ({@code *}) and has not exactly two levels,</li>
     *     <li>contains an asterisk at any level but the first.</li>
     * </ul>
     * @throws org.eclipse.ditto.json.JsonParseException if the value of a metadata header cannot be parsed.
     */
    @Override
    @Nullable
    public Metadata get() {
        return getInlineMetadata().orElseGet(() -> {

            final SortedSet<MetadataHeader> metadataHeaders = getMetadataHeadersToPut();
            final Optional<JsonValue> optionalJsonValue = mergedThing.toJson().getValue(command.getResourcePath());
            if (!metadataHeaders.isEmpty() && optionalJsonValue.isPresent()) {
                final var expandedMetadataHeaders = metadataHeaders.stream()
                        .flatMap(this::expandWildcards)
                        .map(mh -> {
                            if (command instanceof MergeThing && !command.getResourcePath().isEmpty()) {
                                return MetadataHeader.of(
                                        MetadataHeaderKey.of(command.getResourcePath().append(mh.getKey().getPath())),
                                        mh.getValue()
                                );
                            } else {
                                return mh;
                            }
                        })
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                final Metadata metadata;
                if (command instanceof MergeThing) {
                    if (command.getResourcePath().isEmpty()) {
                        metadata = buildMetadata(expandedMetadataHeaders);
                    } else {
                        metadata = Metadata.newMetadata(buildMetadata(expandedMetadataHeaders)
                                .getValue(command.getResourcePath())
                                .map(JsonValue::asObject)
                                .orElseGet(JsonObject::empty));
                    }
                } else {
                    metadata = buildMetadata(expandedMetadataHeaders);
                }

                final var thingJsonObject = mergedThing.toBuilder()
                        .setMetadata(metadata)
                        .build()
                        .toJson(FIELDS_NOT_HIDDEN_OR_METADATA);

                ThingCommandSizeValidator.getInstance().ensureValidSize(
                        thingJsonObject::getUpperBoundForStringSize,
                        () -> thingJsonObject.toString().length(),
                        command::getDittoHeaders);

                return metadata;
            }
            return existingMetadata;
        });
    }

    private Optional<Metadata> getInlineMetadata() {
        return command instanceof CreateThing createThing ? createThing.getThing().getMetadata() : Optional.empty();
    }

    private SortedSet<MetadataHeader> getMetadataHeadersToPut() {
        final DittoHeaders dittoHeaders = command.getDittoHeaders();
        return dittoHeaders.getMetadataHeadersToPut();
    }

    private Stream<MetadataHeader> expandWildcards(final MetadataHeader metadataHeader) {
        if (containsWildcard(metadataHeader)) {
            MetadataWildcardValidator.validateMetadataWildcard(command.getResourcePath(),
                    metadataHeader.getKey().toString(), DittoHeaderDefinition.PUT_METADATA.getKey());
            final var jsonPointers = MetadataFieldsWildcardResolver.resolve(command, mergedThing,
                    metadataHeader.getKey().getPath(), DittoHeaderDefinition.PUT_METADATA.getKey());
            return jsonPointers.stream()
                    .map(p -> {
                        if (p.getPrefixPointer(command.getResourcePath().getLevelCount())
                                .filter(prefix -> prefix.equals(command.getResourcePath()))
                                .isPresent()) {
                            return p.getSubPointer(command.getResourcePath().getLevelCount()).orElse(p);
                        } else {
                            return p;
                        }
                    })
                    .map(MetadataHeaderKey::of)
                    .map(metadataHeaderKey -> MetadataHeader.of(metadataHeaderKey, metadataHeader.getValue()));
        } else {
            return Stream.of(metadataHeader);
        }
    }

    private boolean containsWildcard(final MetadataHeader metadataHeader) {
        return metadataHeader.getKey()
                .toString()
                .contains("/*/");
    }

    private Metadata buildMetadata(final Set<MetadataHeader> metadataHeaders) {
        final MetadataBuilder metadataBuilder = getMetadataBuilder();

        final Consumer<MetadataHeader> addMetadataToBuilder = metadataHeader -> {
            final MetadataHeaderKey metadataHeaderKey = metadataHeader.getKey();
            final JsonValue metadataHeaderValue = metadataHeader.getValue();
            metadataBuilder.set(metadataHeaderKey.getPath(), metadataHeaderValue);
        };
        metadataHeaders.forEach(addMetadataToBuilder);

        return metadataBuilder.build();
    }

    private MetadataBuilder getMetadataBuilder() {
        if (null == existingMetadata) {
            return Metadata.newBuilder();
        }
        if (!command.getResourcePath().isEmpty()) {
            return Metadata.newBuilder().setAll(JsonObject.newBuilder()
                    .set(command.getResourcePath(), JsonObject.newBuilder().setAll(existingMetadata).build())
                    .build());
        } else {
            return Metadata.newBuilder().setAll(existingMetadata);
        }
    }

}
