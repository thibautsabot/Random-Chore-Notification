# Random Chore Notification

Android app that nags you to do small chores from your own list at random times during the day.

- Package: `app.randomchorenotification`
- Min SDK 26, no network access, no third-party dependencies
- License: GPL-3.0-only (see `LICENSE`)

## Build

```
./gradlew assembleDebug      # debug APK
./gradlew testDebugUnitTest  # unit tests
./gradlew bundleRelease      # release bundle, signed only if keystore.properties exists
```

Release signing reads `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) from the project root. It is gitignored.

## Distribution

Published on F-Droid. Listing text lives in `fastlane/metadata/android/`.
