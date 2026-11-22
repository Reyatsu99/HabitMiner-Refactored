import html as html_lib

import pandas as pd

def generate_evaluation_report(
    results: dict,
    habits: list,
    baseline_profile: dict,
    sample_usage: pd.DataFrame,
    output_file: str = 'habitminer_report.html'
) -> str:
    """Generates and saves report, returns output path"""
    
    # Extract data for charts
    e1 = results.get("exp1", {})
    e2 = results.get("exp2", {})
    e3 = results.get("exp3", {})
    
    html = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>HabitMiner Evaluation Report</title>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    <style>
        :root {{
            --bg-color: #0F172A;
            --card-bg: #1E293B;
            --text-main: #F8FAFC;
            --text-muted: #94A3B8;
            --accent: #38BDF8;
            --accent-green: #34D399;
            --accent-red: #F87171;
            --border: #334155;
        }}
        body {{
            font-family: 'Segoe UI', system-ui, sans-serif;
            background-color: var(--bg-color);
            color: var(--text-main);
            margin: 0;
            padding: 2rem;
            line-height: 1.6;
        }}
        .container {{
            max-width: 1200px;
            margin: 0 auto;
        }}
        header {{
            margin-bottom: 2rem;
            border-bottom: 1px solid var(--border);
            padding-bottom: 1rem;
        }}
        h1 {{ margin: 0; font-weight: 300; letter-spacing: 1px; color: var(--accent); }}
        h2 {{ margin-top: 0; font-weight: 400; color: var(--text-main); }}
        
        .metrics-grid {{
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
            gap: 1rem;
            margin-bottom: 2rem;
        }}
        .metric-card {{
            background: var(--card-bg);
            padding: 1.5rem;
            border-radius: 8px;
            border: 1px solid var(--border);
            text-align: center;
        }}
        .metric-value {{ font-size: 2rem; font-weight: 600; color: var(--accent); margin-bottom: 0.5rem; }}
        .metric-label {{ font-size: 0.875rem; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.5px; }}
        
        .section {{
            background: var(--card-bg);
            padding: 2rem;
            border-radius: 8px;
            border: 1px solid var(--border);
            margin-bottom: 2rem;
        }}
        
        .chart-container {{
            position: relative;
            height: 300px;
            width: 100%;
            margin-top: 1rem;
        }}
        
        table {{
            width: 100%;
            border-collapse: collapse;
            margin-top: 1rem;
        }}
        th, td {{
            padding: 0.75rem;
            text-align: left;
            border-bottom: 1px solid var(--border);
        }}
        th {{ color: var(--text-muted); font-weight: 500; text-transform: uppercase; font-size: 0.875rem; }}
    </style>
</head>
<body>
    <div class="container">
        <header>
            <h1>HabitMiner Evaluation Report</h1>
            <p style="color: var(--text-muted)">Context-aware personalized behavioral data mining</p>
        </header>
        
        <div class="metrics-grid">
            <div class="metric-card">
                <div class="metric-value">{e1.get('pattern_count', 0)}</div>
                <div class="metric-label">Habits Discovered</div>
            </div>
            <div class="metric-card">
                <div class="metric-value">{e1.get('avg_confidence', 0):.2f}</div>
                <div class="metric-label">Avg Confidence</div>
            </div>
            <div class="metric-card">
                <div class="metric-value">{e2.get('markov_top1', 0):.1%}</div>
                <div class="metric-label">Top-1 Accuracy</div>
            </div>
            <div class="metric-card">
                <div class="metric-value">{e2.get('markov_top3', 0):.1%}</div>
                <div class="metric-label">Top-3 Accuracy</div>
            </div>
            <div class="metric-card">
                <div class="metric-value">{e3.get('f1', 0):.2f}</div>
                <div class="metric-label">Deviation F1</div>
            </div>
            <div class="metric-card">
                <div class="metric-value">{e3.get('false_positive_rate', 0):.3f}</div>
                <div class="metric-label">FPR</div>
            </div>
        </div>
        
        <div class="section">
            <h2>Experiment 1: Habit Discovery</h2>
            <table>
                <thead>
                    <tr><th>Habit Name</th><th>Pattern</th><th>Time Slot</th><th>Confidence</th><th>Support</th></tr>
                </thead>
                <tbody>
"""
    for h in habits[:5]:
        html += f"<tr><td>{html_lib.escape(str(h.habit_name))}</td><td>{html.escape(str(h.pattern_str))}</td><td>{html.escape(str(h.time_slot))}</td><td>{h.confidence:.2f}</td><td>{h.support} days</td></tr>"
        
    html += """
                </tbody>
            </table>
        </div>
        
        <div class="section" style="display: grid; grid-template-columns: 1fr 1fr; gap: 2rem;">
            <div>
                <h2>Experiment 2: Prediction</h2>
                <div class="chart-container">
                    <canvas id="predChart"></canvas>
                </div>
            </div>
            <div>
                <h2>Experiment 3: Deviation</h2>
                <div class="chart-container">
                    <canvas id="devChart"></canvas>
                </div>
            </div>
        </div>
        
    </div>
    
    <script>
        Chart.defaults.color = '#94A3B8';
        Chart.defaults.borderColor = '#334155';
        
        new Chart(document.getElementById('predChart'), {
            type: 'bar',
            data: {
                labels: ['Top-1 Accuracy', 'Top-3 Accuracy'],
                datasets: [
                    {
                        label: 'Baseline',
                        data: [""" + f"{e2.get('baseline_top1', 0)}, {e2.get('baseline_top3', 0)}" + """],
                        backgroundColor: '#64748B'
                    },
                    {
                        label: 'HabitMiner',
                        data: [""" + f"{e2.get('markov_top1', 0)}, {e2.get('markov_top3', 0)}" + """],
                        backgroundColor: '#38BDF8'
                    }
                ]
            },
            options: { responsive: true, maintainAspectRatio: false, scales: { y: { beginAtZero: true, max: 1 } } }
        });
        
        new Chart(document.getElementById('devChart'), {
            type: 'bar',
            data: {
                labels: ['Precision', 'Recall', 'F1 Score'],
                datasets: [{
                    label: 'Score',
                    data: [""" + f"{e3.get('precision', 0)}, {e3.get('recall', 0)}, {e3.get('f1', 0)}" + """],
                    backgroundColor: ['#34D399', '#38BDF8', '#A78BFA']
                }]
            },
            options: { responsive: true, maintainAspectRatio: false, scales: { y: { beginAtZero: true, max: 1 } }, plugins: { legend: { display: false } } }
        });
    </script>
</body>
</html>
"""
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(html)
        
    return output_file
