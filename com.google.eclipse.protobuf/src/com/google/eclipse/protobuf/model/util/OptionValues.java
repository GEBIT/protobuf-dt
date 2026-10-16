package com.google.eclipse.protobuf.model.util;

import org.eclipse.emf.ecore.EObject;

import com.google.eclipse.protobuf.protobuf.AbstractOption;
import com.google.eclipse.protobuf.protobuf.AnyFieldName;
import com.google.eclipse.protobuf.protobuf.BuiltinLiteral;
import com.google.eclipse.protobuf.protobuf.ComplexTypeLink;
import com.google.eclipse.protobuf.protobuf.DefaultValueFieldOption;
import com.google.eclipse.protobuf.protobuf.DoubleLink;
import com.google.eclipse.protobuf.protobuf.FieldName;
import com.google.eclipse.protobuf.protobuf.Group;
import com.google.eclipse.protobuf.protobuf.HexNumberLink;
import com.google.eclipse.protobuf.protobuf.IndexedElement;
import com.google.eclipse.protobuf.protobuf.JsonNameFieldOption;
import com.google.eclipse.protobuf.protobuf.Literal;
import com.google.eclipse.protobuf.protobuf.LiteralLink;
import com.google.eclipse.protobuf.protobuf.LongLink;
import com.google.eclipse.protobuf.protobuf.MapType;
import com.google.eclipse.protobuf.protobuf.MapTypeLink;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.eclipse.protobuf.protobuf.ModifierEnum;
import com.google.eclipse.protobuf.protobuf.Protobuf;
import com.google.eclipse.protobuf.protobuf.ScalarTypeLink;
import com.google.eclipse.protobuf.protobuf.TypeLink;
import com.google.eclipse.protobuf.protobuf.Value;
import com.google.eclipse.protobuf.protobuf.ValueField;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/** Expected types for option paths and TextFormat assignments, including implicit map roles. */
@Singleton
public class OptionValues {
  @Inject private Options options;
  @Inject private ModelObjects modelObjects;

  public IndexedElement declarationOf(EObject context) {
    for (EObject current = context; current != null; current = current.eContainer()) {
      if (current instanceof DefaultValueFieldOption) {
        return resolvedDeclaration(current.eContainer());
      }
      if (current instanceof JsonNameFieldOption) {
        return null;
      }
      if (current instanceof AbstractOption) {
        return resolvedDeclaration(options.sourceOf((AbstractOption) current));
      }
      if (current instanceof ValueField) {
        FieldName name = ((ValueField) current).getName();
        return name == null || name instanceof AnyFieldName ? null : resolvedDeclaration(name.getTarget());
      }
      if (current instanceof FieldName) {
        return current instanceof AnyFieldName ? null : resolvedDeclaration(((FieldName) current).getTarget());
      }
    }
    return null;
  }

  private IndexedElement resolvedDeclaration(EObject declaration) {
    return declaration instanceof IndexedElement && !declaration.eIsProxy() ? (IndexedElement) declaration : null;
  }

  public EObject typeOf(IndexedElement declaration) {
    if (declaration == null || declaration.eIsProxy()) {
      return null;
    }
    if (declaration instanceof Group) {
      return declaration;
    }
    if (!(declaration instanceof MessageField)) {
      return null;
    }
    MessageField field = (MessageField) declaration;
    TypeLink link;
    if (field.eContainer() instanceof MapType) {
      MapType map = (MapType) field.eContainer();
      link = "key".equals(field.getName()) ? map.getKeyType() : "value".equals(field.getName()) ? map.getValueType() : null;
    } else {
      link = field.getType();
    }
    if (link instanceof ScalarTypeLink) {
      return ((ScalarTypeLink) link).getTarget() == null ? null : link;
    }
    EObject target = link instanceof ComplexTypeLink ? ((ComplexTypeLink) link).getTarget()
        : link instanceof MapTypeLink ? ((MapTypeLink) link).getTarget() : null;
    return target == null || target.eIsProxy() ? null : target;
  }

  public EObject expectedType(EObject context) {
    for (EObject current = context; current != null; current = current.eContainer()) {
      if (current instanceof ValueField) {
        FieldName name = ((ValueField) current).getName();
        if (name instanceof AnyFieldName) {
          EObject target = ((AnyFieldName) name).getEmbeddedType();
          return target == null || target.eIsProxy() ? null : target;
        }
        break;
      }
      if (current instanceof AnyFieldName) {
        EObject target = ((AnyFieldName) current).getEmbeddedType();
        return target == null || target.eIsProxy() ? null : target;
      }
      if (current instanceof AbstractOption) {
        break;
      }
    }
    return typeOf(declarationOf(context));
  }

  public boolean isRepeated(IndexedElement declaration) {
    if (declaration instanceof Group) {
      return ((Group) declaration).getModifier() == ModifierEnum.REPEATED;
    }
    if (!(declaration instanceof MessageField) || declaration.eContainer() instanceof MapType) {
      return false;
    }
    MessageField field = (MessageField) declaration;
    return field.getModifier() == ModifierEnum.REPEATED || field.getType() instanceof MapTypeLink;
  }

  public boolean booleanValue(Value value) {
    if (value instanceof LiteralLink && ((LiteralLink) value).getTarget() instanceof BuiltinLiteral) {
      return ((BuiltinLiteral) ((LiteralLink) value).getTarget()).isBooleanValue();
    }
    return value instanceof LongLink && java.math.BigInteger.ONE.equals(((LongLink) value).getTarget())
        || value instanceof HexNumberLink && java.math.BigInteger.ONE.equals(((HexNumberLink) value).getTarget());
  }

  public double floatingValue(Value value) {
    if (value instanceof DoubleLink) {
      return ((DoubleLink) value).getTarget();
    }
    if (value instanceof LongLink && ((LongLink) value).getTarget() != null) {
      return ((LongLink) value).getTarget().doubleValue();
    }
    if (value instanceof HexNumberLink && ((HexNumberLink) value).getTarget() != null) {
      return ((HexNumberLink) value).getTarget().doubleValue();
    }
    if (value instanceof LiteralLink && ((LiteralLink) value).getTarget() instanceof BuiltinLiteral) {
      LiteralLink link = (LiteralLink) value;
      double result = ((BuiltinLiteral) link.getTarget()).getFloatingValue();
      return link.isIsNegative() ? -result : result;
    }
    return Double.NaN;
  }

  public Literal builtinBoolean(boolean value, EObject context) {
    if (context != null && context.eResource() != null) {
      context.eResource().getContents();
    }
    Protobuf root = modelObjects.rootOf(context);
    if (root != null) {
      for (BuiltinLiteral literal : root.getBuiltinLiterals()) {
        if ((value ? "true" : "false").equals(literal.getName())) {
          return literal;
        }
      }
    }
    return null;
  }
}
