/*
 * Copyright 2022-2024 the original author or authors.
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
package org.springframework.modulith.observability;

import io.micrometer.tracing.Tracer;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcher;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.modulith.runtime.ApplicationModulesRuntime;
import org.springframework.util.Assert;

/**
 * A {@link BeanPostProcessor} that decorates beans exposed by application modules with an interceptor that registers
 * module entry and exit to create tracing spans for those invocations.
 *
 * @author Oliver Drotbohm
 */
public class ModuleTracingBeanPostProcessor extends ModuleTracingSupport implements BeanPostProcessor {

	public static final String MODULE_BAGGAGE_KEY = "org.springframework.modulith.module";

	private final ApplicationModulesRuntime runtime;
	private final Supplier<Tracer> tracer;
	private final Map<String, Advisor> advisors;
	private final ConfigurableListableBeanFactory factory;

	/**
	 * Creates a new {@link ModuleTracingBeanPostProcessor} for the given {@link ApplicationModulesRuntime} and
	 * {@link Tracer}.
	 *
	 * @param runtime must not be {@literal null}.
	 * @param tracer must not be {@literal null}.
	 */
	public ModuleTracingBeanPostProcessor(ApplicationModulesRuntime runtime, Supplier<Tracer> tracer,
			ConfigurableListableBeanFactory factory) {

		Assert.notNull(runtime, "ApplicationModulesRuntime must not be null!");
		Assert.notNull(tracer, "Tracer must not be null!");

		this.runtime = runtime;
		this.tracer = tracer;
		this.advisors = new HashMap<>();
		this.factory = factory;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.config.BeanPostProcessor#postProcessAfterInitialization(java.lang.Object, java.lang.String)
	 */
	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {

		var type = runtime.getUserClass(bean, beanName);

		if (!type.isInstance(bean) || isInfrastructureBean(beanName) || !runtime.isApplicationClass(type)) {
			return bean;
		}

		if (alreadyAdvised(bean)) {
			return bean;
		}

		var modules = runtime.get();

		return modules.getModuleByType(type.getName()).map(DefaultObservedModule::new).map(it -> {

			var moduleType = it.getObservedModuleType(type, modules);

			return moduleType != null //
					? addAdvisor(bean, getOrBuildAdvisor(it, moduleType)) //
					: bean;

		}).orElse(bean);
	}

	private boolean isInfrastructureBean(String beanName) {

		return factory.containsBean(beanName) &&
				factory.getBeanDefinition(beanName).getRole() == BeanDefinition.ROLE_INFRASTRUCTURE;
	}

	private Advisor getOrBuildAdvisor(ObservedModule module, ObservedModuleType type) {

		return advisors.computeIfAbsent(module.getName(), __ -> {
			return new ApplicationModuleObservingAdvisor(type, ModuleEntryInterceptor.of(module, tracer.get()));
		});
	}

	private static boolean alreadyAdvised(Object bean) {

		return bean instanceof Advised advised
				&& Arrays.stream(advised.getAdvisors())
						.anyMatch(ApplicationModuleObservingAdvisor.class::isInstance);
	}

	private static class ObservableTypeMethodMatcher extends StaticMethodMatcher {

		private final ObservedModuleType type;

		/**
		 * Creates a new {@link ObservableTypeMethodMatcher} for the given {@link ObservedModuleType}.
		 *
		 * @param type must not be {@literal null}.
		 */
		private ObservableTypeMethodMatcher(ObservedModuleType type) {

			Assert.notNull(type, "ObservableModuleType must not be null!");

			this.type = type;
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.aop.MethodMatcher#matches(java.lang.reflect.Method, java.lang.Class)
		 */
		@Override
		public boolean matches(Method method, Class<?> targetClass) {
			return type.getMethodsToIntercept().test(method);
		}
	}

	static class ApplicationModuleObservingAdvisor extends DefaultPointcutAdvisor {

		private static final long serialVersionUID = -391548409986032658L;

		public ApplicationModuleObservingAdvisor(ObservedModuleType type, ModuleEntryInterceptor interceptor) {
			super(new ComposablePointcut(new ObservableTypeMethodMatcher(type)), interceptor);
		}
	}
}
