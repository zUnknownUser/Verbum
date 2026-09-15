# Build with Go; Debian runtime includes FFmpeg for continuous chapter MP3s.
# Runs as nonroot. Build from the repo root:
#   docker build -f backend/Dockerfile -t verbum-api .
FROM golang:1.24 AS build
WORKDIR /src
COPY backend/go.mod backend/go.sum ./
RUN go mod download
COPY backend/ ./
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/api ./cmd/api
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/migrate ./cmd/migrate

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates ffmpeg \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /var/cache/verbum/tts && chown -R 65532:65532 /var/cache/verbum
USER 65532:65532
WORKDIR /app
COPY --from=build /out/api /app/api
COPY --from=build /out/migrate /app/migrate
COPY backend/db/migrations /app/db/migrations
COPY backend/db/seed/fixtures.json /app/db/seed/fixtures.json
ENV VERBUM_ADDR=:8080
ENV VERBUM_TTS_CACHE_DIR=/var/cache/verbum/tts
EXPOSE 8080
CMD ["/app/api"]
