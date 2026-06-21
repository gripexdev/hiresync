import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { NotificationsComponent } from './notifications.component';
import { NotificationService } from '../../core/services/notification.service';
import { Notification } from '../../core/models/notification.model';

describe('NotificationsComponent', () => {
  let fixture: ComponentFixture<NotificationsComponent>;
  let component: NotificationsComponent;
  let svc: jasmine.SpyObj<NotificationService>;

  function notif(id: string, read: boolean): Notification {
    return { id, type: 'cv_optimized', title: 'CV optimisé', message: 'msg',
      read, createdAt: new Date().toISOString() } as Notification;
  }

  beforeEach(() => {
    svc = jasmine.createSpyObj('NotificationService', ['getAll', 'markRead', 'markAllRead']);
    svc.getAll.and.returnValue(of([notif('1', false), notif('2', true)]));

    TestBed.configureTestingModule({
      imports: [NotificationsComponent],
      providers: [provideRouter([]), { provide: NotificationService, useValue: svc }],
    });

    fixture = TestBed.createComponent(NotificationsComponent);
    component = fixture.componentInstance;
  });

  it('ngOnInit loads notifications and stops loading', () => {
    fixture.detectChanges();

    expect(component.notifications().length).toBe(2);
    expect(component.loading()).toBeFalse();
  });

  it('unreadCount reflects only the unread notifications', () => {
    fixture.detectChanges();

    expect(component.unreadCount).toBe(1);
  });

  it('markRead marks a single notification as read locally after the server call', () => {
    fixture.detectChanges();
    svc.markRead.and.returnValue(of(void 0));

    component.markRead(notif('1', false));

    expect(svc.markRead).toHaveBeenCalledWith('1');
    expect(component.notifications().find((n) => n.id === '1')?.read).toBeTrue();
    expect(component.unreadCount).toBe(0);
  });

  it('markRead does nothing for an already-read notification', () => {
    fixture.detectChanges();

    component.markRead(notif('2', true));

    expect(svc.markRead).not.toHaveBeenCalled();
  });

  it('markAllRead flips every notification to read', () => {
    fixture.detectChanges();
    svc.markAllRead.and.returnValue(of(void 0));

    component.markAllRead();

    expect(component.unreadCount).toBe(0);
    expect(component.notifications().every((n) => n.read)).toBeTrue();
  });

  it('timeAgo formats minutes, hours, and days correctly', () => {
    const now = Date.now();
    expect(component.timeAgo(new Date(now - 5 * 60000).toISOString())).toBe('Il y a 5 min');
    expect(component.timeAgo(new Date(now - 3 * 3600000).toISOString())).toBe('Il y a 3h');
    expect(component.timeAgo(new Date(now - 2 * 86400000).toISOString())).toBe('Il y a 2 j');
  });
});
