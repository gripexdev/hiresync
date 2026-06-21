import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpEventType, provideHttpClient } from '@angular/common/http';
import { Subject } from 'rxjs';
import { CvService, UploadProgressEvent } from './cv.service';
import { environment } from '../../../environments/environment';
import { CV, CVOptimizationResult } from '../models/cv.model';

describe('CvService', () => {
  let service: CvService;
  let httpMock: HttpTestingController;
  const base = `${environment.apiUrl}/cv`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CvService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getPage() requests /cv/versions with page/size params', () => {
    let received: number | undefined;
    service.getPage(1, 5).subscribe((p) => (received = p.totalElements));

    const req = httpMock.expectOne((r) => r.url === `${base}/versions` && r.params.get('page') === '1' && r.params.get('size') === '5');
    req.flush({ content: [], totalElements: 7, totalPages: 2, number: 1, size: 5 });

    expect(received).toBe(7);
  });

  it('getAll() fetches a large page and flattens it to a plain array', () => {
    let received: CV[] = [];
    service.getAll().subscribe((cvs) => (received = cvs));

    const req = httpMock.expectOne((r) => r.url === `${base}/versions` && r.params.get('size') === '100');
    req.flush({ content: [{ id: 'cv1' } as CV, { id: 'cv2' } as CV], totalElements: 2, totalPages: 1, number: 0, size: 100 });

    expect(received.map((c) => c.id)).toEqual(['cv1', 'cv2']);
  });

  it('upload() emits a done event with the parsed CV on success', () => {
    const events: UploadProgressEvent[] = [];
    const file = new File(['%PDF-1.4'], 'cv.pdf', { type: 'application/pdf' });

    service.upload(file).subscribe((e) => events.push(e));

    const req = httpMock.expectOne(`${base}/upload`);
    expect(req.request.method).toBe('POST');
    req.event({ type: HttpEventType.Response, body: { id: 'cv1', atsScore: 70 } as CV } as any);

    expect(events).toEqual([{ type: 'done', cv: { id: 'cv1', atsScore: 70 } as CV }]);
  });

  it('upload() emits progress events with a rounded percentage', () => {
    const events: UploadProgressEvent[] = [];
    const file = new File(['%PDF-1.4'], 'cv.pdf', { type: 'application/pdf' });

    service.upload(file).subscribe((e) => events.push(e));

    const req = httpMock.expectOne(`${base}/upload`);
    req.event({ type: HttpEventType.UploadProgress, loaded: 50, total: 200 } as any);

    expect(events).toEqual([{ type: 'progress', progress: 25 }]);
  });

  it('upload() emits an error event when the request fails', () => {
    const events: UploadProgressEvent[] = [];
    const file = new File(['%PDF-1.4'], 'cv.pdf', { type: 'application/pdf' });

    service.upload(file).subscribe((e) => events.push(e));

    const req = httpMock.expectOne(`${base}/upload`);
    req.flush('fail', { status: 500, statusText: 'Server Error' });

    expect(events.length).toBe(1);
    expect(events[0].type).toBe('error');
  });

  it('setActive() PATCHes /cv/{id}/activate', () => {
    service.setActive('cv1').subscribe();
    const req = httpMock.expectOne(`${base}/cv1/activate`);
    expect(req.request.method).toBe('PATCH');
    req.flush(null);
  });

  it('delete() DELETEs /cv/{id}', () => {
    service.delete('cv1').subscribe();
    const req = httpMock.expectOne(`${base}/cv1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('optimize() POSTs to /cv/optimize', () => {
    service.optimize({ cvId: 'cv1', jobId: 'job1' } as any).subscribe();
    const req = httpMock.expectOne(`${base}/optimize`);
    expect(req.request.method).toBe('POST');
    req.flush({ optimizationId: 'opt1', status: 'queued' });
  });

  it('getOptimizationResult() requests /cv/optimize/{id}', () => {
    let received: CVOptimizationResult | undefined;
    service.getOptimizationResult('opt1').subscribe((r) => (received = r));

    httpMock.expectOne(`${base}/optimize/opt1`).flush({ status: 'completed' } as CVOptimizationResult);

    expect(received?.status).toBe('completed');
  });

  it('generateCoverLetter() includes the regenerate flag in the query string', () => {
    service.generateCoverLetter('opt1', true).subscribe();
    const req = httpMock.expectOne(`${base}/optimize/opt1/cover-letter?regenerate=true`);
    expect(req.request.method).toBe('POST');
    req.flush({ subject: 's', body: 'b' });
  });

  it('getHistoryPage() omits status when set to "all" and trims a blank query', () => {
    service.getHistoryPage({ status: 'all', q: '   ', page: 0, size: 10 }).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${base}/optimization-history`);
    expect(req.request.params.has('status')).toBeFalse();
    expect(req.request.params.has('q')).toBeFalse();
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 });
  });

  it('getHistoryPage() includes status and trimmed q when both are set', () => {
    service.getHistoryPage({ status: 'completed', q: '  ocp  ', page: 0, size: 10 }).subscribe();

    const req = httpMock.expectOne((r) => r.url === `${base}/optimization-history`);
    expect(req.request.params.get('status')).toBe('completed');
    expect(req.request.params.get('q')).toBe('ocp');
    req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 });
  });

  it('getHistoryStats() requests /cv/optimization-history/stats', () => {
    let received: { bestScore: number } | undefined;
    service.getHistoryStats().subscribe((s) => (received = s));

    httpMock.expectOne(`${base}/optimization-history/stats`).flush({
      total: 4, completed: 3, rejected: 1, failed: 0, avgGain: 18, bestScore: 92,
    });

    expect(received?.bestScore).toBe(92);
  });

  it('downloadOptimizedCv() requests a Blob from /cv/download/{id}', () => {
    service.downloadOptimizedCv('opt1').subscribe();
    const req = httpMock.expectOne(`${base}/download/opt1`);
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob());
  });

  it('boostKeywords() PATCHes the keyword list', () => {
    service.boostKeywords('opt1', ['Kubernetes', 'Terraform']).subscribe();
    const req = httpMock.expectOne(`${base}/optimize/opt1/boost-keywords`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ keywords: ['Kubernetes', 'Terraform'] });
    req.flush({} as CVOptimizationResult);
  });

  it('getStructuredCv() requests /cv/structured/{id}', () => {
    let received: { fullName: string } | undefined;
    service.getStructuredCv('opt1').subscribe((cv: any) => (received = cv));

    const req = httpMock.expectOne(`${base}/structured/opt1`);
    expect(req.request.method).toBe('GET');
    req.flush({ fullName: 'Othmane Sadiky' });

    expect(received?.fullName).toBe('Othmane Sadiky');
  });

  it('renderPdf() POSTs html+fileName and expects a Blob response', () => {
    service.renderPdf('<html></html>', 'cv.pdf').subscribe();
    const req = httpMock.expectOne(`${base}/render-pdf`);
    expect(req.request.body).toEqual({ html: '<html></html>', fileName: 'cv.pdf' });
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob());
  });

  it('pollUntilDone() polls every 3s and emits only once a terminal status is reached', fakeAsync(() => {
    const cancel$ = new Subject<void>();
    const results: CVOptimizationResult[] = [];

    service.pollUntilDone('opt1', cancel$).subscribe((r) => results.push(r));

    tick(3000);
    httpMock.expectOne(`${base}/optimize/opt1`).flush({ status: 'processing' } as CVOptimizationResult);
    expect(results).toEqual([]);

    tick(3000);
    httpMock.expectOne(`${base}/optimize/opt1`).flush({ status: 'completed' } as CVOptimizationResult);
    expect(results.length).toBe(1);
    expect(results[0].status).toBe('completed');

    cancel$.next();
    cancel$.complete();
  }));
});
