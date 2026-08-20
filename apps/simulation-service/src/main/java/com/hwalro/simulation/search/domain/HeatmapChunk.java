package com.hwalro.simulation.search.domain;

public class HeatmapChunk {
    private Integer chunkSequence;
    private String densityData;

    public Integer getChunkSequence() {
        return chunkSequence;
    }

    public void setChunkSequence(Integer chunkSequence) {
        this.chunkSequence = chunkSequence;
    }

    public String getDensityData() {
        return densityData;
    }

    public void setDensityData(String densityData) {
        this.densityData = densityData;
    }
}
