# protobuf-dt

## About
This project provides IDE integration to Eclipse users when working on protobuf
files. 

This is a fork of an originally Google-initiated project which has been largely dormant for years and is currently without an official maintainer, albeit the produc t itself has been working reliably for years without any changes. It was forked because it's in active use at GEBIT and some shortcomings regarding missing capabilites have come up.

Files without a syntax or edition declaration use proto2, including nested messages, regardless
of the internal-source filtering preference. Explicit `syntax = "proto3";` supports
both implicit and optional field labels; `required` is rejected.
Proto2 defaults and the proto2 new-file template remain unchanged.

The language core supports proto3 maps, oneofs, optional presence and streaming RPCs,
with typed native/custom option aggregates, expanded Any values and exact unsigned-64
constants. Field, enum, reservation and extension-number diagnostics follow their
separate protobuf domains; implicit map roles are not source declarations.

Explicit `edition = "2023";` and `edition = "2024";` declarations enable Editions;
other edition headers remain errors. Editions fields are unlabeled or repeated, with
presence, packing, enum openness, UTF-8, message encoding and JSON policy resolved
from typed feature settings and the selected descriptor's metadata. Custom FeatureSet
extensions, inherited leaf settings, support windows and deprecation warnings use the
same policy in validation and completion. Incomplete descriptors are diagnosed without
silently substituting bundled feature definitions.

Edition 2024 supports option-only imports and per-type `export`/`local` visibility in
the actual reference scopes, including hyperlinks and completion. Naming rules and
STRICT visibility follow the selected feature policy. Reserved names use identifiers
in Editions and quoted strings in proto2/proto3. Edition-specific quick fixes repair
unsupported years and remove forbidden labels or entire explicit defaults.

Bundled `descriptor.proto` and `java_features.proto` come verbatim from
`com.google.protobuf:protobuf-java:4.36.2` (upstream v36.2); provenance and the upstream
BSD notice are in `com.google.eclipse.protobuf/google/protobuf/`. Other bundled
well-known schemas are intentionally unchanged. Project dependencies/import roots
take precedence over bundled well-known files. An explicitly selected Options
definition file supplies both native options and the canonical descriptor import;
an unreadable or older selection is not silently mixed with bundled members.

Completion and quickfixes follow the effective dialect and the selected option context.
Formatting preserves explicit optional labels, expanded Any URL prefixes and typed metadata.
Filesystem import roots reuse workspace resource identities, so navigation and refactoring
operate on the existing workspace editor rather than a duplicate filesystem document.
Archive import and symbol hyperlinks open UTF-8, read-only members with their originating
project's descriptor and import settings; different project contexts do not share an editor.
Changing descriptor settings relinks open documents without discarding unsaved text.
Compiler diagnostics use registered Eclipse marker types and preserve source file/line
locations; matching compiler/editor errors are deduplicated. CDT declaration navigation
supports both legacy and current protoc header banners.


## Installing

This forked plugin has now been listed in the Eclipse Marketplace and can thus easily be installed into any Eclipse version (all versions newer than Neon should work): https://marketplace.eclipse.org/content/protocol-buffer-editor

## Preparing a development workspace

Download the [Eclipse Installer](https://eclipse.org/downloads/).

Run it.

If you see a yellow exclamation mark in the top-right, it means the installer
is out-of-date. Click on the exclamation mark and then click on the word
**UPDATE**. After the installer finishes updating, it will restart and you can
continue with these steps.

Click the menu button in the upper-right corner.

Select "ADVANCED MODE...".

Select "Eclipse IDE for Eclipse Committers".

Select "Neon".

Click **Next**.

This page is titled "Product". Click the little green "+" near the top-right of
the screen.

Paste this link into the box titled "Resource URIs:".

https://raw.githubusercontent.com/GEBIT/protobuf-dt/master/releng/ProtobufEditor.setup

Click **OK**.

Click the checkbox next to "Protobuf Editor".

Click **Next**.

In "Target Platform", select "Neon".

Click **Finish**.

Wait for Eclipse to install, then click **Finish** to close the wizard. Wait for
the build to finish.

Expand **releng**.

Right-click on **GenerateProtobuf.mwe2.launch** and run this launch config, which should generate the DSL code.

Use this checked-in workflow: its custom generator and Ecore postprocessor preserve
the shared and transient model roles. Do not substitute stock Xtext generation or
hand-edit runtime/UI generated parsers, serializers or model implementations.

## Building a release

This plugin currently uses the "oldschool" PDE export workflow from an Eclipse IDE, so you can build an update site by exporting the "feature" project as an installable feature. This also means that version numbers must be manually managed in the META-INF files.

## Releasing

The plugin is currently released on GitHub and uses GitHub Releases as an Eclipse updatesite. This is possible by flattening the update site structure with the "repoflattener.java" jbang script checked in under /releng/jbang/repoflattener.java, which can be given a "normal" updatesite path and which then produces a flattened updatesite without the typical "plugins" and "features" directories. The files in this flattened updatesite can then be released on GitHub and imported into Eclipse as a normal updatesite. These update sites can also be submitted to the Eclipse Marketplace for inclusion into the official plugin directory.