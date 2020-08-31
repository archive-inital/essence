public interface IClassifier<T> {
	String getName();
	double getWeight();
	double getScore(T a, T b, OldClassEnvironment env);
}