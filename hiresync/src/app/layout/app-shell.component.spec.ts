import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, NavigationStart, provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { AppShellComponent } from './app-shell.component';
import { AuthService } from '../core/auth/auth.service';
import { NotificationService } from '../core/services/notification.service';

describe('AppShellComponent', () => {
  let fixture: ComponentFixture<AppShellComponent>;
  let component: AppShellComponent;
  let routerEvents: Subject<unknown>;

  beforeEach(() => {
    routerEvents = new Subject();
    const authSpy = jasmine.createSpyObj('AuthService', ['logout'], { user: () => null });
    const notifsSpy = jasmine.createSpyObj('NotificationService', [], { unreadCount: () => 0 });

    TestBed.configureTestingModule({
      imports: [AppShellComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authSpy },
        { provide: NotificationService, useValue: notifsSpy },
      ],
    });

    const router = TestBed.inject(Router);
    Object.defineProperty(router, 'events', { get: () => routerEvents.asObservable(), configurable: true });
    Object.defineProperty(router, 'url', { value: '/dashboard', configurable: true });

    fixture = TestBed.createComponent(AppShellComponent);
    component = fixture.componentInstance;
  });

  it('starts with the sidebar expanded and the mobile nav closed', () => {
    expect(component.sidebarCollapsed()).toBeFalse();
    expect(component.mobileNavOpen()).toBeFalse();
  });

  it('a route navigation closes the mobile nav drawer', () => {
    fixture.detectChanges();
    component.mobileNavOpen.set(true);

    routerEvents.next(new NavigationStart(1, '/jobs'));

    expect(component.mobileNavOpen()).toBeFalse();
  });

  it('non-navigation router events are ignored', () => {
    fixture.detectChanges();
    component.mobileNavOpen.set(true);

    routerEvents.next({ type: 'SomeOtherEvent' });

    expect(component.mobileNavOpen()).toBeTrue();
  });
});
