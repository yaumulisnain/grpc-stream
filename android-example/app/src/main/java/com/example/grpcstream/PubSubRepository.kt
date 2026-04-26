package com.example.grpcstream

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.flow.Flow
import pubsub.PubSubGrpcKt
import pubsub.Pubsub

class PubSubRepository(
    host: String = "10.0.2.2",
    port: Int = 8082
) {
    private val channel: ManagedChannel = ManagedChannelBuilder
        .forAddress(host, port)
        .usePlaintext()
        .build()

    private val stub = PubSubGrpcKt.PubSubCoroutineStub(channel)

    fun subscribe(topic: String): Flow<Pubsub.Event> {
        val request = Pubsub.SubscribeRequest.newBuilder()
            .setTopic(topic)
            .build()
        return stub.subscribe(request)
    }

    suspend fun publish(topic: String, data: String): Pubsub.PublishResponse {
        val request = Pubsub.PublishRequest.newBuilder()
            .setTopic(topic)
            .setData(data)
            .build()
        return stub.publish(request)
    }

    fun close() {
        channel.shutdownNow()
    }
}
