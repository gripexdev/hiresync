import { Injectable, OnDestroy, inject } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Subject, Observable, filter, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { CVOptimizationWsEvent } from '../models/cv.model';

export interface WsMessage<T = unknown> {
  destination: string;
  payload: T;
}

/**
 * WebSocket service — STOMP over SockJS.
 *
 * Backend wiring (Spring Boot):
 *   @EnableWebSocketMessageBroker
 *   registry.addEndpoint("/ws/notifications").withSockJS()
 *   registry.enableSimpleBroker("/topic", "/user")
 *
 * Topics used:
 *   /user/topic/cv-optimization   — CV Optimizer Service pushes completion events
 *   /topic/notifications          — global broadcast (future)
 */
@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private auth   = inject(AuthService);
  private client?: Client;
  private subs   = new Map<string, StompSubscription>();
  private msgs$  = new Subject<WsMessage>();

  // ── Typed streams ────────────────────────────────────────────────────────────
  /** CV optimization progress/completion events from the backend */
  readonly cvOptimization$: Observable<CVOptimizationWsEvent> = this.msgs$.pipe(
    filter(m => m.destination === '/user/topic/cv-optimization'),
    map(m => m.payload as CVOptimizationWsEvent),
  );

  // ── Connection ────────────────────────────────────────────────────────────────
  connect(): void {
    if (this.client?.connected) return;

    this.client = new Client({
      // SockJS transport — matches Spring Boot endpoint
      webSocketFactory: () => new SockJS(`${environment.wsUrl}/notifications`) as WebSocket,

      // Attach JWT so the backend's HandshakeInterceptor can authenticate
      connectHeaders: {
        Authorization: `Bearer ${this.auth.token() ?? ''}`,
      },

      // Auto-reconnect every 5s on disconnect
      reconnectDelay: 5000,

      onConnect: () => {
        this._subscribe('/user/topic/cv-optimization');
        this._subscribe('/user/topic/notifications');
      },

      onDisconnect: () => {
        this.subs.clear();
      },

      onStompError: (frame) => {
        console.error('[WS] STOMP error:', frame.headers['message']);
      },
    });

    this.client.activate();
  }

  disconnect(): void {
    this.subs.forEach(s => s.unsubscribe());
    this.subs.clear();
    this.client?.deactivate();
  }

  /** Subscribe to an additional destination at runtime */
  subscribeTo<T>(destination: string): Observable<T> {
    this._subscribe(destination);
    return this.msgs$.pipe(
      filter(m => m.destination === destination),
      map(m => m.payload as T),
    );
  }

  // ── Internal ──────────────────────────────────────────────────────────────────
  private _subscribe(dest: string): void {
    if (this.subs.has(dest) || !this.client?.connected) return;
    const sub = this.client.subscribe(dest, (msg: IMessage) => {
      try {
        this.msgs$.next({ destination: dest, payload: JSON.parse(msg.body) });
      } catch {
        this.msgs$.next({ destination: dest, payload: msg.body });
      }
    });
    this.subs.set(dest, sub);
  }

  ngOnDestroy(): void {
    this.disconnect();
    this.msgs$.complete();
  }
}
