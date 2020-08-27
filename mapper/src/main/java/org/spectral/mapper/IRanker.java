package org.spectral.mapper;

import java.util.List;

public interface IRanker<T> {
	List<RankResult<T>> rank(T src, T[] dsts, ClassifierLevel level, ClassEnvironment env, double maxMismatch);
}