import { Injectable, OnDestroy, inject } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { Subject, Observable, filter, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { CVOptimizationWsEvent } from '../models/cv.model';

export interface WsMessage<T = unknown> {
  destination: string;
  payload: T;
}

/**
 * WebSocket service — STOMP over native WebSocket (no SockJS).
 *
 * SockJS was removed because sockjs-client uses Node.js's `global` which
 * doesn't exist in the browser and crashes Vite's dependency pre-bundler.
 * Modern browsers (Chrome, Firefox, Edge, Safari) all support native WebSocket.
 *
 * Backend wiring (Spring Boot WebSocketConfig):
 *   registry.addEndpoint("/ws/notifications").setAllowedOriginPatterns("*")   ← native WS
 *   registry.addEndpoint("/ws/notifications").withSockJS()                     ← SockJS (optional)
 *
 * Native WebSocket URL format: ws://host/path  (not http://)
 */
@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private auth   = inject(AuthService);
  private client?: Client;
  private subs   = new Map<string, StompSubscription>();
  private msgs$  = new Subject<WsMessage>();

  // ── Typed streams ─────────────────────────────────────────────────────────────
  readonly cvOptimization$: Observable<CVOptimizationWsEvent> = this.msgs$.pipe(
    filter(m => m.destination === '/user/topic/cv-optimization'),
    map(m => m.payload as CVOptimizationWsEvent),
  );

  // ── Connection ────────────────────────────────────────────────────────────────
  connect(): void {
    if (this.client?.connected) return;
    try {
      // Convert http:// base URL to ws:// for native WebSocket
      const wsUrl = environment.wsUrl
        .replace(/^https:\/\//, 'wss://')
        .replace(/^http:\/\//, 'ws://');

      this.client = new Client({
        // Native WebSocket — no SockJS dependency needed
        brokerURL: `${wsUrl}/notifications`,

        connectHeaders: {
          Authorization: `Bearer ${this.auth.token() ?? ''}`,
        },

        reconnectDelay: 5000,

        onConnect: () => {
          this._subscribe('/user/topic/cv-optimization');
          this._subscribe('/user/topic/notifications');
        },

        onDisconnect: () => this.subs.clear(),

        onStompError: frame =>
          console.warn('[WS] STOMP error:', frame.headers['message']),
      });

      this.client.activate();
    } catch (e) {
      console.warn('[WS] WebSocket not available:', e);
    }
  }

  disconnect(): void {
    this.subs.forEach(s => s.unsubscribe());
    this.subs.clear();
    this.client?.deactivate();
  }

  subscribeTo<T>(destination: string): Observable<T> {
    this._subscribe(destination);
    return this.msgs$.pipe(
      filter(m => m.destination === destination),
      map(m => m.payload as T),
    );
  }

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
