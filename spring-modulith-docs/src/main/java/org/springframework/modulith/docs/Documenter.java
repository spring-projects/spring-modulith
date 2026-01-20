/*
 * Copyright 2018-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.modulith.docs;

import static org.springframework.modulith.docs.Asciidoctor.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.springframework.lang.Contract;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.DependencyDepth;
import org.springframework.modulith.core.DependencyType;
import org.springframework.modulith.core.SpringBean;
import org.springframework.modulith.core.util.ApplicationModulesExporter;
import org.springframework.modulith.docs.Groupings.JMoleculesGroupings;
import org.springframework.modulith.docs.Groupings.SpringGroupings;
import org.springframework.modulith.docs.util.BuildSystemUtils;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.structurizr.Workspace;
import com.structurizr.export.IndentingWriter;
import com.structurizr.export.plantuml.AbstractPlantUMLExporter;
import com.structurizr.export.plantuml.C4PlantUMLExporter;
import com.structurizr.export.plantuml.StructurizrPlantUMLExporter;
import com.structurizr.model.Component;
import com.structurizr.model.Container;
import com.structurizr.model.Element;
import com.structurizr.model.Model;
import com.structurizr.model.Relationship;
import com.structurizr.model.SoftwareSystem;
import com.structurizr.model.Tags;
import com.structurizr.view.ComponentView;
import com.structurizr.view.ModelView;
import com.structurizr.view.RelationshipView;
import com.structurizr.view.Shape;
import com.structurizr.view.Styles;
import com.tngtech.archunit.core.domain.JavaClass;

/**
 * API to create documentation for {@link ApplicationModules}.
 *
 * @author Oliver Drotbohm
 * @author Cora Iberkleid
 * @author Tobias Haindl
 * @author Alexander Miller
 */
public class Documenter {

	private static final Map<DependencyType, String> DEPENDENCY_DESCRIPTIONS = new LinkedHashMap<>();
	private static final String INVALID_FILE_NAME_PATTERN = "Configured file name pattern does not include a '%s' placeholder for the module name!";

	private static final String DEFAULT_COMPONENTS_FILE = "components.puml";
	private static final String DEFAULT_MODULE_COMPONENTS_FILE = "module-%s.puml";

	static {
		DEPENDENCY_DESCRIPTIONS.put(DependencyType.EVENT_LISTENER, "listens to");
		DEPENDENCY_DESCRIPTIONS.put(DependencyType.DEFAULT, "depends on");
	}

	private final ApplicationModules modules;
	private final Workspace workspace;
	private final Container container;
	private final ConfigurationProperties properties;
	private final Options options;

	private boolean cleared;

	private @Nullable Map<ApplicationModule, Component> components;

	/**
	 * Creates a new {@link Documenter} for the {@link ApplicationModules} created for the given modulith type in the
	 * default output folder ({@code spring-modulith-docs}).
	 *
	 * @param modulithType must not be {@literal null}.
	 */
	public Documenter(Class<?> modulithType) {
		this(ApplicationModules.of(modulithType));
	}

	/**
	 * Creates a new {@link Documenter} for the given {@link ApplicationModules} instance in the default output folder
	 * ({@code spring-modulith-docs}).
	 *
	 * @param modules must not be {@literal null}.
	 */
	public Documenter(ApplicationModules modules) {
		this(modules, Options.defaults());
	}

	/**
	 * Creates a new {@link Documenter} for the given {@link ApplicationModules} and output folder.
	 *
	 * @param modules must not be {@literal null}.
	 * @param options must not be {@literal null}.
	 * @since 1.2
	 */
	public Documenter(ApplicationModules modules, Options options) {

		Assert.notNull(modules, "Modules must not be null!");

		this.modules = modules;
		this.options = options;
		this.workspace = new Workspace("Modulith", "");

		workspace.getViews().getConfiguration()
				.getStyles()
				.addElementStyle(Tags.COMPONENT)
				.shape(Shape.Component);

		Model model = workspace.getModel();
		String systemName = getDefaultedSystemName();

		SoftwareSystem system = model.addSoftwareSystem(systemName, "");

		this.container = system.addContainer(systemName, "", "");
		this.properties = new ConfigurationProperties();
		this.cleared = false;
	}

	/**
	 * Writes all available documentation:
	 * <ul>
	 * <li>The entire set of modules as overview component diagram.</li>
	 * <li>Individual component diagrams per module to include all upstream modules.</li>
	 * <li>The Module Canvas for each module.</li>
	 * </ul>
	 * using {@link DiagramOptions#defaults()} and {@link CanvasOptions#defaults()}.
	 *
	 * @return the current instance, will never be {@literal null}.
	 */
	public Documenter writeDocumentation() {
		return writeDocumentation(DiagramOptions.defaults(), CanvasOptions.defaults());
	}

	/**
	 * Writes all available documentation:
	 * <ul>
	 * <li>The entire set of modules as overview component diagram.</li>
	 * <li>Individual component diagrams per module to include all upstream modules.</li>
	 * <li>The Module Canvas for each module.</li>
	 * </ul>
	 *
	 * @param diagramOptions must not be {@literal null}.
	 * @param canvasOptions must not be {@literal null}.
	 * @return the current instance, will never be {@literal null}.
	 */
	public Documenter writeDocumentation(DiagramOptions diagramOptions, CanvasOptions canvasOptions) {

		potentiallyWipeOutputFolder();

		return writeModulesAsPlantUml(diagramOptions)
				.writeIndividualModulesAsPlantUml(diagramOptions)
				.writeModuleCanvases(canvasOptions)
				.writeAggregatingDocument(diagramOptions, canvasOptions)
				.writeModuleMetadata();
	}

	/**
	 * Writes aggregating document called {@code all-docs.adoc} that includes any existing component diagrams and
	 * canvases. using {@link DiagramOptions#defaults()} and {@link CanvasOptions#defaults()}.
	 *
	 * @return the current instance, will never be {@literal null}.
	 * @since 1.2.2
	 */
	public Documenter writeAggregatingDocument() {
		return writeAggregatingDocument(DiagramOptions.defaults(), CanvasOptions.defaults());
	}

