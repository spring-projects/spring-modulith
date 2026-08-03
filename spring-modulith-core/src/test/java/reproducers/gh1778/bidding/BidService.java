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
package reproducers.gh1778.bidding;

import reproducers.gh1778.review.analysis.AnalyzerService;

/**
 * Depends on a non-exposed type of the shared review module.
 *
 * @author Burak Kalayci
 */
public class BidService {

	private final AnalyzerService analyzerService;

	public BidService(AnalyzerService analyzerService) {
		this.analyzerService = analyzerService;
	}
}
