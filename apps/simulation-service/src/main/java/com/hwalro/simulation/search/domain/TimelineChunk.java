package com.hwalro.simulation.search.domain;

public class TimelineChunk {
    private Integer chunkSequence;
    private String frameData;

    public Integer getChunkSequence() {
        return chunkSequence;
    }

    public void setChunkSequence(Integer chunkSequence) {
        this.chunkSequence = chunkSequence;
    }

    public String getFrameData() {
        return frameData;
    }

    public void setFrameData(String frameData) {
        this.frameData = frameData;
    }
}
