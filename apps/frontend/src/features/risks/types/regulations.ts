export type RegulationSummary = {
  serialNumber: string;
  lawId: string;
  name: string;
  lawType: string;
  effectiveDate: string;
};

export type RegulationArticle = {
  number: string;
  title: string;
  content: string;
  section: boolean;
};

export type RegulationDetail = {
  lawId: string;
  name: string;
  lawType: string;
  competentAuthority: string;
  effectiveDate: string;
  articles: RegulationArticle[];
};

export type SearchResponse = {
  totalCount: number;
  page: number;
  hasNext: boolean;
  items: RegulationSummary[];
};
