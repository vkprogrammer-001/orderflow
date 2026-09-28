// Command loadgen fires concurrent orders at OrderFlow and reports API latency, throughput and,
// with -follow, how long each saga took to reach a final state.
//
//	go run . -base http://localhost:8080/order -n 1000 -c 64 -follow
package main

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"math/big"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"sync"
	"time"
)

type line struct {
	SKU      string `json:"sku"`
	Quantity int    `json:"quantity"`
}

type orderRequest struct {
	CustomerID    string `json:"customerId"`
	PaymentMethod string `json:"paymentMethod"`
	Lines         []line `json:"lines"`
}

type orderResponse struct {
	ID        string    `json:"id"`
	Status    string    `json:"status"`
	CreatedAt time.Time `json:"createdAt"`
	UpdatedAt time.Time `json:"updatedAt"`
}

type result struct {
	status  int
	body    string
	latency time.Duration
	orderID string
	sentAt  time.Time
	err     error
}

var terminal = map[string]bool{"CONFIRMED": true, "REJECTED": true, "CANCELLED": true}

func main() {
	base := flag.String("base", "http://localhost:8080/order", "order-service base URL (through the edge proxy)")
	apiKey := flag.String("key", os.Getenv("ORDERFLOW_API_KEY"), "X-API-Key, only needed when calling the service directly")
	total := flag.Int("n", 500, "orders to place")
	concurrency := flag.Int("c", 32, "concurrent workers")
	skus := flag.String("skus", "CHARGER-35W,BRAID-CABLE-1M,EARBUDS-ANC,SLEEVE-14,WATCH-BAND-S,STYLUS-2", "comma-separated SKUs to pick from")
	methods := flag.String("methods", "CARD,CARD,CARD,UPI,WALLET", "payment methods to pick from (repeat to weight)")
	retryRate := flag.Int("retry-pct", 10, "percent of requests re-sent with the same Idempotency-Key, to prove replays are safe")
	rate := flag.Float64("rate", 0, "target orders per second (open loop); 0 sends as fast as the workers allow")
	follow := flag.Bool("follow", false, "poll each order until it reaches a final state and report saga latency")
	timeout := flag.Duration("timeout", 3*time.Minute, "overall time limit for -follow")
	pollEvery := flag.Duration("poll", 500*time.Millisecond, "with -follow, how often to re-check orders still in flight")
	followWorkers := flag.Int("follow-workers", 16, "with -follow, concurrent status requests (kept low so polling doesn't load the system under test)")
	flag.Parse()

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt)
	defer stop()

	client := &http.Client{
		Timeout:   10 * time.Second,
		Transport: &http.Transport{MaxIdleConnsPerHost: *concurrency, MaxConnsPerHost: *concurrency},
	}
	skuList := strings.Split(*skus, ",")
	methodList := strings.Split(*methods, ",")

	jobs := make(chan int)
	results := make(chan result, *total)
	var wg sync.WaitGroup

	started := time.Now()
	for w := 0; w < *concurrency; w++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for i := range jobs {
				req := orderRequest{
					CustomerID:    fmt.Sprintf("load-%04d", i%500),
					PaymentMethod: pick(methodList),
					Lines:         []line{{SKU: pick(skuList), Quantity: 1 + randInt(3)}},
				}
				key := randomKey()
				r := place(ctx, client, *base, *apiKey, key, req)
				results <- r
				if r.err == nil && randInt(100) < *retryRate {
					// Same key, same body: must return the same order id, never a second order.
					again := place(ctx, client, *base, *apiKey, key, req)
					if again.err == nil && again.orderID != r.orderID {
						fmt.Fprintf(os.Stderr, "IDEMPOTENCY VIOLATION: key %s gave %s then %s\n", key, r.orderID, again.orderID)
					}
				}
			}
		}()
	}

	go feedJobs(ctx, jobs, *total, *rate)
	go func() { wg.Wait(); close(results) }()

	var latencies []time.Duration
	codes := map[int]int{}
	var placed []result
	errors := 0
	var firstErr error
	firstFailure := map[int]string{}
	for r := range results {
		if r.err != nil {
			if firstErr == nil {
				firstErr = r.err
			}
			errors++
			continue
		}
		if r.status >= 400 && firstFailure[r.status] == "" {
			firstFailure[r.status] = r.body
		}
		codes[r.status]++
		latencies = append(latencies, r.latency)
		if r.status == http.StatusAccepted {
			placed = append(placed, r)
		}
	}
	elapsed := time.Since(started)

	target := "max"
	if *rate > 0 {
		target = fmt.Sprintf("%.0f/s", *rate)
	}
	fmt.Printf("\nPlaced %d orders in %v with %d workers, target rate %s (%.0f req/s achieved)\n",
		len(latencies), elapsed.Round(time.Millisecond), *concurrency, target, float64(len(latencies))/elapsed.Seconds())
	fmt.Printf("  HTTP status   %s   transport errors=%d\n", Counts(codes), errors)
	if firstErr != nil {
		fmt.Printf("  first error   %v\n", firstErr)
	}
	fmt.Printf("  API latency   %s\n", Summary(latencies))
	for status, body := range firstFailure {
		if len(body) > 300 {
			body = body[:300] + "..."
		}
		fmt.Printf("  first %d body %s\n", status, strings.TrimSpace(body))
	}

	if *follow && len(placed) > 0 {
		followSagas(ctx, client, *base, *apiKey, placed, *timeout, *followWorkers, *pollEvery)
	}
}

