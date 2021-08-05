# Test Plugins

These plugins are for testing the Plugin Management system of the engine.

# Building

To run, run `ant`, which will default to the `buildJars` task, which builds each jar and copies them into the jars folder.
To clean up the jars and plugin build files, run `ant clean`.

# Tests

The plugins that exist, and a brief description of why they exist, are below. For the sake of clarity, dependencies are shown using the convention `A ==> B` means `A lists B in dependencies` and `A --> B` means `A lists B in soft-dependencies`. If a class has multiple dependencies, they will be listed on new lines with `''` indicating it's the same plugin again.

## Standalone

`TestStandalone` is just a plugin that has no dependencies. It should work fine.

## Cyclic dependencies

These plugins depend on each other, and use classes from each other at runtime.

```
TestCycleA ==> TestCycleB
TestCycleB ==> TestCycleA
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

## Error scenarios

* `TestErrorNoName` - Missing the required name field.
* `TestErrorNoVersion` - Missing the required version field.
* `TestErrorNoDescription` - Missing the required description field.

