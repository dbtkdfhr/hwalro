import { describe, expect, it } from 'vitest';
import {
  DRAWING_WORKSPACE_LOADING_MESSAGE,
  SIMULATION_RESULT_LOADING_MESSAGE,
  SIMULATION_SETUP_LOADING_MESSAGE,
} from './workspaceLoadingMessages';

describe('workspace loading messages', () => {
  it('각 핵심 작업 화면은 진입 단계 전체에서 사용할 단일 문구를 제공한다', () => {
    expect(DRAWING_WORKSPACE_LOADING_MESSAGE).toBe('도면을 불러오는 중입니다.');
    expect(SIMULATION_SETUP_LOADING_MESSAGE).toBe('시뮬레이션 설정을 불러오는 중입니다.');
    expect(SIMULATION_RESULT_LOADING_MESSAGE).toBe('시뮬레이션 결과를 준비하고 있습니다.');
  });
});
