package com.google.eclipse.protobuf.ui.outline;

import static com.google.eclipse.protobuf.junit.core.XtextRule.createWith;
import static org.junit.Assert.*;

import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.widgets.Display;
import org.eclipse.xtext.ui.editor.model.XtextDocument;
import org.eclipse.xtext.ui.editor.outline.IOutlineNode;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.junit.Rule;
import org.junit.Test;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.ui.plugin.ProtobufEditorPlugIn;
import com.google.inject.Inject;

public class ProtobufOutlineTreeProvider_Test {
  @Rule public XtextRule xtext = createWith(ProtobufEditorPlugIn.injector());
  @Inject private ProtobufOutlineTreeProvider outlines;
  @Inject private IResourceValidator validator;

  @Test public void should_show_real_oneof_members_and_keep_maps_and_metadata_ranges_as_leaves() {
    String source = "syntax = \"proto2\"; message Payload {} message Container { "
        + "map<string, Payload> payloads = 1; oneof choice { Payload selected = 2; } "
        + "extensions 100 to 200 [verification = UNVERIFIED]; }";
    xtext.parseText(source);
    java.util.List<org.eclipse.xtext.validation.Issue> issues =
        validator.validate(xtext.resource(), CheckMode.ALL, CancelIndicator.NullImpl);
    assertTrue(issues.toString(), issues.isEmpty());
    Display.getDefault().syncExec(() -> {
      XtextDocument document = xtext.injector().getInstance(XtextDocument.class);
      document.set(source);
      document.setInput(xtext.resource());
      IOutlineNode root = outlines.createRoot(document);
      IOutlineNode container = child(root, "Container");
      assertEquals(3, container.getChildren().size());
      IOutlineNode map = child(container, "payloads [1] : map<string, Payload>");
      assertFalse(map.hasChildren());
      assertTrue(map.getChildren().isEmpty());
      IOutlineNode oneof = child(container, "choice");
      assertTrue(oneof.hasChildren());
      assertEquals(1, oneof.getChildren().size());
      assertFalse(child(oneof, "selected [2] : Payload").hasChildren());
      IOutlineNode range = child(container, "100 > 200");
      assertFalse(range.hasChildren());
      assertTrue(range.getChildren().isEmpty());
      assertEquals(2, root.getChildren().size());
    });
  }

  @Test public void should_keep_edition_headers_out_of_the_existing_outline_pipeline() {
    for (String year : new String[] {"2023", "2024"}) {
      String source = "edition='" + year + "'; message Payload {} message Container { "
          + "map<string, Payload> payloads=1; oneof choice { Payload selected=2; } }";
      xtext.parseText(source);
      java.util.List<org.eclipse.xtext.validation.Issue> issues =
          validator.validate(xtext.resource(), CheckMode.ALL, CancelIndicator.NullImpl);
      assertTrue(issues.toString(), issues.isEmpty());
      Display.getDefault().syncExec(() -> {
        XtextDocument document = xtext.injector().getInstance(XtextDocument.class);
        document.set(source);
        document.setInput(xtext.resource());
        IOutlineNode root = outlines.createRoot(document);
        assertEquals(2, root.getChildren().size());
        IOutlineNode container = child(root, "Container");
        assertFalse(child(container, "payloads [1] : map<string, Payload>").hasChildren());
        assertFalse(child(child(container, "choice"), "selected [2] : Payload").hasChildren());
      });
    }
  }

  private IOutlineNode child(IOutlineNode parent, String expected) {
    for (IOutlineNode child : parent.getChildren()) {
      Object label = child.getText();
      String text = label instanceof StyledString ? ((StyledString) label).getString() : String.valueOf(label);
      if (expected.equals(text)) return child;
    }
    throw new AssertionError("Missing outline node " + expected);
  }
}
