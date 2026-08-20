import { RelatedRegulation } from './types';

export function RelatedLawCards({
  laws,
  loading,
  onSelect,
}: {
  laws: RelatedRegulation[];
  loading: boolean;
  onSelect: (lawId: string) => void;
}) {
  return (
    <section className="regulations-related-section">
      <div className="regulations-section-heading">
        <h3>관련 법령</h3>
        {loading && <span>불러오는 중</span>}
      </div>
      {laws.length > 0 ? (
        <div className="regulations-related-list">
          {laws.map((law) => (
            <button key={law.lawId} onClick={() => onSelect(law.lawId)} type="button">
              <strong>{law.name}</strong>
              <span>{law.relationship}</span>
            </button>
          ))}
        </div>
      ) : (
        !loading && <p className="regulations-subtle-message">연결된 관련 법령이 없습니다.</p>
      )}
    </section>
  );
}
