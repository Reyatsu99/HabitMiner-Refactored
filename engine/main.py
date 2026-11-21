import argparse

from baseline_model import build_baselines_for_all_users
from evaluate import print_evaluation_report, run_all_experiments
from feature_extraction import extract_sequences, extract_time_bin_features
from habit_discovery import mine_frequent_sequences
from parser import generate_synthetic_usage
from visualize import generate_evaluation_report


def main(argv=None):
    parser = argparse.ArgumentParser(description="HabitMiner Python Evaluation Engine")
    parser.add_argument("--dataset", choices=["synthetic", "studentlife", "uci_har"], default="synthetic")
    parser.add_argument("--mode", choices=["evaluate", "activity_recognition", "report"], default="evaluate")
    parser.add_argument("--num-days", type=int, default=14, help="Days of synthetic data")
    parser.add_argument("--num-users", type=int, default=3, help="Synthetic users")
    parser.add_argument("--seed", type=int, default=42, help="Random seed for reproducible synthetic data")
    parser.add_argument("--output", type=str, default="habitminer_report.html")
    args = parser.parse_args(argv)

    if args.dataset != "synthetic":
        parser.error(
            f"The {args.dataset} adapter is not implemented. "
            "Only --dataset synthetic is currently supported."
        )
    if args.mode == "activity_recognition":
        parser.error(
            "Activity-recognition evaluation is not implemented. "
            "This command does not produce a model accuracy score."
        )

    print(f"Initializing HabitMiner Engine (Dataset: {args.dataset}, Mode: {args.mode})...")
    try:
        usage_df = generate_synthetic_usage(
            num_users=args.num_users,
            num_days=args.num_days,
            seed=args.seed,
        )
    except ValueError as exc:
        parser.error(str(exc))

    if usage_df.empty:
        parser.error("No synthetic usage data was generated.")

    print("Running experiments...")
    results = run_all_experiments(usage_df)
    if args.mode == "evaluate":
        print_evaluation_report(results)

    print("Generating HTML report...")
    train_seqs = extract_sequences(usage_df)
    habits = mine_frequent_sequences(train_seqs)
    features = extract_time_bin_features(usage_df)
    baselines = build_baselines_for_all_users(features)
    baseline_profile = next(iter(baselines.values())).to_dict() if baselines else {}
    sample = usage_df[usage_df["user_id"] == usage_df["user_id"].iloc[0]].head(50)

    report_path = generate_evaluation_report(
        results=results,
        habits=habits,
        baseline_profile=baseline_profile,
        sample_usage=sample,
        output_file=args.output,
    )
    print(f"Report saved to {report_path}")
    print("Done.")


if __name__ == "__main__":
    main()
