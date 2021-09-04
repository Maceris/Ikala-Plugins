# Ikala-Plugins

Plugins for the Ikala Engine.

## Plugin Descriptions

Each plugin will likely have it's own README, but below are high level descriptions of what each is.

* `EntityComponentSystem` - Provides a framework for the Entity Component System pattern.
* `Example-Plugin` - An example plugin, set up and ready to build. This can be used as a framework to start building your own plugins.
* `Ikala-Console` - A simple Swing console that can be used to see logs and execute commands.
* `Ikala-Database` - A relational database and utilities for interacting with it.
* `Ikala-Graphics` - Proivides utilities for graphics, using LWJGL.
* `Random` - Provides random generation utilities.
* `TestPlugins` - An assortment of plugins for unit testing the Plugin Management system.

## Creating your own

You can get started by making a copy of the Example-Plugin, but you'll need to make some changes:

* If using Eclipse, you'll need to change the name of the project in the `.project` file to what you want to call it
* `build.xml` should have the project name changed as well, `packagesForJavadoc` should be updated to include what packages you want in the docs. You can also reconfigure all the folder names and build options there.
* `CHANGELOG.md` should be cleared out and used to track your changes
* `plugin.yml` needs the details like plugin name and verison changed, and other info can be added as required (see the Ikala-Core wiki)
* `README.md` should be updated to give a description of your plugin or information about the project

From there you can add libraries, add/remove/change classes and tests, etc. Check out the Ikala-Core wiki for more details about what is available and how the framework works.