	/**
	 * Writes aggregating document called {@code all-docs.adoc} that includes any existing component diagrams and
	 * canvases.
	 *
	 * @param diagramOptions must not be {@literal null}.
	 * @param canvasOptions must not be {@literal null}.
	 * @return the current instance, will never be {@literal null}.
	 * @since 1.2.2
	 */
	@Contract("_, _ -> this")
	public Documenter writeAggregatingDocument(DiagramOptions diagramOptions, CanvasOptions canvasOptions) {

		Assert.notNull(diagramOptions, "DiagramOptions must not be null!");
		Assert.notNull(canvasOptions, "CanvasOptions must not be null!");

		potentiallyWipeOutputFolder();

		var asciidoctor = Asciidoctor.withJavadocBase(modules, canvasOptions.getApiBase());

		// Get file name for module overview diagram
		var componentsFilename = diagramOptions.getTargetFileName().orElse(DEFAULT_COMPONENTS_FILE);
		var componentsDoc = new StringBuilder();
		var folder = options.outputFolder;

		if (folder.contains(componentsFilename)) {

			componentsDoc
					.append(asciidoctor.renderHeadline(2, getDefaultedSystemName()))
					.append(asciidoctor.renderPlantUmlInclude(componentsFilename))
					.append(System.lineSeparator());
		}

		// Get file names for individual module diagrams and canvases
		var moduleDocs = modules.stream().map(it -> {

			// Get diagram file name, e.g. module-inventory.puml
			var fileNamePattern = diagramOptions.getTargetFileName().orElse(DEFAULT_MODULE_COMPONENTS_FILE);
			var filename = fileNamePattern.formatted(it.getIdentifier());
			var canvasFilename = canvasOptions.getTargetFileName(it.getIdentifier().toString());
			var content = new StringBuilder();

			content.append(folder.contains(filename) ? asciidoctor.renderPlantUmlInclude(filename) : "")
					.append(folder.contains(canvasFilename) ? asciidoctor.renderGeneralInclude(canvasFilename) : "");

			if (!content.isEmpty()) {

				content
						.insert(0, asciidoctor.renderHeadline(2, it.getDisplayName()))
						.append(System.lineSeparator());
			}

			return content.toString();

		}).collect(Collectors.joining());

		var allDocs = componentsDoc.append(moduleDocs).toString();

		// Write file to all-docs.adoc
		if (!allDocs.isBlank()) {
			options.outputFolder.writeToFile("all-docs.adoc", allDocs);
		}

		return this;
	}

	/**
	 * Writes the PlantUML component diagram for all {@link ApplicationModules} using {@link DiagramOptions#defaults()}.
	 *
	 * @return the current instance, will never be {@literal null}.
	 */
	public Documenter writeModulesAsPlantUml() {
		return writeModulesAsPlantUml(DiagramOptions.defaults());
	}

	/**
	 * Writes the PlantUML component diagram for all {@link ApplicationModules} with the given {@link DiagramOptions}.
	 *
	 * @param diagramOptions must not be {@literal null}.
	 * @return the current instance, will never be {@literal null}.
	 */
	public Documenter writeModulesAsPlantUml(DiagramOptions diagramOptions) {

		Assert.notNull(diagramOptions, "Options must not be null!");

		potentiallyWipeOutputFolder();

		options.outputFolder.writeToFile(diagramOptions.getTargetFileName().orElse(DEFAULT_COMPONENTS_FILE),
				createPlantUml(diagramOptions));

		return this;
	}

	public Documenter writeIndividualModulesAsPlantUml() {
		return writeIndividualModulesAsPlantUml(DiagramOptions.defaults());
	}

	/**
	 * Writes the component diagrams for all individual modules.
	 *
	 * @param options must not be {@literal null}.
	 * @return the current instance, will never be {@literal null}.
	 */
	public Documenter writeIndividualModulesAsPlantUml(DiagramOptions options) {

		Assert.notNull(options, "DiagramOptions must not be null!");

		potentiallyWipeOutputFolder();

		modules.forEach(it -> writeModuleAsPlantUml(it, options));

		return this;
	}

	/**
	 * Writes the PlantUML component diagram for the given {@link ApplicationModule}.
	 *
	 * @param module must not be {@literal null}.
	 * @return the current instance, will never be {@literal null}.
	 */
	public Documenter writeModuleAsPlantUml(ApplicationModule module) {

		Assert.notNull(module, "Module must not be null!");

		return writeModuleAsPlantUml(module, DiagramOptions.defaults());
	}

	/**
	 * Writes the PlantUML component diagram for the given {@link ApplicationModule} with the given rendering
	 * {@link DiagramOptions}.
	 *
	 * @param module must not be {@literal null}.
	 * @param options must not be {@literal null}.
	 * @return the current instance, will never be {@literal null}.
	 */
	public Documenter writeModuleAsPlantUml(ApplicationModule module, DiagramOptions options) {

		Assert.notNull(module, "Module must not be null!");
		Assert.notNull(options, "Options must not be null!");

		potentiallyWipeOutputFolder();

		var view = createComponentView(options, module);
		view.setTitle(options.defaultDisplayName.apply(module));

		addComponentsToView(module, view, options);

		var fileNamePattern = options.getTargetFileName().orElse(DEFAULT_MODULE_COMPONENTS_FILE);

		return writeViewAsPlantUml(view, fileNamePattern.formatted(module.getIdentifier()), options);
	}

	/**
	 * Writes all module canvases using {@link DiagramOptions#defaults()}.
	 *
	 * @return the current instance, will never be {@literal null}.
	 */
	public Documenter writeModuleCanvases() {
		return writeModuleCanvases(CanvasOptions.defaults());
	}

