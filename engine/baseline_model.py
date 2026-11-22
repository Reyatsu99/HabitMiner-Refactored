from dataclasses import dataclass
from typing import Dict, Optional
import pandas as pd
import json

@dataclass
class BaselineBin:
    time_bin: str
    avg_duration_seconds: float
    std_duration_seconds: float
    avg_session_count: float
    std_session_count: float
    category_profile: Dict[str, float]
    data_points: int

class PersonalBaseline:
    def __init__(self, user_id: str):
        self.user_id = user_id
        self.bins: Dict[str, BaselineBin] = {}
    
    def build(self, feature_df: pd.DataFrame) -> None:
        """
        Build baseline from feature_df.
        """
        if feature_df.empty:
            return
            
        df = feature_df[feature_df["user_id"] == self.user_id]
        if df.empty:
            return
            
        for (day_type, time_slot), group in df.groupby(["day_type", "time_slot"]):
            time_bin = f"{day_type}_{time_slot}"
            if len(group) < 5:
                continue
                
            avg_dur = group["total_duration_seconds"].mean()
            std_dur = group["total_duration_seconds"].std(ddof=0)
            avg_sess = group["session_count"].mean()
            std_sess = group["session_count"].std(ddof=0)
            
            # Aggregate category durations to get profile
            cat_totals = {}
            total_dur_all = group["total_duration_seconds"].sum()
            
            for cat_dict_str in group["category_duration_dict"]:
                try:
                    cat_dict = json.loads(cat_dict_str)
                    for cat, dur in cat_dict.items():
                        cat_totals[cat] = cat_totals.get(cat, 0) + dur
                except (TypeError, ValueError):
                    # Ignore malformed category profiles, not unrelated programming errors.
                    pass
                    
            profile = {k: v / total_dur_all for k, v in cat_totals.items()} if total_dur_all > 0 else {}
            
            self.bins[time_bin] = BaselineBin(
                time_bin=time_bin,
                avg_duration_seconds=avg_dur,
                std_duration_seconds=std_dur,
                avg_session_count=avg_sess,
                std_session_count=std_sess,
                category_profile=profile,
                data_points=len(group)
            )
    
    def update(self, new_feature_df: pd.DataFrame, alpha: float = 0.2) -> None:
        """
        Exponential moving average update
        """
        df = new_feature_df[new_feature_df["user_id"] == self.user_id]
        if df.empty:
            return
            
        for (day_type, time_slot), group in df.groupby(["day_type", "time_slot"]):
            time_bin = f"{day_type}_{time_slot}"
            
            new_avg_dur = group["total_duration_seconds"].mean()
            new_std_dur = group["total_duration_seconds"].std(ddof=0)
            new_avg_sess = group["session_count"].mean()
            new_std_sess = group["session_count"].std(ddof=0)
            
            cat_totals = {}
            total_dur_all = group["total_duration_seconds"].sum()
            for cat_dict_str in group["category_duration_dict"]:
                try:
                    cat_dict = json.loads(cat_dict_str)
                    for cat, dur in cat_dict.items():
                        cat_totals[cat] = cat_totals.get(cat, 0) + dur
                except Exception:
                    pass
            new_profile = {k: v / total_dur_all for k, v in cat_totals.items()} if total_dur_all > 0 else {}
            
            if time_bin in self.bins:
                b = self.bins[time_bin]
                b.avg_duration_seconds = alpha * new_avg_dur + (1 - alpha) * b.avg_duration_seconds
                b.std_duration_seconds = alpha * new_std_dur + (1 - alpha) * b.std_duration_seconds
                b.avg_session_count = alpha * new_avg_sess + (1 - alpha) * b.avg_session_count
                b.std_session_count = alpha * new_std_sess + (1 - alpha) * b.std_session_count
                b.data_points += len(group)
                
                # Blend profiles
                all_cats = set(b.category_profile.keys()).union(new_profile.keys())
                blended_profile = {}
                for c in all_cats:
                    old_v = b.category_profile.get(c, 0)
                    new_v = new_profile.get(c, 0)
                    blended_profile[c] = alpha * new_v + (1 - alpha) * old_v
                b.category_profile = blended_profile
            else:
                self.bins[time_bin] = BaselineBin(
                    time_bin=time_bin,
                    avg_duration_seconds=new_avg_dur,
                    std_duration_seconds=new_std_dur,
                    avg_session_count=new_avg_sess,
                    std_session_count=new_std_sess,
                    category_profile=new_profile,
                    data_points=len(group)
                )
    
    def get_bin(self, time_bin: str) -> Optional[BaselineBin]:
        return self.bins.get(time_bin)
    
    def has_enough_data(self) -> bool:
        return len(self.bins) >= 4
    
    def to_dict(self) -> dict:
        return {
            "user_id": self.user_id,
            "bins": {k: v.__dict__ for k, v in self.bins.items()}
        }
    
    @classmethod
    def from_dict(cls, data: dict) -> 'PersonalBaseline':
        pb = cls(data["user_id"])
        for k, v in data.get("bins", {}).items():
            pb.bins[k] = BaselineBin(**v)
        return pb

def build_baselines_for_all_users(feature_df: pd.DataFrame) -> Dict[str, PersonalBaseline]:
    """Build a PersonalBaseline for each unique user_id in feature_df"""
    baselines = {}
    if feature_df.empty:
        return baselines
        
    for user_id in feature_df["user_id"].unique():
        pb = PersonalBaseline(user_id)
        pb.build(feature_df)
        baselines[user_id] = pb
    return baselines
