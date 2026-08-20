import { AxiosError } from 'axios';

export function getSimulationErrorMessage(error: unknown): string {
  if (error instanceof AxiosError) {
    const message = (error.response?.data as { message?: string } | undefined)?.message;
    if (message) {
      return message;
    }
    if (error.response?.status === 404) {
      return '시뮬레이션을 찾을 수 없습니다.';
    }
    if (error.response?.status === 409) {
      return '현재 상태에서는 시뮬레이션 설정을 변경하거나 실행할 수 없습니다.';
    }
    if (error.response?.status === 422) {
      return '시뮬레이션 설정이 올바르지 않습니다. 외곽선, 에이전트, 출입구를 확인해 주세요.';
    }
    if (error.response?.status === 503) {
      return '시뮬레이션 엔진을 사용할 수 없습니다. 설치와 실행 환경을 확인해 주세요.';
    }
  }
  return '요청 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.';
}
