import { Component, ElementRef, EventEmitter, Output, ViewChild, AfterViewInit, OnDestroy } from '@angular/core';
import { environment } from '../../../../environments/environment';

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize(config: { client_id: string; callback: (response: { credential: string }) => void }): void;
          renderButton(parent: HTMLElement, options: Record<string, unknown>): void;
        };
      };
    };
  }
}

/**
 * Renders Google's official "Sign in with Google" button via Google Identity
 * Services (the `gsi/client` script tag loaded in index.html). Emits the raw
 * ID token on (credential) — the parent hands it to AuthService.googleAuth(),
 * which covers both sign-in and first-time sign-up in one call.
 *
 * The GIS script loads `async defer`, so `window.google` may not exist yet
 * when this component mounts — poll briefly until it does.
 */
@Component({
  selector: 'app-google-signin-button',
  standalone: true,
  template: `<div #btnContainer></div>`,
})
export class GoogleSigninButtonComponent implements AfterViewInit, OnDestroy {
  @ViewChild('btnContainer', { static: true }) btnContainer!: ElementRef<HTMLDivElement>;
  @Output() credential = new EventEmitter<string>();

  private pollHandle?: ReturnType<typeof setTimeout>;
  private attempts = 0;
  private readonly maxAttempts = 50; // ~5s at 100ms

  ngAfterViewInit(): void {
    this.waitForGoogle();
  }

  ngOnDestroy(): void {
    if (this.pollHandle) clearTimeout(this.pollHandle);
  }

  private waitForGoogle(): void {
    if (window.google?.accounts?.id) {
      this.render();
      return;
    }
    if (++this.attempts >= this.maxAttempts) {
      console.warn('Google Identity Services script failed to load — Google sign-in unavailable.');
      return;
    }
    this.pollHandle = setTimeout(() => this.waitForGoogle(), 100);
  }

  private render(): void {
    window.google!.accounts.id.initialize({
      client_id: environment.googleClientId,
      callback: (response) => this.credential.emit(response.credential),
    });
    window.google!.accounts.id.renderButton(this.btnContainer.nativeElement, {
      theme: 'outline',
      size: 'large',
      width: 340,
      locale: 'fr',
    });
  }
}
