import { ActiveReviewCard } from '../features/home/components/ActiveReviewCard';
import { PriorityRiskPanel } from '../features/home/components/PriorityRiskPanel';
import { QuickActionCard } from '../features/home/components/QuickActionCard';
import { RecentSimulationTable } from '../features/home/components/RecentSimulationTable';
import { WorkStatusCards } from '../features/home/components/WorkStatusCards';
import { useHomeDashboard } from '../features/home/hooks/useHomeDashboard';
import { getSimulationErrorMessage } from '../features/simulations/utils/getSimulationErrorMessage';
import { getRiskErrorMessage } from '../features/risks/utils/getRiskErrorMessage';

function HomePage() {
  const { activeReview, workSummary, recentSimulations, priorityRisks } = useHomeDashboard();

  return (
    <div className="mx-auto w-full max-w-[1360px] px-1 pb-10 sm:px-4">
      <h1 className="sr-only">홈</h1>

      <div className="grid grid-cols-1 items-stretch gap-5 lg:grid-cols-12">
        <div className="lg:col-span-8">
          <ActiveReviewCard
            review={activeReview.data}
            isPending={activeReview.isPending}
            isError={activeReview.isError}
            errorMessage={activeReview.errorMessage}
            onRetry={activeReview.refetch}
          />
        </div>
        <div className="lg:col-span-4">
          <QuickActionCard />
        </div>
      </div>

      <h2 className="mt-8 text-lg font-black tracking-tight text-ink">내 업무 현황</h2>
      <div className="mt-4">
        <WorkStatusCards
          summary={workSummary.data}
          isPending={workSummary.isPending}
          isError={workSummary.isError}
          errorMessage={getSimulationErrorMessage(workSummary.error)}
          onRetry={workSummary.refetch}
        />
      </div>

      <div className="mt-8 grid grid-cols-1 items-stretch gap-5 lg:grid-cols-12">
        <div className="lg:col-span-8">
          <RecentSimulationTable
            rows={recentSimulations.data}
            isPending={recentSimulations.isPending}
            isError={recentSimulations.isError}
            errorMessage={getSimulationErrorMessage(recentSimulations.error)}
            onRetry={recentSimulations.refetch}
          />
        </div>
        <div className="lg:col-span-4">
          <PriorityRiskPanel
            items={priorityRisks.data}
            totalCount={priorityRisks.totalCount}
            isPending={priorityRisks.isPending}
            isError={priorityRisks.isError}
            errorMessage={getRiskErrorMessage(priorityRisks.error)}
            onRetry={priorityRisks.refetch}
          />
        </div>
      </div>
    </div>
  );
}

export default HomePage;
