package com.agentdsl.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在工具方法参数上，提供参数的描述和必填信息。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ToolParam {

    /**
     * 参数名称 (如果不指定，将尝试使用反射获取的参数名，编译时未加 -parameters 可能会导致获得 arg0)。
     */
    String name() default "";

    /**
     * 参数描述。
     */
    String description() default "";

    /**
     * 是否必填，默认 true。
     */
    boolean required() default true;
}
