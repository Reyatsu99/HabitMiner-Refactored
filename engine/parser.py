import random
from datetime import datetime, timedelta

import pandas as pd

def get_time_slot(hour: int) -> str:
    if 6 <= hour < 12:
        return "MORNING"
    elif 12 <= hour < 17:
        return "AFTERNOON"
    elif 17 <= hour < 22:
        return "EVENING"
    else:
        return "NIGHT"

def generate_synthetic_usage(
    num_users: int = 1,
    num_days: int = 14,
    seed: int = 42,
    reference_date: datetime = datetime(2026, 1, 5),
) -> pd.DataFrame:
    """
    Simulates realistic smartphone usage patterns for multiple users.
    Returns DataFrame with columns: user_id, package_name, app_category, start_time, end_time, duration_seconds, time_slot, day_type
    """
    if num_users < 1:
        raise ValueError("num_users must be at least 1")
    if num_days < 1:
        raise ValueError("num_days must be at least 1")

    rng = random.Random(seed)
    packages = {
        "SOCIAL": ["com.instagram.android", "com.facebook.katana", "com.twitter.android"],
        "ENTERTAINMENT": ["com.google.android.youtube", "com.netflix.mediaclient", "com.spotify.music"],
        "PRODUCTIVITY": ["com.google.android.apps.docs", "com.microsoft.office.word", "com.slack"],
        "COMMUNICATION": ["com.whatsapp", "org.telegram.messenger", "com.google.android.gm"],
        "GAMING": ["com.tencent.ig", "com.king.candycrushsaga", "com.supercell.clashofclans"],
        "EDUCATION": ["com.duolingo", "org.coursera.android", "com.quizlet.quizletandroid"]
    }

    records = []
    base_date = reference_date.replace(hour=0, minute=0, second=0, microsecond=0) - timedelta(days=num_days - 1)

    for user_idx in range(num_users):
        user_id = f"user_{user_idx+1}"
        for day in range(num_days):
            current_date = base_date + timedelta(days=day)
            day_type = "WEEKDAY" if current_date.weekday() < 5 else "WEEKEND"
            
            # Morning routine
            if rng.random() > 0.1:
                start = current_date.replace(hour=rng.randint(7, 9), minute=random.randint(0, 59))
                cat = "PRODUCTIVITY" if day_type == "WEEKDAY" else "ENTERTAINMENT"
                dur = random.randint(300, 1800)
                records.append((user_id, rng.choice(packages[cat]), cat, start, start + timedelta(seconds=dur), dur))
            
            # Afternoon usage
            for _ in range(random.randint(2, 5)):
                start = current_date.replace(hour=random.randint(12, 16), minute=random.randint(0, 59))
                cat = rng.choices(["COMMUNICATION", "SOCIAL", "PRODUCTIVITY"], weights=[0.4, 0.4, 0.2])[0]
                dur = random.randint(60, 900)
                records.append((user_id, random.choice(packages[cat]), cat, start, start + timedelta(seconds=dur), dur))

            # Evening routine
            start = current_date.replace(hour=random.randint(18, 21), minute=random.randint(0, 59))
            cat = "ENTERTAINMENT" if random.random() > 0.3 else "SOCIAL"
            dur = random.randint(1200, 3600)
            records.append((user_id, random.choice(packages[cat]), cat, start, start + timedelta(seconds=dur), dur))
            
            # Late night anomaly occasionally
            if random.random() < 0.1:
                start = current_date.replace(hour=random.randint(23, 23), minute=random.randint(0, 59))
                cat = "GAMING"
                dur = random.randint(3600, 7200)
                records.append((user_id, random.choice(packages[cat]), cat, start, start + timedelta(seconds=dur), dur))

    df = pd.DataFrame(records, columns=["user_id", "package_name", "app_category", "start_time", "end_time", "duration_seconds"])
    df["time_slot"] = df["start_time"].dt.hour.apply(get_time_slot)
    df["day_type"] = df["start_time"].dt.weekday.apply(lambda w: "WEEKEND" if w >= 5 else "WEEKDAY")
    df = df.sort_values(by=["user_id", "start_time"]).reset_index(drop=True)
    return df

def parse_studentlife(data_dir: str) -> pd.DataFrame:
    """StudentLife ingestion is not implemented in this evaluation engine yet."""
    raise NotImplementedError(
        "StudentLife ingestion is not implemented. Use --dataset synthetic; "
        "do not treat generated data as a parsed StudentLife export."
    )
def parse_uci_har(data_dir: str) -> tuple[pd.DataFrame, pd.DataFrame]:
    """UCI HAR ingestion and activity-recognition evaluation are not implemented yet."""
    raise NotImplementedError(
        "UCI HAR ingestion/activity recognition is not implemented. "
        "No accuracy result is available from this engine."
    )
