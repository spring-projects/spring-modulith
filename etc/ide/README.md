# Spring Data Code Formatting Settings

This directory contains `eclipse-formatting.xml` and `intellij.importorder` settings files to be used with Eclipse and IntelliJ.

## Eclipse Setup

Import both files in Eclipse through the Preferences dialog.

## IntelliJ Setup

Use the IntelliJ [Eclipse Code Formatter](https://plugins.jetbrains.com/plugin/6546-eclipse-code-formatter) plugin to configure code formatting and import ordering with the newest Eclipse formatter version.

Additionally, make sure to configure your import settings in `Editor -> Code Style -> Java` with the following explicit settings:

* Use tab character indents
* Class count to use import with `*`: 10
* Names count to use static import with `*`: 1
