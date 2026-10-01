import { Button } from '../../../shared/ui/Button'

export function AdminPagination({ page, size, total, onPage }: {
  page: number; size: number; total: number; onPage: (page: number) => void
}) {
  if (total <= size) return null
  return <div className="admin-pagination">
    <Button type="button" variant="secondary" disabled={page === 0} onClick={() => onPage(page - 1)}>Previous</Button>
    <span>Page {page + 1} of {Math.max(1, Math.ceil(total / size))}</span>
    <Button type="button" variant="secondary" disabled={(page + 1) * size >= total} onClick={() => onPage(page + 1)}>Next</Button>
  </div>
}
