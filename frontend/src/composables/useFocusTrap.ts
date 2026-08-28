const focusableSelector = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

export function focusFirst(container: HTMLElement | null): void {
  const first = container?.querySelector<HTMLElement>(focusableSelector)
  first?.focus()
}

export function trapDialogFocus(
  event: KeyboardEvent,
  container: HTMLElement | null,
  close: () => void,
): void {
  if (event.key === 'Escape') {
    event.preventDefault()
    close()
    return
  }
  if (event.key !== 'Tab' || container === null) return

  const focusable = Array.from(container.querySelectorAll<HTMLElement>(focusableSelector))
    .filter(element => element.getAttribute('aria-hidden') !== 'true')
  if (focusable.length === 0) {
    event.preventDefault()
    container.focus()
    return
  }

  const first = focusable[0]!
  const last = focusable[focusable.length - 1]!
  const active = document.activeElement
  if (event.shiftKey && (active === first || !container.contains(active))) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && (active === last || !container.contains(active))) {
    event.preventDefault()
    first.focus()
  }
}
