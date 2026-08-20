package com.hwalro.regulation.law.dto;

/** 국가법령정보센터가 제공하는 법령 간 관계 정보다. */
public record RelatedRegulation(String lawId, String name, String relationship, String sourceUrl) {}
