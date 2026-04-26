package main

import (
	"context"
	"fmt"
	"log"
	"net"
	"sync"
	"time"

	"github.com/google/uuid"
	pb "github.com/example/grpc-stream/server/proto"
	"google.golang.org/grpc"
	"google.golang.org/grpc/reflection"
)

type subscriber struct {
	stream pb.PubSub_SubscribeServer
	topic  string
}

type pubSubServer struct {
	pb.UnimplementedPubSubServer
	mu          sync.RWMutex
	subscribers map[string][]*subscriber
}

func newPubSubServer() *pubSubServer {
	return &pubSubServer{
		subscribers: make(map[string][]*subscriber),
	}
}

func (s *pubSubServer) Subscribe(req *pb.SubscribeRequest, stream pb.PubSub_SubscribeServer) error {
	sub := &subscriber{
		stream: stream,
		topic:  req.Topic,
	}

	s.mu.Lock()
	s.subscribers[req.Topic] = append(s.subscribers[req.Topic], sub)
	s.mu.Unlock()

	log.Printf("New subscriber for topic: %s (total: %d)", req.Topic, len(s.subscribers[req.Topic]))

	// Keep the stream open until the client disconnects
	<-stream.Context().Done()

	// Remove subscriber on disconnect
	s.mu.Lock()
	subs := s.subscribers[req.Topic]
	for i, existing := range subs {
		if existing == sub {
			s.subscribers[req.Topic] = append(subs[:i], subs[i+1:]...)
			break
		}
	}
	s.mu.Unlock()

	log.Printf("Subscriber disconnected from topic: %s", req.Topic)
	return nil
}

func (s *pubSubServer) Publish(ctx context.Context, req *pb.PublishRequest) (*pb.PublishResponse, error) {
	eventID := uuid.New().String()
	event := &pb.Event{
		Id:        eventID,
		Topic:     req.Topic,
		Data:      req.Data,
		Timestamp: time.Now().UnixMilli(),
	}

	s.mu.RLock()
	subs := s.subscribers[req.Topic]
	s.mu.RUnlock()

	successCount := 0
	for _, sub := range subs {
		if err := sub.stream.Send(event); err != nil {
			log.Printf("Failed to send to subscriber: %v", err)
		} else {
			successCount++
		}
	}

	log.Printf("Published event %s to topic '%s' (%d/%d subscribers received)",
		eventID, req.Topic, successCount, len(subs))

	return &pb.PublishResponse{
		Id:      eventID,
		Success: true,
	}, nil
}

func main() {
	port := 50051
	lis, err := net.Listen("tcp", fmt.Sprintf(":%d", port))
	if err != nil {
		log.Fatalf("Failed to listen: %v", err)
	}

	grpcServer := grpc.NewServer()
	pb.RegisterPubSubServer(grpcServer, newPubSubServer())

	// Enable reflection for debugging tools like grpcurl
	reflection.Register(grpcServer)

	log.Printf("gRPC server listening on :%d", port)
	if err := grpcServer.Serve(lis); err != nil {
		log.Fatalf("Failed to serve: %v", err)
	}
}