	/**
	 * Writes all module canvases using the given {@link DiagramOptions}.
	 *
	 * @param canvasOptions must not be {@literal null}.
	 * @return the current instance, will never be {@literal null}.
	 */
	public Documenter writeModuleCanvases(CanvasOptions canvasOptions) {

		Assert.notNull(canvasOptions, "CanvasOptions must not be null!");

		potentiallyWipeOutputFolder();

		modules.forEach(module -> {

			var filename = canvasOptions.getTargetFileName(module.getIdentifier().toString());

			options.outputFolder.writeToFile(filename, toModuleCanvas(module, canvasOptions));
		});

		return this;
	}

	/**
	 * Writes application module metadata to the build folder for inclusion at runtime.
	 *
	 * @return will never be {@literal null}.
	 * @see ApplicationModulesExporter
	 * @since 1.4
	 */
	public Documenter writeModuleMetadata() {

		var content = new ApplicationModulesExporter(modules).toFullJson();
		var path = Path.of(BuildSystemUtils.getResourceTarget(), ApplicationModulesExporter.DEFAULT_LOCATION);

		try {

			Files.createDirectories(path.getParent());
			Files.writeString(path, content, StandardOpenOption.CREATE);

		} catch (IOException o_O) {
			throw new UncheckedIOException(o_O);
		}

		return this;
	}

	String toModuleCanvas(ApplicationModule module) {
		return toModuleCanvas(module, CanvasOptions.defaults());
	}

	String toModuleCanvas(ApplicationModule module, String apiBase) {
		return toModuleCanvas(module, CanvasOptions.defaults().withApiBase(apiBase));
	}

	String toModuleCanvas(ApplicationModule module, CanvasOptions options) {

		var asciidoctor = Asciidoctor.withJavadocBase(modules, options.getApiBase());
		var filter = options.hideInternalFilter(module);
		var aggregates = module.getAggregateRoots().stream().filter(filter).toList();
		var valueTypes = module.getValueTypes().stream().filter(filter).toList();

		Function<List<JavaClass>, String> mapper = asciidoctor::typesToBulletPoints;

		return new StringBuilder() //

				.append(startTable("%autowidth.stretch, cols=\"h,a\""))
				.append(addTableRow("Description", asciidoctor.renderModuleDescription(module), options))
				.append(addTableRow("Base package", asciidoctor.toInlineCode(module.getBasePackage().getName()), options))

				// Spring components
				.append(addTableRow("Spring components", asciidoctor.renderSpringBeans(module, options), options)) //
				.append(addTableRow("Bean references", asciidoctor.renderBeanReferences(module), options)) //

				// Aggregates
				.append(addTableRow("Aggregate roots", options, aggregates, mapper)) //
				.append(addTableRow("Value types", options, valueTypes, mapper)) //

				// Events
				.append(addTableRow("Published events", asciidoctor.renderPublishedEvents(module), options)) //
				.append(addTableRow("Events listened to", asciidoctor.renderEventsListenedTo(module), options)) //

				// Properties
				.append(addTableRow("Properties",
						asciidoctor.renderConfigurationProperties(properties.getModuleProperties(module)), options)) //
				.append(startOrEndTable())
				.toString();
	}

	ApplicationModules getModules() {
		return modules;
	}

	String toPlantUml() {
		return createPlantUml(DiagramOptions.defaults());
	}

	private void addDependencies(ApplicationModule module, Component component, DiagramOptions options) {

		DEPENDENCY_DESCRIPTIONS.entrySet().stream().forEach(entry -> {

			module.getDirectDependencies(modules, entry.getKey()).uniqueModules() //
					.map(it -> getComponents(options).get(it)) //
					.map(it -> component.uses(it, entry.getValue())) //
					.filter(it -> it != null) //
					.forEach(it -> it.addTags(entry.getKey().toString()));
		});

		module.getBootstrapDependencies(modules)
				.map(it -> component.uses(getComponents(options).get(it), "uses"))
				.filter(it -> it != null)
				.forEach(it -> it.addTags(DependencyType.USES_COMPONENT.toString()));
	}

	@SuppressWarnings("null")
	private Map<ApplicationModule, Component> getComponents(DiagramOptions options) {

		if (this.components == null) {

			this.components = modules.stream() //
					.collect(Collectors.toMap(Function.identity(),
							it -> container.addComponent(options.defaultDisplayName.apply(it), "", "Module")));

			components.forEach((key, value) -> addDependencies(key, value, options));
		}

		return this.components;
	}

	private void addComponentsToView(ApplicationModule module, ComponentView view, DiagramOptions options) {

		Supplier<Stream<ApplicationModule>> bootstrapDependencies = () -> module.getBootstrapDependencies(modules,
				options.dependencyDepth);
		Supplier<Stream<ApplicationModule>> otherDependencies = () -> options.getDependencyTypes()
				.flatMap(it -> module.getDirectDependencies(modules, it).uniqueModules());

		Supplier<Stream<ApplicationModule>> dependencies = () -> Stream.concat(bootstrapDependencies.get(),
				otherDependencies.get());

		addComponentsToView(dependencies, view, options, it -> it.add(getComponents(options).get(module)));
	}

	private void addComponentsToView(Supplier<Stream<ApplicationModule>> modules, ComponentView view,
			DiagramOptions options,
			Consumer<ComponentView> afterCleanup) {

		var styles = view.getViewSet().getConfiguration().getStyles();
		var components = getComponents(options);

		modules.get() //
				.distinct()
				.filter(options.exclusions.negate()) //
				.map(it -> applyBackgroundColor(it, components, options, styles)) //
				.filter(options.componentFilter) //
				.forEach(view::add);

		// Remove filtered dependency types
		DependencyType.allBut(options.getDependencyTypes()) //
				.map(Object::toString) //
				.forEach(it -> view.removeRelationshipsWithTag(it));

		afterCleanup.accept(view);

		// Filter outgoing relationships of target-only modules
		modules.get().filter(options.targetOnly) //
				.forEach(module -> {

					Component component = components.get(module);

					view.getRelationships().stream() //
							.map(RelationshipView::getRelationship) //
							.filter(it -> it.getSource().equals(component)) //
							.forEach(it -> view.remove(it));
				});

		// … as well as all elements left without a relationship
		if (options.hideElementsWithoutRelationships()) {
			view.removeElementsWithNoRelationships();
		}

		afterCleanup.accept(view);

		// Remove default relationships if more qualified ones exist
		view.getRelationships().stream() //
				.map(RelationshipView::getRelationship) //
				.collect(Collectors.groupingBy(Connection::of)) //
				.values().stream() //
				.forEach(it -> potentiallyRemoveDefaultRelationship(view, it));
	}

