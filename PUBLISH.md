# Publish

Set environments

```
export ORG_GRADLE_PROJECT_sonatypeUsername=
export ORG_GRADLE_PROJECT_sonatypePassword=
```

## Prepare

```
gradlew clean
```

## Publish


### Auto stage

Call

```shell
gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository
```

to publish all publications to Sonatype's OSSRH Nexus and subsequently close and release the corresponding staging
repository, effectively making the artifacts available in Maven Central (usually after a few minutes).

### Publish Only

```shell
gradlew publishToSonatype
```
