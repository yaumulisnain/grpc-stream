# gRPC Stream — PubSub Example

A complete example of **real-time pub/sub messaging** using gRPC server-side streaming. The Go server pushes events to subscribed clients as they are published — similar to a pub/sub system, but delivered over gRPC streams.

## Architecture

```
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│  Web Client  │──────▶│    Envoy     │──────▶│  Go Server   │
│  (grpc-web)  │◀──────│   (proxy)    │◀──────│  (gRPC)      │
└──────────────┘       └──────────────┘       └──────────────┘
     :8080                  :8081                  :50051
```

- **Go Server** — gRPC server with `Subscribe` (server-streaming) and `Publish` (unary) RPCs
- **Envoy Proxy** — Translates grpc-web requests from browsers into native gRPC (HTTP/2)
- **Web Client** — JavaScript browser app using `grpc-web` to subscribe and publish

## Proto Definition

```protobuf
service PubSub {
  rpc Subscribe(SubscribeRequest) returns (stream Event);
  rpc Publish(PublishRequest) returns (PublishResponse);
}

message SubscribeRequest {
  string topic = 1;
}

message Event {
  string id = 1;
  string topic = 2;
  string data = 3;
  int64 timestamp = 4;
}

message PublishRequest {
  string topic = 1;
  string data = 2;
}

message PublishResponse {
  string id = 1;
  bool success = 2;
}
```

## Quick Start (Docker Compose)

```bash
docker compose up --build
```

This starts all three services:

| Service    | URL                    |
| ---------- | ---------------------- |
| Web Client | http://127.0.0.1:8080   |
| Envoy      | http://127.0.0.1:8081   |
| gRPC Server| grpc://127.0.0.1:50051  |

Open the web client, subscribe to a topic (e.g. `news`), then publish messages to see them appear in real time.

## Manual Setup

### 1. Run the Go Server

```bash
cd server
go run main.go
# gRPC server listening on :50051
```

### 2. Run Envoy Proxy

```bash
docker run --rm -p 8081:8081 \
  -v $(pwd)/envoy/envoy.yaml:/etc/envoy/envoy.yaml:ro \
  --network host \
  envoyproxy/envoy:v1.31-latest
```

### 3. Run the Web Client

```bash
cd web-client
npm install
npm run dev
# Open http://127.0.0.1:8080
```

## How It Works

1. **Subscribe** — The client opens a server-streaming RPC (`Subscribe`) with a topic name. The server keeps this stream open.
2. **Publish** — Any client (web, mobile, CLI) calls the unary `Publish` RPC with a topic and data.
3. **Fan-out** — The server iterates over all subscribers for that topic and sends the event through their open streams.
4. **Receive** — Subscribed clients receive the event in real time through the open stream.

---

## Kotlin Android Client

### Setup

Add to your `build.gradle.kts` (app-level):

```kotlin
plugins {
    id("com.google.protobuf") version "0.9.4"
}

dependencies {
    implementation("io.grpc:grpc-okhttp:1.63.0")
    implementation("io.grpc:grpc-stub:1.63.0")
    implementation("io.grpc:grpc-protobuf-lite:1.63.0")
    implementation("com.google.protobuf:protobuf-kotlin-lite:4.26.1")
    implementation("io.grpc:grpc-kotlin-stub:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.26.1"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.63.0"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.1:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") { option("lite") }
                create("kotlin") { option("lite") }
            }
            task.plugins {
                create("grpc") { option("lite") }
                create("grpckt") { option("lite") }
            }
        }
    }
}
```

Place `pubsub.proto` in `app/src/main/proto/`.

### Subscribe to a Topic (Kotlin Coroutines)

```kotlin
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import pubsub.PubSubGrpcKt
import pubsub.Pubsub

class PubSubRepository {

    private val channel = ManagedChannelBuilder
        .forAddress("YOUR_SERVER_IP", 50051)
        .usePlaintext()
        .build()

    private val stub = PubSubGrpcKt.PubSubCoroutineStub(channel)

    /**
     * Subscribe to a topic. Events are emitted as a Flow.
     */
    fun subscribe(topic: String) = stub.subscribe(
        Pubsub.SubscribeRequest.newBuilder()
            .setTopic(topic)
            .build()
    )

    /**
     * Publish a message to a topic.
     */
    suspend fun publish(topic: String, data: String): Pubsub.PublishResponse {
        return stub.publish(
            Pubsub.PublishRequest.newBuilder()
                .setTopic(topic)
                .setData(data)
                .build()
        )
    }

    fun shutdown() {
        channel.shutdown()
    }
}
```

