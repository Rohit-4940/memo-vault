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
    const obs = this.messageSubject.pipe(
      filter(msg => msg.topic === topic),
      map(msg => msg.body)
    );
    if (this.connected) {
      this.doSubscribe(topic);
    } else {
      this.client.onConnect = () => {
        this.connected = true;
        this.doSubscribe(topic);
      };
    }
    return obs;
  }

  private doSubscribe(topic: string) {
    const sub = this.client.subscribe(topic, (message: IMessage) => {
      try {
        const body = JSON.parse(message.body);
        this.messageSubject.next({ topic, body });
      } catch { }
    });
    this.subscriptions.push({ topic, sub });
  }

  unsubscribe(topic: string) {
    const idx = this.subscriptions.findIndex(s => s.topic === topic);
    if (idx !== -1) {
      this.subscriptions[idx].sub.unsubscribe();
      this.subscriptions.splice(idx, 1);
    }
  }

  ngOnDestroy() {
    this.subscriptions.forEach(s => s.sub.unsubscribe());
    this.subscriptions = [];
    if (this.client) this.client.deactivate();
  }
}
