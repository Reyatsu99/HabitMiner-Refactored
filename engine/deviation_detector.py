from dataclasses import dataclass
from typing import List
import pandas as pd
import numpy as np
from scipy.special import rel_entr
import json
import math
from baseline_model import PersonalBaseline

@dataclass
class DeviationEvent:
    time_bin: str
    deviation_type: str
    description: str
    z_score: float
    normalized_score: float
    affected_category: str
    date: str | None = None

def compute_kl_divergence(p: dict, q: dict) -> float:
    """
    KL divergence between two category distributions.
    Handles zero values with small epsilon.
    """
    eps = 1e-5
    all_keys = set(p.keys()).union(q.keys())
    
    p_arr = np.array([p.get(k, 0) + eps for k in all_keys])
    q_arr = np.array([q.get(k, 0) + eps for k in all_keys])
    
    p_arr = p_arr / p_arr.sum()
    q_arr = q_arr / q_arr.sum()
    
    return float(np.sum(rel_entr(p_arr, q_arr)))

def normalized_score(z: float) -> float:
    """sigmoid(|z|) clamped to [0, 1]"""
    return min(1.0, 1.0 / (1.0 + math.exp(-abs(z))))

def detect_deviations(
    today_features: pd.DataFrame,
    baseline: PersonalBaseline
) -> List[DeviationEvent]:
    """
    Compare today's features against baseline.
    """
    deviations = []
    
    for _, row in today_features.iterrows():
        if row["user_id"] != baseline.user_id:
            continue
            
        time_bin = f"{row['day_type']}_{row['time_slot']}"
        b = baseline.get_bin(time_bin)
        
        if not b:
            continue
            
        # Z-score for total_duration
        if b.std_duration_seconds > 0:
            z_dur = (row["total_duration_seconds"] - b.avg_duration_seconds) / b.std_duration_seconds
        else:
            z_dur = 0
            
        if z_dur > 2.0:
            deviations.append(DeviationEvent(
                time_bin=time_bin,
                deviation_type="EXCESS_DURATION",
                description=f"Unusually high screen time ({row['total_duration_seconds']/60:.0f} mins)",
                z_score=z_dur,
                normalized_score=normalized_score(z_dur),
                affected_category=row["dominant_category"],
                date=str(row["date"]),
            ))
            
        # Z-score for session_count
        if b.std_session_count > 0:
            z_sess = (row["session_count"] - b.avg_session_count) / b.std_session_count
        else:
            z_sess = 0
            
        if z_sess > 2.5:
            deviations.append(DeviationEvent(
                time_bin=time_bin,
                deviation_type="HIGH_FREQUENCY",
                description=f"High frequency of app opens ({row['session_count']} sessions)",
                z_score=z_sess,
                normalized_score=normalized_score(z_sess),
                affected_category=row["dominant_category"],
                date=str(row["date"]),
            ))
            
        # Category distribution
        today_profile = {}
        try:
            today_profile = json.loads(row["category_duration_dict"])
            total_dur = sum(today_profile.values())
            if total_dur > 0:
                today_profile = {k: v/total_dur for k, v in today_profile.items()}
        except (TypeError, ValueError):
            # Ignore malformed or empty JSON profile data; surface unrelated failures.
            pass
            
        # KL-divergence
        if today_profile and b.category_profile:
            kl_div = compute_kl_divergence(today_profile, b.category_profile)
            if kl_div > 1.0:
                deviations.append(DeviationEvent(
                    time_bin=time_bin,
                    deviation_type="TEMPORAL_SHIFT",
                    description="Significant shift in app usage distribution",
                    z_score=kl_div,  # mapping KL to z roughly
                    normalized_score=min(1.0, kl_div / 3.0),
                    affected_category=row["dominant_category"],
                    date=str(row["date"]),
                ))
                
        # New behavior check
        base_cats = set(c for c, f in b.category_profile.items() if f > 0.05)
        today_cats = set(c for c, f in today_profile.items() if f > 0.1)
        new_cats = today_cats - base_cats
        
        for c in new_cats:
            deviations.append(DeviationEvent(
                time_bin=time_bin,
                deviation_type="NEW_BEHAVIOR",
                description=f"New significant usage of {c}",
                z_score=2.0,
                normalized_score=0.88,
                affected_category=c,
                date=str(row["date"]),
            ))
            
        # Missing routine
        missing_cats = base_cats - set(today_profile.keys())
        for c in missing_cats:
            # Only trigger if it was very prominent
            if b.category_profile.get(c, 0) > 0.3:
                deviations.append(DeviationEvent(
                    time_bin=time_bin,
                    deviation_type="MISSING_ROUTINE",
                    description=f"Missing expected {c} routine",
                    z_score=-2.0,
                    normalized_score=0.88,
                    affected_category=c,
                    date=str(row["date"]),
                ))
                
    deviations.sort(key=lambda x: x.normalized_score, reverse=True)
    return deviations

