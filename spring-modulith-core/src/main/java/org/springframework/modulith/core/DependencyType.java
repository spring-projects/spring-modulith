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
package org.springframework.modulith.core;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.springframework.modulith.core.Types.JMoleculesTypes;
import org.springframework.modulith.core.Types.SpringTypes;
import org.springframework.util.Assert;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.thirdparty.com.google.common.base.Supplier;

/**
 * The type of dependency between {@link ApplicationModule}s.
 *
 * @author Oliver Drotbohm
 */
public enum DependencyType {

	/**
	 * Indicates that the module depends on the other one by a component dependency, i.e. that other module needs to be
	 * bootstrapped to run the source module.
	 */
	USES_COMPONENT {

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.Module.DependencyType#format(org.springframework.modulith.model.FormatableJavaClass, org.springframework.modulith.model.FormatableJavaClass)
		 */
		@Override
		public String format(FormatableType source, FormatableType target) {
			return String.format("Component %s using %s", source.getAbbreviatedFullName(), target.getAbbreviatedFullName());
		}
	},

	/**
	 * Indicates that the module refers to an entity of the other.
	 */
	ENTITY {

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.Module.DependencyType#format(org.springframework.modulith.model.FormatableJavaClass, org.springframework.modulith.model.FormatableJavaClass)
		 */
		@Override
		public String format(FormatableType source, FormatableType target) {
			return String.format("Entity %s depending on %s", source.getAbbreviatedFullName(),
					target.getAbbreviatedFullName());
		}
	},

	/**
	 * Indicates that the module depends on the other by declaring an event listener for an event exposed by the other
	 * module. Thus, the target module does not have to be bootstrapped to run the source one.
	 */
	EVENT_LISTENER {

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.Module.DependencyType#format(org.springframework.modulith.model.FormatableJavaClass, org.springframework.modulith.model.FormatableJavaClass)
		 */
		@Override
		public String format(FormatableType source, FormatableType target) {
			return String.format("%s listening to events of type %s", source.getAbbreviatedFullName(),
					target.getAbbreviatedFullName());
		}
	},

	DEFAULT {

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.Module.DependencyType#or(com.tngtech.archunit.thirdparty.com.google.common.base.Supplier)
		 */
		@Override
		public DependencyType defaultOr(Supplier<DependencyType> supplier) {
			return supplier.get();
		}

		/*
		 * (non-Javadoc)
		 * @see org.springframework.modulith.model.Module.DependencyType#format(org.springframework.modulith.model.FormatableJavaClass, org.springframework.modulith.model.FormatableJavaClass)
		 */
		@Override
		public String format(FormatableType source, FormatableType target) {
			return String.format("%s depending on %s", source.getAbbreviatedFullName(), target.getAbbreviatedFullName());
		}
	};

	/**
	 * Returns the current {@link DependencyType} or obtains the one provided by the given supplier if the current one is
	 * {@link DependencyType#DEFAULT}.
	 *
	 * @param supplier must not be {@literal null}.
	 * @return
	 */
	DependencyType defaultOr(Supplier<DependencyType> supplier) {

		Assert.notNull(supplier, "The fallback supplier must not be null!");

		return this;
	}

	static DependencyType forParameter(JavaClass type) {
		return type.isAnnotatedWith("javax.persistence.Entity") ? ENTITY : DEFAULT;
	}

	static DependencyType forCodeUnit(JavaCodeUnit codeUnit) {
		return Types.isAnnotatedWith(SpringTypes.AT_EVENT_LISTENER).test(codeUnit) //
				|| Types.isAnnotatedWith(JMoleculesTypes.AT_DOMAIN_EVENT_HANDLER).test(codeUnit) //
						? EVENT_LISTENER
						: DEFAULT;
	}

	static DependencyType forDependency(Dependency dependency) {
		return forParameter(dependency.getTargetClass());
	}

	public abstract String format(FormatableType source, FormatableType target);

	/**
	 * Returns all {@link DependencyType}s except the given ones.
	 *
	 * @param types must not be {@literal null}.
	 * @return
	 */
	public static Stream<DependencyType> allBut(Collection<DependencyType> types) {

		Assert.notNull(types, "Types must not be null!");

		Predicate<DependencyType> isIncluded = types::contains;

		return Arrays.stream(values()) //
				.filter(isIncluded.negate());
	}

	public static Stream<DependencyType> allBut(Stream<DependencyType> types) {
		return allBut(types.toList());
	}

	/**
	 * Returns all {@link DependencyType}s except the given ones.
	 *
	 * @param types must not be {@literal null}.
	 * @return
	 */
	public static Stream<DependencyType> allBut(DependencyType... types) {

		Assert.notNull(types, "Types must not be null!");

		return allBut(Arrays.asList(types));
	}
}
