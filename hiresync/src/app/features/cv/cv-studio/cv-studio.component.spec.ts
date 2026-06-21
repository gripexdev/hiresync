import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';
import { CvStudioComponent } from './cv-studio.component';
import { CvService } from '../../../core/services/cv.service';
import { StructuredCv } from '../../../core/models/studio.model';

describe('CvStudioComponent', () => {
  let fixture: ComponentFixture<CvStudioComponent>;
  let component: CvStudioComponent;
  let svc: jasmine.SpyObj<CvService>;
  let snack: jasmine.SpyObj<MatSnackBar>;

  function structuredCv(overrides: Partial<StructuredCv> = {}): StructuredCv {
    return {
      fullName: 'Othmane Sadiky', jobTitle: 'Développeur Backend',
      contact: { email: 'o@example.com' }, summary: 'Résumé',
      experience: [], education: [], skills: [], ...overrides,
    } as StructuredCv;
  }

  beforeEach(() => {
    svc = jasmine.createSpyObj('CvService', ['getStructuredCv', 'renderPdf']);
    svc.getStructuredCv.and.returnValue(of(structuredCv()));
    snack = jasmine.createSpyObj('MatSnackBar', ['open']);

    TestBed.configureTestingModule({
      imports: [CvStudioComponent],
      providers: [
        provideRouter([]),
        { provide: CvService, useValue: svc },
        { provide: MatSnackBar, useValue: snack },
      ],
    });
    // MatSnackBarModule declares `providers: [MatSnackBar]` on itself; since
    // CvStudioComponent imports it directly, that creates a component-local
    // injector entry that shadows the TestBed-level override above.
    TestBed.overrideComponent(CvStudioComponent, { add: { providers: [{ provide: MatSnackBar, useValue: snack }] } });

    fixture = TestBed.createComponent(CvStudioComponent);
    component = fixture.componentInstance;
    component.id = 'opt-1';
  });

  it('ngOnInit loads the structured CV and defaults missing arrays/contact to safe values', () => {
    svc.getStructuredCv.and.returnValue(of({
      fullName: 'Othmane', jobTitle: 'Dev', summary: 's',
    } as StructuredCv));

    fixture.detectChanges();

    expect(component.loading()).toBeFalse();
    expect(component.cv()?.experience).toEqual([]);
    expect(component.cv()?.education).toEqual([]);
    expect(component.cv()?.skills).toEqual([]);
    expect(component.cv()?.contact).toEqual({});
  });

  it('a failed load stops the spinner and shows an error', () => {
    svc.getStructuredCv.and.returnValue(throwError(() => new Error('404')));

    fixture.detectChanges();

    expect(component.loading()).toBeFalse();
    expect(snack.open).toHaveBeenCalledWith('❌ Impossible de charger le CV', 'OK', { duration: 3500 });
  });

  it('html() re-renders deterministically when the template/options change', () => {
    fixture.detectChanges();
    const initial = component.html();

    component.setTemplate('classic');

    expect(component.html()).not.toBe(initial);
    expect(component.html()).toContain('Othmane Sadiky');
  });

  it('setAccent/setFont/togglePhoto update the options signal', () => {
    fixture.detectChanges();

    component.setAccent('#E11D48');
    expect(component.options().accentColor).toBe('#E11D48');

    component.setFont("'Roboto', sans-serif");
    expect(component.options().fontFamily).toBe("'Roboto', sans-serif");

    component.togglePhoto();
    expect(component.options().showPhoto).toBeFalse();
    component.togglePhoto();
    expect(component.options().showPhoto).toBeTrue();
  });

  it('onAccentInput / onFontChange read the DOM event value', () => {
    fixture.detectChanges();
    const colorInput = document.createElement('input');
    colorInput.value = '#8B5CF6';

    component.onAccentInput({ target: colorInput } as unknown as Event);

    expect(component.options().accentColor).toBe('#8B5CF6');
  });

  describe('onPhotoSelected', () => {
    function fileEvent(file: File): Event {
      const input = document.createElement('input');
      input.type = 'file';
      Object.defineProperty(input, 'files', { value: [file] });
      return { target: input } as unknown as Event;
    }

    it('rejects non-image files', () => {
      fixture.detectChanges();
      const file = new File(['x'], 'cv.pdf', { type: 'application/pdf' });

      component.onPhotoSelected(fileEvent(file));

      expect(snack.open).toHaveBeenCalledWith('❌ Choisissez une image', 'OK', { duration: 3000 });
    });

    it('rejects images over 3MB', () => {
      fixture.detectChanges();
      const file = new File([new Uint8Array(4 * 1024 * 1024)], 'photo.png', { type: 'image/png' });

      component.onPhotoSelected(fileEvent(file));

      expect(snack.open).toHaveBeenCalledWith('❌ Image trop lourde (max 3 MB)', 'OK', { duration: 3000 });
    });

    it('accepts a valid image and embeds it as a data URI, forcing showPhoto on', async () => {
      fixture.detectChanges();
      component.togglePhoto(); // showPhoto -> false
      const file = new File(['fake-image-bytes'], 'photo.png', { type: 'image/png' });

      component.onPhotoSelected(fileEvent(file));
      // FileReader.readAsDataURL is real async browser I/O — not a zone-tracked
      // timer/macrotask, so neither fixture.whenStable() nor fakeAsync's virtual
      // clock will wait for it. Poll instead of guessing a fixed delay, which
      // was flaky on slower CI runners.
      for (let i = 0; i < 50 && !component.cv()?.photo; i++) {
        await new Promise((r) => setTimeout(r, 20));
      }

      expect(component.cv()?.photo).toContain('data:');
      expect(component.options().showPhoto).toBeTrue();
    });
  });

  it('removePhoto clears the photo field without touching other CV data', () => {
    fixture.detectChanges();
    component.cv.update((c) => (c ? { ...c, photo: 'data:image/png;base64,xyz' } : c));

    component.removePhoto();

    expect(component.cv()?.photo).toBeUndefined();
    expect(component.cv()?.fullName).toBe('Othmane Sadiky');
  });

  describe('download', () => {
    it('does nothing if the CV has not loaded yet', () => {
      component.download();
      expect(svc.renderPdf).not.toHaveBeenCalled();
    });

    it('success triggers the download and resets the downloading flag', () => {
      fixture.detectChanges();
      svc.renderPdf.and.returnValue(of(new Blob(['%PDF'])));

      component.download();

      expect(component.downloading()).toBeFalse();
      expect(snack.open).toHaveBeenCalledWith('✅ CV téléchargé en PDF', 'OK', { duration: 3000 });
    });

    it('failure shows an error and resets the downloading flag', () => {
      fixture.detectChanges();
      svc.renderPdf.and.returnValue(throwError(() => new Error('500')));

      component.download();

      expect(component.downloading()).toBeFalse();
      expect(snack.open).toHaveBeenCalledWith('❌ Erreur de génération du PDF', 'OK', { duration: 3500 });
    });
  });
});
