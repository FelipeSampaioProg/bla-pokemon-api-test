package com.pokemon.bff.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the Actuator exposure: endpoints such as env, configprops, heapdump or loggers reveal configuration and
 * memory contents, so only health and info are published unless an operator explicitly asks for more.
 */
class ActuatorExposureTest {

    @Test
    void shouldExposeOnlyHealthAndInfoByDefault() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));

        var include = yaml.getObject().getProperty("management.endpoints.web.exposure.include");

        assertEquals("${MANAGEMENT_ENDPOINTS_INCLUDE:health,info}", include);
    }
}
