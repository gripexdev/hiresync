import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError, Subject } from 'rxjs';
import { CvManagerComponent } from './cv-manager.component';
import { CvService, UploadProgressEvent } from '../../../core/services/cv.service';
import { CV } from '../../../core/models/cv.model';
import { Job } from '../../../core/models/job.model';

describe('CvManagerComponent', () => {
  let fixture: ComponentFixture<CvManagerComponent>;
  let component: CvManagerComponent;
  let svc: jasmine.SpyObj<CvService>;
  let snack: jasmine.SpyObj<MatSnackBar>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let router: jasmine.SpyObj<Router>;

  function cv(id: string, isActive = false): CV {
    return { id, fileName: `${id}.pdf`, atsScore: 70, isActive, optimizationCount: 0 } as CV;
  }

  function page(content: CV[], total = content.length) {
    return { content, totalElements: total, totalPages: 1, number: 0, size: 6, first: true, last: true };
  }

  beforeEach(() => {
    svc = jasmine.createSpyObj('CvService', [
      'getPage', 'getHistoryStats', 'upload', 'setActive', 'delete', 'optimize',
    ]);
    svc.getPage.and.returnValue(of(page([])));
    svc.getHistoryStats.and.returnValue(of({ total: 0, completed: 0, rejected: 0, failed: 0, avgGain: 0, bestScore: 0 }));

    snack = jasmine.createSpyObj('MatSnackBar', ['open']);
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    TestBed.configureTestingModule({
      imports: [CvManagerComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CvService, useValue: svc },
        { provide: MatSnackBar, useValue: snack },
        { provide: MatDialog, useValue: dialog },
      ],
    });
    // MatDialogModule declares `providers: [MatDialog]` on itself, which — because
    // CvManagerComponent imports it directly — creates a component-local injector
    // entry that shadows the TestBed-level override above. Re-override at the
    // component level so our spy actually wins.
    // Same shadowing issue applies to MatSnackBarModule -> MatSnackBar.
    TestBed.overrideComponent(CvManagerComponent, {
      add: { providers: [{ provide: MatDialog, useValue: dialog }, { provide: MatSnackBar, useValue: snack }] },
    });

    fixture = TestBed.createComponent(CvManagerComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    spyOn(router, 'navigate');
  });

  it('ngOnInit loads the first page of CVs and the history count', () => {
    svc.getPage.and.returnValue(of(page([cv('1', true)], 3)));
    svc.getHistoryStats.and.returnValue(of({ total: 7, completed: 5, rejected: 1, failed: 1, avgGain: 10, bestScore: 90 }));

    fixture.detectChanges();

    expect(component.cvs().map((c) => c.id)).toEqual(['1']);
    expect(component.cvTotal()).toBe(3);
    expect(component.historyCount()).toBe(7);
    expect(component.loading()).toBeFalse();
  });

  it('showMoreCvs appends the next page without resetting the first', () => {
    svc.getPage.and.returnValue(of(page([cv('1')], 2)));
    fixture.detectChanges();
    svc.getPage.and.returnValue(of(page([cv('2')], 2)));

    component.showMoreCvs();

    expect(svc.getPage).toHaveBeenCalledWith(1, 6);
    expect(component.cvs().map((c) => c.id)).toEqual(['1', '2']);
  });

  it('showMoreCvs is a no-op once all CVs are already shown', () => {
    svc.getPage.and.returnValue(of(page([cv('1')], 1)));
    fixture.detectChanges();
    svc.getPage.calls.reset();

    component.showMoreCvs();

    expect(svc.getPage).not.toHaveBeenCalled();
  });

  it('toggleExpand flips a CV card open/closed independently', () => {
    fixture.detectChanges();

    component.toggleExpand('1');
    expect(component.isExpanded('1')).toBeTrue();
    expect(component.isExpanded('2')).toBeFalse();

    component.toggleExpand('1');
    expect(component.isExpanded('1')).toBeFalse();
  });

  describe('upload', () => {
    beforeEach(() => fixture.detectChanges());

    function fileInputEvent(file: File): Event {
      const input = document.createElement('input');
      input.type = 'file';
      Object.defineProperty(input, 'files', { value: [file] });
      return { target: input } as unknown as Event;
    }

    it('rejects files over 10MB without calling the service', () => {
      const bigFile = new File([new Uint8Array(11 * 1024 * 1024)], 'big.pdf', { type: 'application/pdf' });

      component.onFileSelected(fileInputEvent(bigFile));

      expect(svc.upload).not.toHaveBeenCalled();
      expect(snack.open).toHaveBeenCalledWith('❌ Fichier trop volumineux (max 10 MB)', 'OK', { duration: 3500 });
    });

    it('progress events update uploadProgress', () => {
      const events = new Subject<UploadProgressEvent>();
      svc.upload.and.returnValue(events.asObservable());
      const file = new File(['x'], 'cv.pdf', { type: 'application/pdf' });

      component.onFileSelected(fileInputEvent(file));
      events.next({ type: 'progress', progress: 42 });

      expect(component.uploadProgress()).toBe(42);
      expect(component.uploading()).toBeTrue();
    });

    it('a "done" event reloads the list and shows a success snackbar', () => {
      const events = new Subject<UploadProgressEvent>();
      svc.upload.and.returnValue(events.asObservable());
      svc.getPage.and.returnValue(of(page([cv('new')], 1)));
      const file = new File(['x'], 'cv.pdf', { type: 'application/pdf' });

      component.onFileSelected(fileInputEvent(file));
      events.next({ type: 'done', cv: cv('new') });

      expect(component.uploading()).toBeFalse();
      expect(component.cvs().map((c) => c.id)).toEqual(['new']);
      expect(snack.open).toHaveBeenCalledWith('✅ CV uploadé — Score ATS initial calculé !', 'OK', { duration: 3500 });
    });

    it('an "error" event resets uploading state and shows the error', () => {
      const events = new Subject<UploadProgressEvent>();
      svc.upload.and.returnValue(events.asObservable());
      const file = new File(['x'], 'cv.pdf', { type: 'application/pdf' });

      component.onFileSelected(fileInputEvent(file));
      events.next({ type: 'error', error: 'Fichier corrompu' });

      expect(component.uploading()).toBeFalse();
      expect(snack.open).toHaveBeenCalledWith('❌ Fichier corrompu', 'OK', { duration: 4000 });
    });
  });

  describe('setActive', () => {
    it('does nothing if the CV is already active', () => {
      fixture.detectChanges();
      component.setActive(cv('1', true));
      expect(svc.setActive).not.toHaveBeenCalled();
    });

    it('marks the chosen CV active and every other one inactive', () => {
      svc.getPage.and.returnValue(of(page([cv('1', true), cv('2', false)], 2)));
      fixture.detectChanges();
      svc.setActive.and.returnValue(of(void 0));

      component.setActive(cv('2', false));

      expect(component.cvs().find((c) => c.id === '1')?.isActive).toBeFalse();
      expect(component.cvs().find((c) => c.id === '2')?.isActive).toBeTrue();
      expect(component.activating()).toBeNull();
    });
  });

  describe('deleteCv', () => {
    it('does nothing if the user cancels the confirm dialog', () => {
      fixture.detectChanges();
      spyOn(window, 'confirm').and.returnValue(false);

      component.deleteCv(cv('1'));

      expect(svc.delete).not.toHaveBeenCalled();
    });

    it('removes the CV and decrements the total on confirmation', () => {
      svc.getPage.and.returnValue(of(page([cv('1')], 1)));
      fixture.detectChanges();
      spyOn(window, 'confirm').and.returnValue(true);
      svc.delete.and.returnValue(of(void 0));

      component.deleteCv(cv('1'));

      expect(component.cvs().length).toBe(0);
      expect(component.cvTotal()).toBe(0);
    });
  });

  describe('triggerOptimization (via openOptimizeDialog)', () => {
    const job: Job = { id: 'job-1', title: 'Backend Dev', company: 'Acme', location: 'Casa', description: 'd', requirements: ['Java'] } as Job;

    beforeEach(() => fixture.detectChanges());

    it('navigates straight to the optimizer when one already exists for this job', () => {
      dialog.open.and.returnValue({ afterClosed: () => of(job) } as any);
      svc.optimize.and.returnValue(of({ optimizationId: 'opt-1', status: 'completed', message: '', alreadyOptimized: true }));

      component.openOptimizeDialog(cv('1'));

      expect(router.navigate).toHaveBeenCalledWith(['/cv/optimize', 'opt-1']);
    });

    it('navigates with query params when a new optimization is queued', () => {
      dialog.open.and.returnValue({ afterClosed: () => of(job) } as any);
      svc.optimize.and.returnValue(of({ optimizationId: 'opt-2', status: 'queued', message: '', alreadyOptimized: false }));

      component.openOptimizeDialog(cv('1'));

      expect(router.navigate).toHaveBeenCalledWith(['/cv/optimize', 'opt-2'], {
        queryParams: { cvId: '1', jobId: 'job-1', jobTitle: 'Backend Dev' },
      });
    });

    it('does nothing when the dialog is dismissed without a job', () => {
      dialog.open.and.returnValue({ afterClosed: () => of(null) } as any);

      component.openOptimizeDialog(cv('1'));

      expect(svc.optimize).not.toHaveBeenCalled();
    });

    it('shows an error snackbar when triggering the optimization fails', () => {
      dialog.open.and.returnValue({ afterClosed: () => of(job) } as any);
      svc.optimize.and.returnValue(throwError(() => new Error('500')));

      component.openOptimizeDialog(cv('1'));

      expect(snack.open).toHaveBeenCalledWith('❌ Impossible de lancer l\'optimisation', 'OK', { duration: 3500 });
    });
  });

  describe('display helpers', () => {
    it('scoreColor/scoreLabel bucket by threshold', () => {
      expect(component.scoreColor(85)).toBe('#10B981');
      expect(component.scoreLabel(85)).toBe('Excellent');
      expect(component.scoreColor(65)).toBe('#F59E0B');
      expect(component.scoreLabel(65)).toBe('Correct');
      expect(component.scoreColor(30)).toBe('#EF4444');
      expect(component.scoreLabel(30)).toBe('À améliorer');
    });

    it('formatSize renders KB below 1MB and MB above', () => {
      expect(component.formatSize(500)).toBe('0 KB');
      expect(component.formatSize(2 * 1024 * 1024)).toBe('2.0 MB');
      expect(component.formatSize(undefined)).toBe('');
    });
  });
});
