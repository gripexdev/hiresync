import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { JobSearchComponent } from './job-search.component';
import { JobService } from '../../../core/services/job.service';
import { Job, JobSearchResult } from '../../../core/models/job.model';

describe('JobSearchComponent', () => {
  let fixture: ComponentFixture<JobSearchComponent>;
  let component: JobSearchComponent;
  let svc: jasmine.SpyObj<JobService>;
  let router: jasmine.SpyObj<Router>;

  function searchResult(jobs: Job[], total = jobs.length, totalPages = 1): JobSearchResult {
    return { jobs, total, totalPages, page: 0, size: 10 };
  }

  beforeEach(() => {
    svc = jasmine.createSpyObj('JobService', ['getFacets', 'search']);
    svc.getFacets.and.returnValue(of({ cities: [], contractTypes: [], experienceLevels: [], sectors: [] }));
    svc.search.and.returnValue(of(searchResult([])));
    spyOn(window, 'scrollTo');

    TestBed.configureTestingModule({
      imports: [JobSearchComponent],
      providers: [
        provideRouter([]),
        { provide: JobService, useValue: svc },
      ],
    });

    fixture = TestBed.createComponent(JobSearchComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    spyOn(router, 'navigate');
  });

  it('ngOnInit loads facets and runs an initial unfiltered search', () => {
    const job = { id: '1', title: 'Dev' } as Job;
    svc.search.and.returnValue(of(searchResult([job], 1, 1)));

    fixture.detectChanges();

    expect(svc.getFacets).toHaveBeenCalled();
    expect(component.jobs().length).toBe(1);
    expect(component.loading()).toBeFalse();
  });

  it('filter changes are debounced before triggering a new search', fakeAsync(() => {
    fixture.detectChanges();
    svc.search.calls.reset();

    component.filters.patchValue({ q: 'java' });
    tick(200);
    expect(svc.search).not.toHaveBeenCalled();

    tick(300);
    expect(svc.search).toHaveBeenCalledWith(jasmine.objectContaining({ q: 'java', page: 0 }));
  }));

  it('changing a filter resets the page back to 0', fakeAsync(() => {
    fixture.detectChanges();
    component.currentPage.set(3);

    component.filters.patchValue({ city: 'casablanca' });
    tick(500);

    expect(component.currentPage()).toBe(0);
  }));

  it('activeFilterCount tracks how many filters are actually set', () => {
    fixture.detectChanges();

    component.filters.patchValue({ q: 'java', city: 'casablanca' });

    expect(component.activeFilterCount()).toBe(2);
  });

  it('clearFilters resets the form and the active count goes back to zero', () => {
    fixture.detectChanges();
    component.filters.patchValue({ q: 'java' });
    expect(component.activeFilterCount()).toBe(1);

    component.clearFilters();

    expect(component.activeFilterCount()).toBe(0);
  });

  it('goToPage ignores null (ellipsis) and out-of-range pages', () => {
    fixture.detectChanges();
    component.totalPages.set(3);
    svc.search.calls.reset();

    component.goToPage(null);
    component.goToPage(-1);
    component.goToPage(5);

    expect(svc.search).not.toHaveBeenCalled();
  });

  it('goToPage navigates to a valid page and scrolls to top', () => {
    fixture.detectChanges();
    component.totalPages.set(3);

    component.goToPage(1);

    expect(component.currentPage()).toBe(1);
    expect(window.scrollTo).toHaveBeenCalled();
  });

  it('visiblePages lists every page when there are 7 or fewer', () => {
    fixture.detectChanges();
    component.totalPages.set(5);

    expect(component.visiblePages()).toEqual([0, 1, 2, 3, 4]);
  });

  it('visiblePages inserts ellipsis markers for large page counts', () => {
    fixture.detectChanges();
    component.totalPages.set(20);
    component.currentPage.set(10);

    const pages = component.visiblePages();

    expect(pages[0]).toBe(0);
    expect(pages[pages.length - 1]).toBe(19);
    expect(pages).toContain(null);
  });

  it('matchClass buckets the match score', () => {
    fixture.detectChanges();
    expect(component.matchClass(85)).toBe('match--high');
    expect(component.matchClass(65)).toBe('match--med');
    expect(component.matchClass(30)).toBe('match--low');
    expect(component.matchClass(undefined)).toBe('');
  });

  it('openSource stops propagation and navigates to the job detail page', () => {
    fixture.detectChanges();
    const event = jasmine.createSpyObj('Event', ['stopPropagation']);

    component.openSource({ id: 'job-1' } as Job, event);

    expect(event.stopPropagation).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/jobs', 'job-1']);
  });

  it('timeAgo formats today/yesterday/N days ago', () => {
    fixture.detectChanges();
    const now = Date.now();
    expect(component.timeAgo(new Date(now).toISOString())).toBe("Aujourd'hui");
    expect(component.timeAgo(new Date(now - 86400000).toISOString())).toBe('Hier');
    expect(component.timeAgo(new Date(now - 3 * 86400000).toISOString())).toBe('Il y a 3 jours');
  });
});
