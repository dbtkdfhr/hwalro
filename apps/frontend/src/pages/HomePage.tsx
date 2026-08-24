import { ActiveReviewCard } from '../features/home/components/ActiveReviewCard';
import { PriorityRiskPanel } from '../features/home/components/PriorityRiskPanel';
import { WorkStatusCards } from '../features/home/components/WorkStatusCards';
import { useHomeDashboard } from '../features/home/hooks/useHomeDashboard';
import { getSimulationErrorMessage } from '../features/simulations/utils/getSimulationErrorMessage';
import { getRiskErrorMessage } from '../features/risks/utils/getRiskErrorMessage';
import '../features/home/home.css';

function HomePage() {
  const { activeReview, workSummary, priorityRisks } = useHomeDashboard();

  return (
    <div className="home-dashboard">
      <h1 className="sr-only">홈</h1>

      <div className="home-dashboard__active-review">
        <ActiveReviewCard
          review={activeReview.data}
          isPending={activeReview.isPending}
          isError={activeReview.isError}
          errorMessage={activeReview.errorMessage}
          onRetry={activeReview.refetch}
        />
      </div>

      <div className="home-dashboard__overview-grid">
        <aside className="home-dashboard__work-status" aria-labelledby="home-work-status-title">
          <h2 id="home-work-status-title" className="home-dashboard__section-title">
            내 업무 현황
          </h2>
          <WorkStatusCards
            summary={workSummary.data}
            isPending={workSummary.isPending}
            isError={workSummary.isError}
            errorMessage={getSimulationErrorMessage(workSummary.error)}
            onRetry={workSummary.refetch}
          />
        </aside>
        <div className="home-dashboard__risks">
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