### Usage in a ViewModel

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EventViewModel : ViewModel() {

    private val repo = PubSubRepository()

    private val _events = MutableStateFlow<List<Pubsub.Event>>(emptyList())
    val events = _events.asStateFlow()

    fun subscribeTo(topic: String) {
        viewModelScope.launch {
            repo.subscribe(topic).collect { event ->
                _events.value = listOf(event) + _events.value
            }
        }
    }

    fun publish(topic: String, message: String) {
        viewModelScope.launch {
            repo.publish(topic, message)
        }
    }

    override fun onCleared() {
        repo.shutdown()
        super.onCleared()
    }
}
```

### Usage in Jetpack Compose

```kotlin
@Composable
fun PubSubScreen(viewModel: EventViewModel = viewModel()) {
    val events by viewModel.events.collectAsState()
    var message by remember { mutableStateOf("") }
    val topic = "news"

    LaunchedEffect(Unit) {
        viewModel.subscribeTo(topic)
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Row {
            TextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message") }
            )
            Button(onClick = {
                viewModel.publish(topic, message)
                message = ""
            }) {
                Text("Send")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(events) { event ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("#${event.topic}", style = MaterialTheme.typography.labelSmall)
                        Text(event.data, style = MaterialTheme.typography.bodyLarge)
                        Text(event.id, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}
```

---

## Swift iOS Client

### Setup with Swift Package Manager

Add the `grpc-swift` package to your Xcode project:

1. In Xcode, go to **File → Add Package Dependencies**
2. Add: `https://github.com/grpc/grpc-swift.git` (version `1.23.0` or later)
3. Add: `https://github.com/grpc/grpc-swift-protobuf.git`
4. Add: `https://github.com/apple/swift-protobuf.git` (version `1.28.0` or later)

### Generate Swift Code

Install the protoc plugins:

```bash
brew install swift-protobuf grpc-swift
```

Generate:

```bash
protoc --swift_out=./Generated \
       --grpc-swift_out=./Generated \
       -I./proto \
       ./proto/pubsub.proto
```

This produces `pubsub.pb.swift` and `pubsub.grpc.swift`.

### Subscribe to a Topic

```swift
import GRPC
import NIOCore
import NIOPosix

class PubSubService: ObservableObject {

    @Published var events: [Pubsub_Event] = []

    private let group = PlatformSupport.makeEventLoopGroup(loopCount: 1)
    private var channel: GRPCChannel?
    private var client: Pubsub_PubSubNIOClient?
    private var call: ServerStreamingCall<Pubsub_SubscribeRequest, Pubsub_Event>?

    init() {
        do {
            channel = try GRPCChannelPool.with(
                target: .host("YOUR_SERVER_IP", port: 50051),
                transportSecurity: .plaintext,
                eventLoopGroup: group
            )
            client = Pubsub_PubSubNIOClient(channel: channel!)
        } catch {
            print("Failed to create channel: \(error)")
        }
    }

    func subscribe(to topic: String) {
        var request = Pubsub_SubscribeRequest()
        request.topic = topic

        call = client?.subscribe(request) { [weak self] event in
            DispatchQueue.main.async {
                self?.events.insert(event, at: 0)
            }
        }

        call?.status.whenComplete { result in
            print("Stream ended: \(result)")
        }
    }

    func publish(topic: String, data: String) {
        var request = Pubsub_PublishRequest()
        request.topic = topic
        request.data = data

        client?.publish(request).response.whenComplete { result in
            switch result {
            case .success(let response):
                print("Published: \(response.id)")
            case .failure(let error):
                print("Publish error: \(error)")
            }
        }
    }

    func unsubscribe() {
        call?.cancel(promise: nil)
        call = nil
    }

    deinit {
        try? channel?.close().wait()
        try? group.syncShutdownGracefully()
    }
}
```

### Usage in SwiftUI

```swift
import SwiftUI

struct PubSubView: View {
    @StateObject private var service = PubSubService()
    @State private var message = ""
    let topic = "news"

    var body: some View {
        VStack {
            HStack {
                TextField("Type a message", text: $message)
                    .textFieldStyle(.roundedBorder)
                Button("Send") {
                    service.publish(topic: topic, data: message)
                    message = ""
                }
            }
            .padding()

            List(service.events, id: \.id) { event in
                VStack(alignment: .leading, spacing: 4) {
                    Text("#\(event.topic)")
                        .font(.caption)
                        .foregroundColor(.blue)
                    Text(event.data)
                        .font(.body)
                    Text(event.id)
                        .font(.caption2)
                        .foregroundColor(.gray)
                }
                .padding(.vertical, 4)
            }
        }
        .onAppear {
            service.subscribe(to: topic)
        }
        .onDisappear {
            service.unsubscribe()
        }
    }
}
```

### Using grpc-swift v2 (Swift 6 / Structured Concurrency)

If you're targeting Swift 6 with the newer `grpc-swift-nio-transport` and `grpc-swift-protobuf` packages, the API uses `AsyncSequence`:

```swift
import GRPCNIOTransportHTTP2

class PubSubServiceV2: ObservableObject {

    @Published var events: [Pubsub_Event] = []

    func subscribe(to topic: String) async throws {
        let transport = try HTTP2ClientTransport.Posix(
            target: .ipv4(host: "YOUR_SERVER_IP", port: 50051)
        )
        let client = GRPCClient(transport: transport)

        try await withGRPCClient(transport: transport) { grpcClient in
            let pubsub = Pubsub_PubSubClient(wrapping: grpcClient)
            var request = Pubsub_SubscribeRequest()
            request.topic = topic

            for try await event in pubsub.subscribe(request) {
                await MainActor.run {
                    self.events.insert(event, at: 0)
                }
            }
        }
    }
}
```

---

## Testing with grpcurl

You can also test the server directly using `grpcurl`:

```bash
# Subscribe (streams events until cancelled)
grpcurl -plaintext -d '{"topic": "news"}' 127.0.0.1:50051 pubsub.PubSub/Subscribe

# Publish (in another terminal)
grpcurl -plaintext -d '{"topic": "news", "data": "Hello World!"}' 127.0.0.1:50051 pubsub.PubSub/Publish
```

## Project Structure

```
.
├── proto/
│   └── pubsub.proto              # Protobuf service definition
├── server/
│   ├── main.go                   # Go gRPC server
│   ├── proto/                    # Generated Go protobuf code
│   ├── go.mod
│   └── Dockerfile
├── web-client/
│   ├── src/
│   │   ├── index.js              # JavaScript client app
│   │   ├── index.html
│   │   ├── styles.css
│   │   └── generated/            # Generated grpc-web stubs
│   ├── webpack.config.js
│   ├── package.json
│   └── Dockerfile
├── envoy/
│   └── envoy.yaml                # Envoy proxy config (grpc-web ↔ gRPC)
└── docker-compose.yml
```

## License

MIT
