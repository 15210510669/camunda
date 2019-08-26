/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import java.util.function.Function;

public class ConversionUtils {
  
  public static final Function<String,Long> stringToLong = (aString) -> aString==null?null:Long.valueOf(aString);
  public static final Function<Long,String> longToString= (aLong) -> aLong==null?null:String.valueOf(aLong);

  public static String toStringOrNull(Object object) {
    return toStringOrDefault(object, null);
  }
  
  public static String toStringOrDefault(Object object,String defaultString) {
    return object == null ? defaultString : object.toString();
  }
  
  public static Long toLongOrNull(String aString) {
    return toLongOrDefault(aString, null);
  }
  
  public static Long toLongOrDefault(String aString,Long defaultValue) {
    return aString == null ? defaultValue : Long.valueOf(aString);
  }
  
}
