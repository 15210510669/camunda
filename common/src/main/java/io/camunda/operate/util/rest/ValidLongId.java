/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.util.rest;


import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;

@Target({ PARAMETER, FIELD })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidLongIdValidator.class)
@Documented
public @interface ValidLongId {
  String message() default "Specified ID is not valid";

  Class<?>[] groups() default { };

  Class<? extends Payload>[] payload() default { };
}
