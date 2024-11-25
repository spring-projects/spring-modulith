package example;

/**
 * Interface comment.
 *
 * @author Oliver Drotbohm
 */
interface SampleInterface {

	/**
	 * Method with nested class parameter.
	 * 
	 * @param parameter
	 */
	void someMethod(SampleClass parameter);

	/**
	 * Nested class comment.
	 *
	 * @author Oliver Drotbohm
	 */
	class SampleClass {}
}
