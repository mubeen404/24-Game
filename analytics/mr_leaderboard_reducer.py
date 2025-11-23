#!/usr/bin/env python3
"""
Reducer for Hadoop Streaming that aggregates per-player statistics.

Aggregates:
  - games_played
  - games_won
  - total_time_ms
Outputs a CSV line for downstream loading.
"""
import sys


def emit(username: str, played: int, won: int, total_time: int) -> None:
    avg_time = (total_time / won / 1000.0) if won > 0 else 0.0
    print(f"{username},{played},{won},{avg_time:.3f}")


def main() -> None:
    current_user = None
    total_played = 0
    total_won = 0
    total_time = 0

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            username, payload = line.split("\t", 1)
            played_str, won_str, time_str = payload.split(",")
        except ValueError:
            continue

        played = int(played_str)
        won = int(won_str)
        time_ms = int(time_str)

        if current_user != username:
            if current_user is not None:
                emit(current_user, total_played, total_won, total_time)
            current_user = username
            total_played = 0
            total_won = 0
            total_time = 0

        total_played += played
        total_won += won
        total_time += time_ms

    if current_user is not None:
        emit(current_user, total_played, total_won, total_time)


if __name__ == "__main__":
    main()


