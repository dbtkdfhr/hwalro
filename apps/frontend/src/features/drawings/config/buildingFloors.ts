export type FloorId = 'B2' | 'B1' | 'F1' | 'F2' | 'F3' | 'F4' | 'F5' | 'F6';

export interface BuildingFloor {
  id: FloorId;
  label: string;
  theme: string;
  description: string;
  linked: boolean;
}

export const BUILDING = {
  name: '더현대 서울',
  nameEn: 'THE HYUNDAI SEOUL',
  address: '서울특별시 영등포구 여의대로 108',
} as const;

export const BUILDING_FLOORS: BuildingFloor[] = [
  {
    id: 'F6',
    label: '6F',
    theme: 'Dining & Art',
    description: '전문식당가와 문화 공간이 있는 최상층입니다.',
    linked: false,
  },
  {
    id: 'F5',
    label: '5F',
    theme: 'Sounds Forest',
    description: '중앙 정원과 아동·가전 존이 있는 층입니다.',
    linked: false,
  },
  {
    id: 'F4',
    label: '4F',
    theme: 'Life & Balance',
    description: '리빙, 골프, 레저 브랜드가 모인 층입니다.',
    linked: false,
  },
  {
    id: 'F3',
    label: '3F',
    theme: 'About Fashion',
    description: '여성·남성 패션과 잡화 매장이 있는 층입니다.',
    linked: false,
  },
  {
    id: 'F2',
    label: '2F',
    theme: 'Modern Mood',
    description: '글로벌 패션과 럭셔리 슈즈 층입니다.',
    linked: false,
  },
  {
    id: 'F1',
    label: '1F',
    theme: 'Exclusive Label',
    description: '해외 명품과 뷰티, 워터폴 가든이 있는 층입니다.',
    linked: false,
  },
  {
    id: 'B1',
    label: 'B1',
    theme: 'Tasty Seoul',
    description: '현대식품관과 푸드 스트리트가 있는 층입니다.',
    linked: false,
  },
  {
    id: 'B2',
    label: 'B2',
    theme: 'Creative Ground',
    description:
      '팝업과 컬처 층입니다. 기본 도면(더현대 지하 2층)이 연결되어 바로 검토를 시작할 수 있습니다.',
    linked: true,
  },
];

export const FLOORS_BY_ID = Object.fromEntries(
  BUILDING_FLOORS.map((floor) => [floor.id, floor]),
) as Record<FloorId, BuildingFloor>;

export const LINKED_FLOOR_IDS: ReadonlySet<FloorId> = new Set(
  BUILDING_FLOORS.filter((floor) => floor.linked).map((floor) => floor.id),
);
