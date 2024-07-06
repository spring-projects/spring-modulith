/*
 * Copyright 2018-2024 the original author or authors.
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
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.lang.Nullable;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModuleDependency;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.DependencyDepth;
import org.springframework.modulith.core.DependencyType;
import org.springframework.modulith.core.SpringBean;
import org.springframework.modulith.docs.Groupings.JMoleculesGroupings;
import org.springframework.modulith.docs.Groupings.SpringGroupings;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.structurizr.Workspace;
import com.structurizr.export.IndentingWriter;
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
 */
public class Documenter {

	private static final Map<DependencyType, String> DEPENDENCY_DESCRIPTIONS = new LinkedHashMap<>();
	private static final String INVALID_FILE_NAME_PATTERN = "Configured file name pattern does not include a '%s' placeholder for the module name!";
	private static final String DEFAULT_LOCATION = "spring-modulith-docs";

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
	private final String outputFolder;

	private Map<ApplicationModule, Component> components;

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
		this(modules, getDefaultOutputDirectory());
	}

	/**
	 * Creates a new {@link Documenter} for the given {@link ApplicationModules} and output folder.
	 *
	 * @param modules must not be {@literal null}.
	 * @param outputFolder must not be {@literal null} or empty.
	 */
	public Documenter(ApplicationModules modules, String outputFolder) {

		Assert.notNull(modules, "Modules must not be null!");
		Assert.hasText(outputFolder, "Output folder must not be null or empty!");

		this.modules = modules;
		this.outputFolder = outputFolder;
		this.workspace = new Workspace("Modulith", "");

		workspace.getViews().getConfiguration()
				.getStyles()
				.addElementStyle(Tags.COMPONENT)
				.shape(Shape.Component);

		Model model = workspace.getModel();
		String systemName = modules.getSystemName().orElse("Modulith");

		SoftwareSystem system = model.addSoftwareSystem(systemName, "");

		this.container = system.addContainer(systemName, "", "");
		this.properties = new ConfigurationProperties();
	}

	/**
	 * Customize the output folder to write the generated files to. Defaults to {@value #DEFAULT_LOCATION}.
	 *
	 * @param outputFolder must not be {@literal null} or empty.
	 * @return will never be {@literal null}.
	 * @deprecated use {@link Documenter(ApplicationModules, String)} directly.
	 */
	@Deprecated(forRemoval = true)
	public Documenter withOutputFolder(String outputFolder) {
		return new Documenter(modules, outputFolder);
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
	 * @param options must not be {@literal null}.
	 * @param canvasOptions must not be {@literal null}.
	 * @return the current instance, will never be {@literal null}.
	 */
	public Documenter writeDocumentation(DiagramOptions options, CanvasOptions canvasOptions) {

		return writeModulesAsPlantUml(options)
				.writeIndividualModulesAsPlantUml(options)
				.writeModuleCanvases(canvasOptions)
				.writeAggregatingDocument(options, canvasOptions);
	}

	/**
	 * Writes aggregating document called 'all-docs.adoc' that includes any existing component diagrams and canvases.
	 * using {@link DiagramOptions#defaults()} and {@link CanvasOptions#defaults()}.
	 *
	 * @return the current instance, will never be {@literal null}.
	 */
	public Documenter writeAggregatingDocument(){

		return writeAggregatingDocument(DiagramOptions.defaults(), CanvasOptions.defaults());
	}

	/**
	 * Writes aggregating document called 'all-docs.adoc' that includes any existing component diagrams and canvases.
	 *
	 * @param options must not be {@literal null}.
	 * @param canvasOptions must not be {@literal null}.
	 * @return the current instance, will never be {@literal null}.
	 */
	public Documenter writeAggregatingDocument(DiagramOptions options, CanvasOptions canvasOptions){

		Assert.notNull(options, "DiagramOptions must not be null!");
		Assert.notNull(canvasOptions, "CanvasOptions must not be null!");

		var asciidoctor = Asciidoctor.withJavadocBase(modules, canvasOptions.getApiBase());
		var outputFolder = new OutputFolder(this.outputFolder);

		// Get file name for module overview diagram
		var componentsFilename = options.getTargetFileName().orElse(DEFAULT_COMPONENTS_FILE);
		var componentsDoc = new StringBuilder();

		if (outputFolder.contains(componentsFilename)) {
			componentsDoc.append(asciidoctor.renderHeadline(2, modules.getSystemName().orElse("Modules")))
					.append(asciidoctor.renderPlantUmlInclude(componentsFilename))
					.append(System.lineSeparator());
		}

		// Get file names for individual module diagrams and canvases
		var moduleDocs = modules.stream().map(it -> {

			// Get diagram file name, e.g. module-inventory.puml
			var fileNamePattern = options.getTargetFileName().orElse(DEFAULT_MODULE_COMPONENTS_FILE);
			Assert.isTrue(fileNamePattern.contains("%s"), () -> String.format(INVALID_FILE_NAME_PATTERN, fileNamePattern));
			var filename = String.format(fileNamePattern, it.getName());

			// Get canvas file name, e.g. module-inventory.adoc
			var canvasFilename = canvasOptions.getTargetFileName(it.getName());

			// Generate output, e.g.:
			/*
			== Inventory
			plantuml::module-inventory.puml[]
			include::module-inventory.adoc[]
			 */
			var content = new StringBuilder();
			content.append((outputFolder.contains(filename) ? asciidoctor.renderPlantUmlInclude(filename) : ""))
					.append((outputFolder.contains(canvasFilename) ? asciidoctor.renderGeneralInclude(canvasFilename) : ""));
			if (!content.isEmpty()) {
				content.insert(0, asciidoctor.renderHeadline(2, it.getDisplayName()))
						.append(System.lineSeparator());
			}
			return content.toString();

		}).collect(Collectors.joining());

		var allDocs = componentsDoc.append(moduleDocs).toString();

		// Write file to all-docs.adoc
		if (!allDocs.isBlank()) {
			Path file = recreateFile("all-docs.adoc");

			try (Writer writer = new FileWriter(file.toFile())) {
				writer.write(allDocs);
			} catch (IOException o_O) {
				throw new RuntimeException(o_O);
			}
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
	 * @param options must not be {@literal null}.
	 * @return the current instance, will never be {@literal null}.
	 */
	public Documenter writeModulesAsPlantUml(DiagramOptions options) {

		Assert.notNull(options, "Options must not be null!");

		Path file = recreateFile(options.getTargetFileName().orElse(DEFAULT_COMPONENTS_FILE));

		try (Writer writer = new FileWriter(file.toFile())) {
			writer.write(createPlantUml(options));
		} catch (IOException o_O) {
			throw new RuntimeException(o_O);
		}

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

		var view = createComponentView(options, module);
		view.setTitle(options.defaultDisplayName.apply(module));

		addComponentsToView(module, view, options);

		var fileNamePattern = options.getTargetFileName().orElse(DEFAULT_MODULE_COMPONENTS_FILE);

		Assert.isTrue(fileNamePattern.contains("%s"), () -> String.format(INVALID_FILE_NAME_PATTERN, fileNamePattern));

		return writeViewAsPlantUml(view, String.format(fileNamePattern, module.getName()), options);
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
	 * @param options must not be {@literal null}.
	 * @return the current instance, will never be {@literal null}.
	 */
	public Documenter writeModuleCanvases(CanvasOptions options) {

		Assert.notNull(options, "CanvasOptions must not be null!");

		modules.forEach(module -> {

			var filename = options.getTargetFileName(module.getName());
			var file = recreateFile(filename);

			try (FileWriter writer = new FileWriter(file.toFile())) {

				writer.write(toModuleCanvas(module, options));

			} catch (IOException o_O) {
				throw new RuntimeException(o_O);
			}
		});

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
				.append(addTableRow("Base package", asciidoctor.toInlineCode(module.getBasePackage().getName()), options))

				// Spring components
				.append(addTableRow("Spring components", asciidoctor.renderSpringBeans(module, options), options)) //
				.append(addTableRow("Bean references", asciidoctor.renderBeanReferences(module), options)) //

				// Aggregates
				.append(addTableRow(aggregates, "Aggregate roots", mapper, options)) //
				.append(addTableRow(valueTypes, "Value types", mapper, options)) //

				// Events
				.append(addTableRow("Published events", asciidoctor.renderEvents(module), options)) //
				.append(addTableRow(module.getEventsListenedTo(modules), "Events listened to", mapper, options)) //

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

			module.getDependencies(modules, entry.getKey()).stream() //
					.map(ApplicationModuleDependency::getTargetModule) //
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

	private Map<ApplicationModule, Component> getComponents(DiagramOptions options) {

		if (components == null) {

			this.components = modules.stream() //
					.collect(Collectors.toMap(Function.identity(),
							it -> container.addComponent(options.defaultDisplayName.apply(it), "", "Module")));

			this.components.forEach((key, value) -> addDependencies(key, value, options));
		}

		return components;
	}

	private void addComponentsToView(ApplicationModule module, ComponentView view, DiagramOptions options) {

		Supplier<Stream<ApplicationModule>> bootstrapDependencies = () -> module.getBootstrapDependencies(modules,
				options.dependencyDepth);
		Supplier<Stream<ApplicationModule>> otherDependencies = () -> options.getDependencyTypes()
				.flatMap(it -> module.getDependencies(modules, it).stream()
						.map(ApplicationModuleDependency::getTargetModule));

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

	private Documenter writeViewAsPlantUml(ComponentView view, String filename, DiagramOptions options) {

		Path file = recreateFile(filename);

		try (Writer writer = new FileWriter(file.toFile())) {

			writer.write(render(view, options));

			return this;

		} catch (IOException o_O) {
			throw new RuntimeException(o_O);
		}
	}

	private String render(ComponentView view, DiagramOptions options) {

		switch (options.style) {

			case C4:

				var c4PlantUmlExporter = new C4PlantUMLExporter();
				var diagram = c4PlantUmlExporter.export(view);

				return diagram.getDefinition();

			case UML:
			default:

				var plantUmlExporter = new CustomizedPlantUmlExporter();
				plantUmlExporter.addSkinParam("componentStyle", "uml1");

				return plantUmlExporter.export(view).getDefinition();
		}
	}

	private String createPlantUml(DiagramOptions options) {

		ComponentView componentView = createComponentView(options);
		componentView.setTitle(modules.getSystemName().orElse("Modules"));

		addComponentsToView(() -> modules.stream(), componentView, options, it -> {});

		return render(componentView, options);
	}

	private ComponentView createComponentView(DiagramOptions options) {
		return createComponentView(options, null);
	}

	private ComponentView createComponentView(DiagramOptions options, @Nullable ApplicationModule module) {

		String prefix = module == null ? "modules-" : module.getName();

		return workspace.getViews() //
				.createComponentView(container, prefix + options.toString(), "");
	}

	private Path recreateFile(String name) {

		try {

			Files.createDirectories(Paths.get(outputFolder));
			Path filePath = Paths.get(outputFolder, name);
			Files.deleteIfExists(filePath);

			return Files.createFile(filePath);

		} catch (IOException o_O) {
			throw new RuntimeException(o_O);
		}
	}

	private static Component applyBackgroundColor(ApplicationModule module,
			Map<ApplicationModule, Component> components,
			DiagramOptions options,
			Styles styles) {

		var component = components.get(module);
		var selector = options.colorSelector;

		// Apply custom color if configured
		selector.apply(module).ifPresent(color -> {

			var tag = module.getName() + "-" + color;
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

	private static <T> String addTableRow(List<T> types, String header, Function<List<T>, String> mapper,
			CanvasOptions options) {
		return options.hideEmptyLines && types.isEmpty() ? "" : writeTableRow(header, mapper.apply(types));
	}

	/**
	 * Returns the default output directory based on the detected build system.
	 *
	 * @return will never be {@literal null}.
	 */
	private static String getDefaultOutputDirectory() {
		return (new File("pom.xml").exists() ? "target" : "build").concat("/").concat(DEFAULT_LOCATION);
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
		 */
		DiagramOptions(Set<DependencyType> dependencyTypes, DependencyDepth dependencyDepth,
				Predicate<ApplicationModule> exclusions, Predicate<Component> componentFilter,
				Predicate<ApplicationModule> targetOnly, @Nullable String targetFileName,
				Function<ApplicationModule, Optional<String>> colorSelector,
				Function<ApplicationModule, String> defaultDisplayName, DiagramStyle style,
				ElementsWithoutRelationships elementsWithoutRelationships) {

			Assert.notNull(dependencyTypes, "Dependency types must not be null!");
			Assert.notNull(dependencyDepth, "Dependency depth must not be null!");
			Assert.notNull(exclusions, "Exclusions must not be null!");
			Assert.notNull(componentFilter, "Component filter must not be null!");
			Assert.notNull(targetOnly, "Target only must not be null!");
			Assert.notNull(colorSelector, "Color selector must not be null!");
			Assert.notNull(defaultDisplayName, "Default display name must not be null!");
			Assert.notNull(style, "DiagramStyle must not be null!");
			Assert.notNull(elementsWithoutRelationships, "ElementsWithoutRelationships must not be null!");

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
		}

		/**
		 * The {@link DependencyDepth} to define which other modules to be included in the diagram to be created.
		 */
		public DiagramOptions withDependencyDepth(DependencyDepth dependencyDepth) {
			return new DiagramOptions(dependencyTypes, dependencyDepth, exclusions, componentFilter, targetOnly,
					targetFileName, colorSelector, defaultDisplayName, style, elementsWithoutRelationships);
		}

		/**
		 * A {@link Predicate} to define the which modules to exclude from the diagram to be created.
		 */
		public DiagramOptions withExclusions(Predicate<ApplicationModule> exclusions) {
			return new DiagramOptions(dependencyTypes, dependencyDepth, exclusions, componentFilter, targetOnly,
					targetFileName, colorSelector, defaultDisplayName, style, elementsWithoutRelationships);
		}

		/**
		 * A {@link Predicate} to define which Structurizr {@link Component}s to be included in the diagram to be created.
		 */
		public DiagramOptions withComponentFilter(Predicate<Component> componentFilter) {
			return new DiagramOptions(dependencyTypes, dependencyDepth, exclusions, componentFilter, targetOnly,
					targetFileName, colorSelector, defaultDisplayName, style, elementsWithoutRelationships);
		}

		/**
		 * A {@link Predicate} to define which of the modules shall only be considered targets, i.e. all efferent
		 * relationships are going to be hidden from the rendered view. Modules that have no incoming relationships will
		 * entirely be removed from the view.
		 */
		public DiagramOptions withTargetOnly(Predicate<ApplicationModule> targetOnly) {
			return new DiagramOptions(dependencyTypes, dependencyDepth, exclusions, componentFilter, targetOnly,
					targetFileName, colorSelector, defaultDisplayName, style, elementsWithoutRelationships);
		}

		/**
		 * The target file name to be used for the diagram to be created. For individual module diagrams this needs to
		 * include a {@code %s} placeholder for the module names.
		 */
		public DiagramOptions withTargetFileName(String targetFileName) {
			return new DiagramOptions(dependencyTypes, dependencyDepth, exclusions, componentFilter, targetOnly,
					targetFileName, colorSelector, defaultDisplayName, style, elementsWithoutRelationships);
		}

		/**
		 * A callback to return a hex-encoded color per {@link ApplicationModule}.
		 */
		public DiagramOptions withColorSelector(Function<ApplicationModule, Optional<String>> colorSelector) {
			return new DiagramOptions(dependencyTypes, dependencyDepth, exclusions, componentFilter, targetOnly,
					targetFileName, colorSelector, defaultDisplayName, style, elementsWithoutRelationships);
		}

		/**
		 * A callback to return a default display names for a given {@link ApplicationModule}. Default implementation just
		 * forwards to {@link ApplicationModule#getDisplayName()}.
		 */
		public DiagramOptions withDefaultDisplayName(Function<ApplicationModule, String> defaultDisplayName) {
			return new DiagramOptions(dependencyTypes, dependencyDepth, exclusions, componentFilter, targetOnly,
					targetFileName, colorSelector, defaultDisplayName, style, elementsWithoutRelationships);
		}

		/**
		 * Which style to render the diagram in. Defaults to {@link DiagramStyle#UML}.
		 */
		public DiagramOptions withStyle(DiagramStyle style) {
			return new DiagramOptions(dependencyTypes, dependencyDepth, exclusions, componentFilter, targetOnly,
					targetFileName, colorSelector, defaultDisplayName, style, elementsWithoutRelationships);
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
					targetFileName, colorSelector, defaultDisplayName, style, elementsWithoutRelationships);
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
					ElementsWithoutRelationships.HIDDEN);
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
					colorSelector, defaultDisplayName, style, elementsWithoutRelationships);
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
				if (result.get(key).isEmpty()) {
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
			 * Creates a {@link Grouping} with the given name.
			 *
			 * @param name must not be {@literal null} or empty.
			 * @return will never be {@literal null}.
			 * @deprecated no replacement as a name-only {@link Grouping} doesn't make any sense in the first place.
			 */
			@Deprecated
			public static Grouping of(String name) {
				return new Grouping(name, __ -> false, null);
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
			 * @param description must not be {@literal null} or empty.
			 * @return will never be {@literal null}.
			 */
			public static Grouping of(String name, Predicate<SpringBean> predicate, String description) {
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

	private static class OutputFolder {

		private final String path;

        OutputFolder(String path) {
            this.path = path;
        }

		boolean contains(String filename) {
			return Files.exists(Paths.get(path, filename));
		}
    }
}
