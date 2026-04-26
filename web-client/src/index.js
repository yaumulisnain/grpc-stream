import './styles.css';
import { PubSubClient } from './generated/pubsub_grpc_web_pb';
import { SubscribeRequest, PublishRequest } from './generated/pubsub_pb';

const ENVOY_URL = 'http://localhost:8081';
const client = new PubSubClient(ENVOY_URL, null, null);

let activeStream = null;

const subscribeTopic = document.getElementById('subscribe-topic');
const subscribeBtn = document.getElementById('subscribe-btn');
const unsubscribeBtn = document.getElementById('unsubscribe-btn');
const publishTopic = document.getElementById('publish-topic');
const publishData = document.getElementById('publish-data');
const publishBtn = document.getElementById('publish-btn');
const statusEl = document.getElementById('status');
const eventsEl = document.getElementById('events');
const clearBtn = document.getElementById('clear-btn');

function setStatus(connected, text) {
  statusEl.textContent = text;
  statusEl.className = 'status ' + (connected ? 'connected' : 'disconnected');
}

function addEvent(event) {
  const item = document.createElement('div');
  item.className = 'event-item';

  const time = new Date(event.getTimestamp()).toLocaleTimeString();

  item.innerHTML = `
    <div class="event-meta">
      <span class="event-topic">#${event.getTopic()}</span>
      <span class="event-time">${time}</span>
    </div>
    <div class="event-data">${event.getData()}</div>
    <div class="event-id">${event.getId()}</div>
  `;

  eventsEl.prepend(item);

  const empty = eventsEl.querySelector('.empty-state');
  if (empty) empty.remove();
}

function subscribe() {
  const topic = subscribeTopic.value.trim();
  if (!topic) return;

  if (activeStream) {
    activeStream.cancel();
  }

  const request = new SubscribeRequest();
  request.setTopic(topic);

  activeStream = client.subscribe(request, {});

  activeStream.on('data', (event) => {
    addEvent(event);
  });

  activeStream.on('status', (status) => {
    console.log('Stream status:', status);
  });

  activeStream.on('error', (err) => {
    console.error('Stream error:', err);
    setStatus(false, `Error: ${err.message}`);
    subscribeBtn.disabled = false;
    unsubscribeBtn.disabled = true;
  });

  activeStream.on('end', () => {
    setStatus(false, 'Stream ended');
    subscribeBtn.disabled = false;
    unsubscribeBtn.disabled = true;
    activeStream = null;
  });

  setStatus(true, `Subscribed to "${topic}"`);
  subscribeBtn.disabled = true;
  unsubscribeBtn.disabled = false;
}

function unsubscribe() {
  if (activeStream) {
    activeStream.cancel();
    activeStream = null;
  }
  setStatus(false, 'Disconnected');
  subscribeBtn.disabled = false;
  unsubscribeBtn.disabled = true;
}

function publish() {
  const topic = publishTopic.value.trim();
  const data = publishData.value.trim();
  if (!topic || !data) return;

  const request = new PublishRequest();
  request.setTopic(topic);
  request.setData(data);

  client.publish(request, {}, (err, response) => {
    if (err) {
      console.error('Publish error:', err);
      return;
    }
    console.log('Published:', response.getId());
    publishData.value = '';
  });
}

subscribeBtn.addEventListener('click', subscribe);
unsubscribeBtn.addEventListener('click', unsubscribe);
publishBtn.addEventListener('click', publish);
clearBtn.addEventListener('click', () => {
  eventsEl.innerHTML = '<div class="empty-state">No events yet. Subscribe to a topic and publish messages.</div>';
});

publishData.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') publish();
});

eventsEl.innerHTML = '<div class="empty-state">No events yet. Subscribe to a topic and publish messages.</div>';
