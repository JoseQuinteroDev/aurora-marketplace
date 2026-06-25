import { ToastService } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;

  beforeEach(() => {
    service = new ToastService();
  });

  it('queues a toast with an incrementing id and the given tone', () => {
    // durationMs = 0 disables the auto-dismiss timer (kept deterministic).
    const first = service.show('hello', 'info', 0);
    const second = service.success('done', 0);

    expect(second).toBe(first + 1);
    expect(service.toasts()).toHaveLength(2);
    expect(service.toasts()[1].tone).toBe('success');
    expect(service.toasts()[0].message).toBe('hello');
  });

  it('success/error/info set the matching tone', () => {
    service.error('boom', 0);
    expect(service.toasts()[0].tone).toBe('error');
  });

  it('dismiss removes only the targeted toast', () => {
    const a = service.show('a', 'info', 0);
    service.show('b', 'info', 0);

    service.dismiss(a);

    expect(service.toasts()).toHaveLength(1);
    expect(service.toasts()[0].message).toBe('b');
  });
});
