from dataclasses import dataclass
from typing import List, Dict

@dataclass
class DiscoveredHabit:
    pattern: List[str]           # e.g. ["SOCIAL", "ENTERTAINMENT"]
    pattern_str: str             # e.g. "SOCIAL → ENTERTAINMENT"
    time_slot: str
    day_type: str
    confidence: float
    support: int                 # number of days pattern appears
    total_days: int
    habit_name: str              # human-readable label

def mine_frequent_sequences(
    sequences: Dict[str, List[List[str]]],
    min_support: int = 3,
    min_confidence: float = 0.4
) -> List[DiscoveredHabit]:
    """
    Frequency-based sequential pattern mining.
    """
    habits = []
    
    for key, daily_seqs in sequences.items():
        uid, day_type, time_slot = key.split("|")
        total_days = len(daily_seqs)
        
        if total_days == 0:
            continue
            
        pattern_counts = {}
        
        for seq in daily_seqs:
            seen_today = set()
            
            # Bigrams
            for i in range(len(seq) - 1):
                pat = tuple(seq[i:i+2])
                seen_today.add(pat)
                
            # Trigrams
            for i in range(len(seq) - 2):
                pat = tuple(seq[i:i+3])
                seen_today.add(pat)
                
            # Unigrams (optional, but good for single dominant app habits)
            for item in seq:
                seen_today.add((item,))
                
            for pat in seen_today:
                pattern_counts[pat] = pattern_counts.get(pat, 0) + 1
                
        for pat, support in pattern_counts.items():
            confidence = support / total_days
            if support >= min_support and confidence >= min_confidence:
                pattern_list = list(pat)
                habits.append(DiscoveredHabit(
                    pattern=pattern_list,
                    pattern_str=" → ".join(pattern_list),
                    time_slot=time_slot,
                    day_type=day_type,
                    confidence=confidence,
                    support=support,
                    total_days=total_days,
                    habit_name=label_habit(pattern_list, time_slot, day_type)
                ))
                
    habits.sort(key=lambda x: x.confidence, reverse=True)
    return habits

def label_habit(pattern: List[str], time_slot: str, day_type: str) -> str:
    """
    Generate human-readable habit name.
    """
    pat_str = " & ".join(pattern)
    time_str = time_slot.capitalize()
    
    if len(pattern) == 1 and pattern[0] == "GAMING" and time_slot == "NIGHT":
        return "Late-Night Gaming Session"
        
    return f"{time_str} {pat_str} Routine"

def compute_pattern_similarity(p1: List[str], p2: List[str]) -> float:
    """
    Jaccard similarity between two patterns.
    """
    set1 = set(p1)
    set2 = set(p2)
    union = len(set1.union(set2))
    if union == 0:
        return 0.0
    return len(set1.intersection(set2)) / union
