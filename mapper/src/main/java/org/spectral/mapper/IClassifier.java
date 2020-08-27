package org.spectral.mapper;

public interface IClassifier<T> {
	String getName();
	double getWeight();
	double getScore(T a, T b, ClassEnvironment env);
}