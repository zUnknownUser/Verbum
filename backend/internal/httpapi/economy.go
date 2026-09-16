package httpapi

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
	"time"
	"verbum/backend/internal/ask"
	"verbum/backend/internal/domain"
	"verbum/backend/internal/store"
	"verbum/backend/internal/tts"
	"verbum/backend/internal/usage"
)

type EconomicOptions struct {
	Usage          *usage.Service
	SpeechVerifier func(context.Context, tts.Request) (tts.Request, error)
	AskModel       string
	VoiceRelay     http.Handler
	VoiceTickets   realtimeBroker
}

func (s EconomicOptions) WrapAsk(next asker) asker {
	if next == nil {
		return nil
	}
	return economicAsk{next, s.Usage, s.AskModel}
}
func (s EconomicOptions) WrapEmbeddings(next queryEmbedder) queryEmbedder {
	if next == nil {
		return nil
	}
	return economicEmbedder{next, s.Usage}
}
func (s EconomicOptions) WrapSpeech(next TextToSpeech) TextToSpeech {
	if next == nil {
		return nil
	}
	return economicSpeech{next, s.Usage, s.SpeechVerifier}
}

type economicEmbedder struct {
	next  queryEmbedder
	usage *usage.Service
}

func (e economicEmbedder) Embed(ctx context.Context, q string) ([]float32, error) {
	b, err := e.usage.Do(ctx, usage.Operation{Kind: "embedding", Key: usage.Hash(usage.Identity(ctx).UID, q), Estimate: 1000, TTL: 24 * time.Hour}, func(ctx context.Context) ([]byte, error) {
		v, err := e.next.Embed(ctx, q)
		if err != nil {
			return nil, err
		}
		return json.Marshal(v)
	})
	if err != nil {
		return nil, err
	}
	var v []float32
	err = json.Unmarshal(b, &v)
	return v, err
}

type economicAsk struct {
	next  asker
	usage *usage.Service
	model string
}

func (e economicAsk) Ask(ctx context.Context, q string) (domain.AskResponse, error) {
	ref, _ := json.Marshal(ask.SelectedPassages(ctx))
	key := usage.Hash(usage.Identity(ctx).UID, q, store.Language(ctx), string(ref), e.model)
	if err := e.usage.Bind(ctx, idempotency(ctx), key); err != nil {
		return domain.AskResponse{}, err
	}
	b, err := e.usage.Do(ctx, usage.Operation{Kind: "ask", Key: key, Estimate: 10_000, TTL: time.Hour}, func(ctx context.Context) ([]byte, error) {
		v, err := e.next.Ask(ctx, q)
		if err != nil {
			return nil, err
		}
		return json.Marshal(v)
	})
	if err != nil {
		return domain.AskResponse{}, err
	}
	var v domain.AskResponse
	err = json.Unmarshal(b, &v)
	return v, err
}

type economicSpeech struct {
	next   TextToSpeech
	usage  *usage.Service
	verify func(context.Context, tts.Request) (tts.Request, error)
}

func (e economicSpeech) run(ctx context.Context, input tts.Request, timed bool) (tts.TimedAudio, error) {
	var empty tts.TimedAudio
	if e.usage == nil || e.verify == nil {
		return empty, usage.Unavailable()
	}
	var err error
	input, err = e.verify(ctx, input)
	if err != nil {
		return empty, err
	}
	input, err = tts.Prepare(input)
	if err != nil {
		return empty, err
	}
	raw, _ := json.Marshal(input)
	key := usage.Hash(string(raw), strconv.FormatBool(timed))
	if input.IsGemini() {
		key = usage.Hash(tts.EconomicIdentity(input), strconv.FormatBool(timed))
	}
	if err = e.usage.Bind(ctx, idempotency(ctx), usage.Hash("tts", key)); err != nil {
		return empty, err
	}
	if cached, ok := e.next.(interface {
		Cached(tts.Request, bool) (tts.TimedAudio, bool)
	}); ok {
		if v, found := cached.Cached(input, timed); found {
			return v, nil
		}
	}
	if input.IsGemini() && timed && e.usage.Store != nil {
		legacyKey := usage.Hash("cost-v1", "tts", usage.Hash(tts.LegacyEconomicIdentity(input), strconv.FormatBool(timed)))
		if b, lookupErr := e.usage.Store.Cached(ctx, legacyKey, e.usage.Now()); lookupErr != nil {
			return empty, usage.Unavailable()
		} else if b != nil {
			var cached tts.TimedAudio
			if json.Unmarshal(b, &cached) == nil && len(cached.Audio) > 0 {
				return cached, nil
			}
		}
	}
	cost := tts.EstimateCost(input, timed)
	data, err := e.usage.Do(ctx, usage.Operation{Kind: "tts", Key: key, Estimate: cost, Permanent: true}, func(ctx context.Context) ([]byte, error) {
		usage.Attempt(ctx)
		var result tts.TimedAudio
		if timed {
			n, ok := e.next.(interface {
				SynthesizeTimed(context.Context, tts.Request) (tts.TimedAudio, error)
			})
			if !ok {
				return nil, tts.ErrUnavailable
			}
			result, err = n.SynthesizeTimed(ctx, input)
		} else {
			result.Audio, err = e.next.Synthesize(ctx, input)
		}
		if err != nil {
			return nil, err
		}
		if !input.IsGemini() {
			usage.Record(ctx, cost)
		}
		return json.Marshal(result)
	})
	if err != nil {
		return empty, err
	}
	err = json.Unmarshal(data, &empty)
	return empty, err
}
func (e economicSpeech) Synthesize(ctx context.Context, r tts.Request) ([]byte, error) {
	v, err := e.run(ctx, r, false)
	return v.Audio, err
}
func (e economicSpeech) SynthesizeTimed(ctx context.Context, r tts.Request) (tts.TimedAudio, error) {
	return e.run(ctx, r, true)
}

type idempotencyKey struct{}

func idempotency(ctx context.Context) string { v, _ := ctx.Value(idempotencyKey{}).(string); return v }
func writeUsageProblem(w http.ResponseWriter, err error) bool {
	var denial *usage.Denial
	if !errors.As(err, &denial) {
		return false
	}
	status := http.StatusTooManyRequests
	switch denial.Code {
	case "usage_unavailable", "budget_exhausted":
		status = 503
	case "plan_required":
		status = 403
	case "idempotency_conflict":
		status = 409
	}
	w.Header().Set("Cache-Control", "no-store")
	if !denial.RetryAt.IsZero() {
		w.Header().Set("Retry-After", strconv.Itoa(max(1, int(time.Until(denial.RetryAt).Seconds()))))
	}
	w.Header().Set("Content-Type", "application/problem+json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(struct {
		Code     string     `json:"code"`
		Message  string     `json:"message"`
		RetryAt  *time.Time `json:"retryAt,omitempty"`
		Fallback string     `json:"fallback"`
	}{denial.Code, "This operation is temporarily limited.", retryDate(denial.RetryAt), "scripture_search"})
	return true
}
func retryDate(t time.Time) *time.Time {
	if t.IsZero() {
		return nil
	}
	return &t
}
