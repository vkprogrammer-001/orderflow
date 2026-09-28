#!/usr/bin/env bash
# Shows where the saga backlog is. Run from anywhere in the repo, during or right after a load test.
set -u
cd "$(dirname "$0")/.."
pg() { docker compose exec -T postgres psql -U orderflow -d "$1" -Atc "$2"; }

echo "== Container CPU / memory"
docker stats --no-stream --format 'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}'

echo; echo "== Orders by status"
pg orders "select status, count(*) from orders group by 1 order by 1"

echo; echo "== Unpublished outbox rows | max publish attempts"
for db in orders inventory payments; do
  printf "%-10s " "$db"; pg "$db" "select count(*) filter (where published_at is null) || ' | ' || coalesce(max(attempts), 0) from outbox_events"
done

echo; echo "== Kafka consumer lag (group topic lag)"
docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --all-groups 2>/dev/null \
  | awk '$6 ~ /^[0-9]+$/ { lag[$1" "$2] += $6 } END { for (k in lag) print k, lag[k] }' | sort

echo; echo "== Most common warnings/errors, last 10 minutes"
docker compose logs --since 10m --no-log-prefix order-service inventory-service payment-service 2>/dev/null \
  | grep -E ' (WARN|ERROR) ' \
  | sed -E 's/^.* (WARN|ERROR) /\1 /; s/[0-9a-f]{8}-[0-9a-f-]{27}/<id>/g' | cut -c1-180 \
  | sort | uniq -c | sort -rn | head -15

echo; echo "== Why orders ended where they did (top reasons)"
pg orders "select status || ' | ' || coalesce(status_reason, '-') || ' | ' || count(*) from orders group by status, status_reason order by count(*) desc limit 12"

echo; echo "== Throughput per stage: rows | first -> last | rows/sec"
rate() { pg "$1" "select count(*) || ' | ' || to_char(min($3), 'HH24:MI:SS') || ' -> ' || to_char(max($3), 'HH24:MI:SS') || ' | ' || round(count(*) / greatest(extract(epoch from max($3) - min($3)), 1)::numeric, 1) from $2"; }
printf "%-22s " "orders placed";         rate orders    orders       created_at
printf "%-22s " "stock decisions";       rate inventory reservations created_at
printf "%-22s " "payment decisions";     rate payments  payments     created_at

echo; echo "== Outbox delay, write -> Kafka (p50 / p99 / max seconds)"
for db in orders inventory payments; do
  printf "%-10s " "$db"; pg "$db" "select round(percentile_cont(0.5) within group (order by extract(epoch from published_at - created_at))::numeric, 3) || ' / ' || round(percentile_cont(0.99) within group (order by extract(epoch from published_at - created_at))::numeric, 3) || ' / ' || round(max(extract(epoch from published_at - created_at))::numeric, 3) from outbox_events where published_at is not null"
done
