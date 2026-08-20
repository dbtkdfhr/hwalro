import { UIEvent } from 'react';
import { RegulationSummary } from './types';

type Props = {
  totalCount: number;
  items: RegulationSummary[];
  loading: boolean;
  error: string;
  selectedSerialNumber: string;
  onScroll: (event: UIEvent<HTMLDivElement>) => void;
  onSelect: (serialNumber: string) => void;
};

const formatDate = (value: string) =>
  value.length === 8 ? `${value.slice(0, 4)}.${value.slice(4, 6)}.${value.slice(6)}` : value;

export function RegulationSearchResults({
  totalCount,
  items,
  loading,
  error,
  selectedSerialNumber,
  onScroll,
  onSelect,
}: Props) {
  return (
    <aside className="regulations-panel">
      <div className="regulations-panel__heading">
        <h2>검색 결과</h2>
        <span>{totalCount}건</span>
      </div>
      <div className="regulations-result-list" onScroll={onScroll}>
        {error && <p className="regulations-status regulations-status--error">{error}</p>}
        {!loading && !error && items.length === 0 && (
          <p className="regulations-status">검색 결과가 없습니다.</p>
        )}
        {items.map((item) => (
          <button
            className={`regulations-result-item ${selectedSerialNumber === item.serialNumber ? 'is-selected' : ''}`}
            key={item.serialNumber}
            onClick={() => onSelect(item.serialNumber)}
            type="button"
          >
            <strong>{item.name}</strong>
            <span>
              {item.lawType} · 시행 {formatDate(item.effectiveDate)}
            </span>
          </button>
        ))}
        {loading && <p className="regulations-status">법령 목록을 불러오는 중입니다.</p>}
      </div>
    </aside>
  );
}
