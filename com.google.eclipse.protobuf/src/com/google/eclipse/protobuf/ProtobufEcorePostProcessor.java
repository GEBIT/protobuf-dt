/*
 * Copyright (c) 2012 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.xtext.GeneratedMetamodel;
import org.eclipse.xtext.xtext.ecoreInference.IXtext2EcorePostProcessor;

/**
 * @author alruiz@google.com (Alex Ruiz)
 */
@SuppressWarnings("restriction")
public class ProtobufEcorePostProcessor implements IXtext2EcorePostProcessor {

  @Override public void process(GeneratedMetamodel metamodel) {
    EPackage p = metamodel.getEPackage();
    for (EClassifier c : p.getEClassifiers()) {
      if (c instanceof EClass && "Literal".equals(c.getName())) {
        processLiteralClass((EClass) c);
      }
    }
    EcoreFactory factory = EcoreFactory.eINSTANCE;
    EClass builtin = factory.createEClass();
    builtin.setName("BuiltinLiteral");
    builtin.getESuperTypes().add((EClass) p.getEClassifier("Literal"));
    EAttribute booleanValue = factory.createEAttribute();
    booleanValue.setName("booleanValue");
    booleanValue.setEType(EcorePackage.Literals.EBOOLEAN);
    builtin.getEStructuralFeatures().add(booleanValue);
    EAttribute floatingValue = factory.createEAttribute();
    floatingValue.setName("floatingValue");
    floatingValue.setEType(EcorePackage.Literals.EDOUBLE);
    builtin.getEStructuralFeatures().add(floatingValue);
    p.getEClassifiers().add(builtin);
    EReference literals = factory.createEReference();
    literals.setName("builtinLiterals");
    literals.setEType(builtin);
    literals.setContainment(true);
    literals.setTransient(true);
    literals.setUpperBound(-1);
    ((EClass) p.getEClassifier("Protobuf")).getEStructuralFeatures().add(literals);
    EClass fieldName = (EClass) p.getEClassifier("FieldName");
    EClass normalName = (EClass) p.getEClassifier("NormalFieldName");
    EClass extensionName = (EClass) p.getEClassifier("ExtensionFieldName");
    EReference target = (EReference) normalName.getEStructuralFeature("target");
    normalName.getEStructuralFeatures().remove(target);
    extensionName.getEStructuralFeatures().remove(extensionName.getEStructuralFeature("target"));
    fieldName.getEStructuralFeatures().add(target);
    EReference entryFields = factory.createEReference();
    entryFields.setName("entryFields");
    entryFields.setEType(p.getEClassifier("MessageField"));
    entryFields.setContainment(true);
    entryFields.setTransient(true);
    entryFields.setUpperBound(-1);
    ((EClass) p.getEClassifier("MapType")).getEStructuralFeatures().add(entryFields);
  }

  private void processLiteralClass(EClass c) {
    for (EAttribute a : c.getEAllAttributes()) {
      if ("index".equals(a.getName())) {
        a.setDefaultValue(-1L);
        break;
      }
    }
  }
}
