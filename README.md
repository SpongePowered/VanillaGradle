![VanillaGradle Logo](docs/logo.png?raw=true)

**VanillaGradle** is a toolchain for *Minecraft: Java Edition* that provides a workspace to interact with the game using official unobfuscated jars provided 
by Mojang Studios. This plugin requires at least Gradle 9.2.1 and Java 25 to run.

**VANILLAGRADLE IS NOT DESIGNED AND IS NOT GENERALLY USEFUL FOR END-USER USE. IT IS FOR TOOLING AND PLATFORM DEVELOPERS WHO HAVE A SOLID WORKING KNOWLEDGE OF GRADLE ONLY!**

### Documentation

The main documentation for **VanillaGradle** can be found in the [Wiki](../../wiki).

For additional help use the channel `#vanillagradle` on the [Sponge Discord server](https://discord.gg/sponge).

Otherwise, to get a workspace up and going with vanilla Minecraft immediately it 
is as simple as creating a Gradle build based on this buildscript:

```gradle
plugins {
    java
    id("org.spongepowered.gradle.vanilla") version "0.3.2"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

minecraft {
    version("26.1") // or: latestRelease() or latestSnapshot()
    runs {
        server()
        client()
    }
}
```

Within IntelliJ IDEA and Eclipse, this will generate run configurations named `runClient` and `runServer`

To have browsable sources in-IDE, run `./gradlew decompile`

### Building VanillaGradle
**VanillaGradle** uses the [Gradle](http://gradle.org/) build automation system. To
perform a build, use Gradle version 9.2.1 and execute

    gradle build

from within the project root directory. *(If you do not have Gradle installed on
your system you can instead run the supplied Gradle wrapper `gradlew`)*

### Version History

**Compatibility:** We use [Semantic Versioning](https://semver.org/) for the user-visible API in VanillaGradle. This applies to every public class 
outside of the `org.spongepowered.gradle.vanilla.internal` package. The contents of the `internal` package are considered implementation details 
and are subject to change at any time.

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
      <td valign="top"><b>0.3.2</b></td>
      <td valign="top">February 2026</td>
      <td valign="top">
        <ul>
          <li>Fix assets download errors</li>
        </ul>
      </td>
    </tr>
    <tr>
      <td valign="top"><b>0.3.1</b></td>
      <td valign="top">January 2026</td>
      <td valign="top">
        <ul>
          <li>Replace Apache HTTP client with the JDK one</li>
          <li>Replace org:immutables with JDK records</li>
          <li>Migrate nullable annotations to JSpecify</li>
          <li>No longer require Sponge maven repository</li>
        </ul>
      </td>
    </tr>
    <tr>
      <td valign="top"><b>0.3.0</b></td>
      <td valign="top">December 2025</td>
      <td valign="top">
        <ul>
          <li>Require Java 25 and Gradle 9</li>
          <li>Remove de-obfuscation</li>
          <li>Bump minimum Minecraft version to 26.1-snapshot-1</li>
        </ul>
      </td>
    </tr>
    <tr>
      <td valign="top"><b>0.2.2</b></td>
      <td valign="top">November 2025</td>
      <td valign="top">
        <ul>
          <li>Require Java 11</li>
          <li>Improve IntelliJ IDEA project sync performance</li>
          <li>Compatibility with shadow plugin 9.0.2</li>
        </ul>
      </td>
    </tr>
    <tr>
      <td valign="top"><b>0.2.1</b></td>
      <td valign="top">November 2025</td>
      <td valign="top">
        <ul>
          <li>Use Forge's renamer for remapping rather than Atlas</li>
          <li>Strip jar signature</li>
          <li>Support Minecraft 1.19-pre1 client</li>
          <li>Choose Vineflower as our default decompiler</li>
          <li>Avoid running asset/native downloads when unnecessary</li>
          <li>More run options (source set, environment variables)</li>
        </ul>
      </td>
    </tr>
    <tr>
      <td valign="top"><b>0.2</b></td>
      <td valign="top">May 2021</td>
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
