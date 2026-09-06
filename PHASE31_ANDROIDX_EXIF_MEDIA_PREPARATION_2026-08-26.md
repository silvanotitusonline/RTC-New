# Phase 31 — AndroidX Exif Media Preparation Migration

**Date:** 2026-08-26  
**Environment boundary:** Local Android source and build validation only. **No Supabase operation, Storage access, user-media access, device test, production change, remote push, Edge deployment, or APK distribution occurred.**

## Change

The bounded image-preparation path previously used the platform `android.media.ExifInterface` class to read orientation from a selected image descriptor. It now uses the maintained AndroidX implementation, `androidx.exifinterface.media.ExifInterface`, supplied through the managed Gradle version catalog.

The official Google Maven metadata confirmed stable AndroidX ExifInterface **1.4.2** before the dependency was added. The code path still reads only the orientation value, normalizes the decoded bitmap, samples decode bounds, caps the output dimension at 2048 pixels, transcodes to JPEG, and enforces the existing 5 MB limit. No media policy, Storage path, server contract, or upload behavior was changed.

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | Added managed `androidx.exifinterface:exifinterface:1.4.2` entry. |
| `app/build.gradle.kts` | Added `implementation(libs.androidx.exifinterface)`. |
| `MediaPreparation.kt` | Replaced the platform Exif import with `androidx.exifinterface.media.ExifInterface`. |
| `test_android_compile_contracts.py` | Added regression coverage for the managed dependency, AndroidX import, descriptor usage, and existing orientation-normalization call. |

## Validation

| Validation | Result |
|---|---:|
| Source-contract suite | **54/54 passed** |
| `testDebugUnitTest` | **Passed** |
| `assembleDebug` | **Passed** |
| `lintDebug` | **Passed** |

The Kotlin compiler emitted the project’s known non-blocking deprecation and future annotation-target warnings. Debug assembly also reported that two native libraries could not be symbol-stripped and were packaged unchanged; the build completed successfully. No lint baseline or suppression was added.

## Remaining Limits

This is a source/build hardening checkpoint, not an orientation test on a physical device. The synthetic-only device protocol still requires image fixtures with rotation and mirrored Exif metadata to verify visual orientation, picker behavior, memory profile, and post-upload presentation on target Android versions. The overall application status remains **NO-GO**.