	private void potentiallyRemoveDefaultRelationship(ModelView view, Collection<Relationship> relationships) {

		if (relationships.size() <= 1) {
			return;
		}

		relationships.stream().filter(it -> it.getTagsAsSet().contains(DependencyType.DEFAULT.toString())) //
				.findFirst().ifPresent(view::remove);
	}

	private Documenter writeViewAsPlantUml(ComponentView view, String filename, DiagramOptions diagramOptions) {

		options.outputFolder.writeToFile(filename, render(view, diagramOptions));

		return this;
	}

	private String render(ComponentView view, DiagramOptions options) {

		switch (options.style) {

			case C4:

				var c4PlantUmlExporter = new C4PlantUMLExporter();
				addSkinParamsFromOptions(c4PlantUmlExporter, options);
				var diagram = c4PlantUmlExporter.export(view);

				return diagram.getDefinition();

			case UML:
			default:

				var plantUmlExporter = new CustomizedPlantUmlExporter();
				plantUmlExporter.addSkinParam("componentStyle", "uml1");
				addSkinParamsFromOptions(plantUmlExporter, options);

				return plantUmlExporter.export(view).getDefinition();
		}
	}

	private String createPlantUml(DiagramOptions options) {

		ComponentView componentView = createComponentView(options);
		componentView.setTitle(getDefaultedSystemName());

		addComponentsToView(() -> modules.stream().filter(Predicate.not(modules::hasParent)), componentView,
				options, it -> {});

		return render(componentView, options);
	}

	private ComponentView createComponentView(DiagramOptions options) {
		return createComponentView(options, null);
	}

	private ComponentView createComponentView(DiagramOptions options, @Nullable ApplicationModule module) {

		String prefix = module == null ? "modules-" : module.getIdentifier().toString();

		return workspace.getViews() //
				.createComponentView(container, prefix + options.toString(), "");
	}

	private void potentiallyWipeOutputFolder() {

		if (options.clean && !cleared) {

			options.outputFolder.deleteIfExists();

			this.cleared = true;
		}
	}

	private void addSkinParamsFromOptions(AbstractPlantUMLExporter exporter, DiagramOptions options) {
		options.skinParams.forEach(exporter::addSkinParam);
	}

	private static Component applyBackgroundColor(ApplicationModule module,
			Map<ApplicationModule, Component> components,
			DiagramOptions options,
			Styles styles) {

		var component = components.get(module);

		if (component == null) {
			throw new IllegalStateException("Couldn't find component for module %s!".formatted(module));
		}

		var selector = options.colorSelector;

		// Apply custom color if configured
		selector.apply(module).ifPresent(color -> {

			var tag = module.getIdentifier() + "-" + color;
			component.addTags(tag);

			// Add or update background color
			styles.getElements().stream()
					.filter(it -> it.getTag().equals(tag))
					.findFirst()
					.orElseGet(() -> styles.addElementStyle(tag))
					.background(color);
		});

		return component;
	}

	private static String addTableRow(String title, String content, CanvasOptions options) {

		return options.hideEmptyLines && (content.isBlank() || content.equalsIgnoreCase("none"))
				? ""
				: writeTableRow(title, content);
	}

	private static <T> String addTableRow(String header, CanvasOptions options, List<T> types,
			Function<List<T>, String> mapper) {
		return options.hideEmptyLines && types.isEmpty() ? "" : writeTableRow(header, mapper.apply(types));
	}

	private String getDefaultedSystemName() {
		return modules.getSystemName().orElse("Modules");
	}

	private static record Connection(Element source, Element target) {
		public static Connection of(Relationship relationship) {
			return new Connection(relationship.getSource(), relationship.getDestination());
		}
	}

	/**
	 * Options to tweak the rendering of diagrams.
	 *
	 * @author Oliver Drotbohm
	 */
	public static class DiagramOptions {

		private static final Set<DependencyType> ALL_TYPES = Arrays.stream(DependencyType.values())
				.collect(Collectors.toSet());

		private final Set<DependencyType> dependencyTypes;
		private final DependencyDepth dependencyDepth;
		private final Predicate<ApplicationModule> exclusions;
		private final Predicate<Component> componentFilter;
		private final Predicate<ApplicationModule> targetOnly;
		private final @Nullable String targetFileName;
		private final Function<ApplicationModule, Optional<String>> colorSelector;
		private final Function<ApplicationModule, String> defaultDisplayName;
		private final DiagramStyle style;
		private final ElementsWithoutRelationships elementsWithoutRelationships;
		private final Map<String, String> skinParams;

