package com.hqc.smrag.service.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AiChatServiceWiringTest {

    @Test
    void identifiesExactlyOneConstructorForSpringInjection() {
        Constructor<?>[] injectableConstructors = Arrays.stream(AiChatService.class.getConstructors())
            .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
            .toArray(Constructor<?>[]::new);

        assertThat(injectableConstructors).hasSize(1);
        assertThat(injectableConstructors[0].getParameterTypes())
            .contains(com.hqc.smrag.service.DocPermissionService.class);
    }
}
