#!/usr/bin/env python3
"""
Mapper for Hadoop Streaming that consumes the game analytics JSON emitted
by the server (via Kafka) and emits per-player statistics.

Input:  JSON lines such as
{
  "event_type": "GAME_FINISHED",
  "winner": "ali",
  "duration_ms": 42000,
  "players": [{"username": "ali", "won": true}, ...],
  "timestamp": 1700793456123
}

Output: key\tvalue where value is a CSV payload:
username\tgames_played,games_won,total_time_ms
"""
import json
import sys


def emit(username: str, played: int, won: int, total_time_ms: int) -> None:
    payload = f"{played},{won},{total_time_ms}"
    print(f"{username}\t{payload}")


def main() -> None:
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        if "\t" in line:
            line = line.split("\t", 1)[1]
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            continue

        if event.get("event_type") != "GAME_FINISHED":
            continue

        duration = int(event.get("duration_ms", 0))
        players = event.get("players", [])
        for player in players:
            username = player.get("username")
            if not username:
                continue
            won = 1 if player.get("won") else 0
            emit(username, 1, won, duration if won else 0)


if __name__ == "__main__":
    main()


