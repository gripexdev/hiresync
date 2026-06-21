import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PaginatorComponent } from './paginator.component';

describe('PaginatorComponent', () => {
  let fixture: ComponentFixture<PaginatorComponent>;
  let component: PaginatorComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PaginatorComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(PaginatorComponent);
    component = fixture.componentInstance;
  });

  function setInputs(total: number, page: number, pageSize: number): void {
    fixture.componentRef.setInput('total', total);
    fixture.componentRef.setInput('page', page);
    fixture.componentRef.setInput('pageSize', pageSize);
    fixture.detectChanges();
  }

  it('computes totalPages from total and pageSize', () => {
    setInputs(95, 1, 10);
    expect(component.totalPages()).toBe(10);
  });

  it('computes totalPages as at least 1 even when there are zero items', () => {
    setInputs(0, 1, 10);
    expect(component.totalPages()).toBe(1);
  });

  it('computes the displayed range for a middle page', () => {
    setInputs(95, 3, 10);
    expect(component.rangeStart()).toBe(21);
    expect(component.rangeEnd()).toBe(30);
  });

  it('caps rangeEnd at the total on the last, partially-filled page', () => {
    setInputs(95, 10, 10);
    expect(component.rangeStart()).toBe(91);
    expect(component.rangeEnd()).toBe(95);
  });

  it('reports multiPage = false when everything fits on one page', () => {
    setInputs(5, 1, 10);
    expect(component.multiPage()).toBeFalse();
  });

  it('reports multiPage = true as soon as there is more than one page', () => {
    setInputs(11, 1, 10);
    expect(component.multiPage()).toBeTrue();
  });

  it('lists every page without ellipses when there are 7 or fewer pages', () => {
    setInputs(70, 1, 10); // 7 pages
    expect(component.pages()).toEqual([1, 2, 3, 4, 5, 6, 7]);
  });

  it('inserts ellipsis markers (-1) around the current page for large ranges', () => {
    setInputs(200, 10, 10); // 20 pages, currently on page 10
    const pages = component.pages();
    expect(pages[0]).toBe(1);
    expect(pages[pages.length - 1]).toBe(20);
    expect(pages).toContain(-1);
    expect(pages).toContain(9);
    expect(pages).toContain(10);
    expect(pages).toContain(11);
  });

  it('go() emits pageChange for a valid target page', () => {
    setInputs(95, 3, 10);
    const emitted: number[] = [];
    component.pageChange.subscribe((p) => emitted.push(p));

    component.go(4);

    expect(emitted).toEqual([4]);
  });

  it('go() does not emit when the target page is out of range', () => {
    setInputs(95, 3, 10);
    const emitted: number[] = [];
    component.pageChange.subscribe((p) => emitted.push(p));

    component.go(0);
    component.go(999);

    expect(emitted).toEqual([]);
  });

  it('go() does not emit when the target page equals the current page', () => {
    setInputs(95, 3, 10);
    const emitted: number[] = [];
    component.pageChange.subscribe((p) => emitted.push(p));

    component.go(3);

    expect(emitted).toEqual([]);
  });

  it('onSize() emits pageSizeChange with the selected numeric value', () => {
    setInputs(95, 1, 10);
    const emitted: number[] = [];
    component.pageSizeChange.subscribe((s) => emitted.push(s));

    const select = document.createElement('select');
    const option = document.createElement('option');
    option.value = '25';
    select.appendChild(option);
    select.value = '25';
    component.onSize({ target: select } as unknown as Event);

    expect(emitted).toEqual([25]);
  });
});
