package org.eclipse.ditto.services.amqpbridge.mapping.mapper;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Map;

import org.eclipse.ditto.protocoladapter.Adaptable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public abstract class AbstractPayloadMapperTest {

    private PayloadMapper mapper;

    abstract protected PayloadMapper createMapper();

    abstract protected List<String> createSupportedContentTypes();

    abstract protected List<PayloadMapperOptions> createValidOptions();

    abstract protected Map<PayloadMapperOptions, Throwable> createInvalidOptions();

    abstract protected PayloadMapperOptions createIncomingOptions();

    abstract protected Map<PayloadMapperMessage, Adaptable> createValidIncomingMappings();

    abstract protected Map<PayloadMapperMessage, Throwable> createInvalidIncomingMappings();

    abstract protected PayloadMapperOptions createOutgoingOptions();

    abstract protected Map<Adaptable, PayloadMapperMessage> createValidOutgoingMappings();

    abstract protected Map<Adaptable, Throwable> createInvalidOutgoingMappings();

    @Before
    public void setUp() throws Exception {
        this.mapper = createMapper();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void configure() throws Exception {
        createValidOptions().forEach(mapper::configure);
    }

    @Test
    public void supportedContentTypes() throws Exception {
        assertThat(mapper.getSupportedContentTypes()).isEqualTo(createSupportedContentTypes());
    }

    @Test
    public void configureNullOptionsFails() throws Exception {
        assertThatNullPointerException().isThrownBy(() -> mapper.configure(null));
    }

    @Test
    public void configureFails() throws Exception {
        createInvalidOptions().forEach((opt, err) ->
                assertThatExceptionOfType(err.getClass()).isThrownBy(() -> mapper.configure(opt))
                        .withMessage(err.getMessage())
                        .withCause(err.getCause()));
    }

    @Test
    public void mapIncoming() throws Exception {
        mapper.configure(createIncomingOptions());
        createValidIncomingMappings().forEach((m, a) -> assertThat(mapper.mapIncoming(m)).isEqualTo(a));
    }

    @Test
    public void mapIncomingNullFails() throws Exception {
        mapper.configure(createIncomingOptions());
        assertThatNullPointerException().isThrownBy(() -> mapper.mapIncoming(null));
    }

    @Test
    public void mapIncomingFails() throws Exception {
        mapper.configure(createIncomingOptions());
        createInvalidIncomingMappings().forEach((m, err) ->
                assertThatExceptionOfType(err.getClass()).isThrownBy(() -> mapper.mapIncoming(m))
                        .withMessage(err.getMessage())
                        .withCause(err.getCause()));
    }

    @Test
    public void mapOutgoing() throws Exception {
        mapper.configure(createOutgoingOptions());
        createValidOutgoingMappings().forEach((a, m) -> assertThat(mapper.mapOutgoing(a)).isEqualTo(m));
    }

    @Test
    public void mapOutgoingNullFails() throws Exception {
        mapper.configure(createOutgoingOptions());
        assertThatNullPointerException().isThrownBy(() -> mapper.mapOutgoing(null));
    }

    @Test
    public void mapOutgoingFails() throws Exception {
        mapper.configure(createOutgoingOptions());
        createInvalidOutgoingMappings().forEach((a, err) ->
                assertThatExceptionOfType(err.getClass()).isThrownBy(() -> mapper.mapOutgoing(a))
                        .withMessage(err.getMessage())
                        .withCause(err.getCause()));
    }


}
