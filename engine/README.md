# HabitMiner Python Evaluation Engine

The HabitMiner evaluation engine validates **context-aware personalized behavioral data mining**. 
It is a small statistical evaluation prototype for frequency-based sequential pattern mining, time-conditioned Markov prediction, and deviation heuristics. It is separate from the Android app's on-device Kotlin analytics and is **not deep learning**.

## Pipeline
1. **Data**: Generate deterministic synthetic app-usage records. StudentLife and UCI HAR adapters are not implemented yet.
2. **Features**: Extract time-binned features (total duration, sessions, category profile).
3. **Pattern Mining**: Frequency-based sequential pattern extraction.
4. **Baseline**: Build a personal behavioral baseline using mean/std and category distribution.
5. **Deviation Detection**: Z-score and KL-divergence to find behavioral anomalies.
6. **Evaluate**: Run three prototype experiments on synthetic data.
7. **Report**: Generate HTML reports with embedded experiment data; Chart.js is loaded from a CDN for interactive charts.

## Experiments
- **Experiment 1 (Habit Discovery)**: Evaluates confidence and support of mined habits.
- **Experiment 2 (Next-Behavior Prediction)**: TimeConditionedMarkovModel vs baseline prediction.
- **Experiment 3 (Deviation Detection)**: Inject controlled anomalies and evaluate Precision/Recall/F1.

## Setup & Data
Install requirements:
```bash
pip install -r requirements.txt
```

Datasets:
- **Synthetic**: The only currently supported input. Supply a seed to reproduce the same data.
- **StudentLife / UCI HAR**: Listed as future adapters only. Selecting either exits with an explicit "not implemented" error; data is never silently replaced with synthetic samples.

## Usage
Evaluate habits (Runs all 3 experiments & generates report):
```bash
python main.py --mode evaluate --dataset synthetic --num-days 14 --num-users 3 --seed 42
```

Activity recognition and real-dataset ingestion are not implemented; the CLI reports this rather than printing fabricated metrics.
