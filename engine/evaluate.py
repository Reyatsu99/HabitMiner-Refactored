import pandas as pd
import numpy as np
from typing import Dict, List, Tuple
from feature_extraction import extract_sequences, extract_time_bin_features
from habit_discovery import mine_frequent_sequences, DiscoveredHabit
from baseline_model import PersonalBaseline, build_baselines_for_all_users
from deviation_detector import detect_deviations, inject_controlled_deviation
from markov_model import TimeConditionedMarkovModel

def split_data_chronologically(df: pd.DataFrame, train_ratio: float = 0.7) -> Tuple[pd.DataFrame, pd.DataFrame]:
    """Split by complete calendar days so a user's day never leaks across train and test."""
    if not 0.0 < train_ratio < 1.0:
        raise ValueError("train_ratio must be between 0 and 1")
    if df.empty:
        return df.copy(), df.copy()
    required = {"user_id", "start_time"}
    missing = required - set(df.columns)
    if missing:
        raise ValueError(f"usage data is missing required columns: {sorted(missing)}")

    df_sorted = df.copy()
    df_sorted["start_time"] = pd.to_datetime(df_sorted["start_time"], errors="coerce")
    df_sorted = df_sorted.dropna(subset=["start_time"]).sort_values(["user_id", "start_time"])
    train_dfs = []
    test_dfs = []

    for _, group in df_sorted.groupby("user_id", sort=False):
        days = sorted(group["start_time"].dt.date.unique())
        if len(days) < 2:
            # A single-day user cannot provide a leakage-free holdout.
            train_dfs.append(group)
            continue
        split_idx = min(max(int(len(days) * train_ratio), 1), len(days) - 1)
        train_days = set(days[:split_idx])
        day_values = group["start_time"].dt.date
        train_dfs.append(group.loc[day_values.isin(train_days)])
        test_dfs.append(group.loc[~day_values.isin(train_days)])

    train = pd.concat(train_dfs, ignore_index=True) if train_dfs else df_sorted.iloc[0:0].copy()
    test = pd.concat(test_dfs, ignore_index=True) if test_dfs else df_sorted.iloc[0:0].copy()
    return train, test

def experiment1_habit_discovery(usage_df: pd.DataFrame, user_id: str = None) -> dict:
    """Experiment 1: Habit Discovery"""
    if user_id:
        usage_df = usage_df[usage_df["user_id"] == user_id]
        
    train_df, _ = split_data_chronologically(usage_df)
    
    train_seqs = extract_sequences(train_df)
    habits = mine_frequent_sequences(train_seqs, min_support=2, min_confidence=0.3)
    
    if not habits:
        return {"avg_confidence": 0.0, "pattern_count": 0, "avg_support": 0.0, "habits": []}
        
    avg_conf = np.mean([h.confidence for h in habits])
    avg_supp = np.mean([h.support for h in habits])
    
    return {
        "avg_confidence": float(avg_conf),
        "pattern_count": len(habits),
        "avg_support": float(avg_supp),
        "habits": habits
    }

def experiment2_next_behavior_prediction(usage_df: pd.DataFrame, user_id: str = None) -> dict:
    """Experiment 2: Next-Behavior Prediction"""
    if user_id:
        usage_df = usage_df[usage_df["user_id"] == user_id]
        
    train_df, test_df = split_data_chronologically(usage_df)
    
    train_seqs = extract_sequences(train_df)
    test_seqs = extract_sequences(test_df)
    
    markov = TimeConditionedMarkovModel()
    markov.fit(train_seqs)
    
    markov_results = markov.evaluate_predictions(test_seqs, top_k=3)
    
    # Baseline: most frequent category overall
    overall_most_freq = train_df["app_category"].mode()
    baseline_pred = overall_most_freq.iloc[0] if not overall_most_freq.empty else "UNKNOWN"
    
    # Evaluate baseline
    correct_top1 = 0
    total = 0
    for key, daily_seqs in test_seqs.items():
        for seq in daily_seqs:
            for i in range(len(seq) - 1):
                actual = seq[i+1]
                if actual == baseline_pred:
                    correct_top1 += 1
                total += 1
                
    baseline_top1 = correct_top1 / total if total > 0 else 0.0
    
    return {
        "baseline_top1": baseline_top1,
        "baseline_top3": baseline_top1, # baseline only predicts 1
        "markov_top1": markov_results["top1_accuracy"],
        "markov_top3": markov_results["top3_accuracy"]
    }

