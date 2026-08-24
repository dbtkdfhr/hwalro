import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useRecordLastActivity } from '../../home/hooks/useRecordLastActivity';
import { useCreateDrawing } from '../hooks/useDrawingMutations';
import {
  Button,
  buttonClassName,
  Card,
  ErrorState,
  Field,
  Input,
  PageHeader,
  Textarea,
} from '../../../components/ui';
import { getDrawingErrorMessage } from '../utils/getDrawingErrorMessage';

function CreateDrawingPage() {
  const navigate = useNavigate();
  const createDrawing = useCreateDrawing();
  const recordLastActivity = useRecordLastActivity();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [withDefaultData, setWithDefaultData] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setErrorMessage(null);
    createDrawing.mutate(
      {
        title: title.trim() === '' ? null : title.trim(),
        description: description.trim() === '' ? null : description.trim(),
        withDefaultData,
      },
      {
        onSuccess: (drawing) => {
          recordLastActivity('LAYOUT_EDIT', drawing.id);
          navigate(`/layout/${drawing.id}`);
        },
        onError: (error) => setErrorMessage(getDrawingErrorMessage(error)),
      },
    );
  };

  return (
    <main className="bg-background">
      <div className="mx-auto w-full max-w-[1360px] px-1 pt-2 pb-10 sm:px-4 lg:pt-4">
        <div className="border-b border-line pb-6">
          <PageHeader
            eyebrow="도면"
            title="도면 등록"
            description="기본 도면 데이터로 시작하거나 빈 도면으로 시작할 수 있습니다. 등록 후 편집 화면에서 배치를 수정할 수 있습니다."
          />
        </div>

        <form onSubmit={handleSubmit} className="mt-5">
          <Card className="bg-surface-raised shadow-neu-raised sm:p-6" aria-label="도면 정보 입력">
            <Field label="시작 방식">
              <div
                role="radiogroup"
                aria-label="시작 방식"
                className="grid grid-cols-1 gap-3 lg:grid-cols-2"
              >
                <label className="flex cursor-pointer items-start gap-3 rounded-xl border border-line bg-surface-raised p-4 shadow-neu-raised transition-[border-color,background-color,box-shadow] has-checked:border-primary has-checked:bg-surface-sunken has-checked:shadow-neu-pressed">
                  <input
                    type="radio"
                    name="drawing-init"
                    checked={withDefaultData}
                    onChange={() => setWithDefaultData(true)}
                    className="mt-1 accent-primary"
                  />
                  <span>
                    <span className="block text-sm font-bold text-ink">기본 도면으로 시작</span>
                    <span className="mt-1 block text-xs text-text-muted">
                      벽·출구·기둥 등 예시 배치가 포함된 도면으로 시작합니다. 참고해 수정하기
                      좋습니다.
                    </span>
                  </span>
                </label>
                <label className="flex cursor-pointer items-start gap-3 rounded-xl border border-line bg-surface-raised p-4 shadow-neu-raised transition-[border-color,background-color,box-shadow] has-checked:border-primary has-checked:bg-surface-sunken has-checked:shadow-neu-pressed">
                  <input
                    type="radio"
                    name="drawing-init"
                    checked={!withDefaultData}
                    onChange={() => setWithDefaultData(false)}
                    className="mt-1 accent-primary"
                  />
                  <span>
                    <span className="block text-sm font-bold text-ink">빈 도면으로 시작</span>
                    <span className="mt-1 block text-xs text-text-muted">
                      벽과 출구 없이 빈 캔버스로 시작합니다. 처음부터 직접 배치를 그립니다.
                    </span>
                  </span>
                </label>
              </div>
            </Field>

            <div className="mt-5 grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
              <Field label="도면명">
                <Input
                  type="text"
                  value={title}
                  onChange={(event) => setTitle(event.target.value)}
                  placeholder="기본 도면 이름이 사용됩니다"
                  maxLength={200}
                />
              </Field>
              <Field label="설명">
                <Textarea
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                  placeholder="도면에 대한 설명을 입력해 주세요. (선택)"
                  maxLength={10000}
                  rows={3}
                />
              </Field>
            </div>

            {errorMessage !== null && <ErrorState message={errorMessage} className="mt-5" />}

            <div className="mt-6 flex items-center gap-3 border-t border-line pt-5">
              <Button
                type="submit"
                size="lg"
                isLoading={createDrawing.isPending}
                disabled={createDrawing.isPending}
              >
                {createDrawing.isPending ? '등록 중...' : '도면 등록'}
              </Button>
              <Link
                to="/drawings"
                className={buttonClassName({ variant: 'secondary', size: 'lg' })}
              >
                취소
              </Link>
            </div>
          </Card>
        </form>
      </div>
    </main>
  );
}

export default CreateDrawingPage;
