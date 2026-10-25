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
- The plugin details in `src/main/resources/plugin.yml`
- Dependency information in `build.gradle` and `gradle.properties`
- Default settings in `src/main/resources/config.yml`, which is copied to the
  plugin's config folder the first time it is loaded

Additionally while the `plugin.yml` file is important for a plugin to be
loaded, it's also where name and version information is pulled when generating
the distributable jars so it is very important to update that information. The
version must be a semantic version, like `1.0.0` or `1.0.0-beta.1`, since it is
compared against the last loaded version to call `onUpgrade`.

Some things to keep in mind:

- Other plugins are only visible if they're listed in `dependencies` or
  `soft-dependencies`, and those are enabled before this plugin.
- Third party jars needed at runtime go in the shared libs folder rather than
  in the plugin jar. Add them to the `library` configuration in `build.gradle`,
  and list them under `libraries` in `plugin.yml`.
- `getListeners` is called each time the plugin is enabled, and the listeners
  are only registered while it is enabled, so it can just return new ones.
- `getName` returns the name from `plugin.yml`, so it doesn't need to be
  overridden. A plugin that returns a different name fails to load.
- Register commands in `onEnable`, and unregister them in `onDisable`.

You will also want to update this file and `CHANGELOG.md` to contain your own information.
