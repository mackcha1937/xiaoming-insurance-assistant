const activeOwners = new Set<symbol>()

function synchronize(): void {
  const page = document.querySelector<HTMLElement>('.page-shell')
  if (page === null) return
  if (activeOwners.size > 0) {
    page.setAttribute('inert', '')
    page.setAttribute('aria-hidden', 'true')
  } else {
    page.removeAttribute('inert')
    page.removeAttribute('aria-hidden')
  }
}

export function createPageIsolation(): (active: boolean) => void {
  const owner = Symbol('modal-owner')
  return active => {
    if (active) activeOwners.add(owner)
    else activeOwners.delete(owner)
    synchronize()
  }
}
