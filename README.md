# Usage Detection Detekt Plugin

## Overview

The Usage Detection Detekt Plugin is a custom plugin for Detekt that detects function usage within other functions, allowing for detection via annotations or custom rules for functions from external libraries.

The Usage Detection Detekt Plugin is a powerful and flexible tool designed to enhance code quality by enforcing customizable rules for function usage. By leveraging annotations, custom rules, and detailed configuration options, the plugin provides comprehensive and context-aware code analysis, helping developers maintain high standards and consistency across their Kotlin projects.

## Features

Main features:

* Detect usage of functions within functions.
* Configure detection rules using annotations.
* Flexible configuration via YAML.

The Usage Detection Detekt Plugin offers a range of features to enhance code quality and maintainability by detecting improper function usage patterns. Below are the detailed features of the plugin:

1) **Function Usage Detection**: The plugin identifies the usage of functions within other functions, helping to enforce best practices and maintain code readability and maintainability.
2) **Annotation-Based Rules**: Supports detection rules based on annotations, allowing developers to define and enforce custom usage policies directly in the codebase. This feature leverages specific annotations to control where and how functions can be used.
3) **Custom Rule Configuration**: Allows for the creation of custom rules to handle function calls from external libraries. Users can define rules for allowed and disallowed function invocations, making the plugin highly flexible and adaptable to various coding standards.
4) **Root and Nested Function Filtering**: The plugin can filter and apply rules to both root-level functions and nested function calls. This ensures comprehensive code analysis and adherence to defined rules throughout the codebase.
5) **Complex Filtering Options**: Supports complex filtering criteria, including:

   * Methods with specific annotations.
   * Classes with specific annotations.
   * Packages.
   * Parameterized annotations.
   * Top-level and class object functions.

6) **Configurable Messages**: Customizable messages for each rule violation, providing clear and actionable feedback to developers when a rule is violated.
7) **Not Allowed Invokes**: Specifies functions or methods that should not be invoked within certain contexts, based on class names, method names, and annotations.
8) **Visit Filter Configurations**: Visit filters allow detailed configuration of which elements (functions, properties, etc.) should be checked by the rules, supporting both inclusion and exclusion filters at different levels of the code structure.
9) **Configurable via YAML**: The plugin's behavior can be fully configured using a YAML file, allowing for easy customization and integration into existing Detekt configurations.
10) **Support for Kotlin's PSI and BindingContext**: Utilizes Kotlin's PSI (Program Structure Interface) and BindingContext to accurately analyze and navigate the code structure, ensuring precise rule application.

## Example Scenarios

* **Database Mutation Detection**: Detects improper database mutation operations within read transactions, ensuring transactional integrity and preventing unintended side effects.
* **Transaction Enforcement**: Ensures that database operations are performed within proper transactional contexts, reducing the risk of data inconsistencies.
* **Native Operation Annotation**: Detects native database operations that lack required annotations, enforcing consistency and adherence to project-specific standards.
* **Usage Restrictions in Specific Contexts**: Enforces usage restrictions for specific functions or methods within certain classes, packages, or annotated contexts, ensuring code adheres to defined architectural guidelines.

## Installation

Add the plugin to your `build.gradle.kts`:

```kotlin
dependencies {
    detektPlugins("ru.code4a:usage-detection-detekt-plugin:<version>")
}
```

## Example usage

```shell
gradlew detektMain
```

## Using Annotations

The plugin can also use annotations to control function usage. The following annotations are supported:

* **AllowedUsageOnlyInClass**: Restricts the usage of functions to a specific class.
* **AllowedUsageOnlyInClasses**: Restricts the usage of functions to a specific list of classes.
* **AllowedUsageOnlyInPackage**: Restricts the usage of functions to a specific package.
* **AllowedUsageOnlyInMethod**: Restricts the usage of functions to a specific method.
* **AllowedUsageOnlyInMethods**: Restricts the usage of functions to a specific list of methods.
