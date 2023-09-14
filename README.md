# protobuf-dt

## About
This project provides IDE integration to Eclipse users when working on protobuf
files. 

This is a fork of an originally Google-initiated project which has been largely dormant for years and is currently without an official maintainer, albeit the produc t itself has been working reliably for years without any changes. It was forked because it's in active use at GEBIT and some shortcomings regarding missing capabilites have come up.

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

## Building a release

This plugin currently uses the "oldschool" PDE export workflow from an Eclipse IDE, so you can build an update site by exporting the "feature" project as an installable feature. This also means that version numbers must be manually managed in the META-INF files.

## Releasing

The plugin is currently released on GitHub and uses GitHub Releases as an Eclipse updatesite. This is possible by flattening the update site structure with the "repoflattener.java" jbang script checked in under /releng/jbang/repoflattener.java, which can be given a "normal" updatesite path and which then produces a flattened updatesite without the typical "plugins" and "features" directories. The files in this flattened updatesite can then be released on GitHub and imported into Eclipse as a normal updatesite. These update sites can also be submitted to the Eclipse Marketplace for inclusion into the official plugin directory.