package org.eclipse.ditto.services.amqpbridge.mapping.mapper.test;

import org.eclipse.ditto.services.amqpbridge.mapping.mapper.PayloadMapper;
import org.eclipse.ditto.services.amqpbridge.mapping.mapper.PayloadMapperOptions;

public class Mappers {

    public static PayloadMapper createNoopMapper(final PayloadMapperOptions options) {
        return new NoopMapper();
    }
}
