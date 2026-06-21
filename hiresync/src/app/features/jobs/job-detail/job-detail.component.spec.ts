import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError, Subject } from 'rxjs';
import { JobDetailComponent } from './job-detail.component';
import { JobService } from '../../../core/services/job.service';
import { CvService } from '../../../core/services/cv.service';
import { Job } from '../../../core/models/job.model';
import { CV } from '../../../core/models/cv.model';

describe('JobDetailComponent', () => {
  let fixture: ComponentFixture<JobDetailComponent>;
  let component: JobDetailComponent;
  let jobSvc: jasmine.SpyObj<JobService>;
  let cvSvc: jasmine.SpyObj<CvService>;
  let snack: jasmine.SpyObj<MatSnackBar>;
  let router: jasmine.SpyObj<Router>;

  function job(overrides: Partial<Job> = {}): Job {
    return { id: 'job-1', title: 'Dev Backend', company: 'Acme', location: 'Casa',
      description: '', requirements: [] as string[], ...overrides } as Job;
  }

  function cv(id: string, isActive: boolean): CV {
    return { id, fileName: `${id}.pdf`, isActive } as CV;
  }

  beforeEach(() => {
    jobSvc = jasmine.createSpyObj('JobService', ['getById', 'getSimilar']);
    jobSvc.getById.and.returnValue(of(job()));
    jobSvc.getSimilar.and.returnValue(of([]));

    cvSvc = jasmine.createSpyObj('CvService', ['getAll', 'optimize']);
    cvSvc.getAll.and.returnValue(of([]));

    snack = jasmine.createSpyObj('MatSnackBar', ['open']);
    snack.open.and.returnValue({ onAction: () => of(undefined) } as any);

    TestBed.configureTestingModule({
      imports: [JobDetailComponent],
      providers: [
        provideRouter([]),
        { provide: JobService, useValue: jobSvc },
        { provide: CvService, useValue: cvSvc },
        { provide: MatSnackBar, useValue: snack },
      ],
    });
    // MatSnackBarModule declares `providers: [MatSnackBar]` on itself; since
    // JobDetailComponent imports it directly, that creates a component-local
    // injector entry that shadows the TestBed-level override above.
    TestBed.overrideComponent(JobDetailComponent, { add: { providers: [{ provide: MatSnackBar, useValue: snack }] } });

    fixture = TestBed.createComponent(JobDetailComponent);
    component = fixture.componentInstance;
    component.id = 'job-1';
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    spyOn(router, 'navigate');
  });

  it('ngOnInit loads the job, similar jobs, and the active CV name', () => {
    cvSvc.getAll.and.returnValue(of([cv('1', false), cv('2', true)]));

    fixture.detectChanges();

    expect(component.job()?.id).toBe('job-1');
    expect(component.loading()).toBeFalse();
    expect(component.activeCvName()).toBe('2.pdf');
  });

  describe('parsedDesc', () => {
    it('splits into Mission/Profil sections when the marker is present', () => {
      jobSvc.getById.and.returnValue(of(job({
        description: 'Développer des APIs.\n\nProfil recherché :\n3 ans d\'expérience minimum.',
      })));
      fixture.detectChanges();

      const sections = component.parsedDesc();

      expect(sections.length).toBe(2);
      expect(sections[0].title).toBe('Mission & Responsabilités');
      expect(sections[1].title).toBe('Profil recherché');
    });

    it('renders a single generic section when there is no marker', () => {
      jobSvc.getById.and.returnValue(of(job({ description: 'Texte sans séparateur.' })));
      fixture.detectChanges();

      const sections = component.parsedDesc();

      expect(sections.length).toBe(1);
      expect(sections[0].title).toBe('Description du poste');
    });

    it('returns an empty array for a blank description', () => {
      jobSvc.getById.and.returnValue(of(job({ description: '   ' })));
      fixture.detectChanges();

      expect(component.parsedDesc()).toEqual([]);
    });

    it('multi-line paragraphs become bullet lists, single lines stay plain', () => {
      jobSvc.getById.and.returnValue(of(job({ description: 'Ligne unique.\n\nPremière ligne\nDeuxième ligne' })));
      fixture.detectChanges();

      const paragraphs = component.parsedDesc()[0].paragraphs;

      expect(paragraphs[0]).toEqual(['Ligne unique.']);
      expect(paragraphs[1]).toEqual(['Première ligne', 'Deuxième ligne']);
    });
  });

  describe('optimizeCV', () => {
    it('prompts to add a CV when the user has none', () => {
      fixture.detectChanges();
      cvSvc.getAll.and.returnValue(of([]));

      component.optimizeCV();

      expect(snack.open).toHaveBeenCalledWith(
        'Ajoutez d\'abord un CV dans « Mon CV » pour pouvoir optimiser.', 'Mon CV', { duration: 5000 });
      expect(component.optimizing()).toBeFalse();
    });

    it('prompts to activate a CV when none is marked active', () => {
      fixture.detectChanges();
      cvSvc.getAll.and.returnValue(of([cv('1', false)]));

      component.optimizeCV();

      expect(snack.open).toHaveBeenCalledWith(
        'Activez le CV à utiliser dans « Mon CV », puis réessayez.', 'Mon CV', { duration: 5000 });
    });

    it('queues a new optimization and navigates with query params', () => {
      fixture.detectChanges();
      cvSvc.getAll.and.returnValue(of([cv('1', true)]));
      cvSvc.optimize.and.returnValue(of({ optimizationId: 'opt-1', status: 'queued', message: '', alreadyOptimized: false }));

      component.optimizeCV();

      expect(router.navigate).toHaveBeenCalledWith(['/cv/optimize', 'opt-1'], {
        queryParams: { cvId: '1', jobId: 'job-1', jobTitle: 'Dev Backend' },
      });
    });

    it('navigates straight to an existing optimization without query params', () => {
      fixture.detectChanges();
      cvSvc.getAll.and.returnValue(of([cv('1', true)]));
      cvSvc.optimize.and.returnValue(of({ optimizationId: 'opt-1', status: 'completed', message: '', alreadyOptimized: true }));

      component.optimizeCV();

      expect(router.navigate).toHaveBeenCalledWith(['/cv/optimize', 'opt-1']);
    });

    it('is a no-op while already optimizing', () => {
      fixture.detectChanges();
      component.optimizing.set(true);

      component.optimizeCV();

      expect(cvSvc.getAll).toHaveBeenCalledTimes(1); // only ngOnInit's call, not a second one
    });

    it('shows an error snackbar if launching the optimization fails', () => {
      fixture.detectChanges();
      cvSvc.getAll.and.returnValue(of([cv('1', true)]));
      cvSvc.optimize.and.returnValue(throwError(() => new Error('500')));

      component.optimizeCV();

      expect(snack.open).toHaveBeenCalledWith('Impossible de lancer l\'optimisation. Réessayez.', 'OK', { duration: 3500 });
      expect(component.optimizing()).toBeFalse();
    });
  });

  it('openJobSource opens sourceUrl in a new tab when available', () => {
    jobSvc.getById.and.returnValue(of(job({ sourceUrl: 'https://rekrute.com/x' })));
    fixture.detectChanges();
    spyOn(window, 'open');

    component.openJobSource();

    expect(window.open).toHaveBeenCalledWith('https://rekrute.com/x', '_blank', 'noopener,noreferrer');
  });

  it('matchClass buckets the score', () => {
    fixture.detectChanges();
    expect(component.matchClass(85)).toBe('match--high');
    expect(component.matchClass(65)).toBe('match--med');
    expect(component.matchClass(30)).toBe('match--low');
  });
});
