package com.hwalro.simulation.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class LayoutSearchProcessRegistryTest {

    @Test
    void stopsProcessWhenCancellationArrivesBeforeProcessRegistration() {
        LayoutSearchProcessRegistry registry = new LayoutSearchProcessRegistry(Process::destroy);
        TrackingProcess process = new TrackingProcess();

        // Given: cancellation has already been requested for a search without a started process.
        registry.cancel(10L);
        assertThat(registry.isCancellationRequested(10L)).isTrue();

        // When: the worker starts the Python process after the cancellation request.
        LayoutSearchProcessRegistry.RunHandle handle = registry.register(10L, process);

        // Then: the process is stopped immediately and the stale cancellation marker is removed.
        assertThat(handle.isCancelled()).isTrue();
        assertThat(process.destroyed).isTrue();
        assertThat(registry.trackedProcessCount()).isZero();
    }

    @Test
    void stopsRegisteredProcessWhenCancellationArrivesDuringRun() {
        LayoutSearchProcessRegistry registry = new LayoutSearchProcessRegistry(Process::destroy);
        TrackingProcess process = new TrackingProcess();
        LayoutSearchProcessRegistry.RunHandle handle = registry.register(11L, process);

        // Given: a Python process is registered and running.
        // When: the user cancels the search.
        registry.cancel(11L);

        // Then: the exact registered process is destroyed and the run observes cancellation.
        assertThat(handle.isCancelled()).isTrue();
        assertThat(process.destroyed).isTrue();
    }

    @Test
    void removesNormalExitHandleSoLaterCancellationCannotStopStaleProcess() {
        LayoutSearchProcessRegistry registry = new LayoutSearchProcessRegistry(Process::destroy);
        TrackingProcess process = new TrackingProcess();
        LayoutSearchProcessRegistry.RunHandle handle = registry.register(12L, process);

        // Given: the process completed normally.
        handle.close();

        // When: a delayed cancellation message reaches the registry.
        registry.cancel(12L);

        // Then: no completed process is destroyed and no active process remains registered.
        assertThat(process.destroyed).isFalse();
        assertThat(registry.trackedProcessCount()).isZero();
    }

    @Test
    void stopsProcessWhenGenerationTimeoutUsesRunHandle() {
        LayoutSearchProcessRegistry registry = new LayoutSearchProcessRegistry(Process::destroy);
        TrackingProcess process = new TrackingProcess();
        LayoutSearchProcessRegistry.RunHandle handle = registry.register(13L, process);

        // Given: the generation deadline elapsed while the process is still registered.
        // When: the runner stops the handle for the deadline.
        handle.stop();

        // Then: the active process is destroyed and the handle reports cancellation.
        assertThat(handle.isCancelled()).isTrue();
        assertThat(process.destroyed).isTrue();
    }

    @Test
    void rejectsASecondProcessForSameSearchWithoutReplacingTheFirst() {
        LayoutSearchProcessRegistry registry = new LayoutSearchProcessRegistry(Process::destroy);
        TrackingProcess first = new TrackingProcess();
        TrackingProcess second = new TrackingProcess();
        registry.register(14L, first);

        // Given: a search already has one registered process.
        // When: a stale worker tries to register another process for the same search.
        // Then: the first process remains authoritative and the second is not retained.
        assertThatThrownBy(() -> registry.register(14L, second))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already has a running process");
        assertThat(first.destroyed).isFalse();
        assertThat(second.destroyed).isFalse();
        assertThat(registry.trackedProcessCount()).isEqualTo(1);
    }

    @Test
    void clearsCancelledMarkerWhenCoordinatorExitsBeforePythonStarts() {
        LayoutSearchProcessRegistry registry = new LayoutSearchProcessRegistry(Process::destroy);
        TrackingProcess process = new TrackingProcess();

        // Given: cancellation arrives before a queued coordinator can start Python.
        registry.cancel(15L);

        // When: the coordinator exits after observing cancellation and clears its idle marker.
        registry.clearIdleCancellation(15L);
        LayoutSearchProcessRegistry.RunHandle handle = registry.register(15L, process);

        // Then: a future stale run is not poisoned by the old cancellation marker.
        assertThat(registry.isCancellationRequested(15L)).isFalse();
        assertThat(handle.isCancelled()).isFalse();
        assertThat(process.destroyed).isFalse();
    }

    private static final class TrackingProcess extends Process {
        private boolean destroyed;

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            return destroyed ? 143 : 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public int exitValue() {
            return destroyed ? 143 : 0;
        }

        @Override
        public void destroy() {
            destroyed = true;
        }
    }
}
