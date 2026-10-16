/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.ui.parser;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static com.google.eclipse.protobuf.junit.core.XtextRule.createWith;

import org.antlr.runtime.CharStream;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.nodemodel.impl.NodeModelBuilder;
import org.eclipse.xtext.ui.editor.preferences.IPreferenceStoreAccess;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.junit.util.MultiLineTextBuilder;
import com.google.eclipse.protobuf.model.util.Syntaxes;
import com.google.eclipse.protobuf.parser.UnknownSyntaxProtobuf;
import com.google.eclipse.protobuf.protobuf.ComplexTypeLink;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.eclipse.protobuf.protobuf.ModifierEnum;
import com.google.eclipse.protobuf.ui.plugin.ProtobufEditorPlugIn;
import com.google.inject.Inject;

/**
 * Tests for <code>{@link PreferenceDrivenProtobufParser#doParse(String, CharStream, NodeModelBuilder, int)}</code>.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
public class PreferenceDrivenProtobufParser_doParse_Test {
  private static String proto1;

  @BeforeClass public static void setUpOnce() {
    MultiLineTextBuilder proto = new MultiLineTextBuilder();
    proto.append("// ignore errors")
         .append("c++header #include 'test/common/proto_class.h'");
    proto1 = proto.toString();
  }

  @Rule public XtextRule xtext = createWith(ProtobufEditorPlugIn.injector());

  @Inject private IPreferenceStoreAccess storeAccess;
  @Inject private org.eclipse.xtext.validation.IResourceValidator validator;
  @Inject private Syntaxes syntaxes;
  private boolean oldInternal;
  private IPreferenceStore store;

  @Before public void setUp() {
    store = storeAccess.getWritablePreferenceStore();
    oldInternal = store.getBoolean("misc.googleInternal");
  }

  @After public void restorePreferences() {
    store.setValue("misc.googleInternal", oldInternal);
  }

  @Test public void should_ignore_legacy_internal_source_only_when_internal_mode_is_enabled() {
    store.setValue("misc.googleInternal", false);
    xtext.parseText(proto1);
    java.util.List<org.eclipse.xtext.validation.Issue> rejected = validator.validate(xtext.resource(),
        org.eclipse.xtext.validation.CheckMode.ALL, org.eclipse.xtext.util.CancelIndicator.NullImpl);
    org.junit.Assert.assertTrue(rejected.toString(), rejected.stream().anyMatch(issue ->
        org.eclipse.xtext.diagnostics.Diagnostic.SYNTAX_DIAGNOSTIC.equals(issue.getCode())));
    store.setValue("misc.googleInternal", true);
    xtext.parseText(proto1);
    assertThat(xtext.root(), instanceOf(UnknownSyntaxProtobuf.class));
    java.util.List<org.eclipse.xtext.validation.Issue> accepted = validator.validate(xtext.resource(),
        org.eclipse.xtext.validation.CheckMode.ALL, org.eclipse.xtext.util.CancelIndicator.NullImpl);
    org.junit.Assert.assertFalse(accepted.toString(), accepted.stream().anyMatch(issue ->
        issue.getSeverity() == org.eclipse.xtext.diagnostics.Severity.ERROR));
  }

  @Test public void should_keep_syntaxless_proto2_fields_and_links_in_both_preference_modes() {
    for (boolean internal : new boolean[] {false, true}) {
      store.setValue("misc.googleInternal", internal);
      xtext.parseText("message Outer { optional int32 count = 1 [default = 7]; "
          + "message Inner { required string text = 1; } optional Inner child = 2; }");
      java.util.List<org.eclipse.xtext.validation.Issue> issues = validator.validate(xtext.resource(),
          org.eclipse.xtext.validation.CheckMode.ALL, org.eclipse.xtext.util.CancelIndicator.NullImpl);
      org.junit.Assert.assertTrue(issues.toString(), issues.isEmpty());
      java.util.List<MessageField> fields = EcoreUtil2.getAllContentsOfType(xtext.root(), MessageField.class);
      assertEquals(ModifierEnum.REQUIRED, fields.stream().filter(field -> "text".equals(field.getName()))
          .findFirst().get().getModifier());
      MessageField child = fields.stream().filter(field -> "child".equals(field.getName())).findFirst().get();
      assertEquals("Inner", ((ComplexTypeLink) child.getType()).getTarget().getName());
    }
  }

  @Test public void should_reject_explicit_unknown_syntax_in_both_preference_modes() {
    for (boolean internal : new boolean[] {false, true}) {
      store.setValue("misc.googleInternal", internal);
      xtext.parseText("syntax = \"unsupported-dialect\"; message Unknown {}");
      java.util.List<org.eclipse.xtext.validation.Issue> issues = validator.validate(xtext.resource(),
          org.eclipse.xtext.validation.CheckMode.ALL, org.eclipse.xtext.util.CancelIndicator.NullImpl);
      assertTrue(issues.toString(), issues.stream().anyMatch(issue ->
          com.google.eclipse.protobuf.validation.ProtobufJavaValidator.SYNTAX_IS_NOT_KNOWN_ERROR
              .equals(issue.getCode())));
    }
  }

  @Test public void should_keep_both_editions_fields_and_links_in_both_preference_modes() {
    for (boolean internal : new boolean[] {false, true}) {
      store.setValue("misc.googleInternal", internal);
      for (String year : new String[] {"2023", "2024"}) {
        xtext.parseText("edition='20' \"" + year.substring(2) + "\"; message Outer { "
            + "int32 count=1 [default=7]; message Inner {} Inner child=2; }");
        java.util.List<org.eclipse.xtext.validation.Issue> issues = validator.validate(xtext.resource(),
            org.eclipse.xtext.validation.CheckMode.ALL, org.eclipse.xtext.util.CancelIndicator.NullImpl);
        assertTrue(issues.toString(), issues.isEmpty());
        assertEquals(year, syntaxes.getName(xtext.root().getEdition()));
        assertTrue(syntaxes.isEditions(xtext.root()));
        assertEquals(ModifierEnum.UNSPECIFIED, xtext.find("count", MessageField.class).getModifier());
        MessageField child = xtext.find("child", MessageField.class);
        assertEquals("Inner", ((ComplexTypeLink) child.getType()).getTarget().getName());
      }
    }
  }

  @Test public void should_keep_unsupported_and_incomplete_editions_in_both_preference_modes() {
    for (boolean internal : new boolean[] {false, true}) {
      store.setValue("misc.googleInternal", internal);
      for (String source : new String[] {"edition='2025'; message M {}", "edition='2026'; message M {}",
          "edition='proto2'; message M {}", "edition=''; message M {}", "edition",
          "edition=; message M {}", "edition='unterminated;\nmessage M {}"}) {
        xtext.parseText("// ignore errors\n" + source);
        org.junit.Assert.assertFalse(xtext.root() instanceof UnknownSyntaxProtobuf);
        org.junit.Assert.assertNotNull(xtext.root().getEdition());
        org.junit.Assert.assertFalse(syntaxes.isProto2(xtext.root()));
        org.junit.Assert.assertFalse(syntaxes.isProto3(xtext.root()));
        java.util.List<org.eclipse.xtext.validation.Issue> issues = validator.validate(xtext.resource(),
            org.eclipse.xtext.validation.CheckMode.ALL, org.eclipse.xtext.util.CancelIndicator.NullImpl);
        assertTrue(issues.toString(), issues.stream().anyMatch(issue ->
            issue.getSeverity() == org.eclipse.xtext.diagnostics.Severity.ERROR));
        org.junit.Assert.assertFalse(issues.toString(), issues.stream().anyMatch(issue ->
            com.google.eclipse.protobuf.validation.ProtobufJavaValidator.SYNTAX_IS_NOT_KNOWN_ERROR.equals(issue.getCode())));
      }
    }
  }

  @Test public void should_keep_declarations_after_misplaced_headers_in_both_preference_modes() {
    for (boolean internal : new boolean[] {false, true}) {
      store.setValue("misc.googleInternal", internal);
      for (String source : new String[] {
          "edition='2024'; edition='2023'; message After {}",
          "edition='2024'; syntax='proto3'; message After {}",
          "syntax='proto3'; edition='2024'; message After {}",
          "message Before {} edition='2024'; message After {}"}) {
        xtext.parseText("// ignore errors\n" + source);
        org.junit.Assert.assertFalse(xtext.root() instanceof UnknownSyntaxProtobuf);
        assertEquals("After", xtext.find("After", com.google.eclipse.protobuf.protobuf.Message.class).getName());
        assertTrue(xtext.resource().getParseResult().hasSyntaxErrors());
        org.junit.Assert.assertFalse(syntaxes.isProto2(xtext.root()));
        java.util.List<org.eclipse.xtext.validation.Issue> issues = validator.validate(xtext.resource(),
            org.eclipse.xtext.validation.CheckMode.ALL, org.eclipse.xtext.util.CancelIndicator.NullImpl);
        assertTrue(issues.toString(), issues.stream().anyMatch(org.eclipse.xtext.validation.Issue::isSyntaxError));
      }
    }
  }
}