		/**
		 * @param dependencyTypes must not be {@literal null}.
		 * @param dependencyDepth must not be {@literal null}.
		 * @param exclusions must not be {@literal null}.
		 * @param componentFilter must not be {@literal null}.
		 * @param targetOnly must not be {@literal null}.
		 * @param targetFileName can be {@literal null}.
		 * @param colorSelector must not be {@literal null}.
		 * @param defaultDisplayName must not be {@literal null}.
		 * @param style must not be {@literal null}.
		 * @param elementsWithoutRelationships must not be {@literal null}.
		 * @param skinParams must not be {@literal null}.
		 */
		DiagramOptions(Set<DependencyType> dependencyTypes, DependencyDepth dependencyDepth,
				Predicate<ApplicationModule> exclusions, Predicate<Component> componentFilter,
				Predicate<ApplicationModule> targetOnly, @Nullable String targetFileName,
				Function<ApplicationModule, Optional<String>> colorSelector,
				Function<ApplicationModule, String> defaultDisplayName, DiagramStyle style,
				ElementsWithoutRelationships elementsWithoutRelationships,
				Map<String, String> skinParams) {

			Assert.notNull(dependencyTypes, "Dependency types must not be null!");
			Assert.notNull(dependencyDepth, "Dependency depth must not be null!");
			Assert.notNull(exclusions, "Exclusions must not be null!");
			Assert.notNull(componentFilter, "Component filter must not be null!");
			Assert.notNull(targetOnly, "Target only must not be null!");
			Assert.notNull(colorSelector, "Color selector must not be null!");
			Assert.notNull(defaultDisplayName, "Default display name must not be null!");
			Assert.notNull(style, "DiagramStyle must not be null!");
			Assert.notNull(elementsWithoutRelationships, "ElementsWithoutRelationships must not be null!");
			Assert.notNull(skinParams, "Skin parameters must not be null!");

			this.dependencyTypes = dependencyTypes;
			this.dependencyDepth = dependencyDepth;
			this.exclusions = exclusions;
			this.componentFilter = componentFilter;
			this.targetOnly = targetOnly;
			this.targetFileName = targetFileName;
			this.colorSelector = colorSelector;
			this.defaultDisplayName = defaultDisplayName;
			this.style = style;
			this.elementsWithoutRelationships = elementsWithoutRelationships;
			this.skinParams = skinParams;
		}

		/**
		 * The {@link DependencyDepth} to define which other modules to be included in the diagram to be created.
		 */
		public DiagramOptions withDependencyDepth(DependencyDepth dependencyDepth) {
			return new DiagramOptions(dependencyTypes, dependencyDepth, exclusions, componentFilter, targetOnly,
					targetFileName, colorSelector, defaultDisplayName, style, elementsWithoutRelationships, skinParams);
		}

		/**
		 * A {@link Predicate} to define the which modules to exclude from the diagram to be created.
		 */
		public DiagramOptions withExclusions(Predicate<ApplicationModule> exclusions) {
			return new DiagramOptions(dependencyTypes, dependencyDepth, exclusions, componentFilter, targetOnly,
					targetFileName, colorSelector, defaultDisplayName, style, elementsWithoutRelationships, skinParams);
		}

		/**
		 * A {@link Predicate} to define which Structurizr {@link Component}s to be included in the diagram to be created.
		 */
		public DiagramOptions withComponentFilter(Predicate<Component> componentFilter) {
			return new DiagramOptions(dependencyTypes, dependencyDepth, exclusions, componentFilter, targetOnly,
					targetFileName, colorSelector, defaultDisplayName, style, elementsWithoutRelationships, skinParams);
		}

		/**
		 * A {@link Predicate} to define which of the modules shall only be considered targets, i.e. all efferent
		 * relationships are going to be hidden from the rendered view. Modules that have no incoming relationships will
		 * entirely be removed from the view.
		 */
		public DiagramOptions withTargetOnly(Predicate<ApplicationModule> targetOnly) {
			return new DiagramOptions(dependencyTypes, dependencyDepth, exclusions, componentFilter, targetOnly,
					targetFileName, colorSelector, defaultDisplayName, style, elementsWithoutRelationships, skinParams);
		}

		/**
		 * The target file name to be used for the diagram to be created. For individual module diagrams this needs to
		 * include a {@code %s} placeholder for the module names.
		 */
		public DiagramOptions withTargetFileName(String targetFileName) {

			Assert.isTrue(targetFileName.contains("%s"), () -> INVALID_FILE_NAME_PATTERN.formatted(targetFileName));

			return new DiagramOptions(dependencyTypes, dependencyDepth, exclusions, componentFilter, targetOnly,
					targetFileName, colorSelector, defaultDisplayName, style, elementsWithoutRelationships, skinParams);
		}

		/**
		 * A callback to return a hex-encoded color per {@link ApplicationModule}.
		 */
		public DiagramOptions withColorSelector(Function<ApplicationModule, Optional<String>> colorSelector) {
			return new DiagramOptions(dependencyTypes, dependencyDepth, exclusions, componentFilter, targetOnly,
					targetFileName, colorSelector, defaultDisplayName, style, elementsWithoutRelationships, skinParams);
		}

		/**
		 * A callback to return a default display names for a given {@link ApplicationModule}. Default implementation just
		 * forwards to {@link ApplicationModule#getDisplayName()}.
		 */
		public DiagramOptions withDefaultDisplayName(Function<ApplicationModule, String> defaultDisplayName) {
			return new DiagramOptions(dependencyTypes, dependencyDepth, exclusions, componentFilter, targetOnly,
					targetFileName, colorSelector, defaultDisplayName, style, elementsWithoutRelationships, skinParams);
		}

		/**
		 * Which style to render the diagram in. Defaults to {@link DiagramStyle#UML}.
		 */
		public DiagramOptions withStyle(DiagramStyle style) {
			return new DiagramOptions(dependencyTypes, dependencyDepth, exclusions, componentFilter, targetOnly,
					targetFileName, colorSelector, defaultDisplayName, style, elementsWithoutRelationships, skinParams);
		}

		/**
		 * Configuration setting to define whether modules that do not have a relationship to any other module shall be
		 * retained in the diagrams created. The default is {@link ElementsWithoutRelationships#HIDDEN}. See
		 * {@link DiagramOptions#withExclusions(Predicate)} for a more fine-grained way of defining which modules to exclude
		 * in case you flip this to {@link ElementsWithoutRelationships#VISIBLE}.
		 *
		 * @see #withExclusions(Predicate)
		 */
		public DiagramOptions withElementsWithoutRelationships(ElementsWithoutRelationships elementsWithoutRelationships) {
			return new DiagramOptions(dependencyTypes, dependencyDepth, exclusions, componentFilter, targetOnly,
					targetFileName, colorSelector, defaultDisplayName, style, elementsWithoutRelationships, skinParams);
		}

