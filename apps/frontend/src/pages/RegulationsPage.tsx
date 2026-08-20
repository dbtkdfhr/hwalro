import { FormEvent, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { RegulationDetailPanel } from './regulations/RegulationDetailPanel';
import { RegulationSearchResults } from './regulations/RegulationSearchResults';
import {
  RegulationDetail,
  RegulationSummary,
  RelatedRegulation,
  SearchResponse,
} from './regulations/types';
import './RegulationsPage.css';

const PAGE_SIZE = 20;

async function request<T>(path: string): Promise<T> {
  const response = await fetch(path);
  if (!response.ok) throw new Error('법령 정보를 불러오지 못했습니다.');
  return response.json() as Promise<T>;
}

function RegulationsPage() {
  const [searchParams] = useSearchParams();
  const urlSerialNumber = searchParams.get('serialNumber');
  const urlQuery = searchParams.get('query') ?? '';
  const [query, setQuery] = useState('');
  const [activeQuery, setActiveQuery] = useState('');
  const [items, setItems] = useState<RegulationSummary[]>([]);
  const [page, setPage] = useState(1);
  const [totalCount, setTotalCount] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState('');
  const [selectedSerialNumber, setSelectedSerialNumber] = useState('');
  const [detail, setDetail] = useState<RegulationDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');
  const [relatedLaws, setRelatedLaws] = useState<RelatedRegulation[]>([]);
  const [relatedLoading, setRelatedLoading] = useState(false);
  const articleRefs = useRef<Record<number, HTMLElement | null>>({});
  const hasLoadedInitialList = useRef(false);
  const latestListRequestId = useRef(0);

  async function loadRelatedLaws(lawId: string) {
    setRelatedLoading(true);
    try {
      setRelatedLaws(await request<RelatedRegulation[]>(`/api/regulations/${lawId}/related-laws`));
    } catch {
      setRelatedLaws([]);
    } finally {
      setRelatedLoading(false);
    }
  }
  async function selectDetail(path: string, serialNumber = '') {
    setDetailLoading(true);
    setDetailError('');
    setSelectedSerialNumber(serialNumber);
    try {
      const nextDetail = await request<RegulationDetail>(path);
      setDetail(nextDetail);
      void loadRelatedLaws(nextDetail.lawId);
    } catch (error) {
      setDetail(null);
      setRelatedLaws([]);
      setDetailError(error instanceof Error ? error.message : '법령 상세를 불러오지 못했습니다.');
    } finally {
      setDetailLoading(false);
    }
  }
  function selectLaw(serialNumber: string) {
    void selectDetail(`/api/regulations/${serialNumber}`, serialNumber);
  }
  function selectLawById(lawId: string) {
    void selectDetail(`/api/regulations/by-law-id/${lawId}`);
  }
  async function loadRegulations(
    nextPage: number,
    replace: boolean,
    searchQuery: string,
    initialSerialNumber = '',
  ) {
    const requestId = ++latestListRequestId.current;
    setListLoading(true);
    setListError('');
    let firstSerialNumber = '';
    try {
      const params = new URLSearchParams({ page: String(nextPage), size: String(PAGE_SIZE) });
      if (searchQuery) params.set('query', searchQuery);
      const result = await request<SearchResponse>(`/api/regulations?${params}`);
      if (requestId !== latestListRequestId.current) return;
      setItems((current) => (replace ? result.items : [...current, ...result.items]));
      setPage(result.page);
      setTotalCount(result.totalCount);
      setHasNext(result.hasNext);
      firstSerialNumber = result.items[0]?.serialNumber ?? '';
    } catch (error) {
      if (requestId !== latestListRequestId.current) return;
      setListError(error instanceof Error ? error.message : '법령 목록을 불러오지 못했습니다.');
      if (replace) {
        setItems([]);
        setDetail(null);
      }
    } finally {
      if (requestId === latestListRequestId.current) setListLoading(false);
    }
    if (
      replace &&
      requestId === latestListRequestId.current &&
      (initialSerialNumber || firstSerialNumber)
    ) {
      selectLaw(initialSerialNumber || firstSerialNumber);
    }
  }
  useEffect(() => {
    if (urlSerialNumber) {
      setQuery(urlQuery);
      setActiveQuery(urlQuery);
      void loadRegulations(1, true, urlQuery, urlSerialNumber);
      return;
    }
    if (!hasLoadedInitialList.current) {
      hasLoadedInitialList.current = true;
      void loadRegulations(1, true, '');
    }
  }, [urlQuery, urlSerialNumber]);
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextQuery = query.trim();
    setActiveQuery(nextQuery);
    void loadRegulations(1, true, nextQuery);
  }
  function handleResultScroll(event: React.UIEvent<HTMLDivElement>) {
    const { scrollTop, clientHeight, scrollHeight } = event.currentTarget;
    if (scrollTop + clientHeight >= scrollHeight - 40 && hasNext && !listLoading)
      void loadRegulations(page + 1, false, activeQuery);
  }
  return (
    <main className="regulations-page">
      <div className="regulations-page__content">
        <header className="regulations-page__heading">
          <p className="regulations-page__eyebrow">법령 조회</p>
          <h1>안전 법령</h1>
          <p>안전 검토에 필요한 법령을 검색합니다.</p>
        </header>
        <form className="regulations-search" onSubmit={handleSubmit}>
          <label className="regulations-page__sr-only" htmlFor="law-search">
            법령 검색어
          </label>
          <input
            id="law-search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="예: 화재, 도로, 다중이용업소"
          />
          <button type="submit">검색</button>
        </form>
        <section className="regulations-workspace" aria-label="법령 조회">
          <RegulationSearchResults
            totalCount={totalCount}
            items={items}
            loading={listLoading}
            error={listError}
            selectedSerialNumber={selectedSerialNumber}
            onScroll={handleResultScroll}
            onSelect={selectLaw}
          />
          <RegulationDetailPanel
            detail={detail}
            loading={detailLoading}
            error={detailError}
            relatedLaws={relatedLaws}
            relatedLoading={relatedLoading}
            articleRefs={articleRefs}
            onSelectRelated={selectLawById}
          />
        </section>
      </div>
    </main>
  );
}

export default RegulationsPage;
