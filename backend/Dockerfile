# Two stages: build with the Go toolchain, run on a distroless base (no shell,
# ~20 MB). Build from the repo root so the seed file is in context:
#   docker build -f backend/Dockerfile -t verbum-api .
FROM golang:1.24 AS build
WORKDIR /src
COPY backend/go.mod backend/go.sum ./
RUN go mod download
COPY backend/ ./
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/api ./cmd/api

FROM gcr.io/distroless/static-debian12:nonroot
WORKDIR /app
COPY --from=build /out/api /app/api
COPY backend/db/seed/fixtures.json /app/db/seed/fixtures.json
ENV VERBUM_ADDR=:8080
EXPOSE 8080
ENTRYPOINT ["/app/api"]