		/**
		 * Configuration setting to add arbitrary skin parameters to the created diagrams. Applies to both the UML and C4
		 * {@link DiagramStyle styles}.
		 *
		 * @param name must not be {@literal null} or empty.
		 * @param value can be {@literal null}.
		 * @return will never be {@literal null}.
		 * @since 1.2.7, 1.3.1
		 */
		public DiagramOptions withSkinParam(String name, @Nullable String value) {

			Assert.hasText(name, "Name must not be null or empty!");

			var newSkinParams = new LinkedHashMap<>(skinParams);
			newSkinParams.put(name, value);

			return new DiagramOptions(dependencyTypes, dependencyDepth, exclusions, componentFilter, targetOnly,
					targetFileName, colorSelector, defaultDisplayName, style, elementsWithoutRelationships, newSkinParams);
		}

		/**
		 * Creates a new default {@link DiagramOptions} instance configured to use all dependency types, list immediate
		 * dependencies for individual module instances, not applying any kind of {@link ApplicationModule} or
		 * {@link Component} filters and default file names.
		 *
		 * @return will never be {@literal null}.
		 */
		public static DiagramOptions defaults() {
			return new DiagramOptions(ALL_TYPES, DependencyDepth.IMMEDIATE, it -> false, it -> true, it -> false, null,
					__ -> Optional.empty(), it -> it.getDisplayName(), DiagramStyle.C4,
					ElementsWithoutRelationships.HIDDEN, new LinkedHashMap<>());
		}

		/**
		 * Select the dependency types that are supposed to be included in the diagram to be created.
		 *
		 * @param types must not be {@literal null}.
		 * @return
		 */
		public DiagramOptions withDependencyTypes(DependencyType... types) {

			Assert.notNull(types, "Dependency types must not be null!");

			Set<DependencyType> dependencyTypes = Arrays.stream(types).collect(Collectors.toSet());

			return new DiagramOptions(dependencyTypes, dependencyDepth, exclusions, componentFilter, targetOnly,
					targetFileName,
					colorSelector, defaultDisplayName, style, elementsWithoutRelationships, skinParams);
		}

		private Optional<String> getTargetFileName() {
			return Optional.ofNullable(targetFileName);
		}

		private Stream<DependencyType> getDependencyTypes() {
			return dependencyTypes.stream();
		}

		private boolean hideElementsWithoutRelationships() {
			return elementsWithoutRelationships.equals(ElementsWithoutRelationships.HIDDEN);
		}

		/**
		 * Different diagram styles.
		 *
		 * @author Oliver Drotbohm
		 */
		public enum DiagramStyle {

			/**
			 * A plain UML component diagram.
			 */
			UML,

			/**
			 * A C4 model component diagram.
			 *
			 * @see <a href="https://c4model.com/#ComponentDiagram">https://c4model.com/#ComponentDiagram</a>
			 */
			C4;
		}

		/**
		 * Configuration setting to define whether modules that do not have a relationship to any other module shall be
		 * retained in the diagrams created. The default is {@link ElementsWithoutRelationships#HIDDEN}. See
		 * {@link DiagramOptions#withExclusions(Predicate)} for a more fine-grained way of defining which modules to exclude
		 * in case you flip this to {@link ElementsWithoutRelationships#VISIBLE}.
		 *
		 * @author Oliver Drotbohm
		 * @see DiagramOptions#withExclusions(Predicate)
		 */
		public enum ElementsWithoutRelationships {
			HIDDEN, VISIBLE;
		}
	}

	public static class CanvasOptions {

		static final Grouping FALLBACK_GROUP = new Grouping("Others", __ -> true, null);

		private final List<Grouping> groupers;
		private final @Nullable String apiBase, targetFileName;
		private final boolean hideInternals, hideEmptyLines;

		CanvasOptions(List<Grouping> groupers, @Nullable String apiBase, @Nullable String targetFileName,
				boolean hideInternals, boolean hideEmptyLines) {

			this.groupers = groupers;
			this.apiBase = apiBase;
			this.targetFileName = targetFileName;
			this.hideInternals = hideInternals;
			this.hideEmptyLines = hideEmptyLines;
		}

		/**
		 * Creates a default {@link CanvasOptions} instance configuring component {@link Groupings} for jMolecules (if on
		 * the classpath) and Spring Framework. Use {@link #withoutDefaultGroupings()} if you prefer to register component
		 * {@link Grouping}s yourself.
		 *
		 * @return will never be {@literal null}.
		 * @see #withoutDefaultGroupings()
		 * @see Groupings
		 */
		public static CanvasOptions defaults() {

			return withoutDefaultGroupings()
					.groupingBy(JMoleculesGroupings.getGroupings())
					.groupingBy(SpringGroupings.getGroupings());
		}

		/**
		 * Creates a {@link CanvasOptions} instance that does not register any default component {@link Grouping}s.
		 *
		 * @return will never be {@literal null}.
		 * @see #defaults()
		 * @see Groupings
		 */
		public static CanvasOptions withoutDefaultGroupings() {
			return new CanvasOptions(new ArrayList<>(), null, null, true, true);
		}

		/**
		 * Creates a new {@link CanvasOptions} with the given {@link Grouping}s added.
		 *
		 * @param groupings must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public CanvasOptions groupingBy(Grouping... groupings) {

			var result = new ArrayList<>(groupers);
			result.addAll(List.of(groupings));

			return new CanvasOptions(result, apiBase, targetFileName, hideInternals, hideEmptyLines);
		}

		/**
		 * Registers a component grouping with the given name and selecting filter.
		 *
		 * @param name must not be {@literal null} or empty.
		 * @param filter must not be {@literal null}.
		 * @return will never be {@literal null}.
		 */
		public CanvasOptions groupingBy(String name, Predicate<SpringBean> filter) {
			return groupingBy(Grouping.of(name, filter, null));
		}

		/**
		 * Registers a component grouping with the given name, selecting filter and description.
		 *
		 * @param name must not be {@literal null} or empty.
		 * @param filter must not be {@literal null}.
		 * @param description must not be {@literal null} or empty.
		 * @return will never be {@literal null}.
		 */
		public CanvasOptions groupingBy(String name, Predicate<SpringBean> filter, String description) {

			Assert.hasText(name, "Name must not be null!");
			Assert.notNull(filter, "Filter must not be null!");
			Assert.hasText(description, "Description must not be null!");

			return groupingBy(Grouping.of(name, filter, description));
		}

