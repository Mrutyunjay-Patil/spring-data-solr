/*
 * Copyright 2012-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.solr.core.query.result;

import org.springframework.data.solr.core.query.FilterQuery;
import org.springframework.data.solr.core.query.SimpleQuery;
import org.springframework.data.solr.core.query.SimpleStringCriteria;

/**
 * Trivial implementation of {@link FacetQueryEntry}
 * 
 * @author Christoph Strobl
 */
public class SimpleFacetQueryEntry extends ValueCountEntry implements FacetQueryEntry {

	public SimpleFacetQueryEntry(String value, long count) {
		super(value, count);
	}

	@Override
	public String getKey() {
		return getValue();
	}

	@Override
	public FilterQuery getQuery() {
		return new SimpleQuery(new SimpleStringCriteria(getValue()));
	}

}
