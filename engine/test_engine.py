import pandas as pd
import pytest

from baseline_model import PersonalBaseline
from evaluate import experiment3_deviation_detection, split_data_chronologically
from feature_extraction import extract_time_bin_features
from habit_discovery import mine_frequent_sequences
from markov_model import TimeConditionedMarkovModel
from parser import generate_synthetic_usage, parse_studentlife, parse_uci_har


@pytest.fixture
def synthetic_data():
    return generate_synthetic_usage(num_users=1, num_days=7, seed=7)


def test_generate_synthetic_usage(synthetic_data):
    assert not synthetic_data.empty
    assert {"user_id", "app_category", "start_time", "end_time"}.issubset(synthetic_data.columns)
    assert len(synthetic_data["user_id"].unique()) == 1


def test_synthetic_generator_is_reproducible():
    first = generate_synthetic_usage(num_users=2, num_days=14, seed=123)
    second = generate_synthetic_usage(num_users=2, num_days=14, seed=123)
    pd.testing.assert_frame_equal(first, second)


@pytest.mark.parametrize("num_users,num_days", [(0, 7), (1, 0), (-1, 7), (1, -2)])
def test_synthetic_generator_rejects_invalid_sizes(num_users, num_days):
    with pytest.raises(ValueError):
        generate_synthetic_usage(num_users=num_users, num_days=num_days)


def test_unimplemented_dataset_adapters_fail_explicitly(tmp_path):
    with pytest.raises(NotImplementedError, match="StudentLife ingestion is not implemented"):
        parse_studentlife(str(tmp_path))
    with pytest.raises(NotImplementedError, match="UCI HAR ingestion/activity recognition is not implemented"):
        parse_uci_har(str(tmp_path))


def test_feature_extraction(synthetic_data):
    features = extract_time_bin_features(synthetic_data)
    assert not features.empty
    assert "total_duration_seconds" in features.columns
    assert "category_duration_dict" in features.columns


def test_chronological_split_keeps_complete_days_together():
    data = generate_synthetic_usage(num_users=2, num_days=14, seed=21)
    train, test = split_data_chronologically(data, train_ratio=0.7)
    for user_id in data["user_id"].unique():
        train_days = set(train.loc[train.user_id == user_id, "start_time"].dt.date)
        test_days = set(test.loc[test.user_id == user_id, "start_time"].dt.date)
        assert train_days.isdisjoint(test_days)
        if train_days and test_days:
            assert max(train_days) < min(test_days)


def test_chronological_split_rejects_invalid_ratio():
    data = generate_synthetic_usage(num_users=1, num_days=3, seed=9)
    with pytest.raises(ValueError, match="train_ratio must be between 0 and 1"):
        split_data_chronologically(data, train_ratio=1.0)


def test_mine_frequent_sequences():
    seqs = {
        "u1|WEEKDAY|MORNING": [
            ["SOCIAL", "PRODUCTIVITY"],
            ["SOCIAL", "PRODUCTIVITY"],
            ["SOCIAL", "PRODUCTIVITY"],
            ["SOCIAL", "GAMING"],
        ]
    }
    habits = mine_frequent_sequences(seqs, min_support=2, min_confidence=0.5)
    assert any(h.pattern == ["SOCIAL", "PRODUCTIVITY"] for h in habits)


def test_baseline_build(synthetic_data):
    features = extract_time_bin_features(synthetic_data)
    baseline = PersonalBaseline(user_id="user_1")
    baseline.build(features)
    assert isinstance(baseline.bins, dict)


def test_markov_model():
    seqs = {
        "u1|WEEKDAY|MORNING": [
            ["SOCIAL", "PRODUCTIVITY"],
            ["SOCIAL", "PRODUCTIVITY"],
        ]
    }
    model = TimeConditionedMarkovModel()
    model.fit(seqs)
    predictions = model.predict_next("SOCIAL", "WEEKDAY", "MORNING", top_k=1)
    assert predictions[0][0] == "PRODUCTIVITY"


def test_controlled_deviation_metrics_use_injected_day_bins():
    data = generate_synthetic_usage(num_users=1, num_days=28, seed=42)
    result = experiment3_deviation_detection(data)
    assert result["injected_bins"] > 0
    assert 0.0 <= result["precision"] <= 1.0
    assert 0.0 <= result["recall"] <= 1.0
    assert 0.0 <= result["f1"] <= 1.0
    assert 0.0 <= result["false_positive_rate"] <= 1.0
    assert result["true_positives"] + result["false_negatives"] == result["injected_bins"]
