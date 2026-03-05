package com.agentdsl.runtime;

import com.agentdsl.core.spec.ParameterSpec;
import com.agentdsl.core.spec.ToolSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ToolCallResolverParamMapTest {

    @Test
    public void testTypeMappingMatch() throws Exception {
        ToolSpec tool = new ToolSpec("http_get");

        List<ParameterSpec> paramSpecs = new ArrayList<>();
        ParameterSpec p1 = new ParameterSpec();
        p1.setName("url");
        paramSpecs.add(p1);

        ParameterSpec p2 = new ParameterSpec();
        p2.setName("headers");
        paramSpecs.add(p2);

        tool.setParameters(paramSpecs);

        DummyHttpTool toolBean = new DummyHttpTool();
        tool.setToolBeanMethod(toolBean, DummyHttpTool.class.getMethod("httpGet", String.class, String.class));
        Map<String, Object> params = new HashMap<>();
        params.put("url", "https://example.com");
        params.put("headers", "{\"a\":\"b\"}");

        // Emulate the logic in AgentRegistry lambda
        java.lang.reflect.Method method = tool.getToolBeanMethod();
        Object bean = tool.getToolBean();
        java.lang.reflect.Parameter[] methodParams = method.getParameters();
        List<com.agentdsl.core.spec.ParameterSpec> pSpecs = tool.getParameters();

        Object[] args = new Object[methodParams.length];
        for (int i = 0; i < methodParams.length; i++) {
            String paramName = (pSpecs != null && i < pSpecs.size())
                    ? pSpecs.get(i).getName()
                    : methodParams[i].getName();
            Object val = params.get(paramName);
            args[i] = com.agentdsl.core.utils.ConvertUtils.convertArg(val, methodParams[i].getType());
            System.out.println("i=" + i + " -> name=" + paramName + ", val=" + val + ", converted=" + args[i]);
        }

        Object result = method.invoke(bean, args);
        assertEquals("URL: https://example.com", result);
    }

    public static class DummyHttpTool {
        public String httpGet(String url, String headers) {
            return "URL: " + url;
        }
    }
}
