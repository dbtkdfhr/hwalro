import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { FileText } from 'lucide-react';
import { useDeleteDrawing, useDrawingList, useDuplicateDrawing } from '../hooks';
import DrawingListTable from '../components/DrawingListTable';
import {
  Button,
  buttonClassName,
  Card,
  EmptyState,
  ErrorState,
  Input,
  Modal,
  PageHeader,
  Pagination,
} from '../../../components/ui';
import { useDebounce } from '../../../hooks/useDebounce';
import { can } from '../../auth/capabilities';
import { useAuth } from '../../auth/context/AuthContext';
import { getDrawingErrorMessage } from '../utils/getDrawingErrorMessage';
import type { DrawingSummary } from '../types/drawing';

const PAGE_SIZE = 5;

function DrawingListPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const canManage = can(user?.roles, 'drawings.manage');
  const [page, setPage] = useState(1);
  const [query, setQuery] = useState('');
  const debouncedQuery = useDebounce(query, 300);
  const [drawingToDelete, setDrawingToDelete] = useState<DrawingSummary | null>(null);
  const [drawingToBlock, setDrawingToBlock] = useState<DrawingSummary | null>(null);
  const { items, totalCount, isPending, isError, error, nameById } = useDrawingList(
    page,
    PAGE_SIZE,
    debouncedQuery,
  );
  const deleteDrawing = useDeleteDrawing();
  const duplicateDrawing = useDuplicateDrawing();

  const pageCount = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));

  useEffect(() => {
    if (!isPending && !isError && page > pageCount) {
      setPage(pageCount);
    }
  }, [isPending, isError, page, pageCount]);

  const handleDelete = (drawing: DrawingSummary) => {
    if (drawing.simulationCount > 0) {
      setDrawingToBlock(drawing);
      return;
    }
    setDrawingToDelete(drawing);
  };

  const handleDuplicate = (drawing: DrawingSummary) => {
    duplicateDrawing.mutate(drawing.id, {
      onSuccess: (duplicated) => navigate(`/layout/${duplicated.id}`),
      onError: (duplicateError) => {
        window.alert(getDrawingErrorMessage(duplicateError));
      },
    });
  };

  const confirmDelete = () => {
    if (drawingToDelete === null) {
      return;
    }
    deleteDrawing.mutate(drawingToDelete.id, {
      onError: (deleteError) => {
        window.alert(getDrawingErrorMessage(deleteError));
      },
    });
    setDrawingToDelete(null);
  };

  return (
    <main className="bg-background">
      <div className="mx-auto w-full max-w-[1360px] px-1 pt-2 pb-10 sm:px-4 lg:pt-4">
        <div className="border-b border-line pb-6">
          <PageHeader
            eyebrow="도면"
            title="도면 목록"
            description={
              canManage
                ? '등록된 도면을 확인하고 관리합니다. 도면명을 선택하면 수정 화면으로 이동합니다.'
                : '담당 구역이 있는 도면입니다. 도면명을 선택하면 배치와 담당 구역을 확인할 수 있습니다.'
            }
            actions={
              canManage ? (
                <Link
                  to="/drawings/new"
                  className={buttonClassName({ variant: 'primary', size: 'lg' })}
                >
                  도면 등록
                </Link>
              ) : null
            }
          />
        </div>

        <Card className="mt-5" aria-label="도면 검색">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
            <label htmlFor="drawing-search" className="sr-only">
              도면 제목 검색
            </label>
            <Input
              id="drawing-search"
              type="search"
              value={query}
              onChange={(event) => {
                setQuery(event.target.value);
                setPage(1);
              }}
              placeholder="도면 제목 검색"
              className="min-w-0 flex-1"
            />
          </div>
        </Card>

        <Card padded={false} className="mt-5 overflow-hidden" aria-label="도면 목록">
          {isPending ? (
            <div className="flex min-h-64 items-center justify-center px-6 text-center text-sm text-text-muted">
              도면을 불러오는 중...
            </div>
          ) : isError ? (
            <div className="flex min-h-64 items-center justify-center px-6">
              <ErrorState message={getDrawingErrorMessage(error)} className="w-full" />
            </div>
          ) : items.length > 0 ? (
            <>
              <DrawingListTable
                items={items}
                onDelete={handleDelete}
                onDuplicate={handleDuplicate}
                nameById={nameById}
                canManage={canManage}
              />
              <div className="flex flex-col items-center justify-between gap-3 border-t border-line px-5 py-3 sm:flex-row">
                <p className="text-sm tabular-nums text-text-muted">
                  총 {totalCount.toLocaleString()}건
                </p>
                <Pagination
                  page={page}
                  pageCount={pageCount}
                  onPageChange={setPage}
                  ariaLabel="도면 목록 페이지"
                />
              </div>
            </>
          ) : totalCount > 0 ? (
            <EmptyState
              icon={FileText}
              title="페이지에 표시할 도면이 없습니다."
              description="다른 페이지로 이동해 도면을 확인해 보세요."
            />
          ) : debouncedQuery.trim() ? (
            <EmptyState
              icon={FileText}
              title="검색 결과가 없습니다."
              description="다른 검색어로 도면을 검색해 보세요."
            />
          ) : (
            <EmptyState
              icon={FileText}
              title="등록된 도면이 없습니다."
              description="도면 등록 버튼으로 첫 도면을 만들어 보세요."
              action={
                <Link
                  to="/drawings/new"
                  className={buttonClassName({ variant: 'primary', size: 'md' })}
                >
                  도면 등록
                </Link>
              }
            />
          )}
        </Card>

        <Modal
          open={drawingToDelete !== null}
          onClose={() => setDrawingToDelete(null)}
          title="도면 삭제"
          size="sm"
          description={
            drawingToDelete !== null
              ? `도면 "${drawingToDelete.title}"을(를) 삭제하시겠습니까?`
              : undefined
          }
          footer={
            <>
              <Button type="button" variant="secondary" onClick={() => setDrawingToDelete(null)}>
                취소
              </Button>
              <Button type="button" variant="danger" onClick={confirmDelete}>
                삭제
              </Button>
            </>
          }
        >
          <p className="text-sm text-text-muted">삭제한 도면은 복구할 수 없습니다.</p>
        </Modal>

        <Modal
          open={drawingToBlock !== null}
          onClose={() => setDrawingToBlock(null)}
          title="도면 삭제 불가"
          size="sm"
          description={
            drawingToBlock !== null
              ? `도면 "${drawingToBlock.title}"은(는) 시뮬레이션이 연결되어 있어 삭제할 수 없습니다.`
              : undefined
          }
          footer={
            <Button type="button" variant="primary" onClick={() => setDrawingToBlock(null)}>
              확인
            </Button>
          }
        >
          <p className="text-sm text-text-muted">
            시뮬레이션 연결을 해제한 뒤 다시 삭제할 수 있습니다.
          </p>
        </Modal>
      </div>
    </main>
  );
}

export default DrawingListPage;
