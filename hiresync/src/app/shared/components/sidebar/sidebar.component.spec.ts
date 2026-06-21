import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { SidebarComponent } from './sidebar.component';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificationService } from '../../../core/services/notification.service';

describe('SidebarComponent', () => {
  let fixture: ComponentFixture<SidebarComponent>;
  let component: SidebarComponent;
  let auth: jasmine.SpyObj<AuthService>;

  function withUrl(url: string) {
    return jasmine.createSpyObj('Router', ['navigate'], { url });
  }

  function setUp(url: string): void {
    auth = jasmine.createSpyObj('AuthService', ['logout'], { user: () => null });
    const notifsSpy = jasmine.createSpyObj('NotificationService', [], { unreadCount: () => 2 });

    TestBed.configureTestingModule({
      imports: [SidebarComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: NotificationService, useValue: notifsSpy },
        { provide: Router, useValue: withUrl(url) },
      ],
    });

    fixture = TestBed.createComponent(SidebarComponent);
    component = fixture.componentInstance;
  }

  it('exposes the 6 expected navigation entries', () => {
    setUp('/dashboard');
    expect(component.navItems.length).toBe(6);
    expect(component.navItems.map((n) => n.route)).toEqual([
      '/dashboard', '/jobs', '/cv', '/cv/history', '/applications', '/notifications',
    ]);
  });

  it('an exact route only matches itself, not its sub-routes', () => {
    setUp('/cv/history');
    expect(component.isActive('/cv', true)).toBeFalse();
    expect(component.isActive('/cv/history', true)).toBeTrue();
  });

  it('a non-exact route matches any sub-path (e.g. job detail under /jobs)', () => {
    setUp('/jobs/abc-123');
    expect(component.isActive('/jobs', false)).toBeTrue();
  });

  it('query params are stripped before matching', () => {
    setUp('/dashboard?ref=email');
    expect(component.isActive('/dashboard', true)).toBeTrue();
  });

  it('logout() delegates to AuthService', () => {
    setUp('/dashboard');
    component.logout();
    expect(auth.logout).toHaveBeenCalled();
  });
});
