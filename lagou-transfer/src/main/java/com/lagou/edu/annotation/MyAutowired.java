package com.lagou.edu.annotation;

import java.lang.annotation.*;


/**
 * 暂时只考虑到属性上加注解，构造函数，方法名上不考虑
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MyAutowired {
}
