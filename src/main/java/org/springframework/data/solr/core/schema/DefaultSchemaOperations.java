/*
 * Copyright 2016-2020 the original author or authors.
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
package org.springframework.data.solr.core.schema;

import java.util.Map;
import java.io.IOException;

import org.apache.solr.client.api.util.SolrVersion;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.schema.SchemaRequest;
import org.apache.solr.client.solrj.request.schema.SchemaRequest.AddField;
import org.apache.solr.client.solrj.response.schema.SchemaRepresentation;
import org.apache.solr.client.solrj.response.schema.SchemaResponse.UpdateResponse;
import org.apache.solr.common.SolrException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.solr.core.SolrTemplate;
import org.springframework.data.solr.core.schema.SchemaDefinition.CopyFieldDefinition;
import org.springframework.data.solr.core.schema.SchemaDefinition.FieldDefinition;
import org.springframework.data.solr.core.schema.SchemaDefinition.SchemaField;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * {@link SchemaOperations} implementation based on {@link SolrTemplate}.
 *
 * @author Christoph Strobl
 * @since 2.1
 */
public class DefaultSchemaOperations implements SchemaOperations {

	private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSchemaOperations.class);

	private final SolrTemplate template;
	private final String collection;

	public DefaultSchemaOperations(String collection, SolrTemplate template) {

		Assert.hasText(collection, "Collection must not be null or empty");
		Assert.notNull(template, "Template must not be null");

		this.template = template;
		this.collection = collection;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.solr.core.schema.SchemaOperations#getSchemaName()
	 */
	@Override
	public String getSchemaName() {

		return template
				.execute(solrClient -> new SchemaRequest.SchemaName().process(solrClient, collection).getSchemaName());

	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.solr.core.schema.SchemaOperations#getSchemaVersion()
	 */
	@Override
	public Double getSchemaVersion() {
		return template.execute(
				solrClient -> new Double(new SchemaRequest.SchemaVersion().process(solrClient, collection).getSchemaVersion()));
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.solr.core.schema.SchemaOperations#readSchema()
	 */
	@Override
	public SchemaDefinition readSchema() {

		SchemaRepresentation representation = template
				.execute(solrClient -> new SchemaRequest().process(solrClient, collection).getSchemaRepresentation());

		SchemaDefinition sd = new SchemaDefinition(collection);

		for (Map<String, Object> fieldValueMap : representation.getFields()) {
			sd.addFieldDefinition(FieldDefinition.fromMap(fieldValueMap));
		}
		for (Map<String, Object> fieldValueMap : representation.getCopyFields()) {

			CopyFieldDefinition cf = CopyFieldDefinition.fromMap(fieldValueMap);
			sd.addCopyField(cf);

			if (sd.getFieldDefinition(cf.getSource()) != null) {
				sd.getFieldDefinition(cf.getSource()).setCopyFields(cf.getDestination());
			}
		}

		return sd;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.solr.core.schema.SchemaOperations#addField(org.springframework.data.solr.core.schema.SchemaDefinition.SchemaField)
	 */
	@Override
	public void addField(final SchemaField field) {

		if (field instanceof FieldDefinition) {
			addField((FieldDefinition) field);
		} else if (field instanceof CopyFieldDefinition) {
			addCopyField((CopyFieldDefinition) field);
		}
	}

	private void addField(final FieldDefinition field) {

		LOGGER.info("Adding field: " + field.getName() + ", Type: " + field.getType() + ", Attributes: " + field.asMap().toString());

		template.execute(solrClient -> {

			UpdateResponse response = new SchemaRequest.AddField(field.asMap()).process(solrClient, collection);
			if (hasErrors(response)) {
				throw new SchemaModificationException(
						String.format("Adding field %s with args %s to collection %s failed with status %s; Server returned %s",
								field.getName(), field.asMap(), collection, response.getStatus(), response));
			}
			return Integer.valueOf(response.getStatus());
		});

		if (!CollectionUtils.isEmpty(field.getCopyFields())) {

			CopyFieldDefinition cf = new CopyFieldDefinition();
			cf.setSource(field.getName());
			cf.setDestination(field.getCopyFields());

			addCopyField(cf);
		}
	}

	// private int addField(FieldDefinition field) {
	// 	LOGGER.info("Adding field: " + field.getName() + ", Type: " + field.getType() + ", Attributes: " + field.asMap().toString());
	// 	System.out.println("Adding field: " + field.getName() + ", Type: " + field.getType() + ", Attributes: " + field.asMap().toString());
	// 	Map<String, Object> fieldMap = field.asMap();
	// 	LOGGER.debug("Field map: {}", fieldMap);

	// 	try {
	// 		SolrClient solrClient = new Http2SolrClient.Builder("http://localhost:8983/solr").build();
	// 		AddField addField = new AddField(fieldMap);
	// 		UpdateResponse response = addField.process(solrClient, collection);
	// 		LOGGER.debug("AddField response: {}", response);

	// 		if (response.getStatus() != 0) {
	// 			LOGGER.error("AddField failed with status: {}", response.getStatus());
	// 			throw new SolrServerException("AddField failed with status: " + response.getStatus());
	// 		}

	// 		if (response.getException() != null) {
	// 			LOGGER.error("AddField failed with exception: {}", response.getException());
	// 			throw new SolrServerException("AddField failed with exception: " + response.getException());
	// 		}

	// 		return Integer.valueOf(response.getStatus());
	// 	} catch (SolrServerException e) {
	// 		LOGGER.error("AddField failed with exception: {}", e.getMessage());
	// 		// throw e;
	// 	} catch (IOException e) {
	// 		LOGGER.error("AddField failed with exception: {}", e.getMessage());
	// 		// throw new SolrServerException("AddField failed with exception: " + e.getMessage());
	// 	}
	// 	return -1;
	// }

	private void addCopyField(final CopyFieldDefinition field) {

		template.execute(solrClient -> {

			UpdateResponse response = new SchemaRequest.AddCopyField(field.getSource(), field.getDestination())
					.process(solrClient, collection);

			if (hasErrors(response)) {
				throw new SchemaModificationException(String.format(
						"Adding copy field %s with destinations %s to collection %s failed with status %s; Server returned %s",
						field.getSource(), field.getDestination(), collection, response.getStatus(), response));
			}

			return Integer.valueOf(response.getStatus());
		});
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.solr.core.schema.SchemaOperations#removeField(java.lang.String)
	 */
	@Override
	public void removeField(final String name) {

		LOGGER.info("Remove field: " + name);

		template.execute(solrClient -> {

			try {
				UpdateResponse response = new SchemaRequest.DeleteField(name).process(solrClient, collection);
				if (hasErrors(response)) {
					throw new SchemaModificationException(
							String.format("Removing field with name %s from collection %s failed with status %s; Server returned %s",
									name, collection, response.getStatus(), response));
				}

				return Integer.valueOf(response.getStatus());
			} catch (Exception e) {
				throw new SchemaModificationException(
						String.format("Removing field with name %s from collection %s failed", name, collection));
			}
		});
	}

	private boolean hasErrors(UpdateResponse response) {

		if (response.getStatus() != 0
				|| response.getResponse() != null && !CollectionUtils.isEmpty(response.getResponse().getAll("errors"))) {
			return true;
		}

		return false;
	}

}
