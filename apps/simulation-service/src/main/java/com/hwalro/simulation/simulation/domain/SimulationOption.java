package com.hwalro.simulation.simulation.domain;

import java.math.BigDecimal;

public class SimulationOption {
    private Long id;
    private Long simulationId;
    private Integer randomSeed;
    private String modelProfile;
    private String routingProfile;
    private Integer totalPeople;
    private BigDecimal walkingSpeed;
    private BigDecimal reactionTime;
    private BigDecimal initialResponseTimeMean;
    private BigDecimal initialResponseTimeStdDev;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSimulationId() {
        return simulationId;
    }

    public void setSimulationId(Long simulationId) {
        this.simulationId = simulationId;
    }

    public Integer getRandomSeed() {
        return randomSeed;
    }

    public void setRandomSeed(Integer randomSeed) {
        this.randomSeed = randomSeed;
    }

    public String getModelProfile() {
        return modelProfile;
    }

    public void setModelProfile(String modelProfile) {
        this.modelProfile = modelProfile;
    }

    public String getRoutingProfile() {
        return routingProfile;
    }

    public void setRoutingProfile(String routingProfile) {
        this.routingProfile = routingProfile;
    }

    public Integer getTotalPeople() {
        return totalPeople;
    }

    public void setTotalPeople(Integer totalPeople) {
        this.totalPeople = totalPeople;
    }

    public BigDecimal getWalkingSpeed() {
        return walkingSpeed;
    }

    public void setWalkingSpeed(BigDecimal walkingSpeed) {
        this.walkingSpeed = walkingSpeed;
    }

    public BigDecimal getReactionTime() {
        return reactionTime;
    }

    public void setReactionTime(BigDecimal reactionTime) {
        this.reactionTime = reactionTime;
    }

    public BigDecimal getInitialResponseTimeMean() {
        return initialResponseTimeMean;
    }

    public void setInitialResponseTimeMean(BigDecimal initialResponseTimeMean) {
        this.initialResponseTimeMean = initialResponseTimeMean;
    }

    public BigDecimal getInitialResponseTimeStdDev() {
        return initialResponseTimeStdDev;
    }

    public void setInitialResponseTimeStdDev(BigDecimal initialResponseTimeStdDev) {
        this.initialResponseTimeStdDev = initialResponseTimeStdDev;
    }
}
