from typing import List, Dict, Tuple

class TimeConditionedMarkovModel:
    def __init__(self):
        # time_bin -> (state -> (next_state -> prob))
        self.transitions: Dict[str, Dict[str, Dict[str, float]]] = {}
        # time_bin -> (state -> count)
        self.state_counts: Dict[str, Dict[str, int]] = {}
        # all seen states
        self.vocab = set()
        
    def fit(self, sequences: Dict[str, List[List[str]]]):
        """
        Sequences dict key: "user_id|day_type|time_slot".
        Each call replaces the previous fitted state.
        """
        self.transitions.clear()
        self.state_counts.clear()
        self.vocab.clear()

        # First pass to build vocab and raw counts
        raw_transitions = {}
        
        for key, daily_seqs in sequences.items():
            parts = key.split("|")
            if len(parts) != 3:
                raise ValueError(f"Invalid sequence key: {key!r}")
            _, day_type, time_slot = parts
            time_bin = f"{day_type}_{time_slot}"
            
            if time_bin not in raw_transitions:
                raw_transitions[time_bin] = {}
                self.state_counts[time_bin] = {}
                
            for seq in daily_seqs:
                for i in range(len(seq) - 1):
                    s1 = seq[i]
                    s2 = seq[i+1]
                    self.vocab.add(s1)
                    self.vocab.add(s2)
                    
                    if s1 not in raw_transitions[time_bin]:
                        raw_transitions[time_bin][s1] = {}
                        
                    raw_transitions[time_bin][s1][s2] = raw_transitions[time_bin][s1].get(s2, 0) + 1
                    self.state_counts[time_bin][s1] = self.state_counts[time_bin].get(s1, 0) + 1
                    
        # Apply Laplace smoothing and convert to probabilities
        vocab_size = len(self.vocab)
        
        for time_bin, trans_counts in raw_transitions.items():
            self.transitions[time_bin] = {}
            for s1 in self.vocab:
                self.transitions[time_bin][s1] = {}
                
                # Total count for s1 in this time_bin (with smoothing)
                total_s1 = self.state_counts[time_bin].get(s1, 0) + vocab_size
                
                for s2 in self.vocab:
                    # Count for s1->s2 (with smoothing)
                    count_s1_s2 = trans_counts.get(s1, {}).get(s2, 0) + 1
                    prob = count_s1_s2 / total_s1
                    self.transitions[time_bin][s1][s2] = prob
                    
    def predict_next(self, current_state: str, day_type: str, time_slot: str, top_k: int = 1) -> List[Tuple[str, float]]:
        if top_k < 1:
            raise ValueError("top_k must be at least 1")
        time_bin = f"{day_type}_{time_slot}"
        
        if time_bin not in self.transitions or current_state not in self.transitions[time_bin]:
            # Fallback to uniform distribution over vocab if state not seen in this bin
            if not self.vocab:
                return []
            prob = 1.0 / len(self.vocab)
            preds = [(s, prob) for s in self.vocab]
            return sorted(preds, key=lambda x: (-x[1], x[0]))[:top_k]
            
        probs = self.transitions[time_bin][current_state]
        preds = [(k, v) for k, v in probs.items()]
        return sorted(preds, key=lambda x: (-x[1], x[0]))[:top_k]

    def evaluate_predictions(self, test_sequences: Dict[str, List[List[str]]], top_k: int = 3) -> dict:
        if top_k < 1:
            raise ValueError("top_k must be at least 1")
        correct_top1 = 0
        correct_topk = 0
        total_predictions = 0
        
        for key, daily_seqs in test_sequences.items():
            _, day_type, time_slot = key.split("|")
            
            for seq in daily_seqs:
                for i in range(len(seq) - 1):
                    s1 = seq[i]
                    actual_next = seq[i+1]
                    
                    preds = self.predict_next(s1, day_type, time_slot, top_k=top_k)
                    pred_states = [p[0] for p in preds]
                    
                    if pred_states:
                        if actual_next == pred_states[0]:
                            correct_top1 += 1
                        if actual_next in pred_states:
                            correct_topk += 1
                    total_predictions += 1
                    
        return {
            "top1_accuracy": correct_top1 / total_predictions if total_predictions > 0 else 0.0,
            f"top{top_k}_accuracy": correct_topk / total_predictions if total_predictions > 0 else 0.0,
            "total_samples": total_predictions
        }
