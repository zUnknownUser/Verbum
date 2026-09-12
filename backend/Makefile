.PHONY: run test vet fmt build docker

run:      ## serve the fixtures on :8080
	go run ./cmd/api

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
