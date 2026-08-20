import { AxiosError } from 'axios';

export function getDrawingErrorMessage(error: unknown): string {
  if (error instanceof AxiosError) {
    const message = (error.response?.data as { message?: string } | undefined)?.message;
    if (message) {
      return message;
    }
    if (error.response?.status === 404) {
      return '도면을 찾을 수 없습니다.';
    }
  }
  return '요청 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.';
}
