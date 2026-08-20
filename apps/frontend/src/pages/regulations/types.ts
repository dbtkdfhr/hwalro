export type {
  RegulationArticle,
  RegulationDetail,
  RegulationSummary,
  SearchResponse,
} from '../../features/risks/types/regulations';

export type RelatedRegulation = { lawId: string; name: string; relationship: string };
