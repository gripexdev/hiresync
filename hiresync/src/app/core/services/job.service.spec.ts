import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { JobService } from './job.service';
import { environment } from '../../../environments/environment';
import { Job } from '../models/job.model';

describe('JobService', () => {
  let service: JobService;
  let httpMock: HttpTestingController;

  function springPage(content: Partial<Job>[], totalElements = content.length) {
    return { content, totalElements, totalPages: 1, number: 0, size: 20 };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(JobService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('search() sends page/size as query params and maps the Spring page envelope', () => {
    service.search({ page: 1, size: 10 }).subscribe((result) => {
      expect(result.total).toBe(2);
      expect(result.jobs.length).toBe(2);
    });

    const req = httpMock.expectOne(
      (r) => r.url === `${environment.apiUrl}/jobs` && r.params.get('page') === '1' && r.params.get('size') === '10',
    );
    req.flush(springPage([
      { id: '1', title: 'Dev Backend' } as Job,
      { id: '2', title: 'Dev Frontend' } as Job,
    ], 2));
  });

  it('search() defaults to page 0, size 20 when not provided', () => {
    let received: { page: number; size: number } | undefined;
    service.search({}).subscribe((r) => (received = r));

    const req = httpMock.expectOne(
      (r) => r.params.get('page') === '0' && r.params.get('size') === '20',
    );
    req.flush(springPage([]));

    expect(received?.page).toBe(0);
    expect(received?.size).toBe(20);
  });

  it('search() only appends optional filters that are actually set', () => {
    service.search({ q: 'java', city: 'Casablanca' }).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/jobs`);
    expect(req.request.params.get('q')).toBe('java');
    expect(req.request.params.get('city')).toBe('Casablanca');
    expect(req.request.params.has('contractType')).toBeFalse();
    req.flush(springPage([]));
  });

  it('search() falls back to scrapedAt when postedAt is missing', () => {
    let received: Job | undefined;
    service.search({}).subscribe((result) => (received = result.jobs[0]));

    const req = httpMock.expectOne(`${environment.apiUrl}/jobs?page=0&size=20`);
    req.flush(springPage([{ id: '1', postedAt: undefined, scrapedAt: '2026-01-01' } as unknown as Job]));

    expect(received?.postedAt).toBe('2026-01-01');
  });

  it('getFacets() requests /jobs/facets', () => {
    let received: { cities: { key: string }[] } | undefined;
    service.getFacets().subscribe((f) => (received = f));

    const req = httpMock.expectOne(`${environment.apiUrl}/jobs/facets`);
    expect(req.request.method).toBe('GET');
    req.flush({
      cities: [{ key: 'casablanca', label: 'Casablanca', count: 5 }],
      contractTypes: [], experienceLevels: [], sectors: [],
    });

    expect(received?.cities[0].key).toBe('casablanca');
  });

  it('getById() requests the job and applies the same postedAt fallback', () => {
    let received: Job | undefined;
    service.getById('42').subscribe((j) => (received = j));

    const req = httpMock.expectOne(`${environment.apiUrl}/jobs/42`);
    req.flush({ id: '42', scrapedAt: '2026-02-02' } as unknown as Job);

    expect(received?.postedAt).toBe('2026-02-02');
  });

  it('getSimilar() excludes the current job and caps the result at 3', () => {
    let received: Job[] = [];
    service.getSimilar('2').subscribe((jobs) => (received = jobs));

    const req = httpMock.expectOne((r) => r.url === `${environment.apiUrl}/jobs`);
    req.flush(springPage([
      { id: '1' } as Job, { id: '2' } as Job, { id: '3' } as Job,
      { id: '4' } as Job, { id: '5' } as Job,
    ]));

    expect(received.map((j) => j.id)).toEqual(['1', '3', '4']);
  });
});
