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
package org.springframework.modulith.moments.support;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.lang.Nullable;
import org.springframework.modulith.moments.Quarter;
import org.springframework.modulith.moments.ShiftedQuarter;
import org.springframework.util.Assert;

/**
 * Configuration properties for {@link Moments}.
 *
 * @author Oliver Drotbohm
 */
@ConfigurationProperties(prefix = "spring.modulith.moments")
public class MomentsProperties {

	public static final MomentsProperties DEFAULTS = new MomentsProperties(null, null, null, (Month) null, false);

	private final Granularity granularity;
	private final ZoneId zoneId;
	private final Locale locale;

	private final boolean enableTimeMachine;

	private final ShiftedQuarters quarters;

	/**
	 * Creates a new {@link MomentsProperties} for the given {@link Granularity}, {@link ZoneId}, {@link Locale} and
	 * quarter start {@link Month}.
	 *
	 * @param granularity can be {@literal null}, defaults to {@value Granularity#HOURS}.
	 * @param zoneId the time zone id to use, defaults to {@code UTC}.
	 * @param locale
	 * @param quarterStartMonth the {@link Month} at which quarters start. Defaults to {@value Month#JANUARY}, resulting
	 *          in {@link ShiftedQuarter}s without any shift.
	 */
	@ConstructorBinding
	private MomentsProperties(@Nullable @DefaultValue("hours") Granularity granularity,
			@Nullable ZoneId zoneId, @Nullable Locale locale, @Nullable Month quarterStartMonth,
			@DefaultValue("false") boolean enableTimeMachine) {

		this.granularity = granularity == null ? Granularity.HOURS : granularity;
		this.zoneId = zoneId == null ? ZoneOffset.UTC : zoneId;
		this.locale = locale == null ? Locale.getDefault() : locale;
		this.quarters = ShiftedQuarters.of(quarterStartMonth == null ? Month.JANUARY : quarterStartMonth);
		this.enableTimeMachine = enableTimeMachine;
	}

	/**
	 * Creates a new {@link MomentsProperties} for the given {@link Granularity}, {@link ZoneId}, {@link Locale}, whether
	 * to enable the {@link TimeMachine} and {@link ShiftedQuarters}.
	 *
	 * @param granularity must not be {@literal null}.
	 * @param zoneId must not be {@literal null}.
	 * @param locale must not be {@literal null}.
	 * @param enableTimeMachine
	 * @param quarters must not be {@literal null}.
	 */
	private MomentsProperties(Granularity granularity, ZoneId zoneId, Locale locale, boolean enableTimeMachine,
			ShiftedQuarters quarters) {

		Assert.notNull(granularity, "Granilarity must not be null!");
		Assert.notNull(zoneId, "ZoneId must not be null!");
		Assert.notNull(locale, "Locale must not be null!");
		Assert.notNull(quarters, "ShiftedQuarters must not be null!");

		this.granularity = granularity;
		this.zoneId = zoneId;
		this.locale = locale;
		this.enableTimeMachine = enableTimeMachine;
		this.quarters = quarters;
	}

	/**
	 * The {@link ZoneId} to determine times which are attached to the events published. Defaults to
	 * {@link ZoneOffset#UTC}.
	 *
	 * @return will never be {@literal null}.
	 */
	public ZoneId getZoneId() {
		return zoneId;
	}

	/**
	 * The {@link Locale} to use when determining week boundaries. Defaults to {@link Locale#getDefault()}.
	 *
	 * @return will never be {@literal null}.
	 */
	public Locale getLocale() {
		return locale;
	}

	/**
	 * Whether to enable the {@link TimeMachine}.
	 */
	public boolean isEnableTimeMachine() {
		return enableTimeMachine;
	}

	/**
	 * Returns whether to create hourly events.
	 */
	boolean isHourly() {
		return Granularity.HOURS.equals(granularity);
	}

	/**
	 * Returns the {@link ShiftedQuarter} for the given reference date.
	 *
	 * @param reference must not be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	public ShiftedQuarter getShiftedQuarter(LocalDate reference) {

		Assert.notNull(reference, "Reference date must not be null!");

		return quarters.getCurrent(reference);
	}

	MomentsProperties withGranularity(Granularity granularity) {
		return new MomentsProperties(granularity, zoneId, locale, enableTimeMachine, quarters);
	}

	MomentsProperties withZoneId(ZoneId zoneId) {
		return new MomentsProperties(granularity, zoneId, locale, enableTimeMachine, quarters);
	}

	MomentsProperties withLocale(Locale locale) {
		return new MomentsProperties(granularity, zoneId, locale, enableTimeMachine, quarters);
	}

	/**
	 * The granularity of events to publish.
	 *
	 * @author Oliver Drotbohm
	 */
	static enum Granularity {

		/**
		 * Publish hourly events. Will include daily events.
		 */
		HOURS,

		/**
		 * Publish daily events only.
		 */
		DAYS;
	}

	private static class ShiftedQuarters {

		private final List<ShiftedQuarter> quarters;

		private ShiftedQuarters(List<ShiftedQuarter> quarters) {
			this.quarters = quarters;
		}

		public ShiftedQuarter getCurrent(LocalDate reference) {

			return quarters.stream()
					.filter(it -> it.contains(reference))
					.findFirst()
					.orElseThrow(() -> new IllegalStateException());
		}

		public static ShiftedQuarters of(Month shift) {

			return new ShiftedQuarters(Arrays.stream(Quarter.values())
					.map(it -> ShiftedQuarter.of(it, shift))
					.toList());
		}
	}
}
