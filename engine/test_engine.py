from types import SimpleNamespace

import pandas as pd
import pytest

from baseline_model import PersonalBaseline
from deviation_detector import compute_kl_divergence, inject_controlled_deviation
from evaluate import experiment3_deviation_detection, split_data_chronologically
from feature_extraction import extract_time_bin_features
from habit_discovery import mine_frequent_sequences
from markov_model import TimeConditionedMarkovModel
from parser import generate_synthetic_usage, parse_studentlife, parse_uci_har
from visualize import generate_evaluation_report


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


def test_baseline_build_replaces_stale_bins_and_validates_alpha(synthetic_data):
    features = extract_time_bin_features(synthetic_data)
    baseline = PersonalBaseline(user_id="user_1")
    baseline.build(features)
    assert baseline.bins
    baseline.build(features.iloc[0:0])
    assert baseline.bins == {}

    with pytest.raises(ValueError, match="alpha must be between 0 and 1"):
        baseline.update(features, alpha=1.5)


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


def test_kl_divergence_handles_empty_and_invalid_profiles():
    assert compute_kl_divergence({}, {}) == 0.0
    assert compute_kl_divergence({}, {"SOCIAL": 1.0}) == 0.0
    assert compute_kl_divergence({"SOCIAL": 1.0}, {"SOCIAL": 1.0}) == pytest.approx(0.0)
    with pytest.raises(ValueError, match="cannot contain negative values"):
        compute_kl_divergence({"SOCIAL": -1.0}, {"SOCIAL": 1.0})
    with pytest.raises(ValueError, match="finite values"):
        compute_kl_divergence({"SOCIAL": float("nan")}, {"SOCIAL": 1.0})

def test_markov_fit_replaces_old_state_and_uses_stable_ties():
    model = TimeConditionedMarkovModel()
    model.fit({"u1|WEEKDAY|MORNING": [["SOCIAL", "PRODUCTIVITY"]]})
    model.fit({"u1|WEEKDAY|MORNING": [["SOCIAL", "GAMING"]]})

    predictions = model.predict_next("SOCIAL", "WEEKDAY", "MORNING", top_k=2)
    assert [state for state, _ in predictions] == ["GAMING", "SOCIAL"]
    assert all(state != "PRODUCTIVITY" for state, _ in predictions)

    with pytest.raises(ValueError, match="top_k must be at least 1"):
        model.predict_next("SOCIAL", "WEEKDAY", "MORNING", top_k=0)


def test_same_slot_anomaly_is_inserted_even_when_category_already_exists():
    data = generate_synthetic_usage(num_users=1, num_days=7, seed=31)
    existing = data.iloc[[0]].copy()
    existing["app_category"] = "GAMING"
    existing["time_slot"] = "EVENING"
    data = pd.concat([data, existing], ignore_index=True)

    days = data["start_time"].dt.date.nunique()
    injected = inject_controlled_deviation(
        data,
        "user_1",
        from_time_slot="EVENING",
        to_time_slot="EVENING",
        category="GAMING",
    )

    assert len(injected) == len(data) + days
    assert injected["controlled_anomaly"].sum() == days


def test_report_escapes_habit_text(tmp_path):
    output = tmp_path / "report.html"
    habit = SimpleNamespace(
        habit_name="<script>alert(1)</script>",
        pattern_str="<img src=x onerror=alert(1)>",
        time_slot="<b>EVENING</b>",
        confidence=0.9,
        support=5,
    )
    generate_evaluation_report(
        results={"exp1": {}, "exp2": {}, "exp3": {}},
        habits=[habit],
        baseline_profile={},
        sample_usage=pd.DataFrame(),
        output_file=str(output),
    )
    report = output.read_text(encoding="utf-8")
    assert "&lt;script&gt;alert(1)&lt;/script&gt;" in report
    assert "&lt;img src=x onerror=alert(1)&gt;" in report
    assert "<script>alert(1)</script>" not in report

def test_controlled_deviation_metrics_use_injected_day_bins():
    data = generate_synthetic_usage(num_users=1, num_days=28, seed=42)
    result = experiment3_deviation_detection(data)
    assert result["injected_bins"] > 0
    assert 0.0 <= result["precision"] <= 1.0
    assert 0.0 <= result["recall"] <= 1.0
    assert 0.0 <= result["f1"] <= 1.0
    assert 0.0 <= result["false_positive_rate"] <= 1.0
    assert result["true_positives"] + result["false_negatives"] == result["injected_bins"]
