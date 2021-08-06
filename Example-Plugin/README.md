# Example-Plugin

This plugin is an example of what plugins look like and a template for
creating new plugins.

## Building

You will need Ant to compile the plugin, below are the important targets.

The build file is set up to be generic and work for most plugins with only
small configuration if you use the structure specified below. If you have
a different folder structure, you can also easily reconfigure the folder names.

For your own plugin, you'll need to configure the project name, and the
`packagesForJavadoc` property to tell it what packages you want to
generate javadoc for. There is also configuration for other things like
leaving in debug symbols or verbose output.

Additionally while the `plugin.yml` file is important for a plugin to be
loaded, it's also where name and version information is pulled when generating
the distributable jars.

### Ant targets

These are the important ant targets:

- `clean` Deletes generated files and directories, distribution files, compiled classes, etc.
- `compile` Compiles the source code.
- `compileTest` (default) Compiles the source and test code.
- `dist` Compiles the source, generates plugin, source, and javadoc jars.
- `test` Runs all the tests.

## Folder Structure

The standard directory structure is laid out below:

- `src/` (source goes here)
- `test/` (test code goes here)
- `bin/` (used during building)
  - `classes` (output from compiling `src`)
  - `test-classes` (output from compiling `test`)
  - `test-report` (output from running JUnit test suites)
- `lib/` (libraries)
  - `bundled/` (needed for compilation, included in distribution jar)
  - `info/` (source and javadoc jars, for use in an IDE)
  - `plugins/` (needed for compilation, not included in distribution jars)
  - `testing/` (needed for compiling/running test code)
- `javadoc/` (javadoc is built here)
- `dist/` (jars generated for distribution go here)
- `build.xml` (Ant build file)
- `plugin.yml` (contains plugin information)
