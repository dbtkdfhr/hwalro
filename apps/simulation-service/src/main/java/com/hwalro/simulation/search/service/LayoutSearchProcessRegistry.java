package com.hwalro.simulation.search.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

final class LayoutSearchProcessRegistry {
    private final ConcurrentMap<Long, Entry> entries = new ConcurrentHashMap<>();
    private final Consumer<Process> terminator;

    LayoutSearchProcessRegistry(Consumer<Process> terminator) {
        this.terminator = terminator;
    }

    RunHandle register(long searchId, Process process) {
        AtomicReference<Entry> registered = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        entries.compute(searchId, (ignored, existing) -> {
            if (existing == null) {
                Entry entry = Entry.running(process);
                registered.set(entry);
                return entry;
            }
            if (existing.isCancelled()) {
                registered.set(existing);
                cancelled.set(true);
                return null;
            }
            throw new IllegalStateException("Layout search " + searchId + " already has a running process.");
        });
        Entry entry = registered.get();
        if (cancelled.get()) {
            terminator.accept(process);
        }
        return new RunHandle(searchId, entry, this);
    }

    void cancel(long searchId) {
        AtomicReference<Process> processToStop = new AtomicReference<>();
        entries.compute(searchId, (ignored, existing) -> {
            Entry entry = existing == null ? Entry.cancelled() : existing;
            processToStop.set(entry.cancel());
            return entry;
        });
        Process process = processToStop.get();
        if (process != null) {
            terminator.accept(process);
        }
    }

    boolean isCancellationRequested(long searchId) {
        Entry entry = entries.get(searchId);
        return entry != null && entry.isCancelled();
    }

    void clearIdleCancellation(long searchId) {
        entries.computeIfPresent(searchId, (ignored, entry) -> entry.hasProcess() ? entry : null);
    }

    int trackedProcessCount() {
        return (int) entries.values().stream().filter(Entry::hasProcess).count();
    }

    private void close(long searchId, Entry entry) {
        entries.remove(searchId, entry);
    }

    static final class RunHandle implements AutoCloseable {
        private final long searchId;
        private final Entry entry;
        private final LayoutSearchProcessRegistry registry;

        private RunHandle(long searchId, Entry entry, LayoutSearchProcessRegistry registry) {
            this.searchId = searchId;
            this.entry = entry;
            this.registry = registry;
        }

        boolean isCancelled() {
            return entry.isCancelled();
        }

        void stop() {
            registry.cancel(searchId);
        }

        @Override
        public void close() {
            registry.close(searchId, entry);
        }
    }

    private static final class Entry {
        private final AtomicReference<Process> process;
        private volatile boolean cancelled;

        private Entry(Process process, boolean cancelled) {
            this.process = new AtomicReference<>(process);
            this.cancelled = cancelled;
        }

        private static Entry running(Process process) {
            return new Entry(process, false);
        }

        private static Entry cancelled() {
            return new Entry(null, true);
        }

        private Process cancel() {
            cancelled = true;
            return process.getAndSet(null);
        }

        private boolean hasProcess() {
            return process.get() != null;
        }

        private boolean isCancelled() {
            return cancelled;
        }
    }
}
