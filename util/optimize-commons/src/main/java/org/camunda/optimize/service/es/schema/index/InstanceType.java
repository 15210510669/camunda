/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.FORMAT_PROPERTY_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.IGNORE_ABOVE_CHAR_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.IGNORE_ABOVE_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.IGNORE_MALFORMED;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LOWERCASE_NGRAM;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LOWERCASE_NORMALIZER;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_PROPERTY_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_DATE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_DOUBLE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_KEYWORD;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_LONG;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_TEXT;

public interface InstanceType {

  String MULTIVALUE_FIELD_DATE = "date";
  String MULTIVALUE_FIELD_LONG = "long";
  String MULTIVALUE_FIELD_DOUBLE = "double";
  String N_GRAM_FIELD = "nGramField";
  String LOWERCASE_FIELD = "lowercaseField";


  default XContentBuilder addValueMultifields(XContentBuilder builder) throws IOException {
    // @formatter:off
    return builder
      // search relevant fields
      .startObject(N_GRAM_FIELD)
        .field(MAPPING_PROPERTY_TYPE, TYPE_TEXT)
        .field("analyzer", LOWERCASE_NGRAM)
      .endObject()
      .startObject(LOWERCASE_FIELD)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
        .field("normalizer", LOWERCASE_NORMALIZER)
        .field(IGNORE_ABOVE_SETTING, IGNORE_ABOVE_CHAR_LIMIT)
      .endObject()
      // multi type fields
      .startObject(MULTIVALUE_FIELD_DATE)
        .field(MAPPING_PROPERTY_TYPE, TYPE_DATE)
        .field(FORMAT_PROPERTY_TYPE, OPTIMIZE_DATE_FORMAT)
        .field(IGNORE_MALFORMED, true)
      .endObject()
      .startObject(MULTIVALUE_FIELD_LONG)
        .field(MAPPING_PROPERTY_TYPE, TYPE_LONG)
        .field(IGNORE_MALFORMED, true)
      .endObject()
      .startObject(MULTIVALUE_FIELD_DOUBLE)
        .field(MAPPING_PROPERTY_TYPE, TYPE_DOUBLE)
        .field(IGNORE_MALFORMED, true)
      .endObject()
      // boolean is not supported to be ignored if malformed, see https://github.com/elastic/elasticsearch/pull/29522
      // it is enough tough to just filter on the default string value with true/false at query time
       ;
    // @formatter:on
  }
}
