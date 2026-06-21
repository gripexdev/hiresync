import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ApplicationService } from './application.service';
import { environment } from '../../../environments/environment';
import { Application } from '../models/application.model';

describe('ApplicationService', () => {
  let service: ApplicationService;
  let httpMock: HttpTestingController;
  const base = `${environment.apiUrl}/applications`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ApplicationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getPage() sends page/size without a status param when none is given', () => {
    service.getPage({ page: 0, size: 10 }).subscribe();

    const req = httpMock.expectOne((r) => r.url === base);
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.has('status')).toBeFalse();
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 });
  });

  it('getPage() includes status when fetching a single kanban column', () => {
    service.getPage({ status: 'interview', page: 0, size: 10 }).subscribe();

    const req = httpMock.expectOne((r) => r.url === base);
    expect(req.request.params.get('status')).toBe('interview');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 });
  });

  it('getStats() requests /applications/stats', () => {
    let received: { total: number } | undefined;
    service.getStats().subscribe((s) => (received = s));

    const req = httpMock.expectOne(`${base}/stats`);
    expect(req.request.method).toBe('GET');
    req.flush({ total: 3, pending: 1, interviews: 1, offers: 0, rejected: 1 });

    expect(received?.total).toBe(3);
  });

  it('apply() POSTs the CV id and cover note to /applications/{jobId}', () => {
    service.apply('job-1', { cvId: 'cv-1', coverNote: 'Motivé' }).subscribe();

    const req = httpMock.expectOne(`${base}/job-1`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ cvId: 'cv-1', coverNote: 'Motivé' });
    req.flush({} as Application);
  });

  it('checkApplied() requests /applications/check/{jobId}', () => {
    let result: { applied: boolean } | undefined;
    service.checkApplied('job-1').subscribe((r) => (result = r));

    httpMock.expectOne(`${base}/check/job-1`).flush({ applied: true });

    expect(result?.applied).toBeTrue();
  });

  it('markApplied() POSTs the cvId to /applications/{jobId}/mark-applied', () => {
    service.markApplied('job-1', 'cv-9').subscribe();

    const req = httpMock.expectOne(`${base}/job-1/mark-applied`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ cvId: 'cv-9' });
    req.flush({} as Application);
  });

  it('updateStatus() PATCHes the new status to /applications/{id}/status', () => {
    service.updateStatus('app-1', 'offer').subscribe();

    const req = httpMock.expectOne(`${base}/app-1/status`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'offer' });
    req.flush({} as Application);
  });
});
