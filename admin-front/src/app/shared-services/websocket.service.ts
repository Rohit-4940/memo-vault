import { Injectable, OnDestroy } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Observable, Subject, filter, map } from 'rxjs';
import { environment } from '../../environments/environment';
import { LocalStorageUtil } from '../@core/utils/local-storage-util';

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private client!: Client;
  private connected = false;
  private messageSubject = new Subject<{ topic: string; body: any }>();
  private subscriptions: { topic: string; sub: any }[] = [];
  /**
   * Every topic the app currently wants to receive. Kept separate from the live subscriptions
   * so they can be re-established after a reconnect and de-duplicated when a component
   * subscribes to the same topic twice.
   */
  private desiredTopics = new Set<string>();

  constructor() {
    const token = LocalStorageUtil.getStorage()?.at?.accessToken;
    if (!token) return;
    this.client = new Client({
      webSocketFactory: () => new SockJS(`${environment.baseApi}/api/v1/ws`),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.connected = true;
        // A reconnect invalidates the previous broker-side subscriptions.
        this.subscriptions = [];
        this.desiredTopics.forEach(topic => this.doSubscribe(topic));
      },
      onDisconnect: () => {
        this.connected = false;
      },
      onStompError: (frame) => {
        console.error('STOMP error', frame.headers['message']);
      }
    });
    this.client.activate();
  }

  subscribe(topic: string): Observable<any> {
    this.desiredTopics.add(topic);
    if (this.connected) {
      this.doSubscribe(topic);
    }
    return this.messageSubject.pipe(
      filter(msg => msg.topic === topic),
      map(msg => msg.body)
    );
  }

  private doSubscribe(topic: string) {
    if (this.subscriptions.some(s => s.topic === topic)) {
      return;
    }
    const sub = this.client.subscribe(topic, (message: IMessage) => {
      try {
        const body = JSON.parse(message.body);
        this.messageSubject.next({ topic, body });
      } catch { }
    });
    this.subscriptions.push({ topic, sub });
  }

  unsubscribe(topic: string) {
    this.desiredTopics.delete(topic);
    const idx = this.subscriptions.findIndex(s => s.topic === topic);
    if (idx !== -1) {
      this.subscriptions[idx].sub.unsubscribe();
      this.subscriptions.splice(idx, 1);
    }
  }

  ngOnDestroy() {
    this.subscriptions.forEach(s => s.sub.unsubscribe());
    this.subscriptions = [];
    this.desiredTopics.clear();
    if (this.client) this.client.deactivate();
  }
}
