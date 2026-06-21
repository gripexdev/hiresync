import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ApplicationsComponent } from './applications.component';
import { ApplicationService } from '../../core/services/application.service';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Application } from '../../core/models/application.model';
import { Page } from '../../core/models/page.model';

describe('ApplicationsComponent', () => {
  let fixture: ComponentFixture<ApplicationsComponent>;
  let component: ApplicationsComponent;
  let svc: jasmine.SpyObj<ApplicationService>;
  let snack: jasmine.SpyObj<MatSnackBar>;

  function page(content: Application[], total = content.length): Page<Application> {
    return { content, totalElements: total, totalPages: 1, number: 0, size: 6, first: true, last: true };
  }

  function app(id: string, status: Application['status']): Application {
    return { id, jobTitle: 'Dev', company: 'Acme', status } as Application;
  }

  beforeEach(async () => {
    svc = jasmine.createSpyObj('ApplicationService', ['getPage', 'updateStatus']);
    snack = jasmine.createSpyObj('MatSnackBar', ['open']);
    svc.getPage.and.returnValue(of(page([])));

    await TestBed.configureTestingModule({
      imports: [ApplicationsComponent],
      providers: [
        provideRouter([]),
        { provide: ApplicationService, useValue: svc },
        { provide: MatSnackBar, useValue: snack },
      ],
    });
    // MatSnackBarModule declares `providers: [MatSnackBar]` on itself; since
    // ApplicationsComponent imports it directly, that creates a component-local
    // injector entry that shadows the TestBed-level override above.
    TestBed.overrideComponent(ApplicationsComponent, { add: { providers: [{ provide: MatSnackBar, useValue: snack }] } });
    await TestBed.compileComponents();

    fixture = TestBed.createComponent(ApplicationsComponent);
    component = fixture.componentInstance;
  });

  it('ngOnInit() fetches all 5 kanban columns in parallel and populates them', () => {
    svc.getPage.calls.reset();
    svc.getPage.and.callFake((opts: any) =>
      of(page([app('1', opts.status)], opts.status === 'applied' ? 3 : 1)));

    fixture.detectChanges(); // triggers ngOnInit

    expect(svc.getPage).toHaveBeenCalledTimes(5);
    expect(component.loading()).toBeFalse();
    expect(component.kanban()['applied'].total).toBe(3);
    expect(component.totalCount()).toBe(3 + 1 + 1 + 1 + 1);
  });

  it('ngOnInit() failure stops the loading spinner without throwing', () => {
    svc.getPage.and.returnValue(throwError(() => new Error('network down')));

    expect(() => fixture.detectChanges()).not.toThrow();
    expect(component.loading()).toBeFalse();
  });

  it('changeStatus() optimistically moves the card before the server responds', () => {
    fixture.detectChanges();
    component.kanban.set({
      applied:   { items: [app('1', 'applied')], page: 0, total: 1, loading: false },
      in_review: { items: [], page: 0, total: 0, loading: false },
      interview: { items: [], page: 0, total: 0, loading: false },
      offer:     { items: [], page: 0, total: 0, loading: false },
      rejected:  { items: [], page: 0, total: 0, loading: false },
    });
    svc.updateStatus.and.returnValue(of(app('1', 'interview')));

    component.changeStatus(app('1', 'applied'), 'interview');

    // Moved immediately, before the (synchronous-in-test) subscribe callback even runs.
    expect(component.kanban()['applied'].items.length).toBe(0);
    expect(component.kanban()['interview'].items[0].id).toBe('1');
    expect(component.kanban()['interview'].total).toBe(1);
  });

  it('changeStatus() rolls back the optimistic move when the server call fails', () => {
    fixture.detectChanges();
    component.kanban.set({
      applied:   { items: [app('1', 'applied')], page: 0, total: 1, loading: false },
      in_review: { items: [], page: 0, total: 0, loading: false },
      interview: { items: [], page: 0, total: 0, loading: false },
      offer:     { items: [], page: 0, total: 0, loading: false },
      rejected:  { items: [], page: 0, total: 0, loading: false },
    });
    svc.updateStatus.and.returnValue(throwError(() => new Error('500')));

    component.changeStatus(app('1', 'applied'), 'interview');

    expect(component.kanban()['interview'].items.length).toBe(0);
    expect(component.kanban()['applied'].items[0].id).toBe('1');
    expect(component.kanban()['applied'].total).toBe(1);
    expect(snack.open).toHaveBeenCalledWith('Échec de la mise à jour du statut.', 'OK', { duration: 3500 });
  });

  it('changeStatus() is a no-op when the target status equals the current one', () => {
    fixture.detectChanges();
    component.changeStatus(app('1', 'applied'), 'applied');

    expect(svc.updateStatus).not.toHaveBeenCalled();
  });

  it('onDrop() within the same column does nothing', () => {
    fixture.detectChanges();
    const sameContainer = { data: 'applied' } as any;
    component.onDrop({
      previousContainer: sameContainer,
      container: sameContainer,
      item: { data: app('1', 'applied') },
    } as any);

    expect(svc.updateStatus).not.toHaveBeenCalled();
  });

  it('onDrop() across columns triggers changeStatus to the target column', () => {
    fixture.detectChanges();
    component.kanban.set({
      applied:   { items: [app('1', 'applied')], page: 0, total: 1, loading: false },
      in_review: { items: [], page: 0, total: 0, loading: false },
      interview: { items: [], page: 0, total: 0, loading: false },
      offer:     { items: [], page: 0, total: 0, loading: false },
      rejected:  { items: [], page: 0, total: 0, loading: false },
    });
    svc.updateStatus.and.returnValue(of(app('1', 'offer')));

    component.onDrop({
      previousContainer: { data: 'applied' },
      container: { data: 'offer' },
      item: { data: app('1', 'applied') },
    } as any);

    expect(svc.updateStatus).toHaveBeenCalledWith('1', 'offer');
  });

  it('switchView("table") loads the table exactly once, lazily', () => {
    fixture.detectChanges();
    svc.getPage.calls.reset();
    svc.getPage.and.returnValue(of(page([app('1', 'applied')], 1)));

    component.switchView('table');
    component.switchView('kanban');
    component.switchView('table');

    expect(svc.getPage).toHaveBeenCalledTimes(1);
    expect(component.tableRows().length).toBe(1);
  });

  it('showMore() appends the next page to the existing column items', () => {
    fixture.detectChanges();
    component.kanban.set({
      applied:   { items: [app('1', 'applied')], page: 0, total: 2, loading: false },
      in_review: { items: [], page: 0, total: 0, loading: false },
      interview: { items: [], page: 0, total: 0, loading: false },
      offer:     { items: [], page: 0, total: 0, loading: false },
      rejected:  { items: [], page: 0, total: 0, loading: false },
    });
    svc.getPage.and.returnValue(of(page([app('2', 'applied')], 2)));

    component.showMore('applied');

    expect(svc.getPage).toHaveBeenCalledWith({ status: 'applied', page: 1, size: 6 });
    expect(component.kanban()['applied'].items.map((a) => a.id)).toEqual(['1', '2']);
  });

  it('showLess() replaces items with just the first page', () => {
    fixture.detectChanges();
    component.kanban.set({
      applied:   { items: [app('1', 'applied'), app('2', 'applied')], page: 1, total: 2, loading: false },
      in_review: { items: [], page: 0, total: 0, loading: false },
      interview: { items: [], page: 0, total: 0, loading: false },
      offer:     { items: [], page: 0, total: 0, loading: false },
      rejected:  { items: [], page: 0, total: 0, loading: false },
    });
    svc.getPage.and.returnValue(of(page([app('1', 'applied')], 2)));

    component.showLess('applied');

    expect(svc.getPage).toHaveBeenCalledWith({ status: 'applied', page: 0, size: 6 });
    expect(component.kanban()['applied'].items.map((a) => a.id)).toEqual(['1']);
  });

  it('matchClass() buckets the match score into high/med/low CSS classes', () => {
    expect(component.matchClass(85)).toBe('match--high');
    expect(component.matchClass(65)).toBe('match--med');
    expect(component.matchClass(40)).toBe('match--low');
    expect(component.matchClass(undefined)).toBe('');
  });

  it('labelFor() resolves a status id to its French column label', () => {
    expect(component.labelFor('interview')).toBe('Entretien');
  });
});
