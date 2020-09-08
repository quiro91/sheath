# Production Releases

1. Checkout `origin/main`.
1. Update the `CHANGELOG.md` file with the changes of this release.
1. Update the version in `gradle.properties` and remove the `-SNAPSHOT` suffix.
1. Commit the changes and create a tag:
   ```
   git commit -am "Releasing v0.1.0."
   git tag v1.0.0
   ```
1. Push the artifacts to Maven Central and the Gradle Plugin Portal.
   ```
   ./gradlew clean uploadArchives --no-daemon --no-parallel --no-build-cache && cd gradle-plugin && ./gradlew clean uploadArchives publishPlugins --no-daemon --no-parallel --no-build-cache && cd ..
   ```
1. Close and release the staging repository at [Sonatype](https://oss.sonatype.org).
1. Update the version in `gradle.properties` and add the `-SNAPSHOT` suffix.
1. Commit the change:
   ```
   git commit -am "Prepare next development version."
   ```
1. Push git changes:
   ```
   git push && git push --tags
   ```
1. Create the release on GitHub:
   1. Go to the [Releases](https://github.com/quiro91/sheath/releases) page for the GitHub project.
   1. Click "Draft a new release".
   1. Enter the tag name you just pushed.
   1. Title the release with the same name as the tag.
   1. Copy & paste the changelog entry for this release into the description.
   1. If this is a pre-release version, check the pre-release box.
   1. Hit "Publish release".

# Installing in Maven Local

```
./gradlew clean installArchives --no-build-cache && cd gradle-plugin && ./gradlew clean installArchives --no-build-cache && cd ..
```