def inject_controlled_deviation(
    usage_df: pd.DataFrame,
    user_id: str,
    from_time_slot: str,
    to_time_slot: str,
    category: str = "GAMING",
) -> pd.DataFrame:
    """
    Add a clearly marked controlled category anomaly to each eligible test day.

    Existing matching sessions are moved by five hours when changing time slots. If none
    exist (which is common in the synthetic generator), one deterministic 45-minute session
    is inserted at the target time. The marker is carried through feature extraction and
    is used only for evaluation labels; it is never a model input.
    """
    df = usage_df.copy()
    if df.empty:
        df["controlled_anomaly"] = pd.Series(dtype=bool)
        return df

    df["start_time"] = pd.to_datetime(df["start_time"], errors="coerce")
    df["end_time"] = pd.to_datetime(df["end_time"], errors="coerce")
    if "controlled_anomaly" not in df.columns:
        df["controlled_anomaly"] = False
    else:
        df["controlled_anomaly"] = df["controlled_anomaly"].fillna(False).astype(bool)

    mask = (
        (df["user_id"] == user_id)
        & (df["app_category"] == category)
        & (df["time_slot"] == from_time_slot)
    )
    if mask.any() and from_time_slot != to_time_slot:
        df.loc[mask, "start_time"] += pd.Timedelta(hours=5)
            df.loc[mask, "end_time"] += pd.Timedelta(hours=5)
        df.loc[mask, "time_slot"] = to_time_slot
        df.loc[mask, "day_type"] = df.loc[mask, "start_time"].dt.weekday.apply(
            lambda weekday: "WEEKEND" if weekday >= 5 else "WEEKDAY"
        )
        df.loc[mask, "controlled_anomaly"] = True
        return df

    user_days = sorted(
        df.loc[df["user_id"] == user_id, "start_time"].dropna().dt.date.unique()
    )
    if not user_days:
        return df
    target_hours = {"MORNING": 9, "AFTERNOON": 14, "EVENING": 20, "NIGHT": 23}
    hour = target_hours.get(to_time_slot)
    if hour is None:
        raise ValueError(f"Unsupported time slot: {to_time_slot}")

    injected = []
    for day in user_days:
        start = pd.Timestamp(day).replace(hour=hour, minute=0, second=0, microsecond=0)
        end = start + pd.Timedelta(minutes=45)
        injected.append(
            {
                "user_id": user_id,
                "package_name": f"synthetic.controlled.{category.lower()}",
                "app_category": category,
                "start_time": start,
                "end_time": end,
                "duration_seconds": 45 * 60,
                "time_slot": to_time_slot,
                "day_type": "WEEKEND" if start.weekday() >= 5 else "WEEKDAY",
                "controlled_anomaly": True,
            }
        )
    return pd.concat([df, pd.DataFrame(injected)], ignore_index=True)
