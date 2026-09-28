package main

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"sync"
	"testing"
	"time"
)

// fakeOrderService issues ids, honours Idempotency-Key, and flips orders to CONFIRMED on the 2nd read.
func fakeOrderService(t *testing.T) *httptest.Server {
	var mu sync.Mutex
	byKey := map[string]string{}
	reads := map[string]int{}
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		mu.Lock()
		defer mu.Unlock()
		switch {
		case r.Method == http.MethodPost:
			key := r.Header.Get("Idempotency-Key")
			id, ok := byKey[key]
			if !ok {
				id = randomKey()
				byKey[key] = id
			}
			w.WriteHeader(http.StatusAccepted)
			now := time.Now()
			_ = json.NewEncoder(w).Encode(orderResponse{ID: id, Status: "PENDING", CreatedAt: now, UpdatedAt: now})
		default:
			id := r.URL.Path[len("/api/v1/orders/"):]
			reads[id]++
			status := "PENDING"
			if reads[id] >= 2 {
				status = "CONFIRMED"
			}
			created := time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)
			_ = json.NewEncoder(w).Encode(orderResponse{ID: id, Status: status,
				CreatedAt: created, UpdatedAt: created.Add(1500 * time.Millisecond)})
		}
	}))
}

func TestPlaceIsIdempotentAgainstServer(t *testing.T) {
	srv := fakeOrderService(t)
	defer srv.Close()
	req := orderRequest{CustomerID: "c", PaymentMethod: "CARD", Lines: []line{{SKU: "A", Quantity: 1}}}

	first := place(context.Background(), srv.Client(), srv.URL, "", "same-key", req)
	second := place(context.Background(), srv.Client(), srv.URL, "", "same-key", req)

	if first.err != nil || first.status != http.StatusAccepted {
		t.Fatalf("unexpected first result %+v", first)
	}
	if first.orderID != second.orderID {
		t.Fatalf("replay produced a new order: %s vs %s", first.orderID, second.orderID)
	}
}

func TestAwaitFinalUsesServerTimestampsAndNeverBlocksOnOneOrder(t *testing.T) {
	srv := fakeOrderService(t)
	defer srv.Close()
	var ids []string
	for i := 0; i < 5; i++ {
		ids = append(ids, place(context.Background(), srv.Client(), srv.URL, "", randomKey(), orderRequest{CustomerID: "c"}).orderID)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	outcomes, took := awaitFinal(ctx, srv.Client(), srv.URL, "", ids, 2, 10*time.Millisecond, nil)

	if outcomes["CONFIRMED"] != 5 || len(took) != 5 {
		t.Fatalf("outcomes=%v took=%v", outcomes, took)
	}
	if took[0] != 1500*time.Millisecond {
		t.Fatalf("expected server-side duration 1.5s, got %v", took[0])
	}
}

func TestAwaitFinalReportsStillRunningOnTimeout(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_ = json.NewEncoder(w).Encode(orderResponse{Status: "PENDING"})
	}))
	defer srv.Close()

	ctx, cancel := context.WithTimeout(context.Background(), 50*time.Millisecond)
	defer cancel()
	outcomes, _ := awaitFinal(ctx, srv.Client(), srv.URL, "", []string{"a", "b"}, 2, 10*time.Millisecond, nil)

	if outcomes["STILL_RUNNING"] != 2 {
		t.Fatalf("got %v", outcomes)
	}
}
