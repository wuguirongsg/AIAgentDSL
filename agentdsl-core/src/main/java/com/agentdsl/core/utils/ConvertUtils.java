package com.agentdsl.core.utils;

public class ConvertUtils {
    /**
     * 基础类型转换。
     */
    public static Object convertArg(Object value, Class<?> targetType) {
        if (value == null)
            return null;
        if (targetType.isAssignableFrom(value.getClass()))
            return value;
        String str = value.toString();
        if (targetType == int.class || targetType == Integer.class)
            return Integer.parseInt(str);
        if (targetType == long.class || targetType == Long.class)
            return Long.parseLong(str);
        if (targetType == double.class || targetType == Double.class)
            return Double.parseDouble(str);
        if (targetType == float.class || targetType == Float.class)
            return Float.parseFloat(str);
        if (targetType == boolean.class || targetType == Boolean.class)
            return Boolean.parseBoolean(str);
        return str;
    }
}
