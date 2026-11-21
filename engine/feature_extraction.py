import pandas as pd
import numpy as np
from typing import Dict, List, Tuple
import json

def extract_time_bin_features(usage_df: pd.DataFrame, user_id: str = None) -> pd.DataFrame:
    """
    For each (user_id, date, day_type, time_slot) combination,
    compute aggregate features.
    """
    if usage_df.empty:
        return pd.DataFrame()
        
    df = usage_df.copy()
    if user_id:
        df = df[df["user_id"] == user_id]
        
    df["date"] = df["start_time"].dt.date
    
    features = []
    groups = df.groupby(["user_id", "date", "day_type", "time_slot"])
    
    for (uid, date, day_type, time_slot), group in groups:
        total_duration = group["duration_seconds"].sum()
        session_count = len(group)
        unique_categories = group["app_category"].nunique()
        dominant_category = group.groupby("app_category")["duration_seconds"].sum().idxmax()
        
        cat_durations = group.groupby("app_category")["duration_seconds"].sum().to_dict()
        category_duration_dict = json.dumps(cat_durations)
        
        social_frac = cat_durations.get("SOCIAL", 0) / total_duration if total_duration > 0 else 0
        ent_frac = cat_durations.get("ENTERTAINMENT", 0) / total_duration if total_duration > 0 else 0
        prod_frac = cat_durations.get("PRODUCTIVITY", 0) / total_duration if total_duration > 0 else 0
        
        features.append({
            "user_id": uid,
            "date": date,
            "day_type": day_type,
            "time_slot": time_slot,
            "controlled_anomaly": bool(group["controlled_anomaly"].fillna(False).any())
            if "controlled_anomaly" in group.columns
            else False,
            "total_duration_seconds": total_duration,
            "session_count": session_count,
            "unique_categories": unique_categories,
            "dominant_category": dominant_category,
            "category_duration_dict": category_duration_dict,
            "social_fraction": social_frac,
            "entertainment_fraction": ent_frac,
            "productivity_fraction": prod_frac
        })
        
    return pd.DataFrame(features)

def extract_sequences(usage_df: pd.DataFrame) -> Dict[str, List[List[str]]]:
    """
    For each (user_id, day_type, time_slot) bin,
    returns list of daily app_category sequences.
    Key format: "user_id|WEEKDAY|EVENING"
    """
    if usage_df.empty:
        return {}
        
    df = usage_df.copy()
    df["date"] = df["start_time"].dt.date
    
    sequences = {}
    groups = df.groupby(["user_id", "day_type", "time_slot"])
    
    for (uid, day_type, time_slot), group in groups:
        key = f"{uid}|{day_type}|{time_slot}"
        daily_seqs = []
        for date, daily_group in group.groupby("date"):
            sorted_cats = daily_group.sort_values("start_time")["app_category"].tolist()
            if sorted_cats:
                daily_seqs.append(sorted_cats)
        if daily_seqs:
            sequences[key] = daily_seqs
            
    return sequences

def compute_motion_features(accel_df: pd.DataFrame) -> pd.Series:
    """
    From raw accelerometer readings (x, y, z columns),
    compute magnitude variance -> STILL / WALKING / ACTIVE
    Low var (<0.5) = STILL, med (<2.0) = WALKING, else ACTIVE
    """
    if accel_df.empty or not all(col in accel_df.columns for col in ["x", "y", "z"]):
        return pd.Series(dtype=str)
        
    mag = np.sqrt(accel_df["x"]**2 + accel_df["y"]**2 + accel_df["z"]**2)
    var = mag.var()
    
    if var < 0.5:
        state = "STILL"
    elif var < 2.0:
        state = "WALKING"
    else:
        state = "ACTIVE"
        
    return pd.Series([state] * len(accel_df), index=accel_df.index)
