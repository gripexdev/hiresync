import { TestBed } from '@angular/core/testing';
import { WebSocketService, WsMessage } from './websocket.service';
import { AuthService } from '../auth/auth.service';
import { CVOptimizationWsEvent } from '../models/cv.model';

describe('WebSocketService', () => {
  let service: WebSocketService;

  beforeEach(() => {
    const authServiceSpy = jasmine.createSpyObj('AuthService', [], { token: () => 'stub-jwt' });

    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: authServiceSpy }],
    });
    service = TestBed.inject(WebSocketService);
  });

  // The internal `msgs$` Subject is private — this is the standard Angular
  // testing pattern for exercising an RxJS pipeline without opening a real
  // STOMP/WebSocket connection (which connect() would otherwise require).
  function emit(message: WsMessage): void {
    (service as unknown as { msgs$: { next: (m: WsMessage) => void } }).msgs$.next(message);
  }

  it('cvOptimization$ only emits messages for the cv-optimization topic', () => {
    const received: CVOptimizationWsEvent[] = [];
    service.cvOptimization$.subscribe((e) => received.push(e));

    emit({ destination: '/user/topic/notifications', payload: { type: 'irrelevant' } });
    emit({ destination: '/user/topic/cv-optimization', payload: { optimizationId: 'opt1', status: 'completed' } as any });

    expect(received.length).toBe(1);
    expect((received[0] as any).optimizationId).toBe('opt1');
  });

  it('subscribeTo() filters the shared message stream down to the requested destination', () => {
    const received: unknown[] = [];
    service.subscribeTo<{ count: number }>('/user/topic/notifications').subscribe((p) => received.push(p));

    emit({ destination: '/user/topic/cv-optimization', payload: { status: 'completed' } });
    emit({ destination: '/user/topic/notifications', payload: { count: 3 } });

    expect(received).toEqual([{ count: 3 }]);
  });

  it('disconnect() before connect() is a safe no-op', () => {
    expect(() => service.disconnect()).not.toThrow();
  });

  it('ngOnDestroy() completes the shared message stream', () => {
    let completed = false;
    service.cvOptimization$.subscribe({ complete: () => (completed = true) });

    service.ngOnDestroy();

    expect(completed).toBeTrue();
  });

  it('multiple subscribeTo() calls for different destinations stay isolated', () => {
    const a: unknown[] = [];
    const b: unknown[] = [];
    service.subscribeTo('/topic/a').subscribe((p) => a.push(p));
    service.subscribeTo('/topic/b').subscribe((p) => b.push(p));

    emit({ destination: '/topic/a', payload: 'for-a' });
    emit({ destination: '/topic/b', payload: 'for-b' });

    expect(a).toEqual(['for-a']);
    expect(b).toEqual(['for-b']);
  });
});