def experiment3_deviation_detection(usage_df: pd.DataFrame, user_id: str = None) -> dict:
    """Evaluate injected anomalies at the user/day/time-bin level, not per event row."""
    if user_id:
        usage_df = usage_df[usage_df["user_id"] == user_id]

    train_df, test_df = split_data_chronologically(usage_df)
    target_user = user_id or "user_1"
    train_features = extract_time_bin_features(train_df, user_id=target_user)
    baseline = build_baselines_for_all_users(train_features).get(target_user)
    if baseline is None:
        return {
            "precision": 0.0,
            "recall": 0.0,
            "f1": 0.0,
            "false_positive_rate": 0.0,
            "true_positives": 0,
            "false_positives": 0,
            "false_negatives": 0,
            "injected_bins": 0,
        }

    # Evening bins are present in the synthetic baseline for both weekday/weekend where
    # enough training days exist. A controlled 45-minute GAMING session is added there.
    anomalous_test_df = inject_controlled_deviation(
        test_df,
        target_user,
        from_time_slot="EVENING",
        to_time_slot="EVENING",
        category="GAMING",
    )
    clean_features = extract_time_bin_features(test_df, user_id=target_user)
    anomalous_features = extract_time_bin_features(anomalous_test_df, user_id=target_user)

    def key(row):
        return str(row["date"]), f"{row['day_type']}_{row['time_slot']}"

    eligible_keys = {
        key(row)
        for _, row in clean_features.iterrows()
        if baseline.get_bin(f"{row['day_type']}_{row['time_slot']}") is not None
    }
    injected_keys = {
        key(row)
        for _, row in anomalous_features.iterrows()
        if bool(row.get("controlled_anomaly", False))
        and baseline.get_bin(f"{row['day_type']}_{row['time_slot']}") is not None
    }
    if not injected_keys:
        return {
            "precision": 0.0,
            "recall": 0.0,
            "f1": 0.0,
            "false_positive_rate": 0.0,
            "true_positives": 0,
            "false_positives": 0,
            "false_negatives": 0,
            "injected_bins": 0,
        }

    detected_events = detect_deviations(anomalous_features, baseline)
    detected_keys = {
        (event.date, event.time_bin)
        for event in detected_events
        if event.date is not None
    }
    negative_keys = eligible_keys - injected_keys
    true_positives = len(detected_keys & injected_keys)
    false_negatives = len(injected_keys - detected_keys)
    false_positives = len(detected_keys & negative_keys)

    precision = (
        true_positives / (true_positives + false_positives)
        if true_positives + false_positives
        else 0.0
    )
    recall = true_positives / (true_positives + false_negatives) if injected_keys else 0.0
    f1 = (
        2 * precision * recall / (precision + recall)
        if precision + recall
        else 0.0
    )
    return {
        "precision": precision,
        "recall": recall,
        "f1": f1,
        "false_positive_rate": false_positives / len(negative_keys) if negative_keys else 0.0,
        "true_positives": true_positives,
        "false_positives": false_positives,
        "false_negatives": false_negatives,
        "injected_bins": len(injected_keys),
        "true_negatives": len(negative_keys) - false_positives,
    }
def run_all_experiments(usage_df: pd.DataFrame) -> dict:
    """Run all 3 experiments"""
    return {
        "exp1": experiment1_habit_discovery(usage_df),
        "exp2": experiment2_next_behavior_prediction(usage_df),
        "exp3": experiment3_deviation_detection(usage_df)
    }

def print_evaluation_report(results: dict) -> None:
    """Print formatted ASCII report"""
    print("=" * 60)
    print(" HABITMINER EVALUATION REPORT ".center(60, "="))
    print("=" * 60)
    
    print("\n--- EXPERIMENT 1: HABIT DISCOVERY ---")
    e1 = results["exp1"]
    print(f"Patterns Found:   {e1['pattern_count']}")
    print(f"Avg Confidence:   {e1['avg_confidence']:.2f}")
    print(f"Avg Support:      {e1['avg_support']:.1f} days")
    
    print("\nTop 3 Habits:")
    for i, h in enumerate(e1["habits"][:3]):
        print(f"  {i+1}. {h.habit_name} ({h.pattern_str}) - Conf: {h.confidence:.2f}")
        
    print("\n--- EXPERIMENT 2: NEXT-BEHAVIOR PREDICTION ---")
    e2 = results["exp2"]
    print(f"Baseline Top-1:   {e2['baseline_top1']:.2%}")
    print(f"Markov Top-1:     {e2['markov_top1']:.2%}")
    print(f"Markov Top-3:     {e2['markov_top3']:.2%}")
    
    print("\n--- EXPERIMENT 3: DEVIATION DETECTION ---")
    e3 = results["exp3"]
    print(f"Precision:        {e3['precision']:.2f}")
    print(f"Recall:           {e3['recall']:.2f}")
    print(f"F1 Score:         {e3['f1']:.2f}")
    print(f"FPR:              {e3['false_positive_rate']:.4f}")
    print("=" * 60)
