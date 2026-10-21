.PHONY: run run-fixtures test vet fmt build docker migrate seed

run:      ## serve configured PostgreSQL content on :8080
	go run ./cmd/api

run-fixtures: ## explicitly opt into development fixtures
	VERBUM_ENV=development VERBUM_CONTENT_MODE=fixtures go run ./cmd/api

test:     ## unit + contract tests (api/examples must match)
	go test ./...

vet:
	go vet ./... && test -z "$$(gofmt -l .)"

fmt:
	gofmt -w .

build:    ## static binary at bin/api
	CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o bin/api ./cmd/api

docker:   ## image tagged verbum-api (run from repo root context)
	cd .. && docker build -f backend/Dockerfile -t verbum-api .

migrate:  ## apply db/migrations to VERBUM_DATABASE_URL (idempotent)
	go run ./cmd/migrate

seed:     ## load db/seed/fixtures.json into an empty, migrated database
	go run ./cmd/seed
