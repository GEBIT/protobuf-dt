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
import static com.google.eclipse.protobuf.validation.ProtobufJavaValidator.MAP_WITH_MODIFIER_ERROR;
import static com.google.eclipse.protobuf.validation.ProtobufJavaValidator.MISSING_MODIFIER_ERROR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.inject.Inject;

import org.junit.Rule;
import org.junit.Test;

/**
 * Resource-level field-label and dialect regressions.
 */
public class ProtobufJavaValidator_checkFieldModifiers_Test {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(integrationTestModule());

  @Inject private org.eclipse.xtext.validation.IResourceValidator resourceValidator;

  @Test public void should_require_proto2_labels_and_reject_proto3_required_fields() {
    assertIssue("syntax = 'proto2'; message MissingLabel { string value = 1; }", MISSING_MODIFIER_ERROR);
    assertIssue("syntax = 'proto3'; message RequiredLabel { required string value = 1; }",
        ProtobufJavaValidator.REQUIRED_IN_PROTO3_ERROR);
  }

  @Test public void should_accept_legal_ordinary_field_labels_and_unlabeled_maps() {
    String[] sources = {
        "syntax = 'proto2'; message Labels { required string name = 1; optional int32 count = 2; "
            + "repeated bool flags = 3; map<string, int32> values = 4; }",
        "syntax = 'proto3'; message Labels { string name = 1; optional int32 count = 2; "
            + "repeated bool flags = 3; map<string, int32> values = 4; }"};
    for (String source : sources) {
      xtext.parseText(source);
      java.util.List<org.eclipse.xtext.validation.Issue> issues = resourceValidator.validate(
          xtext.resource(), org.eclipse.xtext.validation.CheckMode.ALL, null);
      assertTrue(xtext.resource().getErrors().toString(), xtext.resource().getErrors().isEmpty());
      assertTrue(issues.toString(), issues.isEmpty());
    }
  }

  @Test public void should_reject_labels_on_maps_in_both_dialects() {
    for (String dialect : new String[] {"proto2", "proto3"}) {
      for (String modifier : new String[] {"optional", "required", "repeated"}) {
        assertIssue("syntax = '" + dialect + "'; message LabeledMap { "
            + modifier + " map<string, int32> values = 1; }", MAP_WITH_MODIFIER_ERROR);
      }
    }
  }

  @Test public void should_accept_unlabeled_oneof_members_but_reject_member_labels() {
    for (String dialect : new String[] {"proto2", "proto3"}) {
      xtext.parseText("syntax = '" + dialect + "'; message Choice { oneof kind { string text = 1; int32 number = 2; } }");
      java.util.List<org.eclipse.xtext.validation.Issue> issues = resourceValidator.validate(
          xtext.resource(), org.eclipse.xtext.validation.CheckMode.ALL, null);
      assertTrue(xtext.resource().getErrors().toString(), xtext.resource().getErrors().isEmpty());
      assertTrue(issues.toString(), issues.isEmpty());
      for (String modifier : new String[] {"optional", "required", "repeated"}) {
        assertIssue("syntax = '" + dialect + "'; message LabeledMember { oneof kind { "
            + modifier + " string text = 1; } }", ProtobufJavaValidator.ONEOF_FIELD_WITH_MODIFIER_ERROR);
      }
    }
  }

  private void assertIssue(String source, String issueCode) {
    xtext.parseText(source);
    java.util.List<org.eclipse.xtext.validation.Issue> issues = resourceValidator.validate(
        xtext.resource(), org.eclipse.xtext.validation.CheckMode.ALL, null);
    assertTrue(xtext.resource().getErrors().toString(), xtext.resource().getErrors().isEmpty());
    assertTrue(issues.toString(), issues.stream().anyMatch(issue -> issueCode.equals(issue.getCode())));
  }

  // message Outer { message Inner { string value = 1; } }
  @Test public void should_diagnose_syntaxless_nested_unlabeled_field() {
    java.util.List<org.eclipse.xtext.validation.Issue> issues = resourceValidator.validate(
        xtext.resource(), org.eclipse.xtext.validation.CheckMode.ALL, null);
    assertTrue(xtext.resource().getErrors().isEmpty());
    assertEquals(1, issues.size());
    assertEquals(MISSING_MODIFIER_ERROR, issues.get(0).getCode());
  }

  // syntax = "proto2";
  // message Outer { message Inner { string value = 1; } }
  @Test public void should_diagnose_explicit_proto2_nested_unlabeled_field() {
    java.util.List<org.eclipse.xtext.validation.Issue> issues = resourceValidator.validate(
        xtext.resource(), org.eclipse.xtext.validation.CheckMode.ALL, null);
    assertTrue(xtext.resource().getErrors().isEmpty());
    assertEquals(1, issues.size());
    assertEquals(MISSING_MODIFIER_ERROR, issues.get(0).getCode());
  }

  // syntax = "proto3";
  // message Outer { message Inner { string value = 1; optional int32 count = 2; } }
  @Test public void should_preserve_nested_proto3_implicit_and_optional_fields() {
    assertTrue(resourceValidator.validate(
        xtext.resource(), org.eclipse.xtext.validation.CheckMode.ALL, null).isEmpty());
    assertTrue(xtext.resource().getErrors().isEmpty());
    assertEquals(com.google.eclipse.protobuf.protobuf.ModifierEnum.UNSPECIFIED,
        xtext.find("value", MessageField.class).getModifier());
    assertEquals(com.google.eclipse.protobuf.protobuf.ModifierEnum.OPTIONAL,
        xtext.find("count", MessageField.class).getModifier());
  }

  @Test public void should_report_proto3_default_with_stable_issue_code() {
    xtext.parseText("syntax = 'proto3'; message Defaults { optional int32 value = 1 [default = 2]; }");
    java.util.List<org.eclipse.xtext.validation.Issue> issues = resourceValidator.validate(
        xtext.resource(), org.eclipse.xtext.validation.CheckMode.ALL, null);
    assertTrue(xtext.resource().getErrors().isEmpty());
    assertTrue(issues.toString(), issues.stream().anyMatch(
        issue -> ProtobufJavaValidator.PROTO3_DEFAULT_ERROR.equals(issue.getCode())));
  }

  @Test public void should_reject_map_in_oneof_independently_of_its_label() {
    for (String modifier : new String[] {"", "optional "}) {
      xtext.parseText("syntax = 'proto3'; message MapChoice { oneof choice { "
          + modifier + "map<string, int32> entries = 1; } }");
      java.util.List<org.eclipse.xtext.validation.Issue> issues = resourceValidator.validate(
          xtext.resource(), org.eclipse.xtext.validation.CheckMode.ALL, null);
      assertTrue(xtext.resource().getErrors().isEmpty());
      assertTrue(issues.toString(), issues.stream().anyMatch(
          issue -> issue.getSeverity() == org.eclipse.xtext.diagnostics.Severity.ERROR));
      if (!modifier.isEmpty()) {
        assertTrue(issues.toString(), issues.stream().anyMatch(
            issue -> MAP_WITH_MODIFIER_ERROR.equals(issue.getCode())));
      }
    }
  }
}
