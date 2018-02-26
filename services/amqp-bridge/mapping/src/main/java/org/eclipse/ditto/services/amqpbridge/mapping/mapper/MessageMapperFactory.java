package org.eclipse.ditto.services.amqpbridge.mapping.mapper;

import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.model.amqpbridge.MappingContext;

public interface MessageMapperFactory {

    Optional<MessageMapper> mapperOf(final MappingContext context);

    List<MessageMapper> mappersOf(final List<MappingContext> contexts);

    MessageMapperRegistry registryOf(final List<MappingContext> contexts);
}
