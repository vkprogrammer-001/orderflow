package main

import (
	"context"
	"testing"
	"time"
)

func ms(n ...int) []time.Duration {
	out := make([]time.Duration, len(n))
	for i, v := range n {
		out[i] = time.Duration(v) * time.Millisecond
	}
	return out
}

func TestPercentileNearestRank(t *testing.T) {
	samples := ms(15, 20, 35, 40, 50)
	cases := map[float64]time.Duration{
		0: 15 * time.Millisecond, 30: 20 * time.Millisecond, 40: 20 * time.Millisecond,
		50: 35 * time.Millisecond, 100: 50 * time.Millisecond,
	}
	for p, want := range cases {
		if got := Percentile(samples, p); got != want {
			t.Errorf("p%v = %v, want %v", p, got, want)
		}
	}
}

func TestPercentileDoesNotMutateInput(t *testing.T) {
	samples := ms(3, 1, 2)
	Percentile(samples, 50)
	if samples[0] != 3*time.Millisecond {
		t.Fatalf("input was sorted in place: %v", samples)
	}
}

func TestPercentileEmpty(t *testing.T) {
	if Percentile(nil, 99) != 0 {
		t.Fatal("expected zero for no samples")
	}
}

func TestCountsIsStable(t *testing.T) {
	got := Counts(map[string]int{"REJECTED": 2, "CONFIRMED": 7})
	if got != "CONFIRMED=7 REJECTED=2" {
		t.Fatalf("got %q", got)
	}
}

func TestFeedJobsHoldsTheTargetRate(t *testing.T) {
	jobs := make(chan int)
	start := time.Now()
	go feedJobs(context.Background(), jobs, 21, 200) // 20 gaps of 5ms
	n := 0
	for range jobs {
		n++
	}
	if n != 21 {
		t.Fatalf("got %d jobs", n)
	}
	if took := time.Since(start); took < 90*time.Millisecond {
		t.Fatalf("finished in %v, pacing not applied", took)
	}
}

func TestFeedJobsUnpacedIsImmediate(t *testing.T) {
	jobs := make(chan int, 100)
	feedJobs(context.Background(), jobs, 100, 0)
	if len(jobs) != 100 {
		t.Fatalf("got %d", len(jobs))
	}
}
