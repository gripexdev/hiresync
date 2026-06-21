import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TopbarComponent } from './topbar.component';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificationService } from '../../../core/services/notification.service';

describe('TopbarComponent', () => {
  let fixture: ComponentFixture<TopbarComponent>;
  let component: TopbarComponent;

  beforeEach(() => {
    const authSpy = jasmine.createSpyObj('AuthService', ['logout'], {
      user: () => ({ fullName: 'Othmane Sadiky', avatarUrl: undefined }),
    });
    const notifsSpy = jasmine.createSpyObj('NotificationService', [], { unreadCount: () => 3 });

    TestBed.configureTestingModule({
      imports: [TopbarComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authSpy },
        { provide: NotificationService, useValue: notifsSpy },
      ],
    });

    fixture = TestBed.createComponent(TopbarComponent);
    component = fixture.componentInstance;
  });

  it('creates successfully with default inputs', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
    expect(component.title).toBe('');
    expect(component.showMenuToggle).toBeFalse();
  });

  it('exposes the unread notification count from the service for the badge', () => {
    fixture.detectChanges();
    expect(component.notifs.unreadCount()).toBe(3);
  });

  it('menuToggle emits when triggered', () => {
    fixture.detectChanges();
    let emitted = false;
    component.menuToggle.subscribe(() => (emitted = true));

    component.menuToggle.emit();

    expect(emitted).toBeTrue();
  });
});
