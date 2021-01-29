![VanillaGradle Logo](docs/logo.png?raw=true)

**VanillaGradle** is a toolchain for *Minecraft: Java Edition* that provides a workspace to interact with the game using official mappings provided 
by Mojang Studios.

### Documentation

The main documentation for **VanillaGradle** can be found in the [Wiki](../../wiki) (not available yet).

For additional help use the channel `#vanillagradle` on the [Sponge Discord server](https://discord.gg/sponge).

Otherwise, to get a workspace up and going immediately it is as simple as

```
plugins {
    java
    id("org.spongepowered.gradle.vanilla") version "0.1-SNAPSHOT"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

minecraft {
    version("21w03a")
    runs {
        server()
        client()
    }
}
```

Within IntelliJ IDEA, this will generate run configurations named `runClient` and `runServer`

### Building VanillaGradle
**VanillaGradle** uses the [Gradle](http://gradle.org/) build automation system. To
perform a build, use gradle version 6.8.1 and execute

    gradle

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
      <td valign="top"><b>0.1</b></td>
      <td valign="top">January 2021</td>
      <td valign="top">
        <ul>
          <li>Initial Release</li>
        </ul>
      </td>
    </tr>
  </tbody>
</table>