// feedJobs hands out order numbers, optionally paced to a fixed arrival rate. A fixed rate is how
// real traffic behaves and shows the sustainable throughput; "as fast as possible" only shows
// where the system saturates.
func feedJobs(ctx context.Context, jobs chan<- int, total int, rate float64) {
	defer close(jobs)
	var tick <-chan time.Time
	if rate > 0 {
		t := time.NewTicker(time.Duration(float64(time.Second) / rate))
		defer t.Stop()
		tick = t.C
	}
	for i := 0; i < total; i++ {
		if tick != nil {
			select {
			case <-tick:
			case <-ctx.Done():
				return
			}
		}
		select {
		case jobs <- i:
		case <-ctx.Done():
			return
		}
	}
}

func place(ctx context.Context, c *http.Client, base, apiKey, key string, body orderRequest) result {
	payload, _ := json.Marshal(body)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, base+"/api/v1/orders", bytes.NewReader(payload))
	if err != nil {
		return result{err: err}
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Idempotency-Key", key)
	if apiKey != "" {
		req.Header.Set("X-API-Key", apiKey)
	}
	start := time.Now()
	resp, err := c.Do(req)
	if err != nil {
		return result{err: err}
	}
	defer resp.Body.Close()
	raw, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
	latency := time.Since(start)
	var o orderResponse
	_ = json.Unmarshal(raw, &o)
	r := result{status: resp.StatusCode, latency: latency, orderID: o.ID, sentAt: start}
	if resp.StatusCode >= 400 {
		r.body = string(raw)
	}
	return r
}

// followSagas waits for every placed order to reach a final state and reports the outcomes.
// End-to-end time is taken from the server (updatedAt - createdAt), so polling frequency
// doesn't distort it and polling can stay gentle.
func followSagas(ctx context.Context, c *http.Client, base, apiKey string, placed []result,
	limit time.Duration, workers int, every time.Duration) {
	ids := make([]string, 0, len(placed))
	for _, r := range placed {
		ids = append(ids, r.orderID)
	}
	ctx, cancel := context.WithTimeout(ctx, limit)
	defer cancel()
	started := time.Now()
	outcomes, took := awaitFinal(ctx, c, base, apiKey, ids, workers, every, func(inFlight int) {
		fmt.Fprintf(os.Stderr, "\r  %4d orders still in flight after %v   ", inFlight, time.Since(started).Round(time.Second))
	})
	fmt.Fprintln(os.Stderr)
	fmt.Printf("\nSaga outcomes   %s\n", Counts(outcomes))
	fmt.Printf("  end-to-end    %s   (server time: order placed -> final state)\n", Summary(took))
}

// awaitFinal sweeps all in-flight orders every interval until each is final or ctx expires.
// Sweeping (rather than one worker per order) means a single slow order never blocks the others.
func awaitFinal(ctx context.Context, c *http.Client, base, apiKey string, ids []string, workers int,
	every time.Duration, progress func(inFlight int)) (map[string]int, []time.Duration) {
	pending := make(map[string]bool, len(ids))
	for _, id := range ids {
		pending[id] = true
	}
	outcomes := map[string]int{}
	var took []time.Duration
	for len(pending) > 0 {
		batch := make([]string, 0, len(pending))
		for id := range pending {
			batch = append(batch, id)
		}
		for id, o := range fetchAll(ctx, c, base, apiKey, batch, workers) {
			if terminal[o.Status] {
				outcomes[o.Status]++
				if d := o.UpdatedAt.Sub(o.CreatedAt); d > 0 {
					took = append(took, d)
				}
				delete(pending, id)
			}
		}
		if len(pending) == 0 {
			break
		}
		if progress != nil {
			progress(len(pending))
		}
		select {
		case <-ctx.Done():
			outcomes["STILL_RUNNING"] += len(pending)
			return outcomes, took
		case <-time.After(every):
		}
	}
	return outcomes, took
}

func fetchAll(ctx context.Context, c *http.Client, base, apiKey string, ids []string, workers int) map[string]orderResponse {
	jobs := make(chan string)
	var mu sync.Mutex
	got := make(map[string]orderResponse, len(ids))
	var wg sync.WaitGroup
	for w := 0; w < workers; w++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for id := range jobs {
				if o, ok := fetchOrder(ctx, c, base, apiKey, id); ok {
					mu.Lock()
					got[id] = o
					mu.Unlock()
				}
			}
		}()
	}
	for _, id := range ids {
		jobs <- id
	}
	close(jobs)
	wg.Wait()
	return got
}

func fetchOrder(ctx context.Context, c *http.Client, base, apiKey, id string) (orderResponse, bool) {
	var o orderResponse
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, base+"/api/v1/orders/"+id, nil)
	if err != nil {
		return o, false
	}
	if apiKey != "" {
		req.Header.Set("X-API-Key", apiKey)
	}
	resp, err := c.Do(req)
	if err != nil {
		return o, false
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return o, false
	}
	return o, json.NewDecoder(resp.Body).Decode(&o) == nil
}

func pick(items []string) string { return strings.TrimSpace(items[randInt(len(items))]) }

func randInt(n int) int {
	v, err := rand.Int(rand.Reader, big.NewInt(int64(n)))
	if err != nil {
		return 0
	}
	return int(v.Int64())
}

func randomKey() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}
