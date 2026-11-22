# Code review and cleanup notes

Review branch: `review/quality-cleanup`  
Target: `main`  
Scope: Android app, data collection and persistence, import/export, update delivery, Python evaluation engine, CI and repository documentation.

## Findings fixed in this review

### Android correctness and security
- Corrected the in-app updater's repository target and invalidated cached release metadata when the repository changes. Cached and redirected download URLs are checked against GitHub release hosts; APK checksum, package, version and signing certificate checks remain in place.
- Set the development build code to `20599`, immediately below the `v2.6.0` release code (`20600`), preventing older releases from being offered as updates.
- Disabled automatic Android backup for the local usage and sensor database.
- Corrected the AccessibilityService export flag. Android can bind the service, while `BIND_ACCESSIBILITY_SERVICE` remains required and window-content retrieval stays disabled.
- Made the system-only boot/unlock receiver non-exported to reduce the app's external broadcast attack surface.
- Changed the one-time session-repair marker to be written only after the repair succeeds, so transient database errors can be retried.
- Propagate coroutine cancellation through monitoring, export and update-download paths instead of treating cancellation as an ordinary recoverable error.

### Data export and import
- Moved export work to `Dispatchers.IO`, serialized concurrent exports and removed intermediate CSV files on success or failure.
- Standardized CSV output to UTF-8 and corrected quoting for carriage returns as well as commas, quotes and newlines.
- Re-keyed Wi-Fi place IDs consistently across context snapshots and the places table in privacy-safe exports; redact free-text label values as well as place labels, insight text and app names.
- Bounded ZIP imports to 32 entries, 32 MiB per entry and 128 MiB total uncompressed content. Limits apply to ignored entries too.
- Removed mutable import-result state from the singleton importer, preventing one archive's privacy status from leaking into a later import.
- Preserve earlier completed ZIPs when starting a new export, use collision-resistant millisecond timestamps, and delete incomplete ZIPs after failed writes.
- Added regression coverage for entry-count limits and import-state isolation.

### Python evaluation engine
- Made synthetic data generation fully seed-controlled and anchored its default reference date for reproducible runs.
- Removed silent synthetic-data substitutes for unimplemented StudentLife and UCI HAR adapters, and removed the fabricated activity-recognition result.
- Split holdouts by complete user-days to avoid sessions from the same day appearing in both train and test.
- Marked controlled injected anomalies and calculate detection metrics once per user/day/time-bin rather than counting multiple event types as separate true positives.
- Reset Markov model state on each fit, validate input keys and top-k values, and use deterministic tie-breaking.
- Rebuild baselines from a clean state, validate EMA weights, and define KL divergence for empty profiles.
- Added tests for reproducibility, invalid inputs, day-level splitting, unsupported adapters, refitting and anomaly-label metrics.

### CI and documentation
- Separated the read-only Android build/test job from the write-enabled main-only artifact publisher.
- Added a dedicated Python compile/test workflow.
- Clarified that update checks contact GitHub for release metadata but do not upload usage or sensor records.
- Removed the duplicate Python workflow and kept one canonical Python CI definition.

## Existing design choices and remaining considerations

1. **No GPS coordinate tracking is implemented.** Optional Wi-Fi Places reads the connected access point's BSSID and stores a salted hash with timestamps. Android requires the `ACCESS_FINE_LOCATION` permission to expose that identifier, so enabling Places still requires system location permission and can infer saved places. No latitude/longitude or GPS samples are collected. If the product requirement is *no location permission or place inference at all*, remove the Places feature and its permission declarations rather than describing it as GPS-free.
2. **Automatic backup is disabled.** Users should use the app's manual export before uninstalling or changing devices.
3. **Release updates require a published GitHub Release** with an APK asset and SHA-256 digest. The release workflow also requires the configured signing-key secrets; a different signing key will not update an already-installed build.
4. **The Python engine is a prototype evaluated on synthetic data only.** StudentLife/UCI HAR ingestion and activity-recognition evaluation are not implemented. Synthetic experiment metrics must not be presented as validation on real users.
5. **Package visibility is intentionally broad** because the app categorizes installed apps and analyzes usage history. Review the `QUERY_ALL_PACKAGES` distribution-policy requirements before publishing through Google Play.
6. **The Android project has legacy formatting violations** in untouched Kotlin source files. The new CI gate therefore focuses on debug/release compilation and unit tests rather than introducing a repository-wide Spotless failure. Formatting can be enforced after the existing violations are cleaned.

## Validation

- Android: `assembleDebug`, `assembleRelease`, and `testDebugUnitTest` are run by the PR workflow.
- Python: `compileall` and `pytest` are run by the Python workflow.
- Room: the existing migration test exercises schema 7 through schema 10 and verifies that stored sensor readings survive.
- README local links were checked during the repository cleanup.
- Do not merge this branch until the latest Android and Python workflow runs both conclude with `success`.
