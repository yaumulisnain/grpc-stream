# Android Example (Kotlin) with Nginx gRPC Proxy

This sample app is a native Kotlin Android client for `proto/pubsub.proto`.
It connects to Nginx at `10.0.2.2:8082` (Android emulator -> host machine).

## 1) Start backend and Nginx proxy

Use the Go server from this repo and add an Nginx proxy that forwards gRPC:

```nginx
events {}

http {
  upstream grpc_backend {
    server host.docker.internal:50051;
  }

  server {
    listen 8082 http2;

    location / {
      grpc_pass grpc://grpc_backend;
      grpc_set_header Host $host;
      grpc_read_timeout 3600s;
      grpc_send_timeout 3600s;
    }
  }
}
```

Run example (host network assumptions may vary by OS):

```bash
docker run --rm -p 8082:8082 -v $(pwd)/nginx.conf:/etc/nginx/nginx.conf:ro nginx:alpine
```

## 2) Build APK

Open `android-example` in Android Studio and run:

```bash
./gradlew assembleDebug
```

The output APK is:

`app/build/outputs/apk/debug/app-debug.apk`

## 3) Notes

- In app, open **Settings** to set gRPC server host/port.
- gRPC target is configurable in `gradle.properties`:
  - `grpcHost=10.0.2.2`
  - `grpcPort=8082`
- Emulator uses `10.0.2.2` for host access.
- For a real device, set `grpcHost` to your machine LAN IP.
- This app uses the repository's shared proto via `app/build.gradle.kts` source set:
  copied to `app/src/main/proto/pubsub.proto`.
