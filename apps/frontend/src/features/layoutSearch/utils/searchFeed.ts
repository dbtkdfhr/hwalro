import type { CandidateStatus, LayoutSearch, SearchCandidate } from '../api/layoutSearchApi';

export type SearchReplyTone = 'queued' | 'running' | 'done' | 'failed';

export interface SearchReply {
  key: string;
  tone: SearchReplyTone;
  text: string;
  simulationId: number | null;
}

const FINDING_STATUSES = ['PENDING', 'DIAGNOSING', 'GENERATING'];

function isTrialStatus(status: CandidateStatus) {
  return status !== 'REJECTED_CONSTRAINT' && status !== 'GENERATED';
}

export function trialCandidates(search: LayoutSearch): SearchCandidate[] {
  return [...search.improvedCandidates, ...search.rejectedCandidates]
    .filter((candidate) => isTrialStatus(candidate.status))
    .sort((a, b) => a.candidateId - b.candidateId);
}

function candidateReply(candidate: SearchCandidate): SearchReply {
  const label = `개선안 #${candidate.candidateId}`;
  const prepared = candidate.preparedSimulation ?? null;
  const base = {
    key: `c-${candidate.candidateId}`,
    simulationId: prepared?.simulationId ?? null,
  };
  if (candidate.status === 'RUNNING') {
    return { ...base, tone: 'running', text: `${label} 검증 실행 중` };
  }
  switch (candidate.status) {
    case 'EVALUATED':
      return { ...base, tone: 'done', text: `${label} 실행 완료 · 개선 확인` };
    case 'NOT_IMPROVED':
      return { ...base, tone: 'done', text: `${label} 실행 완료 · 개선 없음` };
    case 'QUEUED':
      return derivedSimulationReply(base, label, prepared);
    default:
      return { ...base, tone: 'failed', text: `${label} 실행 실패` };
  }
}

function derivedSimulationReply(
  base: { key: string; simulationId: number | null },
  label: string,
  prepared: SearchCandidate['preparedSimulation'],
): SearchReply {
  if (!prepared) {
    return { ...base, tone: 'queued', text: `${label} 대기 중` };
  }
  switch (prepared.status) {
    case 'DRAFT':
      return { ...base, tone: 'queued', text: `${label} 초안 준비됨 · 실행 대기` };
    case 'REQUESTED':
    case 'RUNNING':
      return { ...base, tone: 'running', text: `${label} 시뮬레이션 실행 중` };
    case 'COMPLETED':
      return { ...base, tone: 'done', text: `${label} 실행 완료` };
    case 'CANCELLED':
      return { ...base, tone: 'failed', text: `${label} 실행 취소됨` };
    default:
      return { ...base, tone: 'failed', text: `${label} 실행 실패` };
  }
}

function findingReply(search: LayoutSearch, trials: SearchCandidate[]): SearchReply {
  if (FINDING_STATUSES.includes(search.status)) {
    return { key: 'finding', tone: 'running', text: '개선안 후보를 찾는 중', simulationId: null };
  }
  if (trials.length > 0) {
    return {
      key: 'finding',
      tone: 'done',
      text: `개선안 후보 ${trials.length}개를 찾았습니다`,
      simulationId: null,
    };
  }
  return {
    key: 'finding',
    tone: 'done',
    text: '개선 가능한 후보를 찾지 못했습니다',
    simulationId: null,
  };
}

function summaryReply(search: LayoutSearch): SearchReply | null {
  switch (search.status) {
    case 'COMPLETED':
      return {
        key: 'summary',
        tone: 'done',
        text: '배치 개선안 찾기가 완료되었습니다',
        simulationId: null,
      };
    case 'NO_IMPROVEMENT':
      return {
        key: 'summary',
        tone: 'done',
        text: '개선된 배치를 찾지 못했습니다',
        simulationId: null,
      };
    case 'CANCELLED':
      return { key: 'summary', tone: 'done', text: '탐색이 취소되었습니다', simulationId: null };
    case 'FAILED':
      return { key: 'summary', tone: 'failed', text: '탐색이 실패했습니다', simulationId: null };
    default:
      return null;
  }
}

export function layoutSearchReplies(search: LayoutSearch): SearchReply[] {
  const trials = trialCandidates(search);
  const replies: SearchReply[] = [findingReply(search, trials)];
  trials.forEach((candidate) => replies.push(candidateReply(candidate)));
  const summary = summaryReply(search);
  if (summary) {
    replies.push(summary);
  }
  return replies;
}
