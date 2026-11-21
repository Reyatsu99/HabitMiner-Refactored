# Patterns: what was checked before building them

HabitMiner Extended 2.2 adds **Your patterns**: things that are true about how one person uses their phone, found in their own data and shown only when they clearly hold. Before any of them was built, each candidate was run on two real exports (two users, 57 and 62 days, one from the original app and one from Extended). This page records what held up and what didn't. Only aggregate results are given here, with no app names or times from those exports.

## Kept

| Pattern | Rule (in `analytics/Patterns.kt`) | On the two exports |
|---|---|---|
| **Long sessions** | An app's visits of 15+ min are at least 50% of its time and happen at least 0.7 times a day; the app averages 20+ min a day over 7+ days. If most long visits start in one late three-hour block, the card offers a pause **only at night**, starting an hour before that block. | Fired for both users' top video app (over 60% of its time from about two long visits a day). Messaging apps never fired: around 90% of their visits are under 2 minutes. |
| **Late weekday nights and sleep** | Nights before a weekday morning (10+ of them), split at the median time of falling asleep. Shown when the later half slept at least 30 min less. Weekends are left out on purpose: sleeping in makes up for late nights and hides the effect. | Fired for both. Mixed with weekends, the effect almost disappeared for one of them. |
| **This week vs the week before** | The last 7 full days against the 7 before: total time (±30 min and ±10%) and the two apps that changed most (±20 min and ±30%). Needs 12 of the 14 days. | Total time fired in 4–5 of 7 weeks; per-app changes were often larger (40–80%). This is what keeps the list moving from week to week. |
| **Notifications that pull you in** | Phone sessions (gaps under a minute) that start within a minute of the first app's own notification, 20+ of them, lasting 15+ min at least 20% of the time **and** at least twice as often as sessions that didn't start from a notification. | Fired for one user's game (33% against 10%). Messaging notifications did **not** lead to long sessions for either user (about 10% either way), so the common assumption was wrong for both. |
| **How you get there** | For social, video and game apps with 30+ visits: another app, **used for at least 15 seconds**, within 2 minutes before at least 30% of them. | The first version fired for one user at over 55%, but the app "before" was the Google search bar or feed, open for a median of 2.4 seconds: a way of launching the app, not a habit. With the 15-second rule, real sessions in the earlier app came before only 4% of those visits, and the pattern no longer fires for either user. Everyday pairs (camera → gallery, mail → files) are excluded by only looking at leisure apps. |
| **Checking all day** | Quick checks (under 2 minutes) averaged over the busiest twelve hours, shown at 10+ an hour. | One user: about 15 an hour, every day; the other: about 6, so nothing shown. The original idea, a "checking peak hour", never fired: per-hour checking was flat for both. |
| **Last night** | A record (the longest visit starting between 00:00 and 05:00 in two weeks, 30+ min), or a win (3+ nights in a row without a 15+ min visit after midnight, when that used to happen on at least half of nights). | The record fired on about 1 in 10 days; neither user went more than 3 nights in a row without one, so the win is reachable but not automatic. |

## Checked for 2.4

- **What to do about checking all day.** For the top three quick-check apps, the share of checks that came within a minute of the app's own notification: for one user, about 40% for the main messenger but 0–2% for the other two; for the other user, 43% for the messenger and 14–20% for the rest. So quieter notifications help only some apps. The card offers notification settings only for an app where at least 30% of checks follow its notifications, and says which apps you open by yourself. It never offers a pause for chat apps you check dozens of times a day.
- **Weeknights only.** Long sessions after 23:00 were spread evenly over the week for both users (69–70% on Sunday to Thursday nights, against 71% if evenly spread), but they cost sleep only before weekdays. So the night pause offered from the sleep pattern applies Sunday to Thursday nights by default, with "every night" as the other choice; the long-sessions pattern pauses every night.

## Kept behind a strict threshold

- **Busiest hours** (a "danger zone"): shown only when two hours hold 25%+ of screen time (an even spread is about 12%). Neither user ever reached it in any week (highest 22%), but it is kept for people whose use is concentrated.
- **First app of the morning**: shown only when one app is first on at least half of mornings (the alarm clock doesn't count). Both users picked up their phone within 5 minutes of waking on nearly every morning, so that alone isn't news.

## Dropped

- **Phone use in the hour before sleep vs sleep length**: no link for either user.
- **Mood against phone use, sleep or steps**: one user had 24 answers, nearly all 4 or 5 out of 5. Links of about 0.3 at that size are not reliable, so mood stays as it was (one comparison, shown only with enough answers).

## Goal impact

For each Use less app, the Goals screen compares the median day since it was added with the median of the four weeks before, **leaving out the day it was added and the day before**. Goals are often set right after an unusual day (and the 2.1 insight buttons make that more likely), and comparing with that day would make any goal look like it worked. Until there are 5 full days it says so instead of comparing. Pause answers (closed, opened anyway) are counted from the pause screen's own log.
