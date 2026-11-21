# Sequential Habit Mining — Change Notes

## Overview

This branch introduces a bounded, PrefixSpan-style sequential pattern miner for discovering recurring app-use sequences. The goal is to move beyond identifying frequently used apps and instead identify ordered sequences of apps that recur across multiple days.

**Implementation status:** Initial implementation committed and CI passed. This document records the implemented behavior and known limitations identified during code review. It does not claim that the outstanding review findings have already been fixed.

## Files changed

- `android/app/src/main/java/com/habitminer/engine/SequentialPatternMiner.kt`
  - Adds the sequential pattern mining data structures and algorithm.
  - Collapses adjacent sessions with the same package name.
  - Mines variable-length ordered subsequences, including patterns with intervening app events.
  - Counts support by distinct input sequence/day index.
  - Applies maximum pattern length and maximum transition-gap bounds.
- `android/app/src/main/java/com/habitminer/engine/HabitEngine.kt`
  - Integrates sequential mining into habit discovery.
  - Retains launcher filtering and the existing day-type/time-slot segmentation.
  - Groups usage by calendar date within each bin.
  - Filters discovered patterns using a minimum recurrence ratio of 0.4.
  - Stores the package sequence as JSON and a readable app sequence as the pattern description.
- `android/app/src/test/java/com/habitminer/engine/SequentialPatternMinerTest.kt`
  - Adds tests for intervening events, distinct-day support, maximum transition gap, and adjacent duplicate-session collapsing.

## Algorithm behavior

### 1. Input representation

Each daily input is represented by a `DailySequence` containing a day key and timestamped `SequenceEvent` values. Package names are used as stable app identifiers; app names are retained for readable output.

### 2. Preprocessing

Events are sorted by start time. Consecutive events with the same package name are currently merged into one event, with the merged end time set to the maximum end time.

### 3. Pattern expansion

The miner recursively extends prefixes using projected suffix positions. It can discover a subsequence even when other app events occur between its selected elements. For example, the sequence `A → X → B` can support the subsequence `A → B`.

Singleton app tokens seed the search but are not emitted as habit patterns. Emitted patterns contain at least two app tokens.

### 4. Support and bounds

Default parameters:

| Parameter | Default |
|---|---:|
| Minimum support | 5 distinct input days |
| Maximum pattern length | 4 app tokens |
| Maximum transition gap | 15 minutes |

Support is counted once per input day index, regardless of how many matching occurrences exist on that day. Patterns below minimum support are pruned during expansion.

### 5. Habit-engine integration

Mining runs independently for each existing day-type/time-slot bin. The engine calculates:

`recurrence ratio = supporting days / observed days in the bin`

Patterns with a ratio below 0.4 are not added to the discovered-habits result. The stored `confidence` field currently receives this ratio; it should be interpreted as a recurrence ratio, not a statistically calibrated probability.

## CI and verification

The initial CI run exposed two failing tests related to singleton patterns being emitted. The miner was corrected so singleton tokens seed prefix expansion without being returned as habit candidates.

The subsequent GitHub Actions run completed successfully:

- Workflow run: [44915245706](https://github.com/Reyatsu99/HabitMiner-Refactored/actions/runs/44915245706)
- Branch: `feat/context-aware-habit-mining`
- Commit: `02c6958056aba0f139bc041f63120502434c44e8`
- Reported checks included Debug and Release APK builds, unit tests, Room schema validation, and APK artifact publication.

CI success verifies the configured automated checks. It does not establish real-device behavior or performance on large usage histories.

## Known limitations and follow-up work

The following issues were identified in a subsequent source review and remain follow-up work unless separately fixed:

1. **Duplicate day keys:** support uses distinct input day indices, not distinct `dayKey` values. Duplicate `DailySequence` entries for the same calendar day could inflate support. Normalize input to one sequence per day or count distinct day keys.
2. **Aggressive adjacent-session merging:** any consecutive same-package events are merged, even when separated by a long idle interval. Add a continuity-gap rule so separate sessions are not joined.
3. **Overlapping sessions:** transition validation does not explicitly define or reject overlapping events. Establish a normalization/overlap policy before mining.
4. **Projection growth:** recursive expansion can retain many projected positions, particularly when a pattern has repeated occurrences. Benchmark realistic large histories and consider projection deduplication and more efficient projected-database handling.
5. **Context signals:** this implementation mines app sequences within day-type/time-slot bins. It does not yet incorporate accelerometer or ambient-light measurements into pattern discovery or validation.
6. **Predictability metric:** the existing transition-entropy score is separate from this miner and measures transition concentration, not next-app prediction accuracy.

## Recommended next steps

- Fix duplicate-day support accounting.
- Prevent merging same-app sessions across long gaps.
- Define and test overlapping-session handling.
- Add tests for duplicate day keys, long same-app gaps, overlapping events, unsorted input, and invalid durations.
- Benchmark runtime and memory against the previous habit-discovery implementation using identical usage histories.
- Add sensor-context validation as a separate stage after sequential pattern discovery.
- Evaluate next-app prediction with held-out days and suitable prediction metrics if that feature is intended to represent predictive accuracy.

## Scope clarification

This is an initial **sequential app-use pattern mining** improvement. It is a foundation for context-aware habit analysis, not a complete sensor-fusion or context-aware habit-mining implementation.
