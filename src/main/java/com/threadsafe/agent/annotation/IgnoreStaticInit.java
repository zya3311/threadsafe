package com.threadsafe.agent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)  // 只能用于字段
@Retention(RetentionPolicy.RUNTIME)
public @interface IgnoreStaticInit {
} 