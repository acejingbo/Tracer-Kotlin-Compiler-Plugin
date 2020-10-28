# Tracer Kotlin Compiler Plugin

This is an Kotlin Compiler Plugin that wrap method annotation with `@TraceMethod` with `Trace.beginSection` and `Trace.endSection`. This is mainly used as an example of demonstrating how to use Kotlin Compiler Plugin.

This contains two gradle projects, 
- `TracerPlugin`: Generate the a Gradle plugin which wrap the Tracer Kotlin Compiler Plugin
- `TracerPluginExample`: Apply the tracer plugin on a sample code


## Publish
This is an example plugin, so we only publish locally.
```
cd TracerPlugin 
./gradlew publish
```
This shall generate `maven-repo` folder on root level (parallel with `TracerPlugin` and `TracerPluginExample` folder) which contains plugin artifact.

## Usage
### In TracerPluginExample
After publishing, build `TracerPluginExample` project for a sample application that use the plugin.
```
cd TracerPluginExample
./gradlew build
```
This automatically include the plugin from `maven-repo` folder and use it on `Foo.kt`

To verify the code transformation succeed, check `build/generated/ktPlugin` folder, which shall contain a transformed version of `Foo.kt` with `Trace.beginSection` and `Trace.endSection` in place.

### In other gradle project
By copy the artifact in maven folder and include in other project (for example, via `pluginManagement` and `repositories`. See `TracerPluginExample`), they can also use it by
```
plugins {
    id 'example.tracerplugin' version '1.0.0'
}
```

## Debug
In `TracerPlugin`, inside the Compiler Plugin, use `MessageCollector` to log information.

In `TracerPluginExample`, run `./gradlew clean build --info` to see all loggings.
