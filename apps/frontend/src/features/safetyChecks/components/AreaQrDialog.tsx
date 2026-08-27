import { useEffect, useState } from 'react';
import { Check, Copy, Download, Printer } from 'lucide-react';
import QRCode from 'qrcode';
import type { InspectionArea } from '../types';
import { Button } from '../../../components/ui';
import Modal from '../../../components/ui/Modal';

interface AreaQrDialogProps {
  area: InspectionArea | null;
  onClose: () => void;
}

function buildInspectUrl(areaId: number): string {
  return `${window.location.origin}/inspect/${areaId}`;
}

function AreaQrDialog({ area, onClose }: AreaQrDialogProps) {
  const [dataUrl, setDataUrl] = useState<string | null>(null);
  const [qrError, setQrError] = useState<string | null>(null);
  const [copyNotice, setCopyNotice] = useState(false);
  const [copyError, setCopyError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!area) return;
    let active = true;
    setDataUrl(null);
    setQrError(null);
    setCopyNotice(false);
    setCopyError(null);
    void QRCode.toDataURL(buildInspectUrl(area.id), { width: 640, margin: 2 })
      .then((url) => {
        if (active) setDataUrl(url);
      })
      .catch(() => {
        if (active) setQrError('QR 코드를 생성하지 못했습니다. 다시 시도해 주세요.');
      });
    return () => {
      active = false;
    };
  }, [area, attempt]);

  if (!area) return null;

  const areaId = area.id;
  const inspectUrl = buildInspectUrl(areaId);

  async function copyUrl() {
    try {
      await navigator.clipboard.writeText(inspectUrl);
      setCopyNotice(true);
      setCopyError(null);
      window.setTimeout(() => setCopyNotice(false), 2000);
    } catch {
      setCopyNotice(false);
      setCopyError('링크를 복사하지 못했습니다. 아래 주소를 직접 입력해 주세요.');
    }
  }

  function download() {
    if (!dataUrl) return;
    const anchor = document.createElement('a');
    anchor.href = dataUrl;
    anchor.download = `safety-checklist-${areaId}.png`;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
  }

  return (
    <>
      <Modal
        open
        onClose={onClose}
        title="체크리스트 QR 배포"
        description="QR을 인쇄해 현장에 부착하면, 직원이 휴대폰으로 찍어 이 구역의 점검을 진행할 수 있습니다."
        size="sm"
      >
        <div className="flex flex-col items-center">
          {dataUrl ? (
            <img
              src={dataUrl}
              alt={`${area.name} 체크리스트 QR 코드`}
              className="h-56 w-56 rounded-xl border border-line bg-white p-2"
            />
          ) : qrError ? (
            <div className="flex h-56 w-56 flex-col items-center justify-center gap-3 rounded-xl border border-line bg-surface px-4 text-center">
              <span className="text-sm text-text-muted">{qrError}</span>
              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={() => setAttempt((value) => value + 1)}
              >
                다시 시도
              </Button>
            </div>
          ) : (
            <div className="flex h-56 w-56 items-center justify-center rounded-xl border border-line bg-surface">
              <span className="text-sm text-text-muted">QR 생성 중...</span>
            </div>
          )}
          <p className="mt-4 text-sm font-bold text-ink">{area.name}</p>
          <p className="mt-1 w-full break-all text-center text-xs text-text-muted">{inspectUrl}</p>
          <div className="mt-4 flex w-full gap-2">
            <Button
              type="button"
              variant="secondary"
              className="flex-1"
              onClick={() => void copyUrl()}
            >
              {copyNotice ? (
                <Check aria-hidden="true" className="h-4 w-4" />
              ) : (
                <Copy aria-hidden="true" className="h-4 w-4" />
              )}
              {copyNotice ? '복사됨' : '링크 복사'}
            </Button>
            <Button
              type="button"
              variant="secondary"
              className="flex-1"
              onClick={download}
              disabled={!dataUrl}
            >
              <Download aria-hidden="true" className="h-4 w-4" />
              이미지
            </Button>
            <Button
              type="button"
              className="flex-1"
              onClick={() => window.print()}
              disabled={!dataUrl}
            >
              <Printer aria-hidden="true" className="h-4 w-4" />
              인쇄
            </Button>
          </div>
          {copyError && (
            <p
              role="alert"
              className="mt-3 w-full text-center text-xs font-bold text-danger-strong"
            >
              {copyError}
            </p>
          )}
        </div>
      </Modal>
      <div className="checklist-print-area hidden print:block">
        <div className="flex flex-col items-center px-6 py-8">
          <p className="text-lg font-bold">안전 점검 체크리스트</p>
          <p className="mt-1 text-sm">{area.name}</p>
          {dataUrl ? <img src={dataUrl} alt="" className="mt-6 h-64 w-64" /> : null}
          <p className="mt-4 text-xs text-ink/60">휴대폰으로 QR을 스캔하여 점검을 진행하세요.</p>
          <p className="mt-1 text-xs text-ink/60">{inspectUrl}</p>
        </div>
      </div>
    </>
  );
}

export default AreaQrDialog;
