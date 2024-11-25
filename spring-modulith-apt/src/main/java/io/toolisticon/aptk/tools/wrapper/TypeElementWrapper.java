package io.toolisticon.aptk.tools.wrapper;

import io.toolisticon.aptk.tools.ElementUtils;
import io.toolisticon.aptk.tools.TypeMirrorWrapper;
import io.toolisticon.aptk.tools.corematcher.AptkCoreMatchers;
import io.toolisticon.aptk.tools.fluentfilter.FluentElementFilter;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 * <em>Copy of the original {@link TypeElementWrapper} of the Toolisticon APTK project located
 * <a href="https://github.com/toolisticon/aptk">here</a>. Tweaks {@link #getOuterType()} to include
 * {@link ElementKind#INTERFACE} in the outer class lookup to fix the issue reported
 * <a href="https://github.com/toolisticon/aptk/issues/163">here</a>.</em> <br />
 * Wraps a TypeElement to provide some convenience * functionality
 */
public class TypeElementWrapper extends ElementWrapper<TypeElement> {

	private final static String TYPE_ELEMENT_CLASS_NAME = "javax.lang.model.element.TypeElement";

	/**
	 * Hidden constructor.
	 *
	 * @param typeElement The TypeElement to wrap
	 */
	private TypeElementWrapper(TypeElement typeElement) {
		super(typeElement);
	}

	// methods inherited from TypeElement

	/**
	 * Returns the NestingKind.
	 *
	 * @return the NestingKind.
	 */
	public NestingKind getNestingKind() {
		return this.element.getNestingKind();
	}

	public boolean hasNestingKind(NestingKind nestingKind) {
		return nestingKind != null && getNestingKind().equals(nestingKind);
	}

	public boolean isNested() {
		return getNestingKind().isNested();
	}

	/**
	 * Gets the nested name of the element. If the TypeElement isn't a nested type the simple name will be returned.
	 *
	 * @return the nested name of the element
	 */
	public String getNestedName() {

		String name = getSimpleName();

		TypeElementWrapper current = this;

		while (current.isNested()) {

			current = current.getOuterType().get();
			name = current.getSimpleName() + "." + name;

		}

		return name;
	}

	/**
	 * Returns the qualified name.
	 *
	 * @return the qualified name.
	 */
	public String getQualifiedName() {
		return this.element.getQualifiedName().toString();
	}

	/**
	 * Check for qualified name.
	 *
	 * @param name the name to check for
	 * @return true if qualified name matches passed name, otherwise false
	 */
	public boolean hasQualifiedName(String name) {
		return name != null && getQualifiedName().equals(name);
	}

	/**
	 * Gets the binary name for the type element.
	 */
	public String getBinaryName() {

		// According to https://docs.oracle.com/javase/specs/jls/se8/html/jls-13.html the binary name looks like:
		// -- The binary name of a top level type (§7.6) is its canonical name (§6.7).
		// -- The binary name of a member type (§8.5, §9.5) consists of the binary name of its immediately enclosing type,
		// followed by $, followed by the simple name of the member.

		if (this.isNested()) {
			// Safe to call since its of nested kind
			TypeElementWrapper enclosingTypeElement = this.getOuterType().get();
			return enclosingTypeElement.getBinaryName() + "$" + this.getSimpleName();
		} else {
			return this.getQualifiedName();
		}

	}

	/**
	 * Gets the direct superclass
	 *
	 * @return a TypeMirrorWrapper of the direct superclass
	 */
	public TypeMirrorWrapper getSuperclass() {
		return TypeMirrorWrapper.wrap(this.element.getSuperclass());
	}

	/**
	 * Returns list of TypeMirrorWrapper containing all directly implemented interfaces. Doesn't return interfaces
	 * implemented by superclasses.
	 *
	 * @return list of TypeMirrorWrapper containing all directly implemented interfaces
	 */
	public List<TypeMirrorWrapper> getInterfaces() {
		return this.element.getInterfaces().stream().map(TypeMirrorWrapper::wrap).collect(Collectors.toList());
	}

	/**
	 * Returns Set of TypeMirrorWrapper containing all implemented interfaces (including those of superclasses).
	 *
	 * @return Set of TypeMirrorWrapper containing all implemented interfaces
	 */
	public Set<TypeMirrorWrapper> getAllInterfaces() {

		// Add own interfaces
		Set<TypeMirrorWrapper> result = new HashSet<>(this.getInterfaces());

		// recursively add those of superclasses.
		Optional<TypeElementWrapper> superClass = getSuperclass().getTypeElement();
		superClass.ifPresent(typeElementWrapper -> result.addAll(typeElementWrapper.getAllInterfaces()));

		return result;
	}

	/**
	 * Returns list of TypeParameterElementWrapper instances describing all TypeParameters.
	 *
	 * @return list of TypeParameterElementWrapper instances describing all TypeParameters, or an empty list
	 */
	public List<TypeParameterElementWrapper> getTypeParameters() {
		return this.element.getTypeParameters().stream().map(TypeParameterElementWrapper::wrap)
				.collect(Collectors.toList());
	}

	/**
	 * Checks if wrapped TypeElement has type parameters.
	 *
	 * @return true, if TypeElement has type parameters, otherwise false.
	 */
	public boolean hasTypeParameters() {
		return getTypeParameters().size() > 0;
	}

	/**
	 * Returns an enclosed method. Works only if all parameter types are already compiled.
	 *
	 * @param name the method name
	 * @param parameterTypes The parameter types
	 * @return the wrapped ExecutableElement instance if method is found, otherwise null
	 */
	public Optional<ExecutableElementWrapper> getMethod(String name, Class<?>... parameterTypes) {
		List<ExecutableElement> filterResult = FluentElementFilter
				.createFluentElementFilter(this.element.getEnclosedElements())
				.applyFilter(AptkCoreMatchers.IS_METHOD)
				.applyFilter(AptkCoreMatchers.BY_NAME).filterByOneOf(name)
				.applyFilter(AptkCoreMatchers.BY_PARAMETER_TYPE).filterByOneOf(parameterTypes)
				.getResult();
		return Optional.ofNullable(filterResult.size() > 0 ? ExecutableElementWrapper.wrap(filterResult.get(0)) : null);
	}

	/**
	 * Returns list of ExecutableElementWrapper for all methods in wrapped TypeElement, filtered by modifiers.
	 *
	 * @param modifier modifiers used for filtering
	 * @return list of ExecutableElementWrapper for all methods in wrapped TypeElement, or empty list
	 */
	public List<ExecutableElementWrapper> getMethods(Modifier... modifier) {
		return FluentElementFilter.createFluentElementFilter(
				this.element.getEnclosedElements()).applyFilter(AptkCoreMatchers.IS_METHOD)
				.applyFilter(AptkCoreMatchers.BY_MODIFIER).filterByAllOf(modifier)
				.getResult()
				.stream().map(ExecutableElementWrapper::wrap).collect(Collectors.toList());
	}

	/**
	 * Returns list of VariableElementWrapper for all fields in wrapped TypeElement, filtered by modifiers.
	 *
	 * @param modifier modifiers used for filtering
	 * @return list of VariableElementWrapper for all fields in wrapped TypeElement, or empty list
	 */
	public List<VariableElementWrapper> getFields(Modifier... modifier) {
		return FluentElementFilter.createFluentElementFilter(
				this.element.getEnclosedElements()).applyFilter(AptkCoreMatchers.IS_FIELD)
				.applyFilter(AptkCoreMatchers.BY_MODIFIER).filterByAllOf(modifier)
				.getResult()
				.stream().map(VariableElementWrapper::wrap).collect(Collectors.toList());
	}

	/**
	 * Returns Optional of VariableElementWrapper of field with passed name in wrapped TypeElement.
	 *
	 * @param name Name of the field
	 * @return Optional of ExecutableElementWrapper of field with passed name in wrapped TypeElement, or empty list
	 */
	public Optional<VariableElementWrapper> getFieldByName(String name) {
		List<VariableElementWrapper> fields = FluentElementFilter.createFluentElementFilter(
				this.element.getEnclosedElements()).applyFilter(AptkCoreMatchers.IS_FIELD)
				.applyFilter(AptkCoreMatchers.BY_NAME).filterByOneOf(name)
				.getResult()
				.stream().map(VariableElementWrapper::wrap).collect(Collectors.toList());

		return Optional.ofNullable(fields.size() > 0 ? fields.get(0) : null);
	}

	/**
	 * Returns list of VariableElementWrapper for all constructors in wrapped TypeElement, filtered by modifiers.
	 *
	 * @param modifier modifiers used for filtering
	 * @return list of ExecutableElementWrapper for all constructors in wrapped TypeElement, or empty list
	 */
	public List<ExecutableElementWrapper> getConstructors(Modifier... modifier) {
		return FluentElementFilter.createFluentElementFilter(
				this.element.getEnclosedElements()).applyFilter(AptkCoreMatchers.IS_CONSTRUCTOR)
				.applyFilter(AptkCoreMatchers.BY_MODIFIER).filterByAllOf(modifier)
				.getResult()
				.stream().map(ExecutableElementWrapper::wrap).collect(Collectors.toList());
	}

	/**
	 * Returns list of VariableElementWrapper for all inner types in wrapped TypeElement, filtered by modifiers.
	 *
	 * @param modifier modifiers used for filtering
	 * @return list of ExecutableElementWrapper for all inner types in wrapped TypeElement, or empty list
	 */
	public List<TypeElementWrapper> getInnerTypes(Modifier... modifier) {
		return FluentElementFilter.createFluentElementFilter(
				this.element.getEnclosedElements()).applyFilter(AptkCoreMatchers.IS_CLASS)
				.applyFilter(AptkCoreMatchers.BY_MODIFIER).filterByAllOf(modifier)
				.getResult()
				.stream().map(TypeElementWrapper::wrap).collect(Collectors.toList());
	}

	/**
	 * Returns the direct outer type for nested classes. Returned type may not be a top level type, since java allows
	 * nested classes in nested classes.
	 *
	 * @return an Optional containing the outer type of nested classes, if present.
	 */
	public Optional<TypeElementWrapper> getOuterType() {
		if (this.element.getNestingKind() != NestingKind.MEMBER) {
			return Optional.empty();
		}

		return Optional.of(TypeElementWrapper.wrap(ElementUtils.AccessEnclosingElements
				.<TypeElement> getFirstEnclosingElementOfKind(this.element, ElementKind.CLASS, ElementKind.INTERFACE)));
	}

	/**
	 * Returns the direct outer type for nested classes.
	 *
	 * @return an Optional containing the outer type of nested classes, if present.
	 */
	public Optional<TypeElementWrapper> getOuterTopLevelType() {
		if (this.element.getNestingKind() != NestingKind.MEMBER) {
			return Optional.empty();
		}

		return Optional.of(this.getAllEnclosingElements().stream()
				.filter(ElementWrapper::isTypeElement)
				.map(ElementWrapper::toTypeElement)
				.filter(e -> e.hasNestingKind(NestingKind.TOP_LEVEL)).collect(Collectors.toList()).get(0));

	}

	/**
	 * Gets all enum constant names of enum as VariableElements.
	 *
	 * @return A list containing all enum constant names or null if wrapped TypeElement is no enum.
	 */
	public List<VariableElementWrapper> getEnumValues() {
		return isEnum() ? this.filterEnclosedElements()
				.applyFilter(AptkCoreMatchers.BY_ELEMENT_KIND)
				.filterByOneOf(ElementKind.ENUM_CONSTANT)
				.getResult()
				.stream().map(e -> VariableElementWrapper.wrap((VariableElement) e))
				.collect(Collectors.toList())
				: null;
	}

	/**
	 * Returns the record components of this class or interface element in declaration order.
	 *
	 * @return the record components, or an empty list if there are none
	 */
	public List<RecordComponentElementWrapper> getRecordComponents() {

		// This method is available from Java 16 - the introduction of records, so this check is sufficient to prevent
		// reflective calling of method
		if (!isRecord()) {
			return Collections.EMPTY_LIST;
		}
		List<? extends Element> recordComponentElements = this
				.<List<? extends Element>> invokeParameterlessMethodOfElement(TYPE_ELEMENT_CLASS_NAME, "getRecordComponents");

		return recordComponentElements.stream().map(RecordComponentElementWrapper::wrap).collect(Collectors.toList());

	}

	/**
	 * Returns the permitted classes of this class or interface element in declaration order.
	 *
	 * @return the permitted classes, or an empty list if there are none
	 */
	public List<TypeMirrorWrapper> getPermittedSubclasses() {

		// must make sure that method exists, otherwise return the default value
		if (!hasMethod(TYPE_ELEMENT_CLASS_NAME, "getPermittedSubclasses")) {
			// TODO MUST CHECK WHAT SHOULD BE RETURNED FOR NON SEALED CLASSES!
			return Collections.EMPTY_LIST;
		}

		List<TypeMirror> typeMirrors = this.<List<TypeMirror>> invokeParameterlessMethodOfElement(TYPE_ELEMENT_CLASS_NAME,
				"getPermittedSubclasses");
		return typeMirrors.stream().map(TypeMirrorWrapper::wrap).collect(Collectors.toList());
	}

	/**
	 * Wraps a TypeElement.
	 *
	 * @param element the TypeElement to wrap
	 * @return a wrapper instance
	 */
	public static TypeElementWrapper wrap(TypeElement element) {
		return new TypeElementWrapper(element);
	}

	/**
	 * Retrieve TypeElementWrapper by fully qualified name.
	 *
	 * @param fqn the fully qualified name of the element
	 * @return an Optional that contains the wrapper instance, if TypeElement for fqn exists
	 */
	public static Optional<TypeElementWrapper> getByFqn(String fqn) {
		return getByTypeMirror(TypeMirrorWrapper.wrap(fqn));
	}

	/**
	 * Retrieve TypeElementWrapper by fully qualified name.
	 *
	 * @param clazz the class of the element
	 * @return an Optional that contains the wrapper instance, if TypeElement for class exists (only for DeclaredTypes)
	 */
	public static Optional<TypeElementWrapper> getByClass(Class<?> clazz) {
		return getByTypeMirror(TypeMirrorWrapper.wrap(clazz));
	}

	/**
	 * Retrieve TypeElementWrapper by fully qualified name.
	 *
	 * @param typeMirror the TypeMirror of the element
	 * @return an Optional that contains the wrapper instance, if TypeElement for TypeMirror exists (only for
	 *         DeclaredTypes)
	 */
	public static Optional<TypeElementWrapper> getByTypeMirror(TypeMirror typeMirror) {
		return getByTypeMirror(TypeMirrorWrapper.wrap(typeMirror));
	}

	public static Optional<TypeElementWrapper> getByTypeMirror(TypeMirrorWrapper typeMirror) {
		return typeMirror.getTypeElement();
	}
}
