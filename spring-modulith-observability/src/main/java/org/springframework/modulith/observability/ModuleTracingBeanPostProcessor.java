/*
 * Copyright 2022-2023 the original author or authors.
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
import lombok.RequiredArgsConstructor;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.aopalliance.aop.Advice;
import org.springframework.aop.Advisor;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcher;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.modulith.model.ApplicationModules;
import org.springframework.modulith.runtime.ApplicationModulesRuntime;

/**
 * @author Oliver Drotbohm
 */
public class ModuleTracingBeanPostProcessor extends ModuleTracingSupport implements BeanPostProcessor {

	public static final String MODULE_BAGGAGE_KEY = "org.springframework.modulith.module";

	private final ApplicationModulesRuntime runtime;
	private final Tracer tracer;
	private final Map<String, Advisor> advisors = new HashMap<>();

	public ModuleTracingBeanPostProcessor(ApplicationModulesRuntime runtime, Tracer tracer) {

		super(runtime);

		this.runtime = runtime;
		this.tracer = tracer;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.beans.factory.config.BeanPostProcessor#postProcessAfterInitialization(java.lang.Object, java.lang.String)
	 */
	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {

		Class<?> type = getBeanUserClass(bean, beanName);

		if (!runtime.isApplicationClass(type) || !type.isInstance(bean)) {
			return bean;
		}

		ApplicationModules modules = runtime.get();

		return modules.getModuleByType(type.getName())
				.map(DefaultObservedModule::new)
				.map(it -> {

					ObservedModuleType moduleType = it.getObservedModuleType(type, modules);

					return moduleType != null //
							? addAdvisor(bean, getOrBuildAdvisor(it, moduleType)) //
							: bean;

				}).orElse(bean);
	}

	private Advisor getOrBuildAdvisor(ObservedModule module, ObservedModuleType type) {

		return advisors.computeIfAbsent(module.getName(), __ -> {

			Advice interceptor = ModuleEntryInterceptor.of(module, tracer);
			MethodMatcher matcher = new ObservableTypeMethodMatcher(type);
			Pointcut pointcut = new ComposablePointcut(matcher);

			return new DefaultPointcutAdvisor(pointcut, interceptor);
		});
	}

	@RequiredArgsConstructor
	private static class ObservableTypeMethodMatcher extends StaticMethodMatcher {

		private final ObservedModuleType type;

		/*
		 * (non-Javadoc)
		 * @see org.springframework.aop.MethodMatcher#matches(java.lang.reflect.Method, java.lang.Class)
		 */
		@Override
		public boolean matches(Method method, Class<?> targetClass) {
			return type.getMethodsToIntercept().test(method);
		}
	}
}
