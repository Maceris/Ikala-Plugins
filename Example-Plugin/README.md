# Example-Plugin

This plugin is an example of what plugins look like and a template for
creating new plugins.

## Building

This plugin uses Gradle to build. To build you will need Ikala-Core downloaded
as well, in the same folder that the Ikala-Plugins folder was downloaded. To 
build this you will need to run `./gradlew clean build` from the command
line.

## Making your own plugin

This project uses the standard Gradle folder structure. It has been configured 
so that you can run `./gradlew eclipse` to set up for import into Eclipse, 
or `./gradlew idea` to set up for IntelliJ.

For your own plugin, you'll need to configure:

- The root project name in `settings.gradle`
- The plugin details in `src/test/resources/plugin.yml`
- Dependency information in `build.gradle` and `gradle.properties`

Additionally while the `plugin.yml` file is important for a plugin to be
loaded, it's also where name and version information is pulled when generating
the distributable jars so it is very important to update that information.

You will also want to update this file and `CHANGELOG.md` to contain your own information.