		/**
		 * Enables the inclusion of internal components in the module canvas.
		 *
		 * @return will never be {@literal null}.
		 */
		public CanvasOptions revealInternals() {
			return new CanvasOptions(groupers, apiBase, targetFileName, false, hideEmptyLines);
		}

		/**
		 * Enables table rows not containing any values to be retained in the output. By default, no table rows for e.g.
		 * aggregates will be rendered if none are found in the {@link ApplicationModule}.
		 *
		 * @return will never be {@literal null}.
		 */
		public CanvasOptions revealEmptyLines() {
			return new CanvasOptions(groupers, apiBase, targetFileName, hideInternals, false);
		}

		/**
		 * Configures a URI string to act as the base of the Javadoc accessible for the types contained in the canvas. If
		 * set, the output will add links to the Javadoc for those types.
		 *
		 * @param apiBase must not be {@literal null} or empty.
		 * @return will never be {@literal null}.
		 */
		public CanvasOptions withApiBase(String apiBase) {

			Assert.hasText(apiBase, "API base must not be null or empty!");

			return new CanvasOptions(groupers, apiBase, targetFileName, hideInternals, hideEmptyLines);
		}

		/**
		 * Configures the target file name for the canvas to be written. Defaults to {@code module-$moduleName.adoc}.
		 *
		 * @param targetFileName must not be {@literal null} or empty.
		 * @return will never be {@literal null}.
		 */
		public CanvasOptions withTargetFileName(String targetFileName) {
			return new CanvasOptions(groupers, apiBase, targetFileName, hideInternals, hideEmptyLines);
		}

		@Nullable
		String getApiBase() {
			return apiBase;
		}

		Groupings groupBeans(ApplicationModule module) {

			var sources = new ArrayList<Grouping>(groupers);
			sources.add(FALLBACK_GROUP);

			var result = new LinkedMultiValueMap<Grouping, SpringBean>();
			var alreadyMapped = new ArrayList<SpringBean>();

			sources.forEach(it -> {

				var matchingBeans = getMatchingBeans(module, it, alreadyMapped);

				result.addAll(it, matchingBeans);
				alreadyMapped.addAll(matchingBeans);
			});

			// Wipe entries without any beans
			new HashSet<>(result.keySet()).forEach(key -> {

				var value = result.get(key);

				if (value != null && value.isEmpty()) {
					result.remove(key);
				}
			});

			return new Groupings(result);
		}

		Predicate<JavaClass> hideInternalFilter(ApplicationModule module) {
			return hideInternals ? module::isExposed : __ -> true;
		}

		private String getTargetFileName(String moduleName) {
			return (targetFileName == null ? "module-%s.adoc" : targetFileName).formatted(moduleName);
		}

		private List<SpringBean> getMatchingBeans(ApplicationModule module, Grouping filter,
				List<SpringBean> alreadyMapped) {

			return module.getSpringBeans().stream()
					.filter(it -> !hideInternals || it.isApiBean())
					.filter(it -> !alreadyMapped.contains(it))
					.filter(filter::matches)
					.toList();
		}

		public static class Grouping {

			private final String name;
			private final Predicate<SpringBean> predicate;
			private final @Nullable String description;

			/**
			 * Creates a new {@link Grouping} for the given {@link Predicate} and description.
			 *
			 * @param name must not be {@literal null} or empty.
			 * @param predicate must not be {@literal null}.
			 * @param description can be {@literal null}.
			 */
			private Grouping(String name, Predicate<SpringBean> predicate, @Nullable String description) {

				Assert.hasText(name, "Name must not be null or empty!");
				Assert.notNull(predicate, "Predicate must not be null!");
				Assert.isTrue(description == null || !description.isBlank(), "Description must not be empty or null!");

				this.name = name;
				this.predicate = predicate;
				this.description = description;
			}

			/**
			 * Creates a {@link Grouping} with the given name and selecting {@link Predicate}.
			 *
			 * @param name must not be {@literal null} or empty.
			 * @param predicate must not be {@literal null}.
			 * @return will never be {@literal null}.
			 */
			public static Grouping of(String name, Predicate<SpringBean> predicate) {
				return new Grouping(name, predicate, null);
			}

			/**
			 * Creates a {@link Grouping} with the given name, selecting {@link Predicate} and description.
			 *
			 * @param name must not be {@literal null} or empty.
			 * @param predicate must not be {@literal null}.
			 * @param description can be {@literal null}.
			 * @return will never be {@literal null}.
			 */
			public static Grouping of(String name, Predicate<SpringBean> predicate, @Nullable String description) {
				return new Grouping(name, predicate, description);
			}

			/**
			 * Helper method to create a {@link Predicate} for {@link SpringBean}s matching the given name pattern.
			 *
			 * @param pattern must not be {@literal null} or empty.
			 * @return will never be {@literal null}.
			 */
			public static Predicate<SpringBean> nameMatching(String pattern) {

				Assert.hasText(pattern, "Pattern must not be null or empty!");

				return bean -> bean.getFullyQualifiedTypeName().matches(pattern);
			}

			/**
			 * Helper method to create a {@link Predicate} for {@link SpringBean}s implementing the given interface.
			 *
			 * @param type must not be {@literal null}.
			 * @return will never be {@literal null}.
			 */
			public static Predicate<SpringBean> implementing(Class<?> type) {

				Assert.notNull(type, "Type must not be null!");

				return bean -> bean.getType().isAssignableTo(type);
			}

			/**
			 * Helper method to create a {@link Predicate} for {@link SpringBean}s that are a subtype of the given one. In
			 * other words, implement or extend it but are not the type itself.
			 *
			 * @param type must not be {@literal null}.
			 * @return will never be {@literal null}.
			 */
			public static Predicate<SpringBean> subtypeOf(Class<?> type) {

				Assert.notNull(type, "Type must not be null!");

				return implementing(type) //
						.and(bean -> !bean.getType().isEquivalentTo(type));
			}

