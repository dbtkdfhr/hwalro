import { AxiosError } from 'axios';

export function getErrorMessage(error: unknown): string {
  if (error instanceof AxiosError) {
    const message = (error.response?.data as { message?: string } | undefined)?.message;
    if (message) {
      return message;
    }
    if (error.response?.status === 401) {
      return '아이디 또는 비밀번호가 일치하지 않습니다.';
    }
  }
  return '로그인 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.';
}
