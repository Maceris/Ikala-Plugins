# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Example console command, disable and upgrade hooks
- A `library` configuration and `collectLibraries` task for shared libraries
- Documented the optional `plugin.yml` entries
- Test that `plugin.yml` is valid and matches the plugin class

### Changed
- Return new listeners from `getListeners` instead of caching them, since the
  plugin manager keeps the ones it registered
- Use the default `getName`, which comes from `plugin.yml`
- Bundle in javadoc from libraries by default into javadoc distribution file
- Switched build system to Gradle

## [0.0.1] - 2021-08-05
### Added
- Example Plugin class with logger
- Lombok config
- Example event listener
- Example JUnit 5 test
- Standardized build XML
- Standardized library folder structure
