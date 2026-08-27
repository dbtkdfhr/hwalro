import { readFileSync } from 'node:fs';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import Button, { buttonClassName } from './Button';
import Card from './Card';
import Modal from './Modal';
import PageHeader from './PageHeader';

describe('shared UI visual contract', () => {
  it('uses the shared primary and focus tokens for primary actions', () => {
    const classes = buttonClassName({ variant: 'primary', size: 'md' });
    expect(classes).toContain('bg-primary');
    expect(classes).toContain('focus-visible:ring-focus-ring');
    expect(classes).toContain('rounded-lg');
    expect(classes).not.toContain('scale-');
  });

  it('keeps cards flat by default', () => {
    const html = renderToStaticMarkup(<Card>내용</Card>);
    expect(html).toContain('border-line');
    expect(html).not.toContain('shadow-card');
  });

  it('renders page hierarchy without uppercase eyebrow styling', () => {
    const html = renderToStaticMarkup(
      <PageHeader eyebrow="안전 검토" title="도면 목록" description="등록된 도면" />,
    );
    expect(html).not.toContain('uppercase');
    expect(html).not.toContain('font-black');
  });

  it('disables a loading button', () => {
    const html = renderToStaticMarkup(<Button isLoading>저장</Button>);
    expect(html).toContain('disabled');
  });

  it('keeps projector workspace surfaces light and visibly separated', () => {
    const globalStyles = readFileSync(new URL('../../index.css', import.meta.url), 'utf8');

    expect(globalStyles).toContain('--color-workspace: #eef2f0;');
    expect(globalStyles).toContain('--color-canvas-surround: #dfe6e3;');
    expect(globalStyles).toContain('--color-workspace-panel: #ffffff;');
    expect(globalStyles).toContain('--color-workspace-line: #879690;');
    expect(globalStyles).toContain('--color-workspace-text: #17201e;');
    expect(globalStyles).toContain('--color-workspace-muted: #4f5f5a;');
  });

  it('uses one backdrop treatment for every modal implementation', () => {
    const globalStyles = readFileSync(new URL('../../index.css', import.meta.url), 'utf8');
    const modalSource = readFileSync(new URL('./Modal.tsx', import.meta.url), 'utf8');
    const riskZoneSource = readFileSync(
      new URL('../../features/risks/components/RiskZoneEditorDialog.tsx', import.meta.url),
      'utf8',
    );
    const reportDraftSource = readFileSync(
      new URL('../../features/simulationResult/components/ReportDraftDialog.tsx', import.meta.url),
      'utf8',
    );
    const systemManagementSource = readFileSync(
      new URL('../../pages/SystemManagementPage.tsx', import.meta.url),
      'utf8',
    );
    const agentDeletionSource = readFileSync(
      new URL('../../features/simulations/components/AgentDeletionFeedback.tsx', import.meta.url),
      'utf8',
    );

    expect(globalStyles).toContain('--modal-backdrop-color: rgb(16 23 21 / 0.38);');
    expect(globalStyles).toContain('--modal-backdrop-filter: blur(6px);');
    expect(globalStyles).toContain('.app-modal-backdrop');
    expect(globalStyles).toContain('dialog.app-modal-dialog::backdrop');
    for (const source of [modalSource, riskZoneSource, reportDraftSource, systemManagementSource]) {
      expect(source).toContain('app-modal-backdrop');
    }
    expect(agentDeletionSource).toContain('app-modal-dialog');
  });

  it('renders nested modals above workspace dialogs', () => {
    const html = renderToStaticMarkup(
      <Modal open onClose={() => undefined} layer="nested">
        법령 첨부
      </Modal>,
    );
    const riskZoneSource = readFileSync(
      new URL('../../features/risks/components/RiskZoneEditorDialog.tsx', import.meta.url),
      'utf8',
    );

    expect(html).toContain('z-[110]');
    expect(riskZoneSource).toContain('layer="nested"');
  });
});
