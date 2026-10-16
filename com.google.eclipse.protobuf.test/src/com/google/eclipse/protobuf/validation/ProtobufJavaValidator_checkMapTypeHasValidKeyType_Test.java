/*
 * Copyright (c) 2014 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.validation;

import static com.google.eclipse.protobuf.junit.core.IntegrationTestModule.integrationTestModule;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;
import static com.google.eclipse.protobuf.validation.ProtobufJavaValidator.INVALID_MAP_KEY_TYPE_ERROR;
import static org.junit.Assert.assertTrue;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.inject.Inject;

import org.junit.Rule;
import org.junit.Test;

/**
 * Resource-level map key and nested-map diagnostics.
 */
public class ProtobufJavaValidator_checkMapTypeHasValidKeyType_Test {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(integrationTestModule());

  @Inject private org.eclipse.xtext.validation.IResourceValidator resourceValidator;

  @Test public void should_validate_each_legal_map_key_in_both_dialects() {
    String[] keys = {"bool", "string", "int32", "int64", "uint32", "uint64", "sint32", "sint64",
        "fixed32", "fixed64", "sfixed32", "sfixed64"};
    for (String dialect : new String[] {"proto2", "proto3"}) {
      for (String key : keys) {
        xtext.parseText("syntax = '" + dialect + "'; message LegalMap { map<" + key + ", int32> entries = 1; }");
        java.util.List<org.eclipse.xtext.validation.Issue> issues = validateResource();
        assertTrue(dialect + " " + key + ": " + issues, issues.isEmpty());
      }
    }
  }

  @Test public void should_reject_nonintegral_complex_and_nested_map_keys_as_semantics_not_link_errors() {
    for (String key : new String[] {"float", "double", "bytes", "E", "M", "map<string, int32>"}) {
      xtext.parseText("syntax = 'proto3'; enum E { ZERO = 0; } message M {} "
          + "message InvalidMap { map<" + key + ", int32> entries = 1; }");
      java.util.List<org.eclipse.xtext.validation.Issue> issues = validateResource();
      assertTrue(key + ": " + issues, issues.stream().anyMatch(
          issue -> INVALID_MAP_KEY_TYPE_ERROR.equals(issue.getCode())));
    }
  }

  @Test public void should_reject_nested_map_values_with_the_existing_targeted_code() {
    xtext.parseText("syntax = 'proto3'; message NestedMap { map<string, map<string, int32>> entries = 1; }");
    java.util.List<org.eclipse.xtext.validation.Issue> issues = validateResource();
    assertTrue(issues.toString(), issues.stream().anyMatch(
        issue -> ProtobufJavaValidator.MAP_WITH_MAP_VALUE_TYPE_ERROR.equals(issue.getCode())));
  }

  private java.util.List<org.eclipse.xtext.validation.Issue> validateResource() {
    java.util.List<org.eclipse.xtext.validation.Issue> issues = resourceValidator.validate(
        xtext.resource(), org.eclipse.xtext.validation.CheckMode.ALL, null);
    assertTrue(xtext.resource().getErrors().toString(), xtext.resource().getErrors().isEmpty());
    return issues;
  }
}
