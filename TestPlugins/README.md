# Test Plugins

These plugins are for testing the Plugin Management system of the engine.

# Building

To run, run `ant`, which will default to the `buildJars` task, which builds each jar and copies them into the jars folder.
To clean up the jars and plugin build files, run `ant clean`.

# Tests

The plugins that exist, and a brief description of why they exist, are below. For the sake of clarity, dependencies are shown using the convention `A ==> B` means `A lists B in dependencies` and `A --> B` means `A lists B in soft-dependencies`. If a class has multiple dependencies, they will be listed on new lines with `''` indicating it's the same plugin again.

## Standalone

`TestStandalone` is a minimal plugin with no dependencies.

## Cyclic dependencies

These plugins depend on each other, and use classes from each other at runtime.

```
TestCycleA ==> TestCycleB
TestCycleB ==> TestCycleA
```

## Cyclic soft dependencies

These plugins have a soft dependency graph with a loop.

```
TestSoftDependCycleA --> TestSoftDependCycleB
TestSoftDependCycleB --> TestSoftDependCycleC
TestSoftDependCycleC --> TestSoftDependCycleA
```

## Dependencies

These classes test the dependency resolution and load order.

```
TestDependA --> TestDependB
    ''      --> TestDependC
TestDependC --> TestDependD
```

## Soft dependencies

These classes test the load order.

```
TestSoftDependA --> TestSoftDependB
      ''        --> TestSoftDependC
TestSoftDependC --> TestSoftDependD
```

## Complex dependency resolution

This group of plugins has a complex dependency graph with many cycles.

```
TestComplexChainA ==> TestComplexChainB
       ''         --> TestComplexChainC
TestComplexChainB --> TestComplexChainD
TestComplexChainC ==> TestComplexChainA
       ''         --> TestComplexChainD
TestComplexChainD ==> TestComplexChainC
       ''         --> TestComplexChainA
```

## Error scenarios

* `TestErrorInvalidName` - Invalid characters in the name in the plugin config.
* `TestErrorInvalidVersion` - Invalid version in the name in the plugin config.
* `TestErrorMainClassType` - Main class does not extend Plugin.
* `TestErrorNoMainClass` - Missing the required main-class field in the plugin config.
* `TestErrorNoMainClassFile` - Missing the main class, as in the actual Java file.
* `TestErrorNoName` - Missing the required name field in the plugin config.
* `TestErrorNoVersion` - Missing the required version field in the plugin config.
* `TestErrorNoYml` - Missing the required plugin.yml file.
