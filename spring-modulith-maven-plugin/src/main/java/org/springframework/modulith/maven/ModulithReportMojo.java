/*
 * Copyright 2026 the original author or authors.
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
package org.springframework.modulith.maven;

import java.io.File;
import java.net.URLClassLoader;
import java.util.Locale;

import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter.DiagramOptions.DiagramStyle;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

@Mojo(name = "report", defaultPhase = LifecyclePhase.SITE, threadSafe = true,
		requiresDependencyResolution = ResolutionScope.RUNTIME)
@Execute(phase = LifecyclePhase.COMPILE)
public class ModulithReportMojo extends AbstractMavenReport {

	@Component
	private Renderer siteRenderer;

	@Parameter(defaultValue = "${project}", required = true, readonly = true)
	private MavenProject project;

	@Parameter(property = "spring.modulith.report.skip", defaultValue = "false")
	private boolean skip;

	@Parameter(property = "spring.modulith.report.applicationClassName")
	private String applicationClassName;

	@Parameter(property = "spring.modulith.report.basePackage")
	private String basePackage;

	@Parameter(property = "spring.modulith.report.name", defaultValue = "Spring Modulith Report")
	private String siteReportName;

	@Parameter(property = "spring.modulith.report.description", defaultValue = "Application module dependency diagrams generated from Spring Modulith metadata")
	private String siteReportDescription;

	@Parameter(property = "spring.modulith.report.outputdir", defaultValue = "modulith")
	private String siteReportDirectory;

	@Parameter(property = "spring.modulith.report.renderDiagrams", defaultValue = "true")
	private boolean renderDiagrams;

	@Parameter(property = "spring.modulith.report.diagramStyle", defaultValue = "C4")
	private DiagramStyle diagramStyle;

	private final ProjectClassLoaderFactory classLoaderFactory = new ProjectClassLoaderFactory();
	private final ModulithSiteReportGenerator generator = new ModulithSiteReportGenerator(new PlantUmlSvgRenderer());

	@Override
	public String getOutputName() {
		return siteReportDirectory + File.separator + "index";
	}

	@Override
	public String getName(Locale locale) {
		return siteReportName;
	}

	@Override
	public String getDescription(Locale locale) {
		return siteReportDescription;
	}

	@Override
	protected Renderer getSiteRenderer() {
		return siteRenderer;
	}

	@Override
	protected String getOutputDirectory() {
		return getReportOutputDirectory().getAbsolutePath();
	}

	@Override
	protected MavenProject getProject() {
		return project;
	}

	@Override
	protected void executeReport(Locale locale) throws MavenReportException {

		validateConfiguration();

		try (URLClassLoader classLoader = classLoaderFactory.create(project)) {

			var original = Thread.currentThread().getContextClassLoader();

			try {
				Thread.currentThread().setContextClassLoader(classLoader);

				var modules = createApplicationModules(classLoader);
				var outputDirectory = getReportOutputDirectory().toPath().resolve(siteReportDirectory);
				generator.generate(modules, outputDirectory, diagramStyle, renderDiagrams);

			} finally {
				Thread.currentThread().setContextClassLoader(original);
			}

		} catch (Exception ex) {
			throw new MavenReportException("Failed to generate Spring Modulith site report", ex);
		}
	}

	@Override
	public boolean canGenerateReport() {
		return !skip;
	}

	@Override
	public boolean isExternalReport() {
		return true;
	}

	private ApplicationModules createApplicationModules(ClassLoader classLoader) throws ClassNotFoundException {

		if (applicationClassName != null && !applicationClassName.isBlank()) {
			Class<?> applicationType = ClassUtils.forName(applicationClassName, classLoader);
			return ApplicationModules.of(applicationType);
		}

		Assert.hasText(basePackage, "Either applicationClassName or basePackage must be configured!");
		return ApplicationModules.of(basePackage);
	}

	private void validateConfiguration() {

		boolean hasApplicationClass = applicationClassName != null && !applicationClassName.isBlank();
		boolean hasBasePackage = basePackage != null && !basePackage.isBlank();

		if (hasApplicationClass == hasBasePackage) {
			throw new IllegalArgumentException(
					"Configure exactly one of spring.modulith.report.applicationClassName or spring.modulith.report.basePackage.");
		}
	}
}

