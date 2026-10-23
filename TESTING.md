# Local verification

The `qa` build installs as `com.example.diceroller.uitest`. Device tests clear only that separate installation; the ordinary debug/release application remains `com.example.diceroller`. The UI suite checks its target package before clearing data.

Use the existing Android SDK and cached dependencies. From this project in PowerShell:

```powershell
.\gradlew.bat --offline '-Dorg.gradle.java.installations.auto-download=false' '-Pandroid.builder.sdkDownload=false' :app:assembleDebug :app:testQaUnitTest :app:assembleQa :app:assembleQaAndroidTest
```

With the current Android Gradle plugin, `testBuildType = "qa"` selects `testQaUnitTest` as well as the QA instrumentation target. Unit-test reports are in `app/build/reports/tests/testQaUnitTest/`.

To run the device tests on an existing emulator or dedicated test device, use its verified device ID in place of `emulator-5556`:

```powershell
$adbTool = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adbTool -s emulator-5556 install -r app\build\outputs\apk\qa\app-qa.apk
& $adbTool -s emulator-5556 install -r app\build\outputs\apk\androidTest\qa\app-qa-androidTest.apk
& $adbTool -s emulator-5556 shell am instrument -w -r com.example.diceroller.uitest.test/androidx.test.runner.AndroidJUnitRunner
```

Check the test runner's final `OK (...)` output, not just the adb process exit code. The direct runner uses existing tools without the additional Gradle UTP component that may be unavailable offline.

Coverage includes expression boundaries, complete-batch retention/statistics, both legacy history limits, corrupt/unknown-version protection, backup-before-recovery, persistence failure/retry, cancellation, new-ViewModel draft restoration, input rejection, clipboard completeness, pagination, keyboard positioning and actual portrait/landscape history anchoring. For manual checks, verify Chinese/English, large text, dark mode and a narrow window. History warnings should remain actionable even while a keyboard is open.

History format v2 stores complete batches. Legacy v1 records each become an independent one-result batch; their original grouping is not inferred. Recovery backups remain in the app-private `history-backups` directory. Clearing the current history does not delete those recovery backups.

Android Lint 32.4.0 and its required dependencies were downloaded from the configured official Google/Maven repositories with explicit user authorization. Run all three variants, not only the default selected by `lint`:

```powershell
.\gradlew.bat --offline --no-configuration-cache '-Dorg.gradle.java.installations.auto-download=false' '-Pandroid.builder.sdkDownload=false' :app:lintDebug :app:lintQa :app:lintRelease
```

The `android.lint` configuration includes test sources and fails on errors. No baseline, disabled issue list or narrowed `checkOnly` list is used. Reports are under `app/build/reports/lint-results-{debug,qa,release}.{html,xml,sarif}`. Inspect warnings separately: dependency update notifications do not require unrelated upgrades, and typography suggestions must not replace the literal minus operator in example dice expressions.

Palette preferences are stored separately from roll history in app-private `appearance` preferences. Device tests exercise all palette options, actual rendered colors, recreation/persistence, invalid stored-value fallback and independence from clearing history; they modify only the QA installation. Light/dark palette contrast is checked with the existing Compose color utilities. No new production dependency is needed; coroutine test utilities remain at the already cached version 1.9.0.
