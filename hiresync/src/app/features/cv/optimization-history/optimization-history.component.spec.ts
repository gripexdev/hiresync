import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { OptimizationHistoryComponent } from './optimization-history.component';
import { CvService } from '../../../core/services/cv.service';
import { CVOptimizationHistoryItem } from '../../../core/models/cv.model';

describe('OptimizationHistoryComponent', () => {
  let fixture: ComponentFixture<OptimizationHistoryComponent>;
  let component: OptimizationHistoryComponent;
  let svc: jasmine.SpyObj<CvService>;

  const stats = { total: 10, completed: 6, rejected: 2, failed: 2, avgGain: 18, bestScore: 92 };

  function item(id: string): CVOptimizationHistoryItem {
    return { id, jobTitle: 'Dev', company: 'Acme', originalScore: 60, optimizedScore: 85,
      createdAt: '2026-01-01', status: 'completed' } as CVOptimizationHistoryItem;
  }

  function page(content: CVOptimizationHistoryItem[], total = content.length) {
    return { content, totalElements: total, totalPages: 1, number: 0, size: 10, first: true, last: true };
  }

  beforeEach(() => {
    svc = jasmine.createSpyObj('CvService', ['getHistoryStats', 'getHistoryPage']);
    svc.getHistoryStats.and.returnValue(of(stats));
    svc.getHistoryPage.and.returnValue(of(page([item('1')], 1)));

    TestBed.configureTestingModule({
      imports: [OptimizationHistoryComponent],
      providers: [provideRouter([]), { provide: CvService, useValue: svc }],
    });

    fixture = TestBed.createComponent(OptimizationHistoryComponent);
    component = fixture.componentInstance;
  });

  it('ngOnInit loads both stats and the first history page', () => {
    fixture.detectChanges();

    expect(component.stats()).toEqual(stats);
    expect(component.history().length).toBe(1);
    expect(component.loading()).toBeFalse();
    expect(component.isEmpty).toBeFalse();
  });

  it('isEmpty is true once stats report zero total optimizations', () => {
    svc.getHistoryStats.and.returnValue(of({ total: 0, completed: 0, rejected: 0, failed: 0, avgGain: 0, bestScore: 0 }));
    fixture.detectChanges();

    expect(component.isEmpty).toBeTrue();
  });

  it('a failed page load sets errored and stops loading', () => {
    svc.getHistoryPage.and.returnValue(throwError(() => new Error('500')));

    fixture.detectChanges();

    expect(component.errored()).toBeTrue();
    expect(component.loading()).toBeFalse();
  });

  it('filters() reflects live counts from stats', () => {
    fixture.detectChanges();
    const filters = component.filters();

    expect(filters.find((f) => f.key === 'completed')?.count).toBe(6);
    expect(filters.find((f) => f.key === 'rejected')?.count).toBe(2);
  });

  it('setFilter resets to page 1 and reloads with the new filter', () => {
    fixture.detectChanges();
    component.page.set(3);
    svc.getHistoryPage.calls.reset();
    svc.getHistoryPage.and.returnValue(of(page([item('2')], 1)));

    component.setFilter('rejected');

    expect(component.page()).toBe(1);
    expect(svc.getHistoryPage).toHaveBeenCalledWith(
      jasmine.objectContaining({ status: 'rejected', page: 0 }));
  });

  it('setFilter is a no-op when re-selecting the already-active filter', () => {
    fixture.detectChanges();
    svc.getHistoryPage.calls.reset();

    component.setFilter('all');

    expect(svc.getHistoryPage).not.toHaveBeenCalled();
  });

  it('search input is debounced before triggering a reload', fakeAsync(() => {
    fixture.detectChanges();
    svc.getHistoryPage.calls.reset();
    const input = document.createElement('input');
    input.value = 'ocp';

    component.onSearch({ target: input } as unknown as Event);
    tick(100);
    expect(svc.getHistoryPage).not.toHaveBeenCalled(); // not yet — debounce still pending

    tick(300);
    expect(svc.getHistoryPage).toHaveBeenCalledWith(jasmine.objectContaining({ q: 'ocp' }));
  }));

  it('clearSearch resets the query and reloads when search was non-empty', () => {
    fixture.detectChanges();
    component.search.set('ocp');
    svc.getHistoryPage.calls.reset();

    component.clearSearch();

    expect(component.search()).toBe('');
    expect(svc.getHistoryPage).toHaveBeenCalled();
  });

  it('clearSearch does nothing when the search box is already empty', () => {
    fixture.detectChanges();
    svc.getHistoryPage.calls.reset();

    component.clearSearch();

    expect(svc.getHistoryPage).not.toHaveBeenCalled();
  });

  it('goToPage / changePageSize delegate to the server with updated pagination', () => {
    fixture.detectChanges();
    svc.getHistoryPage.calls.reset();

    component.goToPage(2);
    expect(svc.getHistoryPage).toHaveBeenCalledWith(jasmine.objectContaining({ page: 1 }));

    component.changePageSize(25);
    expect(component.page()).toBe(1);
    expect(svc.getHistoryPage).toHaveBeenCalledWith(jasmine.objectContaining({ size: 25, page: 0 }));
  });

  it('scoreColor buckets by threshold', () => {
    expect(component.scoreColor(90)).toBe('#10B981');
    expect(component.scoreColor(65)).toBe('#F59E0B');
    expect(component.scoreColor(20)).toBe('#EF4444');
  });

  it('modelLabel maps known provider ids to friendly names', () => {
    expect(component.modelLabel({ modelUsed: 'Groq Llama 3.3 70B' } as CVOptimizationHistoryItem)).toBe('Groq Llama 3.3');
    expect(component.modelLabel({ modelUsed: 'gemini-2.0-flash' } as CVOptimizationHistoryItem)).toBe('Gemini 2.0 Flash');
    expect(component.modelLabel({ modelUsed: undefined } as CVOptimizationHistoryItem)).toBe('—');
    expect(component.modelLabel({ modelUsed: 'some-unknown-model' } as CVOptimizationHistoryItem)).toBe('some-unknown-model');
  });
});
