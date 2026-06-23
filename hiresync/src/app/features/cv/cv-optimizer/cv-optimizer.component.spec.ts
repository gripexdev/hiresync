import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CvOptimizerComponent } from './cv-optimizer.component';
import { CvService } from '../../../core/services/cv.service';
import { ApplicationService } from '../../../core/services/application.service';
import { WebSocketService } from '../../../core/services/websocket.service';
import { CVOptimizationResult, CVOptimizationWsEvent } from '../../../core/models/cv.model';

describe('CvOptimizerComponent', () => {
  let fixture: ComponentFixture<CvOptimizerComponent>;
  let component: CvOptimizerComponent;
  let cvSvc: jasmine.SpyObj<CvService>;
  let appSvc: jasmine.SpyObj<ApplicationService>;
  let wsSvc: jasmine.SpyObj<WebSocketService>;
  let snack: jasmine.SpyObj<MatSnackBar>;
  let wsEvents: Subject<CVOptimizationWsEvent>;
  let pollEvents: Subject<CVOptimizationResult>;

  function setUpComponent(queryParams: Record<string, string>): void {
    wsEvents = new Subject<CVOptimizationWsEvent>();
    pollEvents = new Subject<CVOptimizationResult>();

    cvSvc = jasmine.createSpyObj('CvService', [
      'getOptimizationResult', 'pollUntilDone', 'boostKeywords',
      'generateCoverLetter', 'downloadOptimizedCv',
    ]);
    cvSvc.pollUntilDone.and.returnValue(pollEvents.asObservable());

    appSvc = jasmine.createSpyObj('ApplicationService', ['markApplied', 'checkApplied']);
    appSvc.checkApplied.and.returnValue(of({ applied: false }));

    wsSvc = jasmine.createSpyObj('WebSocketService', ['connect'], { cvOptimization$: wsEvents.asObservable() });
    snack = jasmine.createSpyObj('MatSnackBar', ['open']);

    TestBed.configureTestingModule({
      imports: [CvOptimizerComponent],
      providers: [
        provideRouter([]),
        { provide: CvService, useValue: cvSvc },
        { provide: ApplicationService, useValue: appSvc },
        { provide: WebSocketService, useValue: wsSvc },
        { provide: MatSnackBar, useValue: snack },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParams } } },
      ],
    });
    // MatSnackBarModule declares `providers: [MatSnackBar]` on itself; since
    // CvOptimizerComponent imports it directly, that creates a component-local
    // injector entry that shadows the TestBed-level override above.
    TestBed.overrideComponent(CvOptimizerComponent, { add: { providers: [{ provide: MatSnackBar, useValue: snack }] } });

    fixture = TestBed.createComponent(CvOptimizerComponent);
    component = fixture.componentInstance;
    component.id = 'opt-1';
  }

  function result(overrides: Partial<CVOptimizationResult> = {}): CVOptimizationResult {
    return { id: 'opt-1', status: 'completed', cvId: 'cv-1', jobId: '11111111-1111-1111-1111-111111111111',
      jobTitle: 'Dev', originalScore: 60, optimizedScore: 85, matchedKeywords: [], missingKeywords: [],
      suggestedChanges: [], ...overrides } as CVOptimizationResult;
  }

  describe('starting a fresh optimization (cvId query param present)', () => {
    beforeEach(() => {
      setUpComponent({ cvId: 'cv-1', jobId: 'job-1', jobTitle: 'Backend Dev' });
      // ngOnInit always checks real status first (_loadExistingResult) — a
      // brand-new optimization is still queued/processing server-side, so
      // that check itself falls through to _startProcessing().
      cvSvc.getOptimizationResult.and.returnValue(of(result({ status: 'queued' })));
    });

    it('connects the WebSocket and starts polling', () => {
      fixture.detectChanges();

      expect(wsSvc.connect).toHaveBeenCalled();
      expect(cvSvc.pollUntilDone).toHaveBeenCalledWith('opt-1', jasmine.anything());
      expect(component.status()).toBe('processing');
    });

    it('a matching WS "completed" event loads the final result', () => {
      fixture.detectChanges();
      cvSvc.getOptimizationResult.and.returnValue(of(result()));

      wsEvents.next({ optimizationId: 'opt-1', status: 'completed', provider: 'Groq' } as CVOptimizationWsEvent);

      expect(component.status()).toBe('completed');
      expect(component.activeProvider()).toBe('Groq');
      expect(component.result()?.optimizedScore).toBe(85);
    });

    it('a WS event for a different optimizationId is ignored', () => {
      fixture.detectChanges();

      wsEvents.next({ optimizationId: 'someone-elses-job', status: 'completed' } as CVOptimizationWsEvent);

      expect(component.status()).toBe('processing');
      expect(cvSvc.getOptimizationResult).toHaveBeenCalledTimes(1);
    });

    it('a WS "rejected" event fetches and shows the rejection verdict', () => {
      fixture.detectChanges();
      cvSvc.getOptimizationResult.and.returnValue(of(result({ status: 'rejected', rejectionReason: 'Profil incompatible' })));

      wsEvents.next({ optimizationId: 'opt-1', status: 'rejected' } as CVOptimizationWsEvent);

      expect(component.status()).toBe('rejected');
      expect(component.result()?.rejectionReason).toBe('Profil incompatible');
    });

    it('a WS "failed" event marks the status failed without an extra fetch', () => {
      fixture.detectChanges();

      wsEvents.next({ optimizationId: 'opt-1', status: 'failed' } as CVOptimizationWsEvent);

      expect(component.status()).toBe('failed');
      expect(cvSvc.getOptimizationResult).toHaveBeenCalledTimes(1);
    });

    it('the polling fallback also resolves a completed result if WS never fires', () => {
      fixture.detectChanges();
      cvSvc.getOptimizationResult.and.returnValue(of(result()));

      pollEvents.next(result());

      expect(component.status()).toBe('completed');
    });
  });

  describe('viewing an existing result from history (no cvId query param)', () => {
    beforeEach(() => setUpComponent({}));

    it('completed result populates state and checks applied status', () => {
      cvSvc.getOptimizationResult.and.returnValue(of(result()));
      appSvc.checkApplied.and.returnValue(of({ applied: true }));

      fixture.detectChanges();

      expect(component.status()).toBe('completed');
      expect(component.jobTitle()).toBe('Dev');
      expect(component.applied()).toBeTrue();
      expect(wsSvc.connect).not.toHaveBeenCalled();
    });

    it('rejected result shows the rejection state', () => {
      cvSvc.getOptimizationResult.and.returnValue(of(result({ status: 'rejected' })));

      fixture.detectChanges();

      expect(component.status()).toBe('rejected');
    });

    it('still-processing result joins the live flow (connects WS)', () => {
      cvSvc.getOptimizationResult.and.returnValue(of(result({ status: 'processing' })));

      fixture.detectChanges();

      expect(wsSvc.connect).toHaveBeenCalled();
    });

    it('a failed fetch marks the status failed', () => {
      cvSvc.getOptimizationResult.and.returnValue(throwError(() => new Error('500')));

      fixture.detectChanges();

      expect(component.status()).toBe('failed');
    });
  });

  describe('keyword boosting', () => {
    beforeEach(() => setUpComponent({}));

    it('toggleKeyword adds/removes from the selected set', () => {
      cvSvc.getOptimizationResult.and.returnValue(of(result({ status: 'processing' })));
      fixture.detectChanges();

      component.toggleKeyword('Docker');
      expect(component.isSelected('Docker')).toBeTrue();
      component.toggleKeyword('Docker');
      expect(component.isSelected('Docker')).toBeFalse();
    });

    it('applyKeywords does nothing when no keyword is selected', () => {
      cvSvc.getOptimizationResult.and.returnValue(of(result({ status: 'processing' })));
      fixture.detectChanges();

      component.applyKeywords();

      expect(cvSvc.boostKeywords).not.toHaveBeenCalled();
    });

    it('applyKeywords success updates the result and clears the selection', () => {
      cvSvc.getOptimizationResult.and.returnValue(of(result({ status: 'processing' })));
      fixture.detectChanges();
      component.toggleKeyword('Docker');
      cvSvc.boostKeywords.and.returnValue(of(result({ optimizedScore: 92 })));

      component.applyKeywords();

      expect(component.boosting()).toBeFalse();
      expect(component.result()?.optimizedScore).toBe(92);
      expect(component.selectedMissing().size).toBe(0);
      expect(snack.open).toHaveBeenCalled();
    });

    it('applyKeywords on a 401 shows a session-expired message', () => {
      cvSvc.getOptimizationResult.and.returnValue(of(result({ status: 'processing' })));
      fixture.detectChanges();
      component.toggleKeyword('Docker');
      cvSvc.boostKeywords.and.returnValue(throwError(() => new HttpErrorResponse({ status: 401 })));

      component.applyKeywords();

      expect(snack.open).toHaveBeenCalledWith(
        'Session expirée — reconnectez-vous puis réessayez.', 'OK', { duration: 5000 });
    });
  });

  describe('cover letter generation', () => {
    beforeEach(() => setUpComponent({}));

    it('success stores the letter', () => {
      cvSvc.getOptimizationResult.and.returnValue(of(result({ status: 'processing' })));
      fixture.detectChanges();
      cvSvc.generateCoverLetter.and.returnValue(of({ subject: 'Candidature', body: 'Bonjour', provider: 'Groq' }));

      component.generateLetter();

      expect(component.coverLetter()?.subject).toBe('Candidature');
      expect(component.generatingLetter()).toBeFalse();
    });

    it('409 conflict shows the server-provided message', () => {
      cvSvc.getOptimizationResult.and.returnValue(of(result({ status: 'processing' })));
      fixture.detectChanges();
      cvSvc.generateCoverLetter.and.returnValue(throwError(() =>
        new HttpErrorResponse({ status: 409, error: { message: 'Pas encore terminé.' } })));

      component.generateLetter();

      expect(component.letterError()).toBe('Pas encore terminé.');
    });

    it('does nothing if already generating', () => {
      cvSvc.getOptimizationResult.and.returnValue(of(result({ status: 'processing' })));
      fixture.detectChanges();
      component.generatingLetter.set(true);

      component.generateLetter();

      expect(cvSvc.generateCoverLetter).not.toHaveBeenCalled();
    });
  });

  describe('icon/color/label canonicalisation fallback', () => {
    beforeEach(() => setUpComponent({}));

    it('known type returns its direct mapping', () => {
      cvSvc.getOptimizationResult.and.returnValue(of(result({ status: 'processing' })));
      fixture.detectChanges();
      expect(component.changeIcon('keyword_added')).toBe('add_circle');
    });

    it('unknown type falls back via canonicalization, e.g. "contact_info_added" -> format bucket', () => {
      cvSvc.getOptimizationResult.and.returnValue(of(result({ status: 'processing' })));
      fixture.detectChanges();
      expect(component.changeColor('contact_info_added')).toBe('#8B5CF6');
      expect(component.changeLabel('contact_info_added')).toBe('Format amélioré');
    });

    it('totally unrecognised type falls back to the neutral default', () => {
      cvSvc.getOptimizationResult.and.returnValue(of(result({ status: 'processing' })));
      fixture.detectChanges();
      expect(component.changeIcon('xyz')).toBe('auto_fix_high');
    });
  });

  describe('download', () => {
    beforeEach(() => setUpComponent({}));

    it('success path toggles the downloading flag off', () => {
      cvSvc.getOptimizationResult.and.returnValue(of(result({ status: 'processing' })));
      fixture.detectChanges();
      cvSvc.downloadOptimizedCv.and.returnValue(of(new Blob(['%PDF'])));

      component.download();

      expect(component.downloading()).toBeFalse();
    });

    it('failure shows an error snackbar and resets the flag', () => {
      cvSvc.getOptimizationResult.and.returnValue(of(result({ status: 'processing' })));
      fixture.detectChanges();
      cvSvc.downloadOptimizedCv.and.returnValue(throwError(() => new Error('500')));

      component.download();

      expect(component.downloading()).toBeFalse();
      expect(snack.open).toHaveBeenCalledWith('❌ Erreur lors du téléchargement', 'OK', { duration: 3500 });
    });
  });

  describe('score helpers', () => {
    beforeEach(() => setUpComponent({}));

    it('scoreColor buckets by threshold', () => {
      cvSvc.getOptimizationResult.and.returnValue(of(result({ status: 'processing' })));
      fixture.detectChanges();
      expect(component.scoreColor(90)).toBe('#10B981');
      expect(component.scoreColor(70)).toBe('#F59E0B');
      expect(component.scoreColor(40)).toBe('#EF4444');
    });
  });
});
