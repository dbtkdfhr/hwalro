import { RegulationArticle } from './types';

export function ArticleList({
  articles,
  articleRefs,
}: {
  articles: RegulationArticle[];
  articleRefs: React.MutableRefObject<Record<number, HTMLElement | null>>;
}) {
  return (
    <section className="regulations-all-articles">
      <h3>전체 조문</h3>
      {articles.map((article, index) => (
        <article
          className={
            article.section ? 'regulations-article-section' : 'regulations-article-content'
          }
          key={`${article.number}-${article.title}-${index}`}
          ref={(element) => {
            articleRefs.current[index] = element;
          }}
        >
          {article.section ? (
            <h4>{article.content}</h4>
          ) : (
            <>
              <h4>
                제{article.number}조 {article.title && `(${article.title})`}
              </h4>
              <p>{article.content}</p>
            </>
          )}
        </article>
      ))}
    </section>
  );
}
