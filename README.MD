![VanillaGradle Logo](docs/logo.png?raw=true)

**VanillaGradle** is a toolchain for *Minecraft: Java Edition* that provides a workspace to interact with the game using official mappings provided 
by Mojang Studios. This plugin requires at least Gradle 6.7 to run, though it is only actively tested against the latest release of every major Gradle 
series supported (currently 6.x and 7.x).

### Documentation

The main documentation for **VanillaGradle** can be found in the [Wiki](../../wiki).

For additional help use the channel `#vanillagradle` on the [Sponge Discord server](https://discord.gg/sponge).

Otherwise, to get a workspace up and going with vanilla Minecraft immediately it 
is as simple as creating a Gradle build based on this buildscript:

```gradle
plugins {
    java
    id("org.spongepowered.gradle.vanilla") version "0.2"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

minecraft {
    version("1.16.5") // or: latestRelease() or latestSnapshot()
    runs {
        server()
        client()
    }
}
```

Within IntelliJ IDEA, this will generate run configurations named `runClient` and `runServer`

To have browsable sources in-IDE, run `./gradlew decompile`

### Building VanillaGradle
**VanillaGradle** uses the [Gradle](http://gradle.org/) build automation system. To
perform a build, use Gradle version 6.9 and execute

    gradle build

from within the project root directory. *(If you do not have Gradle installed on
your system you can instead run the supplied Gradle wrapper `gradlew`)*

### Version History

<table width="100%">
  <thead>
    <tr>
      <th width="15%">Version</th>
      <th width="20%">Date</th>
      <th width="65%">Features / Changes</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td valign="top"><b>0.2</b></td>
      <td valign="top">Map 2021</td>
      <td valign="top">
        <ul>
          <li>Run configurations are generated for Eclipse</li>
          <li>Sources are now visible in-IDE</li>
          <li>Asset download task now validates existing assets</li>
          <li>Minecraft artifacts are now only generated when changed, improving build performance</li>
          <li>Non-access widened jars are stored in a global shared cache rather than per-project</li>
          <li>Custom version manifests can be provided for out-of-band releases, such as combat snapshots</li>
          <li>The <code>javaVersion</code> field recently added to Mojang launcher manifests is now used to determine runtime toolchains</li>
        </ul>
      </td>
    </tr>
    <tr>
      <td valign="top"><b>0.1</b></td>
      <td valign="top">February 2021</td>
      <td valign="top">
        <ul>
          <li>Initial Release</li>
        </ul>
      </td>
    </tr>
  </tbody>
</table>
