import { MutableRefObject } from 'react';
import { ArticleList } from './ArticleList';
import { RelatedLawCards } from './RelatedLawCards';
import { RegulationDetail, RelatedRegulation } from './types';

type Props = {
  detail: RegulationDetail | null;
  loading: boolean;
  error: string;
  relatedLaws: RelatedRegulation[];
  relatedLoading: boolean;
  articleRefs: MutableRefObject<Record<number, HTMLElement | null>>;
  onSelectRelated: (lawId: string) => void;
};
const formatDate = (value: string) =>
  value.length === 8 ? `${value.slice(0, 4)}.${value.slice(4, 6)}.${value.slice(6)}` : value;

export function RegulationDetailPanel({
  detail,
  loading,
  error,
  relatedLaws,
  relatedLoading,
  articleRefs,
  onSelectRelated,
}: Props) {
  if (loading)
    return (
      <section className="regulations-panel regulations-detail" aria-live="polite">
        <p className="regulations-status">법령 상세를 불러오는 중입니다.</p>
      </section>
    );
  if (error)
    return (
      <section className="regulations-panel regulations-detail" aria-live="polite">
        <p className="regulations-status regulations-status--error">{error}</p>
      </section>
    );
  if (!detail)
    return (
      <section className="regulations-panel regulations-detail" aria-live="polite">
        <p className="regulations-status">좌측 목록에서 법령을 선택하세요.</p>
      </section>
    );
  const normalArticles = detail.articles.filter((article) => !article.section);
  const featuredArticle =
    normalArticles.find((article) => article.number === '1') ?? normalArticles[0];
  const keyArticles = normalArticles.filter((article) => article !== featuredArticle).slice(0, 3);
  return (
    <section className="regulations-panel regulations-detail" aria-live="polite">
      <div className="regulations-detail__scroll">
        <div className="regulations-detail__intro">
          <p className="regulations-page__eyebrow">법령 상세</p>
          <h2>{detail.name}</h2>
          <p>
            {detail.lawType} · {detail.competentAuthority} · 시행 {formatDate(detail.effectiveDate)}
          </p>
        </div>
        {featuredArticle && (
          <article className="regulations-featured-article">
            <p className="regulations-page__eyebrow">대표 조문</p>
            <h3>
              제{featuredArticle.number}조 {featuredArticle.title && `(${featuredArticle.title})`}
            </h3>
            <p>{featuredArticle.content}</p>
          </article>
        )}
        <RelatedLawCards laws={relatedLaws} loading={relatedLoading} onSelect={onSelectRelated} />
        {keyArticles.length > 0 && (
          <section className="regulations-key-articles">
            <h3>주요 조문</h3>
            <div>
              {keyArticles.map((article) => {
                const index = detail.articles.indexOf(article);
                return (
                  <button
                    key={`${article.number}-${article.title}`}
                    onClick={() =>
                      articleRefs.current[index]?.scrollIntoView({
                        behavior: 'smooth',
                        block: 'start',
                      })
                    }
                    type="button"
                  >
                    제{article.number}조 {article.title}
                  </button>
                );
              })}
            </div>
          </section>
        )}
        <ArticleList articles={detail.articles} articleRefs={articleRefs} />
      </div>
    </section>
  );
}
