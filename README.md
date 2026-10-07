# Myriad maven repository

Served at https://fyzz-dev.github.io/myriad by GitHub Pages. Each release's workflow adds its artifacts here;
nothing is edited by hand. Use it from an addon's build.gradle:

```groovy
repositories {
    maven { url = "https://fyzz-dev.github.io/myriad" }
}
dependencies {
    implementation "dev.myriad:myriad:0.1.0"
}
```
