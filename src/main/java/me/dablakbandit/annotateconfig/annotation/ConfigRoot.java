package me.dablakbandit.annotateconfig.annotation;

import me.dablakbandit.annotateconfig.NamingStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigRoot {
    String[] header() default {};

    NamingStrategy naming() default NamingStrategy.LOWER_KEBAB_CASE;

    boolean preserveUnknownFields() default true;
}