			public static Predicate<SpringBean> isAnnotatedWith(Class<? extends Annotation> type) {
				return bean -> bean.getType().isAnnotatedWith(type);
			}

			/**
			 * Returns the name of the {@link Grouping}.
			 *
			 * @return will never be {@literal null} or empty.
			 */
			public String getName() {
				return name;
			}

			/**
			 * Returns the description of the {@link Grouping}.
			 *
			 * @return can be {@literal null}.
			 */
			@Nullable
			public String getDescription() {
				return description;
			}

			/**
			 * Returns whether the given {@link SpringBean} matches the {@link Grouping}.
			 *
			 * @param candidate must not be {@literal null}.
			 */
			public boolean matches(SpringBean candidate) {

				Assert.notNull(candidate, "Candidate Spring bean must not be null!");

				return predicate.test(candidate);
			}
		}

		static class Groupings {

			private final MultiValueMap<Grouping, SpringBean> groupings;

			Groupings(MultiValueMap<Grouping, SpringBean> groupings) {

				Assert.notNull(groupings, "Groupings must not be null!");

				this.groupings = groupings;
			}

			Set<Grouping> keySet() {
				return groupings.keySet();
			}

			List<SpringBean> byGrouping(Grouping grouping) {
				return byFilter(grouping::equals);
			}

			List<SpringBean> byGroupName(String name) {
				return byFilter(it -> it.name.equals(name));
			}

			void forEach(BiConsumer<Grouping, List<SpringBean>> consumer) {
				groupings.forEach(consumer);
			}

			private List<SpringBean> byFilter(Predicate<Grouping> filter) {

				return groupings.entrySet().stream()
						.filter(it -> filter.test(it.getKey()))
						.findFirst()
						.map(Entry::getValue)
						.orElseGet(Collections::emptyList);
			}

			boolean hasOnlyFallbackGroup() {
				return groupings.size() == 1 && groupings.get(FALLBACK_GROUP) != null;
			}
		}
	}

	public static class Options {

		private final OutputFolder outputFolder;
		private final boolean clean;

		/**
		 * @param outputFolder the folder to write the files to, can be {@literal null}.
		 * @param clean whether to clean the target directory on rendering.
		 */
		private Options(OutputFolder outputFolder, boolean clean) {

			this.outputFolder = outputFolder;
			this.clean = clean;
		}

		/**
		 * Creates a default {@link Options} instance configuring a default output folder based on the detected build tool
		 * (see {@link OutputFolder#DEFAULT_LOCATION}). Use {@link #withOutputFolder(String)} if you want to customize the
		 * output folder. By default, the output folder is wiped before any files are written to it. Use
		 * {@link #withoutClean()} to disable cleaning of the output folder.
		 *
		 * @return will never be {@literal null}.
		 * @see #withoutClean()
		 * @see #withOutputFolder(String)
		 */
		public static Options defaults() {
			return new Options(OutputFolder.forDefaultLocation(), true);
		}

		/**
		 * Disables the cleaning of the output folder before any files are written.
		 *
		 * @return will never be {@literal null}.
		 */
		public Options withoutClean() {
			return new Options(outputFolder, false);
		}

		/**
		 * Configures the output folder for the created files. The given directory is wiped before any files are written to
		 * it.
		 *
		 * @param folder if null the default location based on the detected build tool will be used (see
		 *          {@link OutputFolder#DEFAULT_LOCATION}). The given folder will be created if it does not exist already.
		 *          Existing folders are supported as well.
		 * @return will never be {@literal null}.
		 */
		public Options withOutputFolder(@Nullable String folder) {
			return new Options(OutputFolder.forLocation(folder), clean);
		}

		OutputFolder getOutputFolder() {
			return outputFolder;
		}
	}

	static class OutputFolder {

		private static final String DEFAULT_LOCATION = (new File("pom.xml").exists() ? "target" : "build")
				.concat("/spring-modulith-docs");

		private final String path;

		private OutputFolder(String path) {

			this.path = path;

			try {
				Files.createDirectories(Path.of(path));
			} catch (IOException o_O) {
				throw new RuntimeException(o_O);
			}
		}

		static OutputFolder forLocation(@Nullable String location) {
			return new OutputFolder(location == null ? DEFAULT_LOCATION : location);
		}

		static OutputFolder forDefaultLocation() {
			return new OutputFolder(DEFAULT_LOCATION);
		}

		boolean contains(String filename) {
			return Files.exists(Paths.get(path, filename));
		}

		OutputFolder deleteIfExists() {

			var path = Path.of(this.path);

			if (!Files.exists(path)) {
				return this;
			}

			try {

				Files.walk(path)
						.sorted(Comparator.reverseOrder())
						.map(Path::toFile)
						.forEach(File::delete);

			} catch (IOException o_O) {
				throw new RuntimeException(o_O);
			}

			return this;
		}

		void writeToFile(String name, String content) {

			var path = deleteIfExists(name).createFile(name);

			try (FileWriter writer = new FileWriter(path.toFile())) {

				writer.write(content);

			} catch (IOException o_O) {
				throw new RuntimeException(o_O);
			}
		}

		private OutputFolder deleteIfExists(String name) {

			try {
				Files.deleteIfExists(Path.of(path, name));
			} catch (IOException o_O) {
				throw new RuntimeException(o_O);
			}

			return this;
		}

		private Path createFile(String name) {

			var path = Path.of(this.path);

			try {

				Files.createDirectories(path);
				return Files.createFile(path.resolve(name));

			} catch (IOException o_O) {
				throw new RuntimeException(o_O);
			}
		}
	}

	private static class CustomizedPlantUmlExporter extends StructurizrPlantUMLExporter {

		@Override
		protected boolean includeTitle(ModelView view) {
			return false;
		};

		@Override
		protected void startContainerBoundary(ModelView view, Container container, IndentingWriter writer) {}

		@Override
		protected void endContainerBoundary(ModelView view, IndentingWriter writer) {};
	};
}
