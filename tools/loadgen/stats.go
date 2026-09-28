package main

import (
	"fmt"
	"sort"
	"strings"
	"time"
)

// Percentile returns the p-th percentile (0-100) using nearest-rank. The input is not modified.
func Percentile(samples []time.Duration, p float64) time.Duration {
	if len(samples) == 0 {
		return 0
	}
	sorted := append([]time.Duration(nil), samples...)
	sort.Slice(sorted, func(i, j int) bool { return sorted[i] < sorted[j] })
	if p <= 0 {
		return sorted[0]
	}
	if p >= 100 {
		return sorted[len(sorted)-1]
	}
	rank := int(p/100*float64(len(sorted))+0.999999) - 1 // ceil(p/100*n) - 1
	if rank < 0 {
		rank = 0
	}
	return sorted[rank]
}

// Summary renders a latency line like "p50 12ms  p95 40ms  p99 88ms  max 120ms".
func Summary(samples []time.Duration) string {
	if len(samples) == 0 {
		return "no samples"
	}
	return fmt.Sprintf("p50 %-8v p95 %-8v p99 %-8v max %v",
		round(Percentile(samples, 50)), round(Percentile(samples, 95)),
		round(Percentile(samples, 99)), round(Percentile(samples, 100)))
}

// Counts renders a map as "a=1 b=2" in key order, so output is stable between runs.
func Counts[K comparable](m map[K]int) string {
	keys := make([]string, 0, len(m))
	byString := make(map[string]int, len(m))
	for k, v := range m {
		s := fmt.Sprint(k)
		keys = append(keys, s)
		byString[s] = v
	}
	sort.Strings(keys)
	parts := make([]string, len(keys))
	for i, k := range keys {
		parts[i] = fmt.Sprintf("%s=%d", k, byString[k])
	}
	return strings.Join(parts, " ")
}

func round(d time.Duration) time.Duration {
	if d > 10*time.Millisecond {
		return d.Round(time.Millisecond)
	}
	return d.Round(10 * time.Microsecond)
}
