import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { NotificationService } from './notification.service';
import { environment } from '../../../environments/environment';
import { Notification } from '../models/notification.model';

describe('NotificationService', () => {
  let service: NotificationService;
  let httpMock: HttpTestingController;
  const base = `${environment.apiUrl}/notifications`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(NotificationService);
    httpMock = TestBed.inject(HttpTestingController);

    // The constructor eagerly calls refreshUnread() — every test must drain it.
    httpMock.expectOne(`${base}/unread-count`).flush({ count: 0 });
  });

  afterEach(() => httpMock.verify());

  it('seeds unreadCount from the constructor refresh', () => {
    expect(service.unreadCount()).toBe(0);
  });

  it('refreshUnread() updates the unreadCount signal from the server', () => {
    service.refreshUnread();
    httpMock.expectOne(`${base}/unread-count`).flush({ count: 7 });

    expect(service.unreadCount()).toBe(7);
  });

  it('refreshUnread() silently ignores errors rather than throwing', () => {
    expect(() => {
      service.refreshUnread();
      httpMock.expectOne(`${base}/unread-count`).flush('boom', { status: 500, statusText: 'Error' });
    }).not.toThrow();
  });

  it('getPage() requests the given page/size and returns the raw Page envelope', () => {
    let received: Notification[] = [];
    service.getPage(2, 10).subscribe((page) => (received = page.content));

    const req = httpMock.expectOne((r) => r.url === base && r.params.get('page') === '2' && r.params.get('size') === '10');
    req.flush({ content: [{ id: '1' } as Notification], totalElements: 1, totalPages: 1, number: 2, size: 10 });

    expect(received.length).toBe(1);
  });

  it('getAll() fetches the first 50 and refreshes the unread badge afterwards', () => {
    let received: Notification[] = [];
    service.getAll().subscribe((n) => (received = n));

    const listReq = httpMock.expectOne((r) => r.url === base && r.params.get('size') === '50');
    listReq.flush({ content: [{ id: 'a' } as Notification], totalElements: 1, totalPages: 1, number: 0, size: 50 });

    httpMock.expectOne(`${base}/unread-count`).flush({ count: 4 });

    expect(received.length).toBe(1);
    expect(service.unreadCount()).toBe(4);
  });

  it('markRead() decrements unreadCount but never below zero', () => {
    service.refreshUnread();
    httpMock.expectOne(`${base}/unread-count`).flush({ count: 1 });
    expect(service.unreadCount()).toBe(1);

    service.markRead('notif-1').subscribe();
    httpMock.expectOne(`${base}/notif-1/read`).flush(null);
    expect(service.unreadCount()).toBe(0);

    service.markRead('notif-2').subscribe();
    httpMock.expectOne(`${base}/notif-2/read`).flush(null);
    expect(service.unreadCount()).toBe(0); // never goes negative
  });

  it('markAllRead() resets unreadCount to zero', () => {
    service.refreshUnread();
    httpMock.expectOne(`${base}/unread-count`).flush({ count: 9 });

    service.markAllRead().subscribe();
    httpMock.expectOne(`${base}/read-all`).flush(null);

    expect(service.unreadCount()).toBe(0);
  });
});
