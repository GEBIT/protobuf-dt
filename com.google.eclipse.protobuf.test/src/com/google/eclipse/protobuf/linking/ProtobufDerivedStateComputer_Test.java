package com.google.eclipse.protobuf.linking;

import static com.google.eclipse.protobuf.junit.core.UnitTestModule.unitTestModule;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;
import static org.junit.Assert.*;

import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.resource.ILocationInFileProvider;
import org.eclipse.xtext.resource.IResourceDescription;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.ITextRegion;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.junit.Rule;
import org.junit.Test;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.model.util.OptionValues;
import com.google.eclipse.protobuf.protobuf.BuiltinLiteral;
import com.google.eclipse.protobuf.protobuf.Literal;
import com.google.eclipse.protobuf.protobuf.LiteralLink;
import com.google.eclipse.protobuf.protobuf.MapType;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.eclipse.protobuf.protobuf.ScalarType;
import com.google.eclipse.protobuf.protobuf.ScalarTypeLink;
import com.google.inject.Inject;

/** References to derived state stay source-addressable without becoming declarations. */
public class ProtobufDerivedStateComputer_Test {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(unitTestModule());
  @Inject private ILocationInFileProvider locations;
  @Inject private IResourceDescription.Manager descriptions;
  @Inject private OptionValues values;
  @Inject private IResourceValidator validator;

  // syntax = "proto3";
  // message Payload { string text = 1; }
  // message M { map<string, Payload> entries = 1; }
  @Test public void should_navigate_map_roles_to_their_real_type_regions_without_exporting_them() {
    Resource resource = xtext.root().eResource();
    EcoreUtil.resolveAll(resource);
    MapType map = EcoreUtil2.getAllContentsOfType(xtext.root(), MapType.class).get(0);
    MessageField key = map.getEntryFields().get(0);
    MessageField value = map.getEntryFields().get(1);
    String source = ((XtextResource) resource).getParseResult().getRootNode().getText();
    assertEquals("string", text(source, locations.getSignificantTextRegion(key)));
    assertEquals("Payload", text(source, locations.getSignificantTextRegion(value)));
    assertSame(resource, key.eResource());
    assertSame(key, resource.getEObject(resource.getURIFragment(key)));
    assertSame(value, resource.getEObject(resource.getURIFragment(value)));
    for (org.eclipse.xtext.resource.IEObjectDescription description : descriptions.getResourceDescription(resource).getExportedObjects()) {
      assertNotEquals(EcoreUtil.getURI(key), description.getEObjectURI());
      assertNotEquals(EcoreUtil.getURI(value), description.getEObjectURI());
    }
    assertTrue(validator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl).isEmpty());
  }

  // syntax = "proto3";
  // message M { map<string, int32> entries = 1; }
  @Test public void should_discard_old_map_roles_and_rebind_types_after_reparse() throws Exception {
    XtextResource resource = (XtextResource) xtext.root().eResource();
    EcoreUtil.resolveAll(resource);
    MapType oldMap = EcoreUtil2.getAllContentsOfType(resource.getContents().get(0), MapType.class).get(0);
    MessageField oldValue = oldMap.getEntryFields().get(1);
    String fragment = resource.getURIFragment(oldValue);
    resource.reparse(resource.getParseResult().getRootNode().getText().replace("map<string, int32>", "map<int32, string>"));
    EcoreUtil.resolveAll(resource);
    MapType map = EcoreUtil2.getAllContentsOfType(resource.getContents().get(0), MapType.class).get(0);
    MessageField value = map.getEntryFields().get(1);
    assertNull(oldValue.eResource());
    assertNotSame(oldValue, value);
    assertSame(value, resource.getEObject(fragment));
    assertEquals(ScalarType.INT32, ((ScalarTypeLink) values.typeOf(map.getEntryFields().get(0))).getTarget());
    assertEquals(ScalarType.STRING, ((ScalarTypeLink) values.typeOf(value)).getTarget());
    assertTrue(validator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl).isEmpty());
  }

  // syntax = "proto2";
  // enum E { true = 0; }
  // message M { optional E choice = 1 [default = true]; optional bool enabled = 2 [default = true]; }
  @Test public void should_distinguish_real_keyword_named_enum_values_from_source_less_scalar_constants() {
    Resource resource = xtext.root().eResource();
    EcoreUtil.resolveAll(resource);
    List<LiteralLink> links = EcoreUtil2.getAllContentsOfType(xtext.root(), LiteralLink.class);
    Literal enumeration = links.get(0).getTarget();
    Literal builtin = links.get(1).getTarget();
    assertFalse(enumeration instanceof BuiltinLiteral);
    assertTrue(builtin instanceof BuiltinLiteral);
    assertNotSame(enumeration, builtin);
    assertEquals(0, locations.getSignificantTextRegion(builtin).getLength());
    assertEquals(0, locations.getFullTextRegion(builtin).getLength());
    assertEquals("true", text(((XtextResource) resource).getParseResult().getRootNode().getText(),
        locations.getSignificantTextRegion(enumeration)));
    for (org.eclipse.xtext.resource.IEObjectDescription description : descriptions.getResourceDescription(resource).getExportedObjects()) {
      assertNotEquals(EcoreUtil.getURI(builtin), description.getEObjectURI());
    }
    assertTrue(validator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl).isEmpty());
  }

  private String text(String source, ITextRegion region) {
    return source.substring(region.getOffset(), region.getOffset() + region.getLength());
  }
}
