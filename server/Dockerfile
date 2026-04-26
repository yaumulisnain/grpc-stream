FROM golang:1.24-alpine AS builder

WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -o /grpc-server .

FROM alpine:3.20
RUN apk add --no-cache ca-certificates
COPY --from=builder /grpc-server /grpc-server
EXPOSE 50051
CMD ["/grpc-server"]
