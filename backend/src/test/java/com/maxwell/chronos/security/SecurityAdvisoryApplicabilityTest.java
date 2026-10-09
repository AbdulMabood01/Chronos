package com.maxwell.chronos.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.view.UrlBasedViewResolver;
import org.springframework.web.servlet.view.xslt.XsltView;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"logging.level.org.springframework=INFO"})
class SecurityAdvisoryApplicabilityTest {
    @Autowired ApplicationContext context;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

    @Test void applicationHandlersDoNotRenderViewsOrStreamFragments() {
        var handlers = mappings.getHandlerMethods().values().stream()
                .filter(h -> h.getBeanType().getPackageName().startsWith("com.maxwell.chronos"))
                .toList();
        assertFalse(handlers.isEmpty());
        for (var handler : handlers) {
            assertTrue(AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), ResponseBody.class)
                    || handler.hasMethodAnnotation(ResponseBody.class), handler.toString());
            String returnType = handler.getMethod().getGenericReturnType().getTypeName();
            for (String forbidden : new String[]{"SseEmitter", "ServerSentEvent", "FragmentsRendering", "ModelAndView"})
                assertFalse(returnType.contains(forbidden), handler.toString());
        }
        assertTrue(context.getBeansOfType(View.class).values().stream().noneMatch(XsltView.class::isInstance));
        for (var resolver : context.getBeansOfType(UrlBasedViewResolver.class).values()) {
            Class<?> viewClass = org.springframework.test.util.ReflectionTestUtils.invokeMethod(resolver, "getViewClass");
            assertFalse(viewClass != null && XsltView.class.isAssignableFrom(viewClass));
        }
    }
}